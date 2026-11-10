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
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import kotlin.math.roundToInt

/**
 * Helper that wraps [InteractiveSegmenter] lifecycle, image binding, and stroke-based interactive
 * segmentation inference.
 */
class InteractiveSegmentationHelper(
    val context: Context,
    var listener: InteractiveSegmentationListener? = null,
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
        return try {
            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(MP_INTERACTIVE_SEGMENTATION_MODEL)
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
            listener?.onError("Interactive segmenter failed to initialize: $message")
            Log.e(TAG, "Interactive segmenter failed to initialize: $message", e)
            false
        }
    }

    /** Sets the target image for subsequent [segment] calls. */
    fun setInputImage(bitmap: Bitmap) {
        val segmenter = interactiveSegmenter ?: return
        val argbBitmap =
            if (bitmap.config == Bitmap.Config.ARGB_8888) bitmap
            else bitmap.copy(Bitmap.Config.ARGB_8888, false)
        try {
            val mpImage = BitmapImageBuilder(argbBitmap).build()
            segmenter.setImage(mpImage)
        } catch (e: Exception) {
            listener?.onError("Failed to set input image: ${e.message}")
            Log.e(TAG, "Failed to set input image", e)
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
            val capacity = floatBuffer.capacity()
            val pixels = IntArray(capacity)
            var selectedCount = 0

            for (i in 0 until capacity) {
                val conf = floatBuffer.get(i)
                if (conf >= FOREGROUND_THRESHOLD) {
                    selectedCount++
                    pixels[i] = SELECTED_OVERLAY_COLOR
                } else {
                    pixels[i] = Color.TRANSPARENT
                }
            }

            val maskBitmap = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            val selectedPercent =
                if (capacity > 0) ((selectedCount * 100f) / capacity).roundToInt() else 0

            val bundle =
                ResultBundle(
                    maskBitmap = maskBitmap,
                    maskWidth = width,
                    maskHeight = height,
                    selectedPercent = selectedPercent,
                    inferenceTimeMs = inferenceTimeMs,
                )
            listener?.onResults(bundle)
            bundle
        } catch (e: Exception) {
            val message = e.message ?: e.javaClass.simpleName
            listener?.onError("Segmentation failed: $message")
            Log.e(TAG, "Segmentation failed", e)
            null
        }
    }

    data class ResultBundle(
        val maskBitmap: Bitmap,
        val maskWidth: Int,
        val maskHeight: Int,
        val selectedPercent: Int = 0,
        val inferenceTimeMs: Long = 0L,
    )

    interface InteractiveSegmentationListener {
        fun onError(error: String)
        fun onResults(result: ResultBundle?)
    }

    companion object {
        const val TAG = "InteractiveSegmentHelper"
        const val SAMPLE_IMAGE_ASSET = "sample_interactive.jpg"
        const val MP_INTERACTIVE_SEGMENTATION_MODEL = "interactive_segmentation.task"

        private const val FOREGROUND_THRESHOLD = 0.5f
        private val SELECTED_OVERLAY_COLOR = Color.argb(135, 0, 172, 193)
    }
}
