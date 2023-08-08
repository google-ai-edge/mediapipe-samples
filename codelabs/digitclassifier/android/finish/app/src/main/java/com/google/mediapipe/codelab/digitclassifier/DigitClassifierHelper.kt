package com.google.mediapipe.codelab.digitclassifier

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

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier.ImageClassifierOptions
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult


class DigitClassifierHelper(
    val context: Context,
    val digitClassifierListener: DigitClassifierListener?
) {

    private var digitClassifier: ImageClassifier? = null

    init {
        setupDigitClassifier()
    }

    private fun setupDigitClassifier() {

        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath("mnist.tflite")

        // Describe additional options
        val optionsBuilder = ImageClassifierOptions.builder()
            .setRunningMode(RunningMode.IMAGE)
            .setBaseOptions(baseOptionsBuilder.build())

        try {
            digitClassifier =
                ImageClassifier.createFromOptions(
                    context,
                    optionsBuilder.build()
                )
        } catch (e: IllegalStateException) {
            digitClassifierListener?.onError(
                "Image classifier failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(TAG, "MediaPipe failed to load model with error: " + e.message)
        }
    }

    fun classify(image: Bitmap) {
        if (digitClassifier == null) {
            setupDigitClassifier()
        }

        // Convert the input Bitmap object to an MPImage object to run inference.
        // Rotating shouldn't be necessary because the text is being extracted from
        // a view that should always be correctly positioned.
        val mpImage = BitmapImageBuilder(image).build()

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        // Run image classification using MediaPipe Image Classifier API
        digitClassifier?.classify(mpImage)?.also { classificationResults ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            digitClassifierListener?.onResults(classificationResults, inferenceTimeMs)
        }
    }

    interface DigitClassifierListener {
        fun onError(error: String)
        fun onResults(
            results: ImageClassifierResult,
            inferenceTime: Long
        )
    }

    companion object {
        private const val TAG = "DigitClassifierHelper"
    }
}