/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.poselandmarker

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
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import java.nio.ByteOrder

/**
 * Helper that wraps [PoseLandmarker] lifecycle, model/delegate/threshold options, and
 * image/video/camera inference.
 */
class PoseLandmarkerHelper(
    var minPoseDetectionConfidence: Float = DEFAULT_POSE_DETECTION_CONFIDENCE,
    var minPoseTrackingConfidence: Float = DEFAULT_POSE_TRACKING_CONFIDENCE,
    var minPosePresenceConfidence: Float = DEFAULT_POSE_PRESENCE_CONFIDENCE,
    var outputSegmentationMasks: Boolean = DEFAULT_OUTPUT_SEGMENTATION_MASKS,
    var currentModel: Int = MODEL_POSE_LANDMARKER_FULL,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    val context: Context,
    val poseLandmarkerHelperListener: LandmarkerListener? = null,
) {
    private var poseLandmarker: PoseLandmarker? = null

    /** Duration of the most recent successful [setupPoseLandmarker] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isClosed: Boolean
        get() = poseLandmarker == null

    init {
        setupPoseLandmarker()
    }

    fun clearPoseLandmarker() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    /**
     * Initializes [PoseLandmarker] on the calling thread. When using [DELEGATE_GPU], callers should
     * invoke this on the same thread that submits frames.
     */
    fun setupPoseLandmarker(): Boolean {
        clearPoseLandmarker()
        val startTime = SystemClock.uptimeMillis()

        val modelName =
            when (currentModel) {
                MODEL_POSE_LANDMARKER_FULL -> MODEL_FULL_PATH
                MODEL_POSE_LANDMARKER_LITE -> MODEL_LITE_PATH
                MODEL_POSE_LANDMARKER_HEAVY -> MODEL_HEAVY_PATH
                else -> {
                    poseLandmarkerHelperListener?.onError("Unsupported model index: $currentModel")
                    return false
                }
            }
        val baseOptionBuilder = BaseOptions.builder().setModelAssetPath(modelName)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionBuilder.setDelegate(Delegate.GPU)
            else -> {
                poseLandmarkerHelperListener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        if (runningMode == RunningMode.LIVE_STREAM && poseLandmarkerHelperListener == null) {
            throw IllegalStateException(
                "poseLandmarkerHelperListener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val optionsBuilder =
                PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionBuilder.build())
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setOutputSegmentationMasks(outputSegmentationMasks)
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            poseLandmarker = PoseLandmarker.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            poseLandmarkerHelperListener?.onError("Pose Landmarker failed to initialize: $detail")
            Log.e(TAG, "MediaPipe failed to load the task with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            poseLandmarkerHelperListener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Pose Landmarker failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Pose Landmarker failed to load model with error: $detail", e)
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
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    /** Samples frames from [videoUri] every [inferenceIntervalMs] ms and returns detection results. */
    fun detectVideoFile(videoUri: Uri, inferenceIntervalMs: Long): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call detectVideoFile while not using RunningMode.VIDEO"
            )
        }
        require(inferenceIntervalMs > 0L) { "inferenceIntervalMs must be positive." }

        val landmarker = poseLandmarker ?: return null
        val startTime = SystemClock.uptimeMillis()
        var didErrorOccur = false

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val videoLengthMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val firstFrame =
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(0)
            if (videoLengthMs == null || firstFrame == null) {
                firstFrame?.recycle()
                return null
            }
            var width: Int = firstFrame.width
            var height: Int = firstFrame.height
            firstFrame.recycle()

            val resultList = mutableListOf<PoseLandmarkerResult>()
            val maskList = mutableListOf<SegmentationMaskSummary?>()
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
                    poseLandmarkerHelperListener?.onError(
                        "Frame at specified time could not be retrieved when detecting in video."
                    )
                    continue
                }
                val argbFrame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)
                try {
                    width = argbFrame.width
                    height = argbFrame.height
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val detectionResult = landmarker.detectForVideo(mpImage, timestampMs)
                    if (detectionResult != null) {
                        resultList.add(detectionResult)
                        maskList.add(
                            detectionResult
                                .segmentationMasks()
                                .orElse(null)
                                ?.firstOrNull()
                                ?.let(::summarizeSegmentationMask)
                        )
                    } else {
                        didErrorOccur = true
                        poseLandmarkerHelperListener?.onError(
                            "ResultBundle could not be returned in detectVideoFile"
                        )
                    }
                } catch (e: RuntimeException) {
                    didErrorOccur = true
                    val detail = e.message ?: "RuntimeException"
                    poseLandmarkerHelperListener?.onError("Pose Landmarker failed on video frame: $detail")
                    Log.e(TAG, "Pose Landmarker failed on video frame with error: $detail", e)
                    break
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
                ResultBundle(
                    results = resultList,
                    inferenceTime = inferenceTimePerFrameMs,
                    inputImageHeight = height,
                    inputImageWidth = width,
                    segmentationMasks = maskList,
                )
            }
        } finally {
            retriever.release()
        }
    }

    /** Runs pose landmark detection on a single still [Bitmap] in [RunningMode.IMAGE]. */
    fun detectImage(image: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call detectImage while not using RunningMode.IMAGE"
            )
        }
        val landmarker = poseLandmarker ?: return null
        val argbImage =
            if (image.config == Bitmap.Config.ARGB_8888) image
            else image.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val startTime = SystemClock.uptimeMillis()
            val mpImage = BitmapImageBuilder(argbImage).build()
            val landmarkResult = landmarker.detect(mpImage)
            if (landmarkResult != null) {
                val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
                val maskSummary =
                    landmarkResult
                        .segmentationMasks()
                        .orElse(null)
                        ?.firstOrNull()
                        ?.let(::summarizeSegmentationMask)
                ResultBundle(
                    results = listOf(landmarkResult),
                    inferenceTime = inferenceTimeMs,
                    inputImageHeight = argbImage.height,
                    inputImageWidth = argbImage.width,
                    segmentationMasks = listOf(maskSummary),
                )
            } else {
                poseLandmarkerHelperListener?.onError("Pose Landmarker failed to detect.")
                null
            }
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            poseLandmarkerHelperListener?.onError("Pose Landmarker failed to detect: $detail")
            Log.e(TAG, "Pose Landmarker failed to detect image with error: $detail", e)
            null
        } finally {
            if (argbImage !== image) argbImage.recycle()
        }
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        val maskSummary =
            result
                .segmentationMasks()
                .orElse(null)
                ?.firstOrNull()
                ?.let(::summarizeSegmentationMask)
        poseLandmarkerHelperListener?.onResults(
            ResultBundle(
                results = listOf(result),
                inferenceTime = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width,
                segmentationMasks = listOf(maskSummary),
            )
        )
    }

    private fun returnLivestreamError(error: RuntimeException) {
        poseLandmarkerHelperListener?.onError(error.message ?: "An unknown error has occurred")
    }

    companion object {
        const val TAG = "PoseLandmarkerHelper"
        const val SAMPLE_IMAGE_ASSET = "sample_pose.jpg"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DEFAULT_POSE_DETECTION_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_TRACKING_CONFIDENCE = 0.5F
        const val DEFAULT_POSE_PRESENCE_CONFIDENCE = 0.5F
        const val DEFAULT_OUTPUT_SEGMENTATION_MASKS = true
        const val DEFAULT_NUM_POSES = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        const val MODEL_POSE_LANDMARKER_FULL = 0
        const val MODEL_POSE_LANDMARKER_LITE = 1
        const val MODEL_POSE_LANDMARKER_HEAVY = 2
        const val MODEL_FULL_PATH = "pose_landmarker_full.task"
        const val MODEL_LITE_PATH = "pose_landmarker_lite.task"
        const val MODEL_HEAVY_PATH = "pose_landmarker_heavy.task"
        private const val MASK_THRESHOLD = 0.5f

        internal fun summarizeSegmentationMask(maskImage: MPImage): SegmentationMaskSummary {
            val width = maskImage.width
            val height = maskImage.height
            val totalPixels = width * height
            val floatBuffer =
                ByteBufferExtractor.extract(maskImage).order(ByteOrder.nativeOrder()).asFloatBuffer()
            val capacity = floatBuffer.capacity()
            val channels = if (totalPixels > 0) (capacity / totalPixels).coerceAtLeast(1) else 1

            if (totalPixels <= 0 || capacity < totalPixels) {
                return SegmentationMaskSummary(
                    width = width,
                    height = height,
                    channels = channels,
                    min = 0.0,
                    max = 0.0,
                    nonFinite = 0,
                    fraction = 0.0,
                    centroidX = null,
                    centroidY = null,
                )
            }

            var minVal = Double.POSITIVE_INFINITY
            var maxVal = Double.NEGATIVE_INFINITY
            var nonFinite = 0
            var selectedCount = 0L
            var sumX = 0.0
            var sumY = 0.0

            for (y in 0 until height) {
                val rowBase = y * width * channels
                for (x in 0 until width) {
                    val pixelBase = rowBase + x * channels
                    for (c in 0 until channels) {
                        val raw = floatBuffer.get(pixelBase + c)
                        val clean =
                            if (raw.isFinite()) {
                                raw.toDouble()
                            } else {
                                nonFinite++
                                0.0
                            }
                        if (clean < minVal) minVal = clean
                        if (clean > maxVal) maxVal = clean
                    }
                    val raw0 = floatBuffer.get(pixelBase)
                    val planeVal = if (raw0.isFinite()) raw0 else 0f
                    if (planeVal > MASK_THRESHOLD) {
                        selectedCount++
                        sumX += x.toDouble()
                        sumY += y.toDouble()
                    }
                }
            }

            val fraction = selectedCount.toDouble() / totalPixels.toDouble()
            val centroidX =
                if (selectedCount > 0L) ((sumX / selectedCount.toDouble()) + 0.5) / width.toDouble()
                else null
            val centroidY =
                if (selectedCount > 0L) ((sumY / selectedCount.toDouble()) + 0.5) / height.toDouble()
                else null

            return SegmentationMaskSummary(
                width = width,
                height = height,
                channels = channels,
                min = if (minVal.isFinite()) minVal else 0.0,
                max = if (maxVal.isFinite()) maxVal else 0.0,
                nonFinite = nonFinite,
                fraction = fraction,
                centroidX = centroidX,
                centroidY = centroidY,
            )
        }
    }

    data class SegmentationMaskSummary(
        val width: Int,
        val height: Int,
        val channels: Int,
        val min: Double,
        val max: Double,
        val nonFinite: Int,
        val fraction: Double,
        val centroidX: Double?,
        val centroidY: Double?,
    )

    data class ResultBundle(
        val results: List<PoseLandmarkerResult>,
        val inferenceTime: Long,
        val inputImageHeight: Int,
        val inputImageWidth: Int,
        val segmentationMasks: List<SegmentationMaskSummary?> = emptyList(),
    )

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
