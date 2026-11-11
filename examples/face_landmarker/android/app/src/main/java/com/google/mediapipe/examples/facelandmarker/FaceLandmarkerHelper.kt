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
package com.google.mediapipe.examples.facelandmarker

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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult

/**
 * Helper that wraps [FaceLandmarker] lifecycle, delegate/threshold options, blendshapes output,
 * and image/video/camera inference.
 */
class FaceLandmarkerHelper(
    var minFaceDetectionConfidence: Float = DEFAULT_FACE_DETECTION_CONFIDENCE,
    var minFaceTrackingConfidence: Float = DEFAULT_FACE_TRACKING_CONFIDENCE,
    var minFacePresenceConfidence: Float = DEFAULT_FACE_PRESENCE_CONFIDENCE,
    var maxNumFaces: Int = DEFAULT_NUM_FACES,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val faceLandmarkerHelperListener: LandmarkerListener? = null,
) {
    private var faceLandmarker: FaceLandmarker? = null

    /** Duration of the most recent successful [setupFaceLandmarker] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isClosed: Boolean
        get() = faceLandmarker == null

    init {
        setupFaceLandmarker()
    }

    fun clearFaceLandmarker() {
        faceLandmarker?.close()
        faceLandmarker = null
    }

    /**
     * Initializes [FaceLandmarker] on the calling thread. When using [DELEGATE_GPU], callers should
     * invoke this on the same thread that submits frames.
     */
    fun setupFaceLandmarker(): Boolean {
        clearFaceLandmarker()
        val startTime = SystemClock.uptimeMillis()

        val baseOptionBuilder = BaseOptions.builder().setModelAssetPath(MP_FACE_LANDMARKER_TASK)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
            else -> {
                faceLandmarkerHelperListener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        if (runningMode == RunningMode.LIVE_STREAM && faceLandmarkerHelperListener == null) {
            throw IllegalStateException(
                "faceLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val optionsBuilder =
                FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionBuilder.build())
                    .setMinFaceDetectionConfidence(minFaceDetectionConfidence)
                    .setMinTrackingConfidence(minFaceTrackingConfidence)
                    .setMinFacePresenceConfidence(minFacePresenceConfidence)
                    .setNumFaces(maxNumFaces)
                    .setOutputFaceBlendshapes(true)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            faceLandmarker = FaceLandmarker.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            faceLandmarkerHelperListener?.onError("Face Landmarker failed to initialize: $detail")
            Log.e(TAG, "MediaPipe failed to load the task with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            faceLandmarkerHelperListener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Face Landmarker failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Face Landmarker failed to load model with error: $detail", e)
            false
        }
    }

    /** Converts a CameraX RGBA [ImageProxy] to an upright bitmap and runs async detection. */
    fun detectLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            imageProxy.close()
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream while not using RunningMode.LIVE_STREAM"
            )
        }
        val frameTime = SystemClock.uptimeMillis()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val width = imageProxy.width
        val height = imageProxy.height

        val bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        imageProxy.use { proxy ->
            bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
        }

        val matrix =
            Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f)
                }
            }
        val rotatedBitmap =
            Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.width,
                bitmapBuffer.height,
                matrix,
                true,
            )
        if (rotatedBitmap !== bitmapBuffer) {
            bitmapBuffer.recycle()
        }

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        faceLandmarker?.detectAsync(mpImage, frameTime)
    }

    /** Samples frames from [videoUri] every [inferenceIntervalMs] ms and returns detection results. */
    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long): VideoResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
            )
        }
        require(inferenceIntervalMs > 0L) { "inferenceIntervalMs must be positive." }

        val startTime = SystemClock.uptimeMillis()
        var didErrorOccur = false

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val videoLengthMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val firstFrame = retriever.getFrameAtTime(0)
            val width = firstFrame?.width
            val height = firstFrame?.height
            firstFrame?.recycle()

            if (videoLengthMs == null || width == null || height == null) {
                return null
            }

            val resultList = mutableListOf<FaceLandmarkerResult>()
            val lastFrameIndex = videoLengthMs / inferenceIntervalMs

            for (i in 0..lastFrameIndex) {
                val timestampMs = i * inferenceIntervalMs
                val frame =
                    retriever.getFrameAtTime(
                        timestampMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    )
                if (frame == null) {
                    didErrorOccur = true
                    faceLandmarkerHelperListener?.onError(
                        "Frame at specified time could not be retrieved when detecting in video."
                    )
                    continue
                }
                val argbFrame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)
                try {
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val detectionResult = faceLandmarker?.detectForVideo(mpImage, timestampMs)
                    if (detectionResult != null) {
                        resultList.add(detectionResult)
                    } else {
                        didErrorOccur = true
                        faceLandmarkerHelperListener?.onError(
                            "ResultBundle could not be returned in detectVideoFile"
                        )
                    }
                } finally {
                    if (argbFrame !== frame) argbFrame.recycle()
                    frame.recycle()
                }
            }

            val frameCount = (lastFrameIndex + 1).coerceAtLeast(1L)
            val inferenceTimePerFrameMs = (SystemClock.uptimeMillis() - startTime) / frameCount

            if (didErrorOccur) {
                null
            } else {
                VideoResultBundle(resultList, inferenceTimePerFrameMs, height, width)
            }
        } finally {
            retriever.release()
        }
    }

    /** Runs face landmark & blendshape detection on a single still [Bitmap] in [RunningMode.IMAGE]. */
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage while not using RunningMode.IMAGE"
            )
        }
        val argbImage =
            if (image.config == Bitmap.Config.ARGB_8888) image
            else image.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val startTime = SystemClock.uptimeMillis()
            val mpImage = BitmapImageBuilder(argbImage).build()
            val landmarkResult = faceLandmarker?.detect(mpImage)
            if (landmarkResult != null) {
                val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
                ResultBundle(
                    result = landmarkResult,
                    inferenceTime = inferenceTimeMs,
                    inputImageHeight = argbImage.height,
                    inputImageWidth = argbImage.width,
                )
            } else {
                faceLandmarkerHelperListener?.onError("Face Landmarker failed to detect.")
                null
            }
        } finally {
            if (argbImage !== image) argbImage.recycle()
        }
    }

    private fun returnLivestreamResult(result: FaceLandmarkerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        faceLandmarkerHelperListener?.onResults(
            ResultBundle(
                result = result,
                inferenceTime = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width,
            )
        )
        if (result.faceLandmarks().isEmpty()) {
            faceLandmarkerHelperListener?.onEmpty()
        }
    }

    private fun returnLivestreamError(error: RuntimeException) {
        faceLandmarkerHelperListener?.onError(error.message ?: "An unknown error has occurred")
    }

    companion object {
        const val TAG = "FaceLandmarkerHelper"
        private const val MP_FACE_LANDMARKER_TASK = "face_landmarker.task"
        const val SAMPLE_IMAGE_ASSET = "sample_face.png"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_FACE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_FACE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_NUM_FACES = 1
        const val MAX_FACES_LIMIT = 2
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
    }

    data class ResultBundle(
        val result: FaceLandmarkerResult,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    data class VideoResultBundle(
        val results: List<FaceLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
        fun onEmpty() {}
    }
}
