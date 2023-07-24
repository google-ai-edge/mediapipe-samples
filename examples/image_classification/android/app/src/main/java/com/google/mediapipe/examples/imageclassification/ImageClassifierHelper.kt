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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifier
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult

class ImageClassifierHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTNETV0,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val imageClassifierListener: ClassifierListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes. If the ImageClassifier
    // will not change, a lazy val would be preferable.
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

    // Return running status of image classifier helper
    fun isClosed(): Boolean {
        return imageClassifier == null
    }

    // Initialize the image classifier using current settings on the
    // thread that is using it. CPU can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // classifier
    fun setupImageClassifier() {
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

        // Check if runningMode is consistent with imageClassifierListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (imageClassifierListener == null) {
                    throw IllegalStateException(
                        "imageClassifierListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }

            else -> {
                // no-op
            }
        }

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder =
                ImageClassifier.ImageClassifierOptions.builder()
                    .setScoreThreshold(threshold)
                    .setMaxResults(maxResults)
                    .setRunningMode(runningMode)
                    .setBaseOptions(baseOptions)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnLivestreamResult)
                optionsBuilder.setErrorListener(this::returnLivestreamError)
            }
            val options = optionsBuilder.build()
            imageClassifier =
                ImageClassifier.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            imageClassifierListener?.onError(
                "Image classifier failed to initialize. See error logs for details"
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            imageClassifierListener?.onError(
                "Image classifier failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image classifier failed to load model with error: " + e.message
            )
        }
    }

    // Runs image classification on live streaming cameras frame-by-frame and
    // returns the results asynchronously to the caller.
    fun classifyLiveStreamFrame(imageProxy: ImageProxy) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call classifyLiveStreamFrame" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

        imageProxy.use {
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        }
        imageProxy.close()
        val mpImage = BitmapImageBuilder(bitmapBuffer).build()
        classifyAsync(mpImage, imageProxy.imageInfo.rotationDegrees, frameTime)
    }

    // Run object detection using MediaPipe Object Detector API
    @VisibleForTesting
    fun classifyAsync(mpImage: MPImage, imageDegree: Int, frameTime: Long) {
        val imageProcessingOptions =
            ImageProcessingOptions.builder().setRotationDegrees(imageDegree)
                .build()
        // As we're using running mode LIVE_STREAM, the classification result will
        // be returned in returnLivestreamResult function
        imageClassifier?.classifyAsync(
            mpImage,
            imageProcessingOptions,
            frameTime
        )
    }

    // Accepted a Bitmap and runs image classification inference on it to
    // return results back to the caller
    fun classifyImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call classifyImage" +
                        " while not using RunningMode.IMAGE"
            )
        }

        if (imageClassifier == null) return null

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run image classification using MediaPipe Image Classifier API
        imageClassifier?.classify(mpImage)?.also { classificationResults ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(listOf(classificationResults), inferenceTimeMs)
        }

        // If imageClassifier?.classify() returns null, this is likely an error. Returning null
        // to indicate this.
        imageClassifierListener?.onError(
            "Image classifier failed to classify."
        )
        return null
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // image classification inference on the video. This process will evaluate
    // every frame in the video and attach the results to a bundle that will
    // be returned.
    fun classifyVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call classifyVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        if (imageClassifier == null) return null

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        // Load frames from the video and run the image classification model.
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, videoUri)
        val videoLengthMs =
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLong()

        // Note: We need to read width/height from frame instead of getting the width/height
        // of the video directly because MediaRetriever returns frames that are smaller than the
        // actual dimension of the video file.
        val firstFrame = retriever.getFrameAtTime(0)
        val width = firstFrame?.width
        val height = firstFrame?.height

        // If the video is invalid, returns a null classification result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run
        // classification on these frames.
        val resultList = mutableListOf<ImageClassifierResult>()
        val numberOfFrameToRead = videoLengthMs.div(inferenceIntervalMs)

        for (i in 0..numberOfFrameToRead) {
            val timestampMs = i * inferenceIntervalMs // ms

            retriever
                .getFrameAtTime(
                    timestampMs * 1000, // convert from ms to micro-s
                    MediaMetadataRetriever.OPTION_CLOSEST
                )
                ?.let { frame ->
                    // Convert the video frame to ARGB_8888 which is required by the MediaPipe
                    val argb8888Frame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)

                    // Convert the input Bitmap object to an MPImage object to run inference
                    val mpImage = BitmapImageBuilder(argb8888Frame).build()

                    // Run image classification using MediaPipe Image Classifier
                    // API
                    imageClassifier?.classifyForVideo(mpImage, timestampMs)
                        ?.let { classificationResult ->
                            resultList.add(classificationResult)
                        }
                        ?: {
                            didErrorOccurred = true
                            imageClassifierListener?.onError(
                                "ResultBundle could not be " +
                                        "returned" +
                                        " in classifyVideoFile"
                            )
                        }
                }
                ?: run {
                    didErrorOccurred = true
                    imageClassifierListener?.onError(
                        "Frame at specified time could not be" +
                                " retrieved when classifying in video."
                    )
                }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs)
        }
    }

    // MPImage isn't necessary for this example, but the listener requires it
    private fun returnLivestreamResult(
        result: ImageClassifierResult,
        image: MPImage
    ) {

        val finishTimeMs = SystemClock.uptimeMillis()

        val inferenceTime = finishTimeMs - result.timestampMs()

        imageClassifierListener?.onResults(
            ResultBundle(
                listOf(result),
                inferenceTime
            )
        )
    }

    // Return errors thrown during classification to this
    // ImageClassifierHelper's caller
    private fun returnLivestreamError(error: RuntimeException) {
        imageClassifierListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }


    // Wraps results from inference, the time it takes for inference to be
    // performed.
    data class ResultBundle(
        val results: List<ImageClassifierResult>,
        val inferenceTime: Long,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTNETV0 = 0
        const val MODEL_EFFICIENTNETV2 = 1
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        private const val TAG = "ImageClassifierHelper"
    }

    interface ClassifierListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
