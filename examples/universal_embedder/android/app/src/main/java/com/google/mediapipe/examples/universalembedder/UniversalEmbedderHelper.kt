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

package com.google.mediapipe.examples.universalembedder

import android.content.Context
import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedder
import com.google.mediapipe.tasks.retrieval.universalembedder.UniversalEmbedderOptions
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream

/** An embedding plus how long it took to produce. */
data class EmbeddingRun(val embedding: Embedding, val millis: Long)

/**
 * Thin wrapper around [UniversalEmbedder] ([MODEL_URL]).
 *
 * The embedder maps text, images and audio into a single shared vector space, which is what makes
 * cross-modal comparison ("does this sentence describe this picture?") possible.
 *
 * Not thread safe: callers must serialize access (see the single-thread dispatcher in
 * [MainActivity]).
 */
class UniversalEmbedderHelper(private val context: Context) {

    private var embedder: UniversalEmbedder? = null
    private var currentlyOnGpu = false

    val isReady: Boolean
        get() = embedder != null

    /** Builds the engine, or rebuilds it if the requested accelerator changed. */
    fun prepare(useGpu: Boolean) {
        if (embedder != null && currentlyOnGpu == useGpu) return
        embedder?.close()
        embedder = null

        // Copy the model from APK assets into filesDir on first launch so we can open a standalone
        // ParcelFileDescriptor for BaseOptions.setModelAssetFileDescriptor.
        val modelFile = File(context.filesDir, MODEL_NAME)
        if (!modelFile.exists()) {
            Log.i(TAG, "Copying $MODEL_NAME from assets to filesDir…")
            try {
                context.assets.open(MODEL_NAME).use { input ->
                    FileOutputStream(modelFile).use { output -> input.copyTo(output) }
                }
            } catch (e: FileNotFoundException) {
                throw IllegalStateException(
                    "Model $MODEL_NAME not found in assets. Download it from $MODEL_URL and " +
                        "place it in app/src/main/assets/$MODEL_NAME.",
                    e
                )
            }
        }

        val delegate = if (useGpu) Delegate.GPU else Delegate.CPU
        val startedAt = System.currentTimeMillis()
        embedder =
            ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                val options = UniversalEmbedderOptions.builder()
                    .setBaseOptions(
                        BaseOptions.builder()
                            .setModelAssetFileDescriptor(pfd.fd)
                            .setDelegate(delegate)
                            .build()
                    )
                    .setCacheDir(context.cacheDir.absolutePath)
                    .setL2Normalize(true)
                    .build()

                UniversalEmbedder.createFromOptions(context, options)
            }
        currentlyOnGpu = useGpu
        Log.i(TAG, "Embedder ready on $delegate in ${System.currentTimeMillis() - startedAt}ms")
    }

    fun embedText(text: String): EmbeddingRun {
        val engine = checkNotNull(embedder) { "prepare() must be called first" }
        val startedAt = System.currentTimeMillis()
        val result = engine.embedText(text)
        return EmbeddingRun(result.embeddings().first(), System.currentTimeMillis() - startedAt)
    }

    fun embedImage(bitmap: Bitmap): EmbeddingRun {
        val engine = checkNotNull(embedder) { "prepare() must be called first" }
        val image = BitmapImageBuilder(bitmap).build()
        val startedAt = System.currentTimeMillis()
        val result = engine.embedImage(image)
        return EmbeddingRun(result.embeddings().first(), System.currentTimeMillis() - startedAt)
    }

    fun close() {
        embedder?.close()
        embedder = null
    }

    companion object {
        private const val TAG = "UniversalEmbedder"
        private const val MODEL_NAME = "embeddinggemma-2-text-vision-440m.litertlm"
        private const val MODEL_URL =
            "https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/" +
                "resolve/main/embeddinggemma-2-text-vision-440m.litertlm"

        /** Cosine similarity, in [-1, 1]. Identical inputs score 1. */
        fun similarity(a: Embedding, b: Embedding): Double =
            UniversalEmbedder.cosineSimilarity(a, b)
    }
}
