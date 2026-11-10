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

package com.google.mediapipe.examples.languagedetector

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.text.languagedetector.LanguageDetector
import com.google.mediapipe.tasks.text.languagedetector.LanguageDetector.LanguageDetectorOptions
import java.util.Locale

/** Helper that wraps [LanguageDetector] lifecycle, options, and language inference. */
class LanguageDetectorHelper(
    val context: Context,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var scoreThreshold: Float = THRESHOLD_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var listener: TextResultsListener? = null,
) {
    private var languageDetector: LanguageDetector? = null

    /** Duration of the most recent successful [initDetector] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isReady: Boolean
        get() = languageDetector != null

    init {
        initDetector()
    }

    fun initDetector(): Boolean {
        clearLanguageDetector()
        val startTime = SystemClock.uptimeMillis()

        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(MODEL_DETECTOR)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> {
                listener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        return try {
            val optionsBuilder =
                LanguageDetectorOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setMaxResults(maxResults)
                    .setScoreThreshold(scoreThreshold)
            languageDetector = LanguageDetector.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            listener?.onError("Language detector failed to initialize: $detail")
            Log.e(TAG, "Language detector failed to load the task with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            listener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Language detector failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Language detector failed to load the task with error: $detail", e)
            false
        }
    }

    /** Detects candidate languages for [text] synchronously and returns sorted predictions. */
    fun detect(text: String): ResultBundle? {
        val detector = languageDetector ?: return null
        val startTime = SystemClock.uptimeMillis()
        val result = detector.detect(text)
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime

        val predictions =
            result
                .languagesAndScores()
                .sortedByDescending { it.probability() }
                .map { prediction ->
                    LanguagePredictionUi(
                        languageCode = prediction.languageCode(),
                        displayName = resolveLanguageName(prediction.languageCode()),
                        probability = prediction.probability(),
                    )
                }

        return ResultBundle(
            predictions = predictions,
            inferenceTime = inferenceTimeMs,
            loadTimeMs = setupDurationMs,
        )
    }

    fun clearLanguageDetector() {
        languageDetector?.close()
        languageDetector = null
    }

    /** A single predicted language with BCP-47 code, human-readable name, and score. */
    data class LanguagePredictionUi(
        val languageCode: String,
        val displayName: String,
        val probability: Float,
    )

    /** Wraps ranked language predictions and timing telemetry from a [detect] invocation. */
    data class ResultBundle(
        val predictions: List<LanguagePredictionUi>,
        val inferenceTime: Long,
        val loadTimeMs: Long = 0L,
    )

    interface TextResultsListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }

    companion object {
        const val TAG = "LanguageDetectorHelper"
        const val MODEL_DETECTOR = "detection_model.tflite"
        const val MAX_RESULTS_DEFAULT = 5
        const val THRESHOLD_DEFAULT = 0.001f
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        internal fun resolveLanguageName(rawCode: String): String {
            val code = rawCode.trim()
            if (code.equals("unknown", ignoreCase = true) || code.isBlank()) {
                return "Unknown"
            }
            val normalized = if (code == "iw") "he" else code
            val display = Locale.forLanguageTag(normalized).getDisplayName(Locale.US)
            return if (display.isNullOrBlank() || display.equals(code, ignoreCase = true)) {
                code.uppercase(Locale.US)
            } else {
                display
            }
        }
    }
}
