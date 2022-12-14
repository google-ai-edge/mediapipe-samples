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
package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
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
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult

class GestureRecognizerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val gestureRecognizerListener: GestureRecognizerListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes. If the GestureRecognizer
    // will not change, a lazy val would be preferable.
    private var gestureRecognizer: GestureRecognizer? = null

    init {
        setupGestureRecognizer()
    }

    fun clearGestureRecognizer() {
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    // Initialize the gesture recognizer using current settings on the
    // thread that is using it. CPU can be used with recognizers
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the recognizer
    fun setupGestureRecognizer() {
        // Set general recognition options, including number of used threads
        val baseOptionBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionBuilder.setDelegate(Delegate.GPU)
            }
        }

        baseOptionBuilder.setModelAssetPath(MP_RECOGNIZER_TASK)

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder =
                GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }
            val options = optionsBuilder.build()
            gestureRecognizer =
                GestureRecognizer.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            gestureRecognizerListener?.onError(
                "Gesture recognizer failed to initialize. See error logs for " + "details"
            )
            Log.e(
                TAG,
                "MP Task Vision failed to load the task with error: " + e.message
            )
        } catch (e: RuntimeException) {
            gestureRecognizerListener?.onError(
                "Gesture recognizer failed to initialize. See error logs for " + "details",
                GPU_ERROR
            )
            Log.e(
                TAG,
                "MP Task Vision failed to load the task with error: " + e.message
            )
        }
    }

    // Convert the ImageProxy to MP Image and feed it to GestureRecognizer.
    fun recognizeLiveStream(
        imageProxy: ImageProxy,
    ) {
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image since we only support front camera
            postScale(
                -1f, 1f, imageProxy.width.toFloat(), imageProxy.height.toFloat()
            )
        }

        // Rotate bitmap to match what our model expects
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        recognizeAsync(mpImage, frameTime)
    }

    // Run hand gesture recognition using MediaPipe Gesture Recognition API
    @VisibleForTesting
    fun recognizeAsync(mpImage: MPImage, frameTime: Long) {
        // As we're using running mode LIVE_STREAM, the recognition result will
        // be returned in returnLivestreamResult function
        gestureRecognizer?.recognizeAsync(mpImage, frameTime)
    }

    // Accepts the URI for a video file loaded from the user's gallery and attempts to run
    // gesture recognizer inference on the video. This process will evaluate
    // every frame in the video and attach the results to a bundle that will be
    // returned.
    fun recognizeVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call recognizeVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        var didErrorOccurred = false

        // Load frames from the video and run the gesture recognizer.
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

        // If the video is invalid, returns a null recognition result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run recognizer
        // on these frames.
        val resultList = mutableListOf<GestureRecognizerResult>()
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

                    // Run gesture recognizer using MediaPipe Gesture Recognizer
                    // API
                    gestureRecognizer?.recognizeForVideo(mpImage, timestampMs)
                        ?.let { recognizerResult ->
                            resultList.add(recognizerResult)
                        } ?: {
                        didErrorOccurred = true
                        gestureRecognizerListener?.onError(
                            "ResultBundle could not be returned" +
                                    " in recognizeVideoFile"
                        )
                    }
                }
                ?: run {
                    didErrorOccurred = true
                    gestureRecognizerListener?.onError(
                        "Frame at specified time could not be" +
                                " retrieved when recognition in video."
                    )
                }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return if (didErrorOccurred) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
        }
    }

    // Accepted a Bitmap and runs gesture recognizer inference on it to
    // return results back to the caller
    fun recognizeImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage" +
                        " while not using RunningMode.IMAGE"
            )
        }


        // Inference time is the difference between the system time at the
        // start and finish of the process
        val startTime = SystemClock.uptimeMillis()

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(image).build()

        // Run gesture recognizer using MediaPipe Gesture Recognizer API
        gestureRecognizer?.recognize(mpImage)?.also { recognizerResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                listOf(recognizerResult),
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        // If gestureRecognizer?.recognize() returns null, this is likely an error. Returning null
        // to indicate this.
        gestureRecognizerListener?.onError(
            "Gesture Recognizer failed to recognize."
        )
        return null
    }

    // Return running status of the recognizer helper
    fun isClosed(): Boolean {
        return gestureRecognizer == null
    }

    // Return the recognition result to the GestureRecognizerHelper's caller
    private fun returnLivestreamResult(
        result: GestureRecognizerResult, input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        gestureRecognizerListener?.onResults(
            ResultBundle(
                listOf(result), inferenceTime, input.height, input.width
            )
        )
    }

    // Return errors thrown during recognition to this GestureRecognizerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        gestureRecognizerListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    companion object {
        val TAG = "GestureRecognizerHelper ${this.hashCode()}"
        private const val MP_RECOGNIZER_TASK = "gesture_recognizer.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val results: List<GestureRecognizerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface GestureRecognizerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
