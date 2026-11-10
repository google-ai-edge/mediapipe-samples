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
package com.google.mediapipe.examples.objectdetection

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
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetector
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult

/**
 * Helper that wraps [ObjectDetector] lifecycle, model/delegate/threshold options, and
 * image/video/camera inference.
 */
class ObjectDetectorHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTDETV0,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    var objectDetectorListener: DetectorListener? = null,
) {
    private var objectDetector: ObjectDetector? = null

    /** Duration of the most recent successful [setupObjectDetector] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isClosed: Boolean
        get() = objectDetector == null

    init {
        setupObjectDetector()
    }

    fun clearObjectDetector() {
        objectDetector?.close()
        objectDetector = null
    }

    /**
     * Initializes [ObjectDetector] on the calling thread. When using [DELEGATE_GPU], callers should
     * invoke this on the same thread that submits frames.
     */
    fun setupObjectDetector(): Boolean {
        clearObjectDetector()
        val startTime = SystemClock.uptimeMillis()

        val modelName =
            when (currentModel) {
                MODEL_EFFICIENTDETV0 -> MODEL_EFFICIENTDETV0_NAME
                MODEL_EFFICIENTDETV2 -> MODEL_EFFICIENTDETV2_NAME
                else -> {
                    objectDetectorListener?.onError("Unsupported model index: $currentModel")
                    return false
                }
            }
        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelName)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> {
                objectDetectorListener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        if (runningMode == RunningMode.LIVE_STREAM && objectDetectorListener == null) {
            throw IllegalStateException(
                "objectDetectorListener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val optionsBuilder =
                ObjectDetector.ObjectDetectorOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setScoreThreshold(threshold)
                    .setMaxResults(maxResults)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            objectDetector = ObjectDetector.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            objectDetectorListener?.onError("Object detector failed to initialize: $detail")
            Log.e(TAG, "TFLite failed to load model with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            objectDetectorListener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Object detector failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Object detector failed to load model with error: $detail", e)
            false
        }
    }

    /** Samples frames from [videoUri] every [inferenceIntervalMs] ms and returns detection results. */
    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
            )
        }
        require(inferenceIntervalMs > 0L) { "inferenceIntervalMs must be positive." }
        if (objectDetector == null) return null

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

            val resultList = mutableListOf<ObjectDetectorResult>()
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
                    objectDetectorListener?.onError(
                        "Frame at specified time could not be retrieved when detecting in video."
                    )
                    continue
                }
                val argbFrame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)
                try {
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val detectionResult = objectDetector?.detectForVideo(mpImage, timestampMs)
                    if (detectionResult != null) {
                        resultList.add(detectionResult)
                    } else {
                        didErrorOccur = true
                        objectDetectorListener?.onError(
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
                ResultBundle(resultList, inferenceTimePerFrameMs, height, width)
            }
        } finally {
            retriever.release()
        }
    }

    /** Converts a CameraX RGBA [ImageProxy] to an upright bitmap and runs async detection. */
    fun detectLivestreamFrame(imageProxy: ImageProxy, isFrontCamera: Boolean = false) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            imageProxy.close()
            throw IllegalArgumentException(
                "Attempting to call detectLivestreamFrame while not using RunningMode.LIVE_STREAM"
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
        objectDetector?.detectAsync(mpImage, frameTime)
    }

    /** Runs object detection on a single still [Bitmap] in [RunningMode.IMAGE]. */
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage while not using RunningMode.IMAGE"
            )
        }
        val detector = objectDetector ?: return null
        val argbImage =
            if (image.config == Bitmap.Config.ARGB_8888) image
            else image.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val startTime = SystemClock.uptimeMillis()
            val mpImage = BitmapImageBuilder(argbImage).build()
            val detectionResult = detector.detect(mpImage)
            if (detectionResult != null) {
                val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
                ResultBundle(
                    results = listOf(detectionResult),
                    inferenceTime = inferenceTimeMs,
                    inputImageHeight = argbImage.height,
                    inputImageWidth = argbImage.width,
                )
            } else {
                objectDetectorListener?.onError("Object detector failed to detect.")
                null
            }
        } finally {
            if (argbImage !== image) argbImage.recycle()
        }
    }

    private fun returnLivestreamResult(result: ObjectDetectorResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        objectDetectorListener?.onResults(
            ResultBundle(
                results = listOf(result),
                inferenceTime = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width,
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        objectDetectorListener?.onError(error.message ?: "An unknown error has occurred")
    }

    data class ResultBundle(
        val results: List<ObjectDetectorResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val inputImageRotation: Int = 0,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTDETV0 = 0
        const val MODEL_EFFICIENTDETV2 = 1
        const val MODEL_EFFICIENTDETV0_NAME = "efficientdet-lite0.tflite"
        const val MODEL_EFFICIENTDETV2_NAME = "efficientdet-lite2.tflite"
        const val SAMPLE_IMAGE_ASSET = "sample_objects.jpg"
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val TAG = "ObjectDetectorHelper"
    }

    interface DetectorListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
