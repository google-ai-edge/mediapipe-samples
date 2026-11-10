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

package com.google.mediapipe.examples.textclassifier

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier.TextClassifierOptions
import java.util.Locale

/** Helper that wraps [TextClassifier] lifecycle, model selection, and sentiment inference. */
class TextClassifierHelper(
    var currentModel: String = MOBILEBERT,
    val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var listener: TextResultsListener? = null,
) {
    private var textClassifier: TextClassifier? = null

    /** Duration of the most recent successful [initClassifier] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isReady: Boolean
        get() = textClassifier != null

    init {
        initClassifier()
    }

    fun initClassifier(): Boolean {
        clearTextClassifier()
        val startTime = SystemClock.uptimeMillis()

        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(currentModel)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> {
                listener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        return try {
            val baseOptions = baseOptionsBuilder.build()
            val options = TextClassifierOptions.builder().setBaseOptions(baseOptions).build()
            textClassifier = TextClassifier.createFromOptions(context, options)
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            listener?.onError("Text classifier failed to initialize: $detail")
            Log.e(TAG, "Text classifier failed to load the task with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            listener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Text classifier failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Text classifier failed to load the task with error: $detail", e)
            false
        }
    }

    /** Classifies [text] synchronously and returns sorted sentiment predictions and timing. */
    fun classify(text: String): ResultBundle? {
        val classifier = textClassifier ?: return null
        val startTime = SystemClock.uptimeMillis()
        val result = classifier.classify(text)
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime

        val categories =
            result
                .classificationResult()
                .classifications()
                .firstOrNull()
                ?.categories()
                ?.sortedByDescending { it.score() }
                ?.map { category ->
                    CategoryPrediction(
                        rawLabel = category.categoryName(),
                        displayLabel = normalizeLabel(currentModel, category.categoryName()),
                        score = category.score(),
                    )
                }
                .orEmpty()

        return ResultBundle(
            categories = categories,
            inferenceTime = inferenceTimeMs,
            loadTimeMs = setupDurationMs,
        )
    }

    fun clearTextClassifier() {
        textClassifier?.close()
        textClassifier = null
    }

    /** A single predicted category with normalized UI label and confidence in `[0, 1]`. */
    data class CategoryPrediction(
        val rawLabel: String,
        val displayLabel: String,
        val score: Float,
    )

    /** Wraps ranked category predictions and timing telemetry from a [classify] invocation. */
    data class ResultBundle(
        val categories: List<CategoryPrediction>,
        val inferenceTime: Long,
        val loadTimeMs: Long = 0L,
    )

    interface TextResultsListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }

    companion object {
        const val TAG = "TextClassifierHelper"
        const val WORD_VEC = "wordvec.tflite"
        const val MOBILEBERT = "mobilebert.tflite"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        internal fun normalizeLabel(model: String, rawLabel: String): String {
            val mapped =
                if (model == WORD_VEC) {
                    when (rawLabel.trim()) {
                        "1" -> "Positive"
                        "0" -> "Negative"
                        else -> rawLabel
                    }
                } else {
                    rawLabel
                }
            return mapped.trim().replaceFirstChar { ch ->
                if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
            }
        }
    }
}
