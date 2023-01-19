/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
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
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions

class TextEmbedderHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_MOBILE_BERT,
    var listener: EmbedderListener? = null
) {
    private var textEmbedder: TextEmbedder? = null

    init {
        setupTextEmbedder()
    }

    fun setupTextEmbedder() {
        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }
        when (currentModel) {
            MODEL_MOBILE_BERT -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_MOBILE_BERT_PATH)
            }
            MODEL_AVERAGE_WORD -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_AVERAGE_WORD_PATH)
            }
        }
        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder =
                TextEmbedderOptions.builder().setBaseOptions(baseOptions)
            val options = optionsBuilder.build()
            textEmbedder = TextEmbedder.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            listener?.onError(
                "Text embedder failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG,
                "Text embedder failed to load model with error: " + e.message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            listener?.onError(
                "Text embedder failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Text embedder failed to load model with error: " + e.message
            )
        }
    }

    //  If both the vectors are aligned, the angle between them
    //  will be 0. cos 0 = 1. So, mathematically, this distance metric will
    //  be used to find the most similar text.
    fun compare(firstText: String, secondText: String): ResultBundle? {
        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        textEmbedder?.let {
            val firstEmbed =
                it.embed(firstText).embeddingResult().embeddings().first()
            val secondEmbed =
                it.embed(secondText).embeddingResult().embeddings().first()
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                TextEmbedder.cosineSimilarity(firstEmbed, secondEmbed),
                inferenceTimeMs
            )
        }
        return null
    }

    fun clearTextEmbedder() {
        textEmbedder?.close()
        textEmbedder = null
    }

    // Wraps results from inference, the time it takes for inference to be
    // performed.
    data class ResultBundle(
        val similarity: Double,
        val inferenceTime: Long,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_MOBILE_BERT = 0
        const val MODEL_AVERAGE_WORD = 1
        const val MODEL_MOBILE_BERT_PATH = "mobile_bert.tflite"
        const val MODEL_AVERAGE_WORD_PATH = "average_word.tflite"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        private const val TAG = "TextEmbedderHelper"
    }

    interface EmbedderListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }
}
