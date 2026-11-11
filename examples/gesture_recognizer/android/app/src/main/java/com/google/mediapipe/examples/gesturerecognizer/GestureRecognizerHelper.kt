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

/**
 * Helper that wraps [GestureRecognizer] lifecycle, delegate/threshold options, and
 * image/video/camera inference.
 */
class GestureRecognizerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val gestureRecognizerListener: GestureRecognizerListener? = null,
) {
    private var gestureRecognizer: GestureRecognizer? = null

    /** Duration of the most recent successful [setupGestureRecognizer] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isClosed: Boolean
        get() = gestureRecognizer == null

    init {
        setupGestureRecognizer()
    }

    fun clearGestureRecognizer() {
        gestureRecognizer?.close()
        gestureRecognizer = null
    }

    /**
     * Initializes [GestureRecognizer] on the calling thread. When using [DELEGATE_GPU], callers
     * should invoke this on the same thread that submits frames.
     */
    fun setupGestureRecognizer(): Boolean {
        clearGestureRecognizer()
        val startTime = SystemClock.uptimeMillis()

        val baseOptionBuilder = BaseOptions.builder().setModelAssetPath(MP_RECOGNIZER_TASK)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
            else -> {
                gestureRecognizerListener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        if (runningMode == RunningMode.LIVE_STREAM && gestureRecognizerListener == null) {
            throw IllegalStateException(
                "gestureRecognizerListener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val optionsBuilder =
                GestureRecognizer.GestureRecognizerOptions.builder()
                    .setBaseOptions(baseOptionBuilder.build())
                    .setMinHandDetectionConfidence(minHandDetectionConfidence)
                    .setMinTrackingConfidence(minHandTrackingConfidence)
                    .setMinHandPresenceConfidence(minHandPresenceConfidence)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            gestureRecognizer = GestureRecognizer.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            gestureRecognizerListener?.onError("Gesture recognizer failed to initialize: $detail")
            Log.e(TAG, "MP Task Vision failed to load the task with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            gestureRecognizerListener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Gesture recognizer failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "MP Task Vision failed to load the task with error: $detail", e)
            false
        }
    }

    /** Converts a CameraX RGBA [ImageProxy] to an upright bitmap and runs async recognition. */
    fun recognizeLiveStream(imageProxy: ImageProxy, isFrontCamera: Boolean = true) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            imageProxy.close()
            throw IllegalArgumentException(
                "Attempting to call recognizeLiveStream while not using RunningMode.LIVE_STREAM"
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
        recognizeAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun recognizeAsync(mpImage: MPImage, frameTime: Long) {
        gestureRecognizer?.recognizeAsync(mpImage, frameTime)
    }

    /** Samples frames from [videoUri] every [inferenceIntervalMs] ms and returns recognition results. */
    fun recognizeVideoFile(videoUri: Uri, inferenceIntervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call recognizeVideoFile while not using RunningMode.VIDEO"
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

            val resultList = mutableListOf<GestureRecognizerResult>()
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
                    gestureRecognizerListener?.onError(
                        "Frame at specified time could not be retrieved when recognition in video."
                    )
                    continue
                }
                val argbFrame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)
                try {
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val recognizerResult = gestureRecognizer?.recognizeForVideo(mpImage, timestampMs)
                    if (recognizerResult != null) {
                        resultList.add(recognizerResult)
                    } else {
                        didErrorOccur = true
                        gestureRecognizerListener?.onError(
                            "ResultBundle could not be returned in recognizeVideoFile"
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

    /** Runs hand gesture recognition on a single still [Bitmap] in [RunningMode.IMAGE]. */
    fun recognizeImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call recognizeImage while not using RunningMode.IMAGE"
            )
        }
        val argbImage =
            if (image.config == Bitmap.Config.ARGB_8888) image
            else image.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val startTime = SystemClock.uptimeMillis()
            val mpImage = BitmapImageBuilder(argbImage).build()
            val recognizerResult = gestureRecognizer?.recognize(mpImage)
            if (recognizerResult != null) {
                val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
                ResultBundle(
                    results = listOf(recognizerResult),
                    inferenceTime = inferenceTimeMs,
                    inputImageHeight = argbImage.height,
                    inputImageWidth = argbImage.width,
                )
            } else {
                gestureRecognizerListener?.onError("Gesture Recognizer failed to recognize.")
                null
            }
        } finally {
            if (argbImage !== image) argbImage.recycle()
        }
    }

    private fun returnLivestreamResult(result: GestureRecognizerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        gestureRecognizerListener?.onResults(
            ResultBundle(
                results = listOf(result),
                inferenceTime = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width,
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        gestureRecognizerListener?.onError(error.message ?: "An unknown error has occurred")
    }

    companion object {
        const val TAG = "GestureRecognizerHelper"
        private const val MP_RECOGNIZER_TASK = "gesture_recognizer.task"
        const val SAMPLE_IMAGE_ASSET = "sample_gesture.jpg"

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
