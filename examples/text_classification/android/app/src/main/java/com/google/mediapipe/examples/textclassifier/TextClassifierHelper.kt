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
package com.google.mediapipe.examples.textclassifier

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import java.util.concurrent.ScheduledThreadPoolExecutor

class TextClassifierHelper(
    var currentModel: String = WORD_VEC,
    val context: Context,
    val listener: TextResultsListener,
) {
    private lateinit var textClassifier: TextClassifier
    private lateinit var executor: ScheduledThreadPoolExecutor

    init {
        initClassifier()
    }

    fun initClassifier() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(currentModel)

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = TextClassifier.TextClassifierOptions.builder()
                .setBaseOptions(baseOptions)
            val options = optionsBuilder.build()
            textClassifier = TextClassifier.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            listener.onError(
                "Text classifier failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "Text classifier failed to load the task with error: " + e
                    .message
            )
        }
    }

    // Run text classification using MediaPipe Text Classifier API
    fun classify(text: String) {
        executor = ScheduledThreadPoolExecutor(1)

        executor.execute {
            // inferenceTime is the amount of time, in milliseconds, that it takes to
            // classify the input text.
            var inferenceTime = SystemClock.uptimeMillis()

            val results = textClassifier.classify(text)

            inferenceTime = SystemClock.uptimeMillis() - inferenceTime

            listener.onResult(results, inferenceTime)
        }
    }

    interface TextResultsListener {
        fun onError(error: String)
        fun onResult(results: TextClassifierResult, inferenceTime: Long)
    }

    companion object {
        const val TAG = "TextClassifierHelper"

        const val WORD_VEC = "wordvec.tflite"
        const val MOBILEBERT = "mobilebert.tflite"
    }
}
