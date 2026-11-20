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
package com.mediapipe.example.interactivesegmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Helper that wraps [InteractiveSegmenter] lifecycle, image binding, and stroke-based interactive
 * segmentation inference.
 */
class InteractiveSegmentationHelper(
    val context: Context,
    var listener: InteractiveSegmentationListener? = null,
    var currentDelegate: Int = DELEGATE_CPU,
) {
    private var interactiveSegmenter: InteractiveSegmenter? = null

    /** Duration of the most recent successful [setupInteractiveSegmenter] call, in milliseconds. */
    @Volatile
    var setupDurationMs: Long = 0L
        private set

    val isClosed: Boolean
        get() = interactiveSegmenter == null

    init {
        setupInteractiveSegmenter()
    }

    fun clearInteractiveSegmenter() {
        interactiveSegmenter?.close()
        interactiveSegmenter = null
    }

    /** Initializes [InteractiveSegmenter] using the bundled MagicTouch task model. */
    fun setupInteractiveSegmenter(): Boolean {
        clearInteractiveSegmenter()
        val startTime = SystemClock.uptimeMillis()
        val delegate =
            when (currentDelegate) {
                DELEGATE_CPU -> Delegate.CPU
                DELEGATE_GPU -> Delegate.GPU
                else -> {
                    listener?.onError("Unsupported delegate index: $currentDelegate")
                    return false
                }
            }
        return try {
            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(MP_INTERACTIVE_SEGMENTATION_MODEL)
                    .setDelegate(delegate)
                    .build()
            val options =
                InteractiveSegmenterOptions.builder()
                    .setBaseOptions(baseOptions)
                    .build()
            interactiveSegmenter = InteractiveSegmenter.createFromOptions(context, options)
            setupDurationMs = SystemClock.uptimeMillis() - startTime
            true
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            listener?.onError(
                "Interactive segmenter failed to initialize: $message",
                errorCode,
            )
            Log.e(TAG, "Interactive segmenter failed to initialize: $message", e)
            false
        }
    }

    /** Sets the target image for subsequent [segment] calls. */
    fun setInputImage(bitmap: Bitmap): Boolean {
        val segmenter = interactiveSegmenter ?: return false
        val argbBitmap =
            if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            val mpImage = BitmapImageBuilder(argbBitmap).build()
            segmenter.setImage(mpImage)
            true
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            listener?.onError("Failed to set input image: $message")
            Log.e(TAG, "Failed to set input image: $message", e)
            false
        } finally {
            if (argbBitmap !== bitmap) argbBitmap.recycle()
        }
    }

    /** Runs interactive segmentation for [strokes] against the currently bound image. */
    fun segment(strokes: List<Stroke>): ResultBundle? {
        val segmenter = interactiveSegmenter
        if (segmenter == null) {
            listener?.onError("Interactive segmenter is not initialized.")
            return null
        }
        if (strokes.isEmpty()) {
            listener?.onResults(null)
            return null
        }
        return try {
            val startTime = SystemClock.uptimeMillis()
            val resultImage = segmenter.segment(strokes)
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime

            val width = resultImage.width
            val height = resultImage.height
            val floatBuffer = ByteBufferExtractor.extract(resultImage).asFloatBuffer()
            val pixelCount = width * height
            val channels = if (pixelCount > 0) (floatBuffer.capacity() / pixelCount).coerceAtLeast(1) else 1
            val pixels = IntArray(pixelCount)
            var selectedCount = 0
            var nonFiniteCount = 0
            var minConfidence = Float.POSITIVE_INFINITY
            var maxConfidence = Float.NEGATIVE_INFINITY
            var sumSelectedX = 0.0
            var sumSelectedY = 0.0
            var minCol = width
            var maxCol = -1
            var minRow = height
            var maxRow = -1

            for (row in 0 until height) {
                val rowOffset = row * width
                for (col in 0 until width) {
                    val pixelIndex = rowOffset + col
                    val raw = floatBuffer.get(pixelIndex * channels)
                    val conf =
                        if (raw.isFinite()) {
                            raw
                        } else {
                            nonFiniteCount++
                            0f
                        }
                    if (conf < minConfidence) minConfidence = conf
                    if (conf > maxConfidence) maxConfidence = conf
                    if (conf > FOREGROUND_THRESHOLD) {
                        selectedCount++
                        sumSelectedX += col.toDouble() + 0.5
                        sumSelectedY += row.toDouble() + 0.5
                        if (col < minCol) minCol = col
                        if (col > maxCol) maxCol = col
                        if (row < minRow) minRow = row
                        if (row > maxRow) maxRow = row
                        pixels[pixelIndex] = SELECTED_OVERLAY_COLOR
                    } else {
                        pixels[pixelIndex] = Color.TRANSPARENT
                    }
                }
            }
            if (pixelCount == 0) {
                minConfidence = 0f
                maxConfidence = 0f
            }

            val maskBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            val selectedPercent =
                if (pixelCount > 0) ((selectedCount * 100f) / pixelCount).roundToInt() else 0
            val foregroundFraction =
                if (pixelCount > 0) selectedCount.toDouble() / pixelCount.toDouble() else 0.0

            val bundle =
                ResultBundle(
                    maskBitmap = maskBitmap,
                    maskWidth = width,
                    maskHeight = height,
                    selectedPercent = selectedPercent,
                    inferenceTimeMs = inferenceTimeMs,
                    channels = channels,
                    minConfidence = minConfidence.toDouble(),
                    maxConfidence = maxConfidence.toDouble(),
                    nonFiniteCount = nonFiniteCount,
                    foregroundFraction = foregroundFraction,
                    centroidX =
                        if (selectedCount > 0) (sumSelectedX / selectedCount.toDouble()) / width.toDouble()
                        else null,
                    centroidY =
                        if (selectedCount > 0) (sumSelectedY / selectedCount.toDouble()) / height.toDouble()
                        else null,
                    boundsMinX = if (selectedCount > 0) minCol.toDouble() / width.toDouble() else null,
                    boundsMaxX = if (selectedCount > 0) (maxCol + 1).toDouble() / width.toDouble() else null,
                    boundsMinY = if (selectedCount > 0) minRow.toDouble() / height.toDouble() else null,
                    boundsMaxY = if (selectedCount > 0) (maxRow + 1).toDouble() / height.toDouble() else null,
                )
            listener?.onResults(bundle)
            bundle
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            listener?.onError("Segmentation failed: $message")
            Log.e(TAG, "Segmentation failed: $message", e)
            null
        }
    }

    data class ResultBundle(
        val maskBitmap: Bitmap,
        val maskWidth: Int,
        val maskHeight: Int,
        val selectedPercent: Int = 0,
        val inferenceTimeMs: Long = 0L,
        val channels: Int = 1,
        val minConfidence: Double = 0.0,
        val maxConfidence: Double = 0.0,
        val nonFiniteCount: Int = 0,
        val foregroundFraction: Double = 0.0,
        val centroidX: Double? = null,
        val centroidY: Double? = null,
        val boundsMinX: Double? = null,
        val boundsMaxX: Double? = null,
        val boundsMinY: Double? = null,
        val boundsMaxY: Double? = null,
    ) {
        /** Returns whether normalized `(normX, normY)` in `[0..1]` has mask confidence `> 0.5`. */
        fun isSelectedAtNormalized(normX: Double, normY: Double): Boolean {
            if (maskWidth <= 0 || maskHeight <= 0) return false
            val row = min((normY * maskHeight).toInt(), maskHeight - 1).coerceAtLeast(0)
            val col = min((normX * maskWidth).toInt(), maskWidth - 1).coerceAtLeast(0)
            return Color.alpha(maskBitmap.getPixel(col, row)) > 0
        }
    }

    interface InteractiveSegmentationListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(result: ResultBundle?)
    }

    companion object {
        const val TAG = "InteractiveSegmentHelper"
        const val SAMPLE_IMAGE_ASSET = "sample_interactive.jpg"
        const val MP_INTERACTIVE_SEGMENTATION_MODEL = "interactive_segmentation.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        private const val FOREGROUND_THRESHOLD = 0.5f
        private val SELECTED_OVERLAY_COLOR = Color.argb(135, 0, 172, 193)
    }
}
