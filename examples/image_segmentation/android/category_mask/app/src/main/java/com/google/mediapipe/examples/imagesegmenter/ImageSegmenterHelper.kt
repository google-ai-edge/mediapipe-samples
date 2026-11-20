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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.Inflater
import kotlin.math.roundToInt

/**
 * Helper that wraps [ImageSegmenter] configured with category mask (and optional per-category
 * confidence mask) output across DeepLabV3, Hair, Selfie, Selfie Landscape, and Selfie Multiclass
 * models.
 */
class ImageSegmenterHelper(
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    var currentModel: Int = MODEL_DEEPLABV3,
    val context: Context,
    var imageSegmenterListener: SegmenterListener? = null,
    var confidenceCategory: String? = null,
) {
    private var imagesegmenter: ImageSegmenter? = null

    /** Category names declared by the currently loaded segmentation model, in class-index order. */
    @Volatile
    var modelLabels: List<String> = emptyList()
        private set

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
        modelLabels = emptyList()
        val startTime = SystemClock.uptimeMillis()

        val modelPath = modelPathForIndex(currentModel)
        if (modelPath == null) {
            imageSegmenterListener?.onError("Unsupported model index: $currentModel")
            return false
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
                    .setOutputConfidenceMasks(confidenceCategory != null)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnSegmentationResult)
                    .setErrorListener(this::returnSegmentationHelperError)
            }

            val created = ImageSegmenter.createFromOptions(context, optionsBuilder.build())
            val labels = resolveModelLabels(created, context, modelPath)
            val requestedCategory = confidenceCategory
            if (requestedCategory != null && requestedCategory !in labels) {
                created.close()
                val msg = "Unknown confidence category \"$requestedCategory\"; model has $labels"
                imageSegmenterListener?.onError(msg)
                Log.e(TAG, msg)
                return false
            }
            imagesegmenter = created
            modelLabels = labels
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
                    "This model or device does not support GPU acceleration: $detail"
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
        val segmenter = imagesegmenter ?: return null
        val argbImage =
            if (image.config == Bitmap.Config.ARGB_8888) image
            else image.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val startTime = SystemClock.uptimeMillis()
            val mpImage = BitmapImageBuilder(argbImage).build()
            val result = segmenter.segment(mpImage)
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
        } catch (e: RuntimeException) {
            val detail = e.message ?: e.javaClass.simpleName
            imageSegmenterListener?.onError("Image segmenter failed: $detail")
            Log.e(TAG, "Image segmenter failed: $detail", e)
            null
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
        val segmenter = imagesegmenter ?: return null

        val startTime = SystemClock.uptimeMillis()
        var didErrorOccur = false

        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, videoUri)
            val videoLengthMs =
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?: return null
            val bundles = mutableListOf<ResultBundle>()
            val lastFrameIndex = ((videoLengthMs - 1L).coerceAtLeast(0L)) / inferenceIntervalMs

            for (i in 0..lastFrameIndex) {
                val timestampMs = i * inferenceIntervalMs
                val frame =
                    retriever.getFrameAtTime(
                        timestampMs * 1000L,
                        MediaMetadataRetriever.OPTION_CLOSEST,
                    )
                if (frame == null) {
                    if (bundles.isNotEmpty()) {
                        break
                    }
                    didErrorOccur = true
                    imageSegmenterListener?.onError(
                        "Frame could not be retrieved while segmenting video."
                    )
                    break
                }
                val argbFrame =
                    if (frame.config == Bitmap.Config.ARGB_8888) frame
                    else frame.copy(Bitmap.Config.ARGB_8888, false)
                try {
                    val mpImage = BitmapImageBuilder(argbFrame).build()
                    val result = segmenter.segmentForVideo(mpImage, timestampMs)
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
                } catch (e: RuntimeException) {
                    val detail = e.message ?: e.javaClass.simpleName
                    didErrorOccur = true
                    imageSegmenterListener?.onError("Video segmentation failed: $detail")
                    Log.e(TAG, "Video segmentation failed: $detail", e)
                    break
                } finally {
                    if (argbFrame !== frame) argbFrame.recycle()
                    frame.recycle()
                }
            }

            val totalInference =
                (SystemClock.uptimeMillis() - startTime) / bundles.size.coerceAtLeast(1).toLong()
            if (didErrorOccur || bundles.isEmpty()) null else VideoResultBundle(bundles, totalInference)
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
        val labels = modelLabels.ifEmpty { imagesegmenter?.labels?.toList().orEmpty() }
        val maskImage = result.categoryMask().orElse(null) ?: return null
        val maskWidth = maskImage.width
        val maskHeight = maskImage.height
        val pixelCount = maskWidth * maskHeight
        val byteBuffer = ByteBufferExtractor.extract(maskImage)
        val pixels = IntArray(pixelCount)
        val detectedIndices = linkedSetOf<Int>()

        val classCounts = IntArray(256)
        val classSumX = DoubleArray(256)
        val classSumY = DoubleArray(256)
        val singleClassForeground = labels.size == 1

        for (row in 0 until maskHeight) {
            val rowOffset = row * maskWidth
            for (col in 0 until maskWidth) {
                val i = rowOffset + col
                val rawIndex = byteBuffer.get(i).toInt() and 0xFF
                classCounts[rawIndex]++
                classSumX[rawIndex] += col.toDouble() + 0.5
                classSumY[rawIndex] += row.toDouble() + 0.5

                if (singleClassForeground) {
                    if (rawIndex == 0) {
                        val normalizedIndex = 15
                        detectedIndices.add(normalizedIndex)
                        pixels[i] = toAlphaColor(LABEL_COLORS[normalizedIndex % LABEL_COLORS.size])
                    } else {
                        pixels[i] = Color.TRANSPARENT
                    }
                } else {
                    if (rawIndex == 0) {
                        pixels[i] = Color.TRANSPARENT
                    } else {
                        val normalizedIndex = normalizeClassIndex(rawIndex, currentModel)
                        detectedIndices.add(normalizedIndex)
                        val baseColor = LABEL_COLORS[normalizedIndex % LABEL_COLORS.size]
                        pixels[i] = toAlphaColor(baseColor)
                    }
                }
            }
        }

        val hasBackgroundLabel = "background" in labels
        val classSegments = buildList {
            for (index in 0..255) {
                val count = classCounts[index]
                if (count == 0) continue
                val label =
                    when {
                        index < labels.size -> labels[index]
                        !hasBackgroundLabel -> "background"
                        else -> index.toString()
                    }
                add(
                    ClassSegmentSummary(
                        index = index,
                        label = label,
                        fraction = if (pixelCount > 0) count.toDouble() / pixelCount.toDouble() else 0.0,
                        centroidX = (classSumX[index] / count.toDouble()) / maskWidth.toDouble(),
                        centroidY = (classSumY[index] / count.toDouble()) / maskHeight.toDouble(),
                    )
                )
            }
        }

        val targetCategory = confidenceCategory
        val confidenceMasks = result.confidenceMasks().orElse(null)
        var confidenceSummary: ConfidenceMaskSummary? = null
        var categories: List<CategoryLegendItem> =
            detectedIndices.sorted().map { idx ->
                CategoryLegendItem(
                    label = labelForIndex(idx, currentModel),
                    argbColor = LABEL_COLORS[idx % LABEL_COLORS.size],
                )
            }

        if (targetCategory != null && !confidenceMasks.isNullOrEmpty()) {
            val catIndex = labels.indexOf(targetCategory)
            if (catIndex in confidenceMasks.indices) {
                val confImage = confidenceMasks[catIndex]
                val confW = confImage.width
                val confH = confImage.height
                val confTotal = confW * confH
                val floatBuf = ByteBufferExtractor.extract(confImage).asFloatBuffer()
                val channels =
                    if (confTotal > 0) (floatBuf.capacity() / confTotal).coerceAtLeast(1) else 1
                var minVal = Float.POSITIVE_INFINITY
                var maxVal = Float.NEGATIVE_INFINITY
                var nonFinite = 0
                var selectedCount = 0
                var selSumX = 0.0
                var selSumY = 0.0
                val colorIndex = if (singleClassForeground) 15 else (catIndex.coerceAtLeast(1))
                val baseColor = LABEL_COLORS[colorIndex % LABEL_COLORS.size]
                val baseRed = Color.red(baseColor)
                val baseGreen = Color.green(baseColor)
                val baseBlue = Color.blue(baseColor)
                val renderConfidenceOverlay = confW == maskWidth && confH == maskHeight

                for (row in 0 until confH) {
                    val rowOffset = row * confW
                    for (col in 0 until confW) {
                        val pixelIdx = rowOffset + col
                        val raw = floatBuf.get(pixelIdx * channels)
                        val clean =
                            if (raw.isFinite()) {
                                raw
                            } else {
                                nonFinite++
                                0f
                            }
                        if (clean < minVal) minVal = clean
                        if (clean > maxVal) maxVal = clean
                        if (clean > CONFIDENCE_THRESHOLD) {
                            selectedCount++
                            selSumX += col.toDouble() + 0.5
                            selSumY += row.toDouble() + 0.5
                        }
                        if (renderConfidenceOverlay) {
                            val alpha =
                                (clean.coerceIn(0f, 1f) * MAX_CONFIDENCE_ALPHA)
                                    .roundToInt()
                                    .coerceIn(0, 255)
                            pixels[pixelIdx] = Color.argb(alpha, baseRed, baseGreen, baseBlue)
                        }
                    }
                }
                if (confTotal == 0) {
                    minVal = 0f
                    maxVal = 0f
                }
                confidenceSummary =
                    ConfidenceMaskSummary(
                        category = targetCategory,
                        index = catIndex,
                        count = confidenceMasks.size,
                        width = confW,
                        height = confH,
                        channels = channels,
                        minConfidence = minVal.toDouble(),
                        maxConfidence = maxVal.toDouble(),
                        nonFiniteCount = nonFinite,
                        foregroundFraction =
                            if (confTotal > 0) selectedCount.toDouble() / confTotal.toDouble()
                            else 0.0,
                        centroidX =
                            if (selectedCount > 0) {
                                (selSumX / selectedCount.toDouble()) / confW.toDouble()
                            } else {
                                null
                            },
                        centroidY =
                            if (selectedCount > 0) {
                                (selSumY / selectedCount.toDouble()) / confH.toDouble()
                            } else {
                                null
                            },
                    )
                categories =
                    if (selectedCount > 0) {
                        listOf(CategoryLegendItem(label = targetCategory, argbColor = baseColor))
                    } else {
                        emptyList()
                    }
            }
        }

        val maskBitmap =
            Bitmap.createBitmap(
                pixels,
                maskWidth,
                maskHeight,
                Bitmap.Config.ARGB_8888,
            )
        return ResultBundle(
            maskBitmap = maskBitmap,
            detectedCategories = categories,
            maskWidth = maskWidth,
            maskHeight = maskHeight,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            inferenceTime = inferenceTimeMs,
            labels = labels,
            classSegments = classSegments,
            confidenceMask = confidenceSummary,
        )
    }

    private fun returnSegmentationHelperError(error: RuntimeException) {
        imageSegmenterListener?.onError(error.message ?: "An unknown error has occurred")
    }

    data class CategoryLegendItem(
        val label: String,
        val argbColor: Int,
    )

    data class ClassSegmentSummary(
        val index: Int,
        val label: String,
        val fraction: Double,
        val centroidX: Double,
        val centroidY: Double,
    )

    data class ConfidenceMaskSummary(
        val category: String,
        val index: Int,
        val count: Int,
        val width: Int,
        val height: Int,
        val channels: Int,
        val minConfidence: Double,
        val maxConfidence: Double,
        val nonFiniteCount: Int,
        val foregroundFraction: Double,
        val centroidX: Double?,
        val centroidY: Double?,
    )

    data class ResultBundle(
        val maskBitmap: Bitmap,
        val detectedCategories: List<CategoryLegendItem>,
        val maskWidth: Int,
        val maskHeight: Int,
        val inputWidth: Int,
        val inputHeight: Int,
        val inferenceTime: Long,
        val labels: List<String> = emptyList(),
        val classSegments: List<ClassSegmentSummary> = emptyList(),
        val confidenceMask: ConfidenceMaskSummary? = null,
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
        const val MODEL_SELFIE_LANDSCAPE = 4

        const val MODEL_DEEPLABV3_PATH = "deeplabv3.tflite"
        const val MODEL_HAIR_SEGMENTER_PATH = "hair_segmenter.tflite"
        const val MODEL_SELFIE_MULTICLASS_PATH = "selfie_multiclass.tflite"
        const val MODEL_SELFIE_SEGMENTER_PATH = "selfie_segmenter.tflite"
        const val MODEL_SELFIE_LANDSCAPE_PATH = "selfie_segmenter_landscape.tflite"

        private const val ALPHA_COLOR = 128
        private const val CONFIDENCE_THRESHOLD = 0.5f
        private const val MAX_CONFIDENCE_ALPHA = 205f
        private val cachedModelLabels = ConcurrentHashMap<String, List<String>>()

        fun modelPathForIndex(modelIndex: Int): String? =
            when (modelIndex) {
                MODEL_DEEPLABV3 -> MODEL_DEEPLABV3_PATH
                MODEL_HAIR_SEGMENTER -> MODEL_HAIR_SEGMENTER_PATH
                MODEL_SELFIE_SEGMENTER -> MODEL_SELFIE_SEGMENTER_PATH
                MODEL_SELFIE_MULTICLASS -> MODEL_SELFIE_MULTICLASS_PATH
                MODEL_SELFIE_LANDSCAPE -> MODEL_SELFIE_LANDSCAPE_PATH
                else -> null
            }

        /**
         * Reads the model's label list, preferring [ImageSegmenter.getLabels] and falling back to
         * the embedded TFLite Metadata `labels.txt` / `labelmap.txt` associated file when MediaPipe
         * Java's graph-node name lookup returns an empty list.
         */
        fun resolveModelLabels(
            segmenter: ImageSegmenter?,
            context: Context,
            modelPath: String,
        ): List<String> {
            val apiLabels = segmenter?.labels?.filter { it.isNotEmpty() }.orEmpty()
            if (apiLabels.isNotEmpty()) return apiLabels
            return labelsForAsset(context, modelPath)
        }

        fun labelsForModel(context: Context, modelIndex: Int): List<String> {
            val modelPath = modelPathForIndex(modelIndex) ?: return emptyList()
            return labelsForAsset(context, modelPath)
        }

        private fun labelsForAsset(context: Context, modelPath: String): List<String> =
            cachedModelLabels.getOrPut(modelPath) {
                runCatching {
                    val bytes = context.assets.open(modelPath).use { it.readBytes() }
                    extractLabelsFromTfliteBytes(bytes)
                }.getOrDefault(emptyList())
            }

        /**
         * Extracts `labels.txt` (or `labelmap.txt`) from the TFLite Metadata ZIP archive appended
         * at the end of [bytes]. Parses the ZIP End-of-Central-Directory and Central Directory
         * directly because Android's `libziparchive` (`ZipFile`) rejects non-zero FlatBuffer
         * preambles.
         */
        internal fun extractLabelsFromTfliteBytes(bytes: ByteArray): List<String> {
            val minEocdOffset = (bytes.size - 65_557).coerceAtLeast(0)
            var eocdOffset = -1
            for (i in bytes.size - 22 downTo minEocdOffset) {
                if (
                    bytes[i] == 0x50.toByte() &&
                        bytes[i + 1] == 0x4b.toByte() &&
                        bytes[i + 2] == 0x05.toByte() &&
                        bytes[i + 3] == 0x06.toByte()
                ) {
                    eocdOffset = i
                    break
                }
            }
            if (eocdOffset < 0) return emptyList()

            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val totalEntries = buf.getShort(eocdOffset + 10).toInt() and 0xFFFF
            val cdOffset = buf.getInt(eocdOffset + 16)
            if (cdOffset < 0 || cdOffset >= bytes.size) return emptyList()

            var pos = cdOffset
            var fallbackPayload: ByteArray? = null
            for (entryIdx in 0 until totalEntries) {
                if (pos + 46 > bytes.size || buf.getInt(pos) != 0x02014b50) break
                val method = buf.getShort(pos + 10).toInt() and 0xFFFF
                val compSize = buf.getInt(pos + 20)
                val uncompSize = buf.getInt(pos + 24)
                val nameLen = buf.getShort(pos + 28).toInt() and 0xFFFF
                val extraLen = buf.getShort(pos + 30).toInt() and 0xFFFF
                val commentLen = buf.getShort(pos + 32).toInt() and 0xFFFF
                val localHeaderOffset = buf.getInt(pos + 42)
                if (pos + 46 + nameLen > bytes.size) break
                val entryName = String(bytes, pos + 46, nameLen, Charsets.UTF_8)
                pos += 46 + nameLen + extraLen + commentLen

                if (entryName != "labels.txt" && entryName != "labelmap.txt") continue
                if (localHeaderOffset < 0 || localHeaderOffset + 30 > bytes.size) continue
                if (buf.getInt(localHeaderOffset) != 0x04034b50) continue
                val lhNameLen = buf.getShort(localHeaderOffset + 26).toInt() and 0xFFFF
                val lhExtraLen = buf.getShort(localHeaderOffset + 28).toInt() and 0xFFFF
                val dataStart = localHeaderOffset + 30 + lhNameLen + lhExtraLen
                if (compSize < 0 || dataStart + compSize > bytes.size) continue

                val decoded =
                    when (method) {
                        0 -> bytes.copyOfRange(dataStart, dataStart + compSize)
                        8 -> {
                            val inflater = Inflater(true)
                            try {
                                inflater.setInput(bytes, dataStart, compSize)
                                val out = ByteArray(uncompSize.coerceAtLeast(0))
                                val count = inflater.inflate(out)
                                if (count == out.size) out else out.copyOf(count)
                            } finally {
                                inflater.end()
                            }
                        }
                        else -> null
                    } ?: continue

                if (entryName == "labels.txt") {
                    return decoded
                        .toString(Charsets.UTF_8)
                        .lineSequence()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                }
                fallbackPayload = decoded
            }
            return fallbackPayload
                ?.toString(Charsets.UTF_8)
                ?.lineSequence()
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toList()
                .orEmpty()
        }

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
                model == MODEL_SELFIE_SEGMENTER || model == MODEL_SELFIE_LANDSCAPE -> 15
                rawIndex in 1..20 -> rawIndex
                else -> (rawIndex % 19) + 1
            }

        internal fun labelForIndex(index: Int, model: Int): String =
            when (model) {
                MODEL_HAIR_SEGMENTER -> if (index == 0) "Background" else "Hair"
                MODEL_SELFIE_SEGMENTER, MODEL_SELFIE_LANDSCAPE ->
                    if (index == 0) "Background" else "Person"
                MODEL_SELFIE_MULTICLASS ->
                    SELFIE_MULTICLASS_LABELS.getOrElse(index) { "Class $index" }
                else -> DEEPLAB_LABELS.getOrElse(index) { "Class $index" }
            }
    }
}
