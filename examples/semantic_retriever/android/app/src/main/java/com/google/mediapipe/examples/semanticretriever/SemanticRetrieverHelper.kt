/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.semanticretriever

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.components.AppSearchVectorStore
import com.google.mediapipe.tasks.retrieval.components.SqliteVectorStore
import com.google.mediapipe.tasks.retrieval.semanticretriever.RetrievalResult
import com.google.mediapipe.tasks.retrieval.semanticretriever.SemanticRetriever
import com.google.mediapipe.tasks.retrieval.semanticretriever.SemanticRetrieverComponents
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File

/**
 * Helper managing [UniversalEmbedder], [SemanticRetriever], and pluggable vector stores
 * ([AppSearchVectorStore] or [SqliteVectorStore]) on CPU or GPU.
 */
class SemanticRetrieverHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentStore: Int = STORE_SQLITE,
    var listener: RetrieverListener? = null,
    private val sqliteDbName: String = DEFAULT_SQLITE_DB,
    private val appSearchDbName: String = DEFAULT_APPSEARCH_DB,
) {
    private var embedder: UniversalEmbedder? = null
    private var semanticRetriever: SemanticRetriever? = null

    /** Duration of the most recent successful [setupSemanticRetriever] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isReady: Boolean
        get() = semanticRetriever != null

    /**
     * Initializes [UniversalEmbedder] and [SemanticRetriever] with the selected delegate and vector
     * store. Returns `true` on success; reports failures to [listener] and returns `false`.
     */
    fun setupSemanticRetriever(): Boolean {
        clearSemanticRetriever()
        val startTime = SystemClock.uptimeMillis()

        val delegate =
            when (currentDelegate) {
                DELEGATE_CPU -> Delegate.CPU
                DELEGATE_GPU -> Delegate.GPU
                else -> {
                    listener?.onError("Unsupported delegate index: $currentDelegate")
                    return false
                }
            }

        return try {
            val modelPath = resolveModelPath(MODEL_FILE_NAME)
            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(modelPath)
                    .setDelegate(delegate)
                    .build()
            val embedderOptions =
                UniversalEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setTextDelegate(delegate)
                    .setVisionDelegate(delegate)
                    .setL2Normalize(true)
                    .setCacheDir(context.cacheDir.absolutePath)
                    .build()

            val createdEmbedder = UniversalEmbedder.createFromOptions(context, embedderOptions)
            val vectorStore =
                when (currentStore) {
                    STORE_APPSEARCH -> AppSearchVectorStore(context, appSearchDbName)
                    STORE_SQLITE ->
                        SqliteVectorStore(
                            context,
                            File(context.filesDir, sqliteDbName).absolutePath,
                            EMBEDDING_DIMENSION,
                        )
                    else -> throw IllegalArgumentException("Unsupported store index: $currentStore")
                }

            val components =
                SemanticRetrieverComponents()
                    .setVectorStore(vectorStore)
                    .addProvider(createdEmbedder.getProvider())

            val createdRetriever = SemanticRetriever.createFromComponents(context, components)
            embedder = createdEmbedder
            semanticRetriever = createdRetriever
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (t: Throwable) {
            clearSemanticRetriever()
            val detail = t.message ?: t::class.java.simpleName
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            Log.e(
                TAG,
                "Failed to initialize SemanticRetriever on delegate=$delegate store=$currentStore: $detail",
                t,
            )
            listener?.onError(
                if (errorCode == GPU_ERROR) {
                    "GPU delegate is unavailable on this device: $detail"
                } else {
                    "Semantic retriever failed to initialize: $detail"
                },
                errorCode,
            )
            false
        }
    }

    /** Inserts an image at [imageUri] with [id] and optional [metadata]. */
    fun insertImage(
        id: String,
        imageUri: Uri,
        metadata: Map<String, String> = emptyMap(),
    ) {
        val retriever = semanticRetriever ?: error("SemanticRetriever is not initialized")
        retriever.insertImage(id, imageUri, metadata)
    }

    /** Returns all currently indexed record IDs, or empty if uninitialized. */
    fun getAllRecordIds(): List<String> = semanticRetriever?.getAllRecordIds().orEmpty()

    /** Deletes all records from the active vector store. */
    fun clearIndex() {
        semanticRetriever?.deleteAll()
    }

    /** Queries the vector store with natural-language [query], returning top [topK] results and timing. */
    fun retrieve(query: String, topK: Int = 5): SearchBundle? {
        val retriever = semanticRetriever ?: return null
        val startTime = SystemClock.uptimeMillis()
        val results = retriever.retrieve(query, topK)
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
        return SearchBundle(
            results = results,
            inferenceTime = inferenceTimeMs,
            loadTimeMs = setupDurationMs,
        )
    }

    private fun resolveModelPath(fileName: String): String {
        val externalCopy =
            listOfNotNull(
                    File("/data/local/tmp/semantic_retriever_models", fileName),
                    context.getExternalFilesDir(null)?.let { File(it, fileName) },
                )
                .firstOrNull { it.canRead() && it.length() > 0L }
        if (externalCopy != null) return externalCopy.absolutePath

        if (context.assets.list("")?.contains(fileName) != true) {
            throw IllegalStateException(
                "$fileName not found in assets. Run ./gradlew :app:verifyModels to download it."
            )
        }
        val dest = File(context.filesDir, fileName)
        val appUpdatedAt =
            runCatching {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .lastUpdateTime
                }
                .getOrDefault(0L)
        if (!dest.exists() || dest.length() == 0L || dest.lastModified() < appUpdatedAt) {
            Log.i(TAG, "Copying $fileName to ${dest.absolutePath}")
            val tmp = File(context.filesDir, "$fileName.tmp")
            context.assets.open(fileName).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(dest)) {
                tmp.delete()
                throw IllegalStateException("Could not extract $fileName to app storage.")
            }
        }
        return dest.absolutePath
    }

    fun clearSemanticRetriever() {
        runCatching { semanticRetriever?.close() }
        semanticRetriever = null
        runCatching { embedder?.close() }
        embedder = null
    }

    data class SearchBundle(
        val results: List<RetrievalResult>,
        val inferenceTime: Long,
        val loadTimeMs: Long = 0L,
    )

    interface RetrieverListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val STORE_APPSEARCH = 0
        const val STORE_SQLITE = 1
        const val MODEL_FILE_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
        const val EMBEDDING_DIMENSION = 768
        const val DEFAULT_SQLITE_DB = "semantic_retriever_vectors.db"
        const val DEFAULT_APPSEARCH_DB = "semantic_retriever_appsearch"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        private const val TAG = "SemanticRetrieverHelper"

        /**
         * Returns true on Android emulators where SwiftShader software Vulkan initializes without
         * throwing yet times out (`VK_TIMEOUT`) during multimodal EmbeddingGemma inference.
         */
        fun isAndroidEmulator(): Boolean {
            val fp = Build.FINGERPRINT
            val hw = Build.HARDWARE
            val model = Build.MODEL
            val product = Build.PRODUCT
            return fp.startsWith("generic") ||
                fp.contains("emulator", ignoreCase = true) ||
                hw.contains("goldfish") ||
                hw.contains("ranchu") ||
                model.contains("Emulator") ||
                model.contains("Android SDK built for") ||
                product.contains("sdk_gphone") ||
                product.contains("emulator")
        }

        fun defaultDelegate(): Int = if (isAndroidEmulator()) DELEGATE_CPU else DELEGATE_GPU
    }
}
