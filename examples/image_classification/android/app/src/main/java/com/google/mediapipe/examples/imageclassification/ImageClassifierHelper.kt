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

package com.google.mediapipe.examples.imageclassification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.components.processors.ClassifierOptions
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult

class ImageClassifierHelper(
    var threshold: Float = 0.5f,
    var maxResults: Int = 3,
    var currentDelegate: Int = 0,
    var currentModel: Int = 0,
    val context: Context,
    val imageClassifierListener: ClassifierListener?
) {
    private var imageClassifier: ImageClassifier? = null

    init {
        setupImageClassifier()
    }

    // Classifier must be closed when creating a new one to avoid returning results to a
    // non-existent object
    fun clearImageClassifier() {
        imageClassifier?.close()
        imageClassifier = null
    }

    fun setupImageClassifier() {
        val classifierOptions = ClassifierOptions.builder()
            .setMaxResults(maxResults)
            .setScoreThreshold(threshold)
            .build()

        val baseOptionsBuilder = BaseOptions.builder()

        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        val modelName =
            when (currentModel) {
                MODEL_EFFICIENTNETV0 -> "efficientnet-lite0.tflite"
                MODEL_EFFICIENTNETV2 -> "efficientnet-lite2.tflite"
                else -> "efficientnet-lite0.tflite"
            }

        baseOptionsBuilder.setModelAssetPath(modelName)

        val optionsBuilder = ImageClassifier.ImageClassifierOptions.builder()
            .setClassifierOptions(classifierOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(this::returnLivestreamResult)
            .setErrorListener(this::returnLivestreamError)
            .setBaseOptions(baseOptionsBuilder.build())

        try {
            imageClassifier =
                ImageClassifier.createFromOptions(context, optionsBuilder.build())
        } catch (e: IllegalStateException) {
            imageClassifierListener?.onError(
                "Image classifier failed to initialize. See error logs for details"
            )
            Log.e(TAG, "Image classifier failed to load model with error: " + e.message)
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            imageClassifierListener?.onError(
                "Image classifier failed to initialize. See error logs for details"
            )
            Log.e(TAG, "Image classifier failed to load model with error: " + e.message)
        }
    }

    // Attempts to classify items in a camera live stream frame
    fun classify(image: ImageProxy) {
        val frameTime = SystemClock.uptimeMillis()
        val bitmapBuffer =
            Bitmap.createBitmap(
                image.width,
                image.height,
                Bitmap.Config.ARGB_8888
            )

        image.use {
            bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer)
        }
        image.close()

        // Used for rotating the frame image so it matches our models
        val matrix = Matrix().apply {
            postRotate(image.imageInfo.rotationDegrees.toFloat())
        }

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        // Attempts to classify an image asynchronously and return the results to our listener
        imageClassifier?.classifyAsync(mpImage, frameTime)
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResults(
            results: List<ImageClassifierResult>?,
            inferenceTime: Long
        )
    }

    // MPImage isn't necessary for this example, but the listener requires it
    private fun returnLivestreamResult(
        result: ImageClassifierResult,
        image: MPImage
    ) {

        val finishTimeMs = SystemClock.uptimeMillis()

        val inferenceTime = finishTimeMs - result.timestampMs()

        imageClassifierListener?.onResults(
            listOf(result),
            inferenceTime
        )
    }

    // Return errors thrown during detection to this ObjectDetectorHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        imageClassifierListener?.onError(error.message ?: "An unknown error has occurred")
    }

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTNETV0 = 0
        const val MODEL_EFFICIENTNETV2 = 1

        private const val TAG = "ImageClassifierHelper"
    }
}
