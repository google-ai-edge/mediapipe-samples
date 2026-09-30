/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
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
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.components.AppSearchVectorStore
import com.google.mediapipe.tasks.retrieval.components.SqliteVectorStore
import com.google.mediapipe.tasks.retrieval.components.VectorStore
import com.google.mediapipe.tasks.retrieval.semanticretriever.RetrievalResult
import com.google.mediapipe.tasks.retrieval.semanticretriever.SemanticRetriever
import com.google.mediapipe.tasks.retrieval.semanticretriever.SemanticRetrieverComponents
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/**
 * Owns the embedding engine and the currently selected vector store.
 *
 * The [UniversalEmbedder] wraps a ~470 MB LiteRT-LM model ([MODEL_URL]), so it is created exactly
 * once and kept alive for the lifetime of the helper. Switching vector stores only swaps the cheap
 * [SemanticRetriever] / [VectorStore] pair on top of that same embedder — tearing the engine down
 * and standing a second one up would either fail to mmap the model file or leave callers holding a
 * closed engine.
 *
 * This class is not thread safe: callers must serialize access (see the single-thread dispatcher
 * in [MainActivity]).
 */
class SemanticRetrieverHelper(private val context: Context) {

    private var universalEmbedder: UniversalEmbedder? = null
    private var semanticRetriever: SemanticRetriever? = null
    private var currentlyOnGpu = false

    /** True once a vector store has been opened and the helper can accept work. */
    val isReady: Boolean
        get() = semanticRetriever != null

    /**
     * Loads the embedding model. Safe to call repeatedly; the engine is only built the first time.
     */
    fun prepareEmbedder(useGpu: Boolean = false) {
        if (universalEmbedder != null && currentlyOnGpu == useGpu) return

        // The accelerator is baked into the engine, so changing it means rebuilding it.
        if (universalEmbedder != null) {
            semanticRetriever?.close()
            semanticRetriever = null
            universalEmbedder?.close()
            universalEmbedder = null
        }

        // Copy the model from APK assets into filesDir on first launch so we can open a standalone
        // ParcelFileDescriptor for BaseOptions.setModelAssetFileDescriptor.
        val modelFile = File(context.filesDir, MODEL_NAME)
        if (!modelFile.exists()) {
            Log.i(TAG, "Copying $MODEL_NAME from assets to filesDir…")
            try {
                context.assets.open(MODEL_NAME).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: FileNotFoundException) {
                throw IllegalStateException(
                    "Model $MODEL_NAME not found in assets. Download it from $MODEL_URL and " +
                        "place it in app/src/main/assets/$MODEL_NAME.",
                    e
                )
            }
            Log.i(TAG, "Model copied (${modelFile.length()} bytes).")
        }

        val delegate = if (useGpu) Delegate.GPU else Delegate.CPU
        val startedAt = System.currentTimeMillis()
        universalEmbedder =
            ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val baseOptions = BaseOptions.builder()
                    .setModelAssetFileDescriptor(pfd.fd)
                    .setDelegate(delegate)
                    .build()

                val embedderOptions = UniversalEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setCacheDir(context.cacheDir.absolutePath)
                    .build()

                UniversalEmbedder.createFromOptions(context, embedderOptions)
            }
        currentlyOnGpu = useGpu
        val elapsed = System.currentTimeMillis() - startedAt
        Log.i(TAG, "Embedding engine ready on $delegate in ${elapsed}ms")
    }

    /**
     * Closes the previous store (if any) and opens [useAppSearch] ? AppSearch : SQLite, reusing the
     * already loaded embedding engine.
     */
    fun openStore(useAppSearch: Boolean) {
        val embedder = requireNotNull(universalEmbedder) {
            "prepareEmbedder() must be called before openStore()"
        }

        // SemanticRetriever.close() closes the vector store it was built with, and leaves the
        // embedder untouched — which is exactly what we want here.
        semanticRetriever?.close()
        semanticRetriever = null

        val vectorStore: VectorStore = if (useAppSearch) {
            AppSearchVectorStore(context, DB_NAME)
        } else {
            SqliteVectorStore(context, DB_NAME, 768)
        }

        val components = SemanticRetrieverComponents()
            .setVectorStore(vectorStore)
            .addProvider(embedder.provider)

        semanticRetriever = SemanticRetriever.createFromComponents(context, components)
        Log.i(TAG, "Opened ${if (useAppSearch) "AppSearch" else "SQLite"} vector store.")
    }

    /**
     * Drops [ids] from the open store. Used to reset state when switching backends, which is safer
     * than deleting the database files out from under a live store.
     */
    fun clear(ids: List<String>) {
        semanticRetriever?.delete(ids)
    }

    fun embedImage(id: String, uri: Uri) {
        checkNotNull(semanticRetriever) { "No vector store is open" }.insertImage(id, uri)
    }

    fun searchImages(query: String, limit: Int = 5): List<RetrievalResult> {
        return semanticRetriever?.retrieve(query, limit) ?: emptyList()
    }

    fun close() {
        semanticRetriever?.close()
        semanticRetriever = null
        universalEmbedder?.close()
        universalEmbedder = null
    }

    private companion object {
        const val TAG = "SemanticRetriever"
        const val MODEL_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/" +
                "resolve/main/embeddinggemma-2-text-vision-440m.litertlm"
        const val DB_NAME = "semantic_db"
    }
}
