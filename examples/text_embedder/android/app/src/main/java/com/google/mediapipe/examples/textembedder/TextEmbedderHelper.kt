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

package com.google.mediapipe.examples.textembedder

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextFormatContext

/**
 * Helper that wraps [TextEmbedder] lifecycle, delegate/model configuration, and cosine similarity
 * computation.
 */
class TextEmbedderHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_MOBILE_BERT,
    var listener: EmbedderListener? = null,
) {
    private var textEmbedder: TextEmbedder? = null

    /** Duration of the most recent successful [setupTextEmbedder] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isReady: Boolean
        get() = textEmbedder != null

    init {
        setupTextEmbedder()
    }

    fun setupTextEmbedder(): Boolean {
        clearTextEmbedder()
        val startTime = SystemClock.uptimeMillis()

        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> {
                listener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }
        when (currentModel) {
            MODEL_MOBILE_BERT -> baseOptionsBuilder.setModelAssetPath(MODEL_MOBILE_BERT_PATH)
            MODEL_UNIVERSAL_SENTENCE_ENCODER ->
                baseOptionsBuilder.setModelAssetPath(MODEL_UNIVERSAL_SENTENCE_ENCODER_PATH)
            MODEL_EMBEDDING_GEMMA -> baseOptionsBuilder.setModelAssetPath(MODEL_EMBEDDING_GEMMA_PATH)
            else -> {
                listener?.onError("Unsupported model index: $currentModel")
                return false
            }
        }

        return try {
            val baseOptions = baseOptionsBuilder.build()
            val options = TextEmbedderOptions.builder().setBaseOptions(baseOptions).build()
            textEmbedder = TextEmbedder.createFromOptions(context, options)
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            listener?.onError("Text embedder failed to initialize: $detail")
            Log.e(TAG, "Text embedder failed to load model with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            // Occurs if the selected model or device does not support the GPU delegate.
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            listener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Text embedder failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Text embedder failed to load model with error: $detail", e)
            false
        }
    }

    /**
     * Embeds a single string with optional [TextFormatContext] (supported by EmbeddingGemma),
     * returning the primary [Embedding] or `null` if not initialized.
     */
    fun embed(
        text: String,
        formatContext: TextFormatContext? = null,
    ): Embedding? {
        val embedder = textEmbedder ?: return null
        val result =
            if (formatContext != null) embedder.embed(text, formatContext)
            else embedder.embed(text)
        return result.embeddingResult().embeddings().firstOrNull()
    }

    /**
     * Computes cosine similarity and latency for comparing [firstText] and [secondText],
     * applying optional [TextFormatContext] prompts when provided.
     */
    fun compare(
        firstText: String,
        secondText: String,
        firstFormatContext: TextFormatContext? = null,
        secondFormatContext: TextFormatContext? = null,
    ): ResultBundle? {
        val embedder = textEmbedder ?: return null
        val startTime = SystemClock.uptimeMillis()
        val firstResult =
            if (firstFormatContext != null) embedder.embed(firstText, firstFormatContext)
            else embedder.embed(firstText)
        val secondResult =
            if (secondFormatContext != null) embedder.embed(secondText, secondFormatContext)
            else embedder.embed(secondText)
        val firstEmbed = firstResult.embeddingResult().embeddings().first()
        val secondEmbed = secondResult.embeddingResult().embeddings().first()
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
        val dims = firstEmbed.floatEmbedding().size
        return ResultBundle(
            similarity = TextEmbedder.cosineSimilarity(firstEmbed, secondEmbed),
            inferenceTime = inferenceTimeMs,
            dimensions = dims,
            loadTimeMs = setupDurationMs,
        )
    }

    fun clearTextEmbedder() {
        textEmbedder?.close()
        textEmbedder = null
    }

    /** Wraps cosine similarity, inference timing, and embedding dimensionality. */
    data class ResultBundle(
        val similarity: Double,
        val inferenceTime: Long,
        val dimensions: Int = 0,
        val loadTimeMs: Long = 0L,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_MOBILE_BERT = 0
        const val MODEL_UNIVERSAL_SENTENCE_ENCODER = 1
        const val MODEL_EMBEDDING_GEMMA = 2
        const val MODEL_MOBILE_BERT_PATH = "mobile_bert.tflite"
        const val MODEL_UNIVERSAL_SENTENCE_ENCODER_PATH = "universal_sentence_encoder.tflite"
        const val MODEL_EMBEDDING_GEMMA_PATH = "embedding_gemma.task"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        private const val TAG = "TextEmbedderHelper"
    }

    interface EmbedderListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }
}
