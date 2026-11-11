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

package com.google.mediapipe.examples.universalembedder

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File

/**
 * Helper managing [UniversalEmbedder] initialization, multimodal text/image embedding extraction,
 * and cosine similarity computation on CPU or GPU.
 */
class UniversalEmbedderHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var listener: EmbedderListener? = null,
) {
    private var universalEmbedder: UniversalEmbedder? = null

    /** Duration of the most recent successful [setupUniversalEmbedder] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isReady: Boolean
        get() = universalEmbedder != null

    /**
     * Initializes [UniversalEmbedder] on [currentDelegate]. Returns `true` on success; invokes
     * [listener] and returns `false` on error.
     */
    fun setupUniversalEmbedder(): Boolean {
        clearUniversalEmbedder()
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
            val options =
                UniversalEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setTextDelegate(delegate)
                    .setVisionDelegate(delegate)
                    .setL2Normalize(true)
                    .setCacheDir(context.cacheDir.absolutePath)
                    .build()
            universalEmbedder = UniversalEmbedder.createFromOptions(context, options)
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (t: Throwable) {
            clearUniversalEmbedder()
            val detail = t.message ?: t::class.java.simpleName
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            Log.e(TAG, "Failed to initialize UniversalEmbedder on delegate=$delegate: $detail", t)
            listener?.onError(
                if (errorCode == GPU_ERROR) {
                    "GPU delegate is unavailable on this device: $detail"
                } else {
                    "Universal embedder failed to initialize: $detail"
                },
                errorCode,
            )
            false
        }
    }

    /** Embeds a single text string into the shared multimodal vector space. */
    fun embedText(text: String): Embedding? =
        universalEmbedder?.embedText(text)?.embeddings()?.firstOrNull()

    /** Embeds an image [Bitmap] into the shared multimodal vector space. */
    fun embedImage(bitmap: Bitmap): Embedding? {
        val embedder = universalEmbedder ?: return null
        val rgba =
            if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        // Do not close Bitmap-backed MPImage here because MPImage.close() recycles the underlying Bitmap.
        val mpImage = BitmapImageBuilder(rgba).build()
        return embedder.embedImage(mpImage).embeddings().firstOrNull()
    }

    /** Embeds two inputs (text or image) and returns their cosine similarity and latency. */
    fun compare(first: Input, second: Input): ResultBundle? {
        if (universalEmbedder == null) return null
        val startTime = SystemClock.uptimeMillis()
        val firstEmbed = embedInput(first) ?: return null
        val secondEmbed = embedInput(second) ?: return null
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
        val similarity = UniversalEmbedder.cosineSimilarity(firstEmbed, secondEmbed)
        return ResultBundle(
            similarity = similarity,
            inferenceTime = inferenceTimeMs,
            dimensions = firstEmbed.floatEmbedding().size,
            loadTimeMs = setupDurationMs,
        )
    }

    private fun embedInput(input: Input): Embedding? =
        when (input) {
            is Input.Text -> embedText(input.value)
            is Input.Image -> embedImage(input.bitmap)
        }

    /**
     * Resolves [fileName] from pushed device storage or copies it from APK assets into internal
     * app storage so LiteRT-LM can memory-map a real filesystem path.
     */
    private fun resolveModelPath(fileName: String): String {
        val externalCopy =
            listOfNotNull(
                    File("/data/local/tmp/universal_embedder_models", fileName),
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

    fun clearUniversalEmbedder() {
        universalEmbedder?.close()
        universalEmbedder = null
    }

    sealed interface Input {
        data class Text(val value: String) : Input
        data class Image(val bitmap: Bitmap) : Input
    }

    data class ResultBundle(
        val similarity: Double,
        val inferenceTime: Long,
        val dimensions: Int = 0,
        val loadTimeMs: Long = 0L,
    )

    interface EmbedderListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_FILE_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        private const val TAG = "UniversalEmbedderHelper"

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
