/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
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
