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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    // Render mode: RGB_OVERLAY shows raw camera, DESENSITIZED draws mosaic background + skeleton
    enum class RenderMode {
        RGB_OVERLAY,
        DESENSITIZED
    }

    private var results: PoseLandmarkerResult? = null
    private var pointPaint = Paint()
    private var linePaint = Paint()

    // Desensitized mode fields
    var renderMode: RenderMode = RenderMode.RGB_OVERLAY
        set(value) {
            field = value
            invalidate()
        }

    // Current frame bitmap used to render mosaic background in desensitized mode
    private var currentBitmap: Bitmap? = null
    // Downscaled bitmap; drawing it back upscaled produces the pixelated mosaic effect
    private var mosaicBitmap: Bitmap? = null
    private var mosaicSrcRect = Rect()
    private var mosaicDstRectF = RectF()

    // Mosaic strength: smaller = coarser blocks (more desensitized), larger = clearer
    private val mosaicScale = 0.08f

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    // Offset mapping image coords to view coords (FILL_START crops the overflow)
    private var offsetX: Float = 0f
    private var offsetY: Float = 0f

    init {
        initPaints()
    }

    fun clear() {
        results = null
        currentBitmap = null
        mosaicBitmap = null
        pointPaint.reset()
        linePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        linePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        linePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        linePaint.style = Paint.Style.STROKE

        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (renderMode == RenderMode.DESENSITIZED) {
            drawDesensitizedBackground(canvas)
        }
        drawPoseLandmarks(canvas)
    }

    /**\n     * Desensitized background: downscale the raw frame then draw it back upscaled\n     * (pixelated mosaic), then overlay a semi-transparent dark scrim to fully obscure\n     * face/clothing details while keeping body silhouette for skeleton alignment.\n     */
    private fun drawDesensitizedBackground(canvas: Canvas) {
        val bmp = currentBitmap ?: run {
            // No frame yet: use a solid dark background
            canvas.drawColor(Color.parseColor("#1A1A1A"))
            return
        }

        // Create or update the downscaled mosaic bitmap
        val targetW = max(1, (bmp.width * mosaicScale).toInt())
        val targetH = max(1, (bmp.height * mosaicScale).toInt())
        if (mosaicBitmap == null ||
            mosaicBitmap!!.width != targetW ||
            mosaicBitmap!!.height != targetH
        ) {
            mosaicBitmap?.recycle()
            mosaicBitmap = Bitmap.createScaledBitmap(bmp, targetW, targetH, false)
        } else {
            // Reuse the existing bitmap and refill pixels (cheaper than createScaledBitmap)
            val canvas2 = Canvas(mosaicBitmap!!)
            canvas2.drawBitmap(bmp, null, Rect(0, 0, targetW, targetH), null)
        }

        mosaicSrcRect = Rect(0, 0, targetW, targetH)
        // Draw the mosaic upscaled by the same scaleFactor as the skeleton, top-left aligned\n        // (matches PreviewView FILL_START). Overflow is clipped by the canvas, so skeleton\n        // and background stay perfectly aligned.
        val scaledW = imageWidth * scaleFactor
        val scaledH = imageHeight * scaleFactor
        mosaicDstRectF = RectF(0f, 0f, scaledW, scaledH)
        val mosaicPaint = Paint().apply {
            isFilterBitmap = false  // Keep pixelated blocks (no smoothing)
            isAntiAlias = false
        }
        canvas.drawBitmap(mosaicBitmap!!, mosaicSrcRect, mosaicDstRectF, mosaicPaint)

        // Overlay a semi-transparent dark scrim to further obscure details and make the skeleton pop
        val scrimPaint = Paint().apply {
            color = Color.argb(140, 10, 10, 10)  // ~55% opacity dark gray
            style = Paint.Style.FILL
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }

    private fun drawPoseLandmarks(canvas: Canvas) {
        results?.let { poseLandmarkerResult ->
            for (landmark in poseLandmarkerResult.landmarks()) {
                for (normalizedLandmark in landmark) {
                    canvas.drawPoint(
                        normalizedLandmark.x() * imageWidth * scaleFactor + offsetX,
                        normalizedLandmark.y() * imageHeight * scaleFactor + offsetY,
                        pointPaint
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach {
                    canvas.drawLine(
                        poseLandmarkerResult.landmarks().get(0).get(it!!.start()).x() * imageWidth * scaleFactor + offsetX,
                        poseLandmarkerResult.landmarks().get(0).get(it.start()).y() * imageHeight * scaleFactor + offsetY,
                        poseLandmarkerResult.landmarks().get(0).get(it.end()).x() * imageWidth * scaleFactor + offsetX,
                        poseLandmarkerResult.landmarks().get(0).get(it.end()).y() * imageHeight * scaleFactor + offsetY,
                        linePaint)
                }
            }
        }
    }

    fun setResults(
        poseLandmarkerResults: PoseLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE,
        bitmap: Bitmap? = null
    ) {
        results = poseLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        // Keep the current frame bitmap for desensitized background rendering
        currentBitmap = bitmap

        scaleFactor = computeScaleFactor(runningMode, width, height, imageWidth, imageHeight)

        // FILL_START aligns the scaled image to the top-left and crops the overflow.
        // Both skeleton and mosaic background use the same alignment, so they overlap exactly.
        offsetX = 0f
        offsetY = 0f

        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 12F

        /**
         * Compute the scale factor that maps normalized image coordinates to view coordinates.
         *
         * - IMAGE / VIDEO: fit the image inside the view (min ratio, letterboxed).
         * - LIVE_STREAM: fill the view like PreviewView FILL_START (max ratio, overflow cropped).
         *
         * Extracted as a pure function so it can be unit-tested without instantiating the View.
         */
        fun computeScaleFactor(
            runningMode: RunningMode,
            viewWidth: Int,
            viewHeight: Int,
            imageWidth: Int,
            imageHeight: Int
        ): Float {
            val w = viewWidth * 1f / imageWidth
            val h = viewHeight * 1f / imageHeight
            return when (runningMode) {
                RunningMode.IMAGE,
                RunningMode.VIDEO -> min(w, h)
                RunningMode.LIVE_STREAM -> max(w, h)
            }
        }
    }
}