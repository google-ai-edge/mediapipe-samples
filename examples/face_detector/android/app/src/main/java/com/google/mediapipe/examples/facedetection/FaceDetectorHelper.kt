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

package com.google.mediapipe.examples.facedetection

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
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult

/**
 * Helper that wraps [FaceDetector] lifecycle, delegate/threshold options, and image/video/camera
 * inference.
 */
class FaceDetectorHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_SHORT_RANGE,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    var faceDetectorListener: DetectorListener? = null,
) {
    private var faceDetector: FaceDetector? = null

    /** Duration of the most recent successful [setupFaceDetector] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isClosed: Boolean
        get() = faceDetector == null

    init {
        setupFaceDetector()
    }

    fun clearFaceDetector() {
        faceDetector?.close()
        faceDetector = null
    }

    /**
     * Initializes [FaceDetector] on the calling thread. When using [DELEGATE_GPU], callers should
     * invoke this on the same thread that submits frames.
     */
    fun setupFaceDetector(): Boolean {
        clearFaceDetector()
        val startTime = SystemClock.uptimeMillis()

        val modelAsset =
            when (currentModel) {
                MODEL_SHORT_RANGE -> MODEL_SHORT_RANGE_NAME
                MODEL_FULL_RANGE -> MODEL_FULL_RANGE_NAME
                else -> {
                    faceDetectorListener?.onError("Unsupported model index: $currentModel")
                    return false
                }
            }
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelAsset)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> {
                faceDetectorListener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        if (runningMode == RunningMode.LIVE_STREAM && faceDetectorListener == null) {
            throw IllegalStateException(
                "faceDetectorListener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val optionsBuilder =
                FaceDetector.FaceDetectorOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setMinDetectionConfidence(threshold)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            faceDetector = FaceDetector.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            faceDetectorListener?.onError("Face detector failed to initialize: $detail")
            Log.e(TAG, "TFLite failed to load model with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            faceDetectorListener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Face detector failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Face detector failed to load model with error: $detail", e)
            false
        }
    }

    /** Runs face detection across sampled frames of the video at [videoUri]. */
    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
            )
        }
        val detector = faceDetector ?: return null

        val startTime = SystemClock.uptimeMillis()
        var didErrorOccur = false
        val retriever = MediaMetadataRetriever()
        val resultList = mutableListOf<FaceDetectorResult>()
        val width: Int
        val height: Int
        val frameCount: Long

        try {
            retriever.setDataSource(context, videoUri)
            val videoLengthMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val firstFrame = retriever.getFrameAtTime(0)
            if (videoLengthMs == null || firstFrame == null) {
                return null
            }
            width = firstFrame.width
            height = firstFrame.height
            frameCount = (videoLengthMs / inferenceIntervalMs).coerceAtLeast(1L)

            for (i in 0..frameCount) {
                val timestampMs = i * inferenceIntervalMs
                val frame = retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val argbFrame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val detection = detector.detectForVideo(mpImage, timestampMs)
                    if (detection != null) {
                        resultList.add(detection)
                    } else {
                        didErrorOccur = true
                        faceDetectorListener?.onError("Result could not be returned in detectVideoFile")
                    }
                } else {
                    didErrorOccur = true
                    faceDetectorListener?.onError(
                        "Frame at specified time could not be retrieved when detecting in video."
                    )
                }
            }
        } finally {
            retriever.release()
        }

        val inferenceTimePerFrameMs =
            ((SystemClock.uptimeMillis() - startTime) / (frameCount + 1L)).coerceAtLeast(0L)

        return if (didErrorOccur) {
            null
        } else {
            ResultBundle(resultList, inferenceTimePerFrameMs, height, width, setupDurationMs)
        }
    }

    /** Runs face detection asynchronously on a CameraX RGBA_8888 [ImageProxy] frame. */
    fun detectLivestreamFrame(imageProxy: ImageProxy, isFrontCamera: Boolean = true) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            imageProxy.close()
            throw IllegalArgumentException(
                "Attempting to call detectLivestreamFrame while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()
        val width: Int
        val height: Int
        val rotationDegrees: Int
        val bitmapBuffer: Bitmap

        imageProxy.use { proxy ->
            width = proxy.width
            height = proxy.height
            rotationDegrees = proxy.imageInfo.rotationDegrees
            bitmapBuffer = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
        }

        val matrix =
            Matrix().apply {
                postRotate(rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, width / 2f, height / 2f)
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
        if (rotatedBitmap != bitmapBuffer) {
            bitmapBuffer.recycle()
        }

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        detectAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun detectAsync(mpImage: MPImage, frameTime: Long) {
        faceDetector?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(result: FaceDetectorResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        faceDetectorListener?.onResults(
            ResultBundle(
                results = listOf(result),
                inferenceTime = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width,
                loadTimeMs = setupDurationMs,
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        faceDetectorListener?.onError(error.message ?: "An unknown error has occurred")
    }

    /** Detects faces in a static [Bitmap], converting to ARGB_8888 if needed. */
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage while not using RunningMode.IMAGE"
            )
        }
        val detector = faceDetector ?: return null

        val argbImage =
            if (image.config == Bitmap.Config.ARGB_8888) image
            else image.copy(Bitmap.Config.ARGB_8888, false)

        val startTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(argbImage).build()
        val detectionResult = detector.detect(mpImage) ?: return null
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
        return ResultBundle(
            results = listOf(detectionResult),
            inferenceTime = inferenceTimeMs,
            inputImageHeight = argbImage.height,
            inputImageWidth = argbImage.width,
            loadTimeMs = setupDurationMs,
        )
    }

    data class ResultBundle(
        val results: List<FaceDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val loadTimeMs: Long = 0L,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_SHORT_RANGE = 0
        const val MODEL_FULL_RANGE = 1
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_SHORT_RANGE_NAME = "face_detection_short_range.tflite"
        const val MODEL_FULL_RANGE_NAME = "face_detection_full_range.tflite"
        const val SAMPLE_IMAGE_ASSET = "sample_faces.jpg"
        const val TAG = "FaceDetectorHelper"
    }

    interface DetectorListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)

        fun onResults(resultBundle: ResultBundle)
    }
}
