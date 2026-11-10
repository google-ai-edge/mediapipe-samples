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
package com.google.mediapipe.examples.imagesegmenter

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult

/**
 * Helper that wraps [ImageSegmenter] configured with category mask output across DeepLabV3, Hair,
 * Selfie, and Selfie Multiclass models.
 */
class ImageSegmenterHelper(
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    var currentModel: Int = MODEL_DEEPLABV3,
    val context: Context,
    var imageSegmenterListener: SegmenterListener? = null,
) {
    private var imagesegmenter: ImageSegmenter? = null

    /** Duration of the most recent successful [setupImageSegmenter] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isClosed: Boolean
        get() = imagesegmenter == null

    init {
        setupImageSegmenter()
    }

    fun clearImageSegmenter() {
        imagesegmenter?.close()
        imagesegmenter = null
    }

    fun setListener(listener: SegmenterListener) {
        imageSegmenterListener = listener
    }

    fun clearListener() {
        imageSegmenterListener = null
    }

    /**
     * Initializes [ImageSegmenter] on the calling thread. When using [DELEGATE_GPU], callers should
     * invoke this on the same thread that submits frames.
     */
    fun setupImageSegmenter(): Boolean {
        clearImageSegmenter()
        val startTime = SystemClock.uptimeMillis()

        val modelPath =
            when (currentModel) {
                MODEL_DEEPLABV3 -> MODEL_DEEPLABV3_PATH
                MODEL_HAIR_SEGMENTER -> MODEL_HAIR_SEGMENTER_PATH
                MODEL_SELFIE_SEGMENTER -> MODEL_SELFIE_SEGMENTER_PATH
                MODEL_SELFIE_MULTICLASS -> MODEL_SELFIE_MULTICLASS_PATH
                else -> {
                    imageSegmenterListener?.onError("Unsupported model index: $currentModel")
                    return false
                }
            }

        val baseOptionsBuilder = BaseOptions.builder().setModelAssetPath(modelPath)
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> {
                imageSegmenterListener?.onError("Unsupported delegate index: $currentDelegate")
                return false
            }
        }

        if (runningMode == RunningMode.LIVE_STREAM && imageSegmenterListener == null) {
            throw IllegalStateException(
                "imageSegmenterListener must be set when runningMode is LIVE_STREAM."
            )
        }

        return try {
            val optionsBuilder =
                ImageSegmenter.ImageSegmenterOptions.builder()
                    .setRunningMode(runningMode)
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setOutputCategoryMask(true)
                    .setOutputConfidenceMasks(false)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnSegmentationResult)
                    .setErrorListener(this::returnSegmentationHelperError)
            }

            imagesegmenter = ImageSegmenter.createFromOptions(context, optionsBuilder.build())
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            imageSegmenterListener?.onError("Image segmenter failed to initialize: $detail")
            Log.e(TAG, "Image segmenter failed to load model with error: $detail", e)
            false
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            imageSegmenterListener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration."
                } else {
                    "Image segmenter failed to initialize: $detail"
                },
                errorCode,
            )
            Log.e(TAG, "Image segmenter failed to load model with error: $detail", e)
            false
        }
    }

    /** Converts a CameraX RGBA [ImageProxy] to an upright bitmap and runs async segmentation. */
    fun segmentLiveStreamFrame(imageProxy: ImageProxy, isFrontCamera: Boolean = false) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            imageProxy.close()
            throw IllegalArgumentException(
                "Attempting to call segmentLiveStreamFrame while not using RunningMode.LIVE_STREAM"
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
        segmentAsync(mpImage, frameTime)
    }

    @VisibleForTesting
    fun segmentAsync(mpImage: MPImage, frameTime: Long) {
        imagesegmenter?.segmentAsync(mpImage, frameTime)
    }

    fun segmentImageFile(mpImage: MPImage): ImageSegmenterResult? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call segmentImageFile while not using RunningMode.IMAGE"
            )
        }
        return imagesegmenter?.segment(mpImage)
    }

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
            val result = imagesegmenter?.segment(mpImage)
            if (result != null) {
                val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
                buildResultBundle(
                    result = result,
                    inferenceTimeMs = inferenceTimeMs,
                    inputWidth = argbImage.width,
                    inputHeight = argbImage.height,
                )
            } else {
                imageSegmenterListener?.onError("Image segmenter failed to segment image.")
                null
            }
        } finally {
            if (argbImage !== image) argbImage.recycle()
        }
    }

    fun segmentVideoFile(mpImage: MPImage, timestampMs: Long = SystemClock.uptimeMillis()): ImageSegmenterResult? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call segmentVideoFile while not using RunningMode.VIDEO"
            )
        }
        return imagesegmenter?.segmentForVideo(mpImage, timestampMs)
    }

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
                    ?: return null
            val bundles = mutableListOf<ResultBundle>()
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
                    imageSegmenterListener?.onError(
                        "Frame could not be retrieved while segmenting video."
                    )
                    continue
                }
                val argbFrame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)
                try {
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val result = imagesegmenter?.segmentForVideo(mpImage, timestampMs)
                    val bundle =
                        result?.let {
                            buildResultBundle(
                                result = it,
                                inferenceTimeMs = inferenceIntervalMs,
                                inputWidth = argbFrame.width,
                                inputHeight = argbFrame.height,
                            )
                        }
                    if (bundle != null) {
                        bundles.add(bundle)
                    } else {
                        didErrorOccur = true
                        imageSegmenterListener?.onError(
                            "Result could not be returned in detectVideoFile."
                        )
                    }
                } finally {
                    if (argbFrame !== frame) argbFrame.recycle()
                    frame.recycle()
                }
            }

            val totalInference =
                (SystemClock.uptimeMillis() - startTime) / (lastFrameIndex + 1).coerceAtLeast(1L)
            if (didErrorOccur) null else VideoResultBundle(bundles, totalInference)
        } finally {
            retriever.release()
        }
    }

    private fun returnSegmentationResult(result: ImageSegmenterResult, image: MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        val bundle =
            buildResultBundle(
                result = result,
                inferenceTimeMs = inferenceTime,
                inputWidth = image.width,
                inputHeight = image.height,
            )
        if (bundle != null) {
            imageSegmenterListener?.onResults(bundle)
        }
    }

    private fun buildResultBundle(
        result: ImageSegmenterResult,
        inferenceTimeMs: Long,
        inputWidth: Int,
        inputHeight: Int,
    ): ResultBundle? {
        val maskImage = result.categoryMask().orElse(null) ?: return null
        val byteBuffer = ByteBufferExtractor.extract(maskImage)
        val capacity = byteBuffer.capacity()
        val pixels = IntArray(capacity)
        val detectedIndices = linkedSetOf<Int>()

        for (i in 0 until capacity) {
            val rawIndex = byteBuffer.get(i).toInt() and 0xFF
            if (rawIndex == 0) {
                pixels[i] = Color.TRANSPARENT
                continue
            }
            val normalizedIndex = normalizeClassIndex(rawIndex, currentModel)
            detectedIndices.add(normalizedIndex)
            val baseColor = LABEL_COLORS[normalizedIndex % LABEL_COLORS.size]
            pixels[i] = toAlphaColor(baseColor)
        }

        val maskBitmap =
            Bitmap.createBitmap(
                pixels,
                maskImage.width,
                maskImage.height,
                Bitmap.Config.ARGB_8888,
            )
        val categories =
            detectedIndices.sorted().map { idx ->
                CategoryLegendItem(
                    label = labelForIndex(idx, currentModel),
                    argbColor = LABEL_COLORS[idx % LABEL_COLORS.size],
                )
            }
        return ResultBundle(
            maskBitmap = maskBitmap,
            detectedCategories = categories,
            maskWidth = maskImage.width,
            maskHeight = maskImage.height,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            inferenceTime = inferenceTimeMs,
        )
    }

    private fun returnSegmentationHelperError(error: RuntimeException) {
        imageSegmenterListener?.onError(error.message ?: "An unknown error has occurred")
    }

    data class CategoryLegendItem(
        val label: String,
        val argbColor: Int,
    )

    data class ResultBundle(
        val maskBitmap: Bitmap,
        val detectedCategories: List<CategoryLegendItem>,
        val maskWidth: Int,
        val maskHeight: Int,
        val inputWidth: Int,
        val inputHeight: Int,
        val inferenceTime: Long,
    )

    data class VideoResultBundle(
        val results: List<ResultBundle>,
        val inferenceTime: Long,
    )

    interface SegmenterListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }

    companion object {
        const val TAG = "ImageSegmenterHelper"
        const val SAMPLE_IMAGE_ASSET = "sample_segmentation.jpg"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val MODEL_DEEPLABV3 = 0
        const val MODEL_HAIR_SEGMENTER = 1
        const val MODEL_SELFIE_SEGMENTER = 2
        const val MODEL_SELFIE_MULTICLASS = 3

        const val MODEL_DEEPLABV3_PATH = "deeplabv3.tflite"
        const val MODEL_HAIR_SEGMENTER_PATH = "hair_segmenter.tflite"
        const val MODEL_SELFIE_MULTICLASS_PATH = "selfie_multiclass.tflite"
        const val MODEL_SELFIE_SEGMENTER_PATH = "selfie_segmenter.tflite"

        private const val ALPHA_COLOR = 128

        val LABEL_COLORS =
            listOf(
                -16777216,
                -8388608,
                -16744448,
                -8355840,
                -16777088,
                -8388480,
                -16744320,
                -8355712,
                -12582912,
                -4194304,
                -12550144,
                -4161536,
                -12582784,
                -4194176,
                -12550016,
                -4161408,
                -16760832,
                -8372224,
                -16728064,
                -8339456,
                -16760704,
            )

        private val DEEPLAB_LABELS =
            listOf(
                "Background",
                "Aeroplane",
                "Bicycle",
                "Bird",
                "Boat",
                "Bottle",
                "Bus",
                "Car",
                "Cat",
                "Chair",
                "Cow",
                "Dining table",
                "Dog",
                "Horse",
                "Motorbike",
                "Person",
                "Potted plant",
                "Sheep",
                "Sofa",
                "Train",
                "TV/Monitor",
            )

        private val SELFIE_MULTICLASS_LABELS =
            listOf("Background", "Hair", "Body skin", "Face skin", "Clothes", "Accessories")

        fun toAlphaColor(color: Int): Int =
            Color.argb(ALPHA_COLOR, Color.red(color), Color.green(color), Color.blue(color))

        internal fun normalizeClassIndex(rawIndex: Int, model: Int): Int =
            when {
                rawIndex == 0 -> 0
                model == MODEL_SELFIE_SEGMENTER -> 15
                rawIndex in 1..20 -> rawIndex
                else -> (rawIndex % 19) + 1
            }

        internal fun labelForIndex(index: Int, model: Int): String =
            when (model) {
                MODEL_HAIR_SEGMENTER -> if (index == 0) "Background" else "Hair"
                MODEL_SELFIE_SEGMENTER -> if (index == 0) "Background" else "Person"
                MODEL_SELFIE_MULTICLASS ->
                    SELFIE_MULTICLASS_LABELS.getOrElse(index) { "Class $index" }
                else -> DEEPLAB_LABELS.getOrElse(index) { "Class $index" }
            }
    }
}
