package com.google.mediapipe.examples.holisticlandmarker

/*
 * Copyright 2024 The TensorFlow Authors. All Rights Reserved.
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
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult

class HolisticLandmarkerHelper(
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val minFacePresenceConfidence: Float,
    val minHandLandmarksConfidence: Float,
    val minPosePresenceConfidence: Float,
    val minFaceDetectionConfidence: Float,
    val minPoseDetectionConfidence: Float,
    val minFaceSuppressionThreshold: Float,
    val minPoseSuppressionThreshold: Float,
    val isFaceBlendShapes: Boolean,
    val isPoseSegmentationMark: Boolean,
    // this listener is only used when running in RunningMode.LIVE_STREAM
    val landmarkerHelperListener: LandmarkerListener? = null
) {
    private var holisticLandmarker: HolisticLandmarker? = null

    init {
        setUpHolisticLandmarker()
    }

    fun setUpHolisticLandmarker() {
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
        // Check if runningMode is consistent with landmarkerHelperListener
        when (runningMode) {
            RunningMode.LIVE_STREAM -> {
                if (landmarkerHelperListener == null) {
                    throw IllegalStateException(
                        "holisticLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
                    )
                }
            }

            else -> {
                // no-op
            }
        }
        try {
            baseOptionBuilder.setModelAssetPath(MP_HOLISTIC_LANDMARKER_TASK)
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder =
                HolisticLandmarker.HolisticLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(runningMode)
                    .setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                    .setMinFaceSuppressionThreshold(minFaceSuppressionThreshold)
                    .setMinFacePresenceConfidence(minFacePresenceConfidence)
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinPoseSuppressionThreshold(minPoseSuppressionThreshold)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setMinHandLandmarksConfidence(minHandLandmarksConfidence)
                    .setOutputFaceBlendshapes(isFaceBlendShapes)
                    .setOutputPoseSegmentationMasks(isPoseSegmentationMark)

            // The ResultListener and ErrorListener only use for LIVE_STREAM mode.
            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            holisticLandmarker =
                HolisticLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            landmarkerHelperListener?.onError(
                "Holistic Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        } catch (e: RuntimeException) {
            landmarkerHelperListener?.onError(
                "Holistic Landmarker failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG, "MediaPipe failed to load the task with error: " + e
                    .message
            )
        }
    }

    fun isClose(): Boolean {
        return holisticLandmarker == null
    }

    fun detectVideoFile(
        videoUri: Uri,
        inferenceIntervalMs: Long
    ): VideoResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile" +
                        " while not using RunningMode.VIDEO"
            )
        }

        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        // Load frames from the video and run the holistic landmarker.
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

        // If the video is invalid, returns a null detection result
        if ((videoLengthMs == null) || (width == null) || (height == null)) return null

        // Next, we'll get one frame every frameInterval ms, then run detection on these frames.
        val resultList = mutableListOf<HolisticLandmarkerResult?>()
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

                    // Run holistic landmarker using MediaPipe Holistic Landmarker API
                    try {
                        holisticLandmarker?.detectForVideo(mpImage, timestampMs)
                            ?.let { detectionResult ->
                                resultList.add(detectionResult)
                            }
                    } catch (e: MediaPipeException) {
                        resultList.add(null)
                    }
                } ?: kotlin.run {
                resultList.add(null)
            }
        }

        retriever.release()

        val inferenceTimePerFrameMs =
            (SystemClock.uptimeMillis() - startTime).div(numberOfFrameToRead)

        return VideoResultBundle(
            resultList,
            inferenceTimePerFrameMs,
            height,
            width
        )
    }

    fun detectImage(image: Bitmap): ResultBundle? {
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

        // Run holistic landmarker using MediaPipe Holistic Landmarker API
        holisticLandmarker?.detect(mpImage)?.also { landmarkResult ->
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                landmarkResult,
                inferenceTimeMs,
                image.height,
                image.width
            )
        }

        // If holisticLandmarker?.detect() returns null, this is likely an error. Returning null
        // to indicate this.
        landmarkerHelperListener?.onError(
            "Holistic Landmarker failed to detect."
        )
        return null
    }

    fun detectLiveStreamCamera(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream" +
                        " while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()

        // Copy out RGB bits from the frame to a bitmap buffer
        val bitmapBuffer =
            Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
        imageProxy.close()

        val matrix = Matrix().apply {
            // Rotate the frame received from the camera to be in the same direction as it'll be shown
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            // flip image if user use front camera
            if (isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
            matrix, true
        )

        // Convert the input Bitmap object to an MPImage object to run inference
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        detectAsync(mpImage, frameTime)
    }

    private fun detectAsync(mpImage: MPImage?, frameTime: Long) {
        holisticLandmarker?.detectAsync(mpImage, frameTime)
    }

    fun clearHolisticLandmarker() {
        holisticLandmarker?.close()
        holisticLandmarker = null
    }

    private fun returnLivestreamResult(
        result: HolisticLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        // Update result to LandMarkerHelper
        landmarkerHelperListener?.onResults(
            ResultBundle(
                result,
                inferenceTime,
                input.height,
                input.width
            )
        )
    }

    // Return errors thrown during detection to this HolisticLandmarkerHelper's
    // caller
    private fun returnLivestreamError(error: RuntimeException) {
        landmarkerHelperListener?.onError(
            error = error.message ?: "Unknown error"
        )
    }

    data class ResultBundle(
        val result: HolisticLandmarkerResult,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    data class VideoResultBundle(
        val results: List<HolisticLandmarkerResult?>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    companion object {
        private const val MP_HOLISTIC_LANDMARKER_TASK =
            "tasks/holistic_landmarker.task"
        const val TAG = "HolisticLandmarkerHelper"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_MIN_FACE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_HAND_LANDMARKS_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_FACE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_MIN_FACE_SUPPRESSION_THRESHOLD = 0.5F
        const val DEFAULT_MIN_POSE_SUPPRESSION_THRESHOLD = 0.5F
        const val DEFAULT_FACE_BLEND_SHAPES = false
        const val DEFAULT_POSE_SEGMENTATION_MARK = false
    }

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
