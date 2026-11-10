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

/**
 * Helper that wraps [ImageClassifier] lifecycle, model/delegate/threshold options, and
 * image/video/camera inference.
 */
class ImageClassifierHelper(
    var threshold: Float = THRESHOLD_DEFAULT,
    var maxResults: Int = MAX_RESULTS_DEFAULT,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_EFFICIENTNETV0,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    var imageClassifierListener: ClassifierListener? = null,
) {
    private var imageClassifier: ImageClassifier? = null

    /** Duration of the most recent successful [setupImageClassifier] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    init {
        setupImageClassifier()
    }

    fun clearImageClassifier() {
        imageClassifier?.close()
        imageClassifier = null
    }

    fun isClosed(): Boolean = imageClassifier == null

    /**
     * Initializes [ImageClassifier] on the calling thread. When using [DELEGATE_GPU], callers
     * should invoke this on the same thread that submits frames.
     */
    fun setupImageClassifier(): Boolean {
        clearImageClassifier()
        val startTime = SystemClock.uptimeMillis()

        val modelName =
            when (currentModel) {
                MODEL_EFFICIENTNETV0 -> MODEL_EFFICIENTNETV0_FILE
                MODEL_EFFICIENTNETV2 -> MODEL_EFFICIENTNETV2_FILE
                else -> {
                    imageClassifierListener?.onError("Unsupported model index: $currentModel")
                    return false
                }
            }

        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelName)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> {
                imageClassifierListener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        if (runningMode == RunningMode.LIVE_STREAM && imageClassifierListener == null) {
            throw IllegalStateException(
                "imageClassifierListener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val optionsBuilder =
                ImageClassifier.ImageClassifierOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setScoreThreshold(threshold)
                    .setMaxResults(maxResults)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            imageClassifier = ImageClassifier.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            imageClassifierListener?.onError("Image classifier failed to initialize: $detail")
            Log.e(TAG, "Image classifier failed to load model with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            imageClassifierListener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Image classifier failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Image classifier failed to load model with error: $detail", e)
            false
        }
    }

    /** Runs image classification asynchronously on a CameraX RGBA_8888 [ImageProxy] frame. */
    fun classifyLiveStreamFrame(imageProxy: ImageProxy) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            imageProxy.close()
            throw IllegalArgumentException(
                "Attempting to call classifyLiveStreamFrame while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()
        val rotationDegrees: Int
        val bitmapBuffer: Bitmap

        imageProxy.use { proxy ->
            rotationDegrees = proxy.imageInfo.rotationDegrees
            bitmapBuffer = Bitmap.createBitmap(proxy.width, proxy.height, Bitmap.Config.ARGB_8888)
            bitmapBuffer.copyPixelsFromBuffer(proxy.planes[0].buffer)
        }

        val mpImage = BitmapImageBuilder(bitmapBuffer).build()
        classifyAsync(mpImage, rotationDegrees, frameTime)
    }

    @VisibleForTesting
    fun classifyAsync(mpImage: MPImage, imageDegree: Int, frameTime: Long) {
        val imageProcessingOptions =
            ImageProcessingOptions.builder().setRotationDegrees(imageDegree).build()
        imageClassifier?.classifyAsync(mpImage, imageProcessingOptions, frameTime)
    }

    /** Classifies a static [Bitmap], converting to ARGB_8888 if needed. */
    fun classifyImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call classifyImage while not using RunningMode.IMAGE"
            )
        }
        val classifier = imageClassifier ?: return null

        val argbImage =
            if (image.config == Bitmap.Config.ARGB_8888) image
            else image.copy(Bitmap.Config.ARGB_8888, false)

        val startTime = SystemClock.uptimeMillis()
        val mpImage = BitmapImageBuilder(argbImage).build()
        val classificationResult = classifier.classify(mpImage)
        val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
        return if (classificationResult != null) {
            ResultBundle(listOf(classificationResult), inferenceTimeMs, setupDurationMs)
        } else {
            imageClassifierListener?.onError("Image classifier failed to classify.")
            null
        }
    }

    /** Classifies sampled frames from the video at [videoUri]. */
    fun classifyVideoFile(videoUri: Uri, inferenceIntervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call classifyVideoFile while not using RunningMode.VIDEO"
            )
        }
        val classifier = imageClassifier ?: return null

        val startTime = SystemClock.uptimeMillis()
        var didErrorOccur = false
        val retriever = MediaMetadataRetriever()
        val resultList = mutableListOf<ImageClassifierResult>()
        val frameCount: Long

        try {
            retriever.setDataSource(context, videoUri)
            val videoLengthMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val firstFrame = retriever.getFrameAtTime(0)
            if (videoLengthMs == null || firstFrame == null) {
                return null
            }
            frameCount = (videoLengthMs / inferenceIntervalMs).coerceAtLeast(1L)

            for (i in 0..frameCount) {
                val timestampMs = i * inferenceIntervalMs
                val frame = retriever.getFrameAtTime(timestampMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST)
                if (frame != null) {
                    val argbFrame =
                        if (frame.config == Bitmap.Config.ARGB_8888) frame
                        else frame.copy(Bitmap.Config.ARGB_8888, false)
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val result = classifier.classifyForVideo(mpImage, timestampMs)
                    if (result != null) {
                        resultList.add(result)
                    } else {
                        didErrorOccur = true
                        imageClassifierListener?.onError(
                            "ResultBundle could not be returned in classifyVideoFile"
                        )
                    }
                } else {
                    didErrorOccur = true
                    imageClassifierListener?.onError(
                        "Frame at specified time could not be retrieved when classifying in video."
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
            ResultBundle(resultList, inferenceTimePerFrameMs, setupDurationMs)
        }
    }

    private fun returnLivestreamResult(result: ImageClassifierResult, @Suppress("UNUSED_PARAMETER") image: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        imageClassifierListener?.onResults(ResultBundle(listOf(result), inferenceTime, setupDurationMs))
    }

    private fun returnLivestreamError(error: RuntimeException) {
        imageClassifierListener?.onError(error.message ?: "An unknown error has occurred")
    }

    data class ResultBundle(
        val results: List<ImageClassifierResult>,
        val inferenceTime: Long,
        val loadTimeMs: Long = 0L,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_EFFICIENTNETV0 = 0
        const val MODEL_EFFICIENTNETV2 = 1
        const val MODEL_EFFICIENTNETV0_FILE = "efficientnet-lite0.tflite"
        const val MODEL_EFFICIENTNETV2_FILE = "efficientnet-lite2.tflite"
        const val SAMPLE_IMAGE_ASSET = "sample_cup.png"
        const val MAX_RESULTS_DEFAULT = 3
        const val THRESHOLD_DEFAULT = 0.5F
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val TAG = "ImageClassifierHelper"
    }

    interface ClassifierListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)

        fun onResults(resultBundle: ResultBundle)
    }
}
