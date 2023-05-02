package com.google.mediapipe.examples.languagedetector

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.languagedetector.LanguageDetector
import com.google.mediapipe.tasks.text.languagedetector.LanguageDetectorResult
import java.util.concurrent.ScheduledThreadPoolExecutor

class LanguageDetectorHelper(
    val context: Context,
    val listener: TextResultsListener,
) {
    private lateinit var languageDetector: LanguageDetector
    private lateinit var executor: ScheduledThreadPoolExecutor

    init {
        initDetector()
    }

    fun initDetector() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(MODEL_DETECTOR)

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = LanguageDetector.LanguageDetectorOptions.builder()
                .setBaseOptions(baseOptions)
            val options = optionsBuilder.build()
            languageDetector = LanguageDetector.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            listener.onError(
                "Language detector failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "Language detector failed to load the task with error: " + e
                    .message
            )
        }
    }

    // Run text language detection using MediaPipe Language Detection API
    fun detect(text: String) {
        executor = ScheduledThreadPoolExecutor(1)

        executor.execute {
            // inferenceTime is the amount of time, in milliseconds, that it takes to
            // detect the language of the input text.
            var inferenceTime = SystemClock.uptimeMillis()

            val results = languageDetector.detect(text)

            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            listener.onResult(results, inferenceTime)
        }
    }

    interface TextResultsListener {
        fun onError(error: String)
        fun onResult(results: LanguageDetectorResult, inferenceTime: Long)
    }

    companion object {
        const val TAG = "LanguageDetectorHelper"

        const val MODEL_DETECTOR = "detection_model.tflite"
    }
}
