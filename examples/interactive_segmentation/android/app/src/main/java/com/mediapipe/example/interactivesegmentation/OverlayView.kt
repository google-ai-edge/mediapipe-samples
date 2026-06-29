/*
 * Copyright 2026 The MediaPipe Authors.
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
package com.mediapipe.example.interactivesegmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke

enum class AppBrushMode {
    POSITIVE,
    NEGATIVE,
    LASSO
}

data class PointF(val x: Float, val y: Float)

class UserStroke(val mode: AppBrushMode) {
    val path = Path()
    val points = mutableListOf<PointF>()

    fun moveTo(x: Float, y: Float) {
        path.moveTo(x, y)
        points.add(PointF(x, y))
    }

    fun lineTo(x: Float, y: Float) {
        path.lineTo(x, y)
        points.add(PointF(x, y))
    }

    fun toStroke(scaleX: Float, scaleY: Float): Stroke {
        val pointsList = points.map { point ->
            NormalizedKeypoint.create(point.x * scaleX, point.y * scaleY)
        }
        val mpBrushMode = when (mode) {
            AppBrushMode.POSITIVE -> Stroke.BrushMode.POSITIVE
            AppBrushMode.NEGATIVE -> Stroke.BrushMode.NEGATIVE
            AppBrushMode.LASSO -> Stroke.BrushMode.LASSO
        }
        return Stroke.builder()
            .setBrushMode(mpBrushMode)
            .setPoints(pointsList)
            .setCompleted(true)
            .build()
    }
}

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    var currentBrushMode: AppBrushMode = AppBrushMode.POSITIVE
    var onStrokesUpdatedListener: ((List<Stroke>) -> Unit)? = null
    var isInteractionEnabled: Boolean = false

    private var maskBitmap: Bitmap? = null
    private val strokes = mutableListOf<UserStroke>()
    private var activeStroke: UserStroke? = null

    private val positivePaint = Paint().apply {
        color = Color.parseColor("#E64CAF50") // Green with 90% opacity
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val negativePaint = Paint().apply {
        color = Color.parseColor("#E6E53935") // Red with 90% opacity
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val lassoStrokePaint = Paint().apply {
        color = Color.parseColor("#E62196F3") // Blue with 90% opacity
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        isAntiAlias = true
    }

    private val lassoFillPaint = Paint().apply {
        color = Color.parseColor("#262196F3") // Blue with 15% opacity
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val maskPaint = Paint().apply {
        alpha = 180 // Semi-transparent overlay mask
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Draw segmentation mask overlay
        maskBitmap?.let {
            val dstRect = Rect(0, 0, width, height)
            canvas.drawBitmap(it, null, dstRect, maskPaint)
        }

        // 2. Draw stored strokes
        for (stroke in strokes) {
            drawSingleStroke(canvas, stroke)
        }

        // 3. Draw active stroke while dragging
        activeStroke?.let { stroke ->
            drawSingleStroke(canvas, stroke)
        }
    }

    private fun drawSingleStroke(canvas: Canvas, stroke: UserStroke) {
        if (stroke.points.isEmpty()) return

        val paint = when (stroke.mode) {
            AppBrushMode.POSITIVE -> positivePaint
            AppBrushMode.NEGATIVE -> negativePaint
            AppBrushMode.LASSO -> lassoStrokePaint
        }

        if (stroke.points.size == 1) {
            val p = stroke.points[0]
            val dotPaint = Paint(paint).apply { style = Paint.Style.FILL }
            canvas.drawCircle(p.x, p.y, 8f, dotPaint)
        } else {
            if (stroke.mode == AppBrushMode.LASSO) {
                val fillPath = Path(stroke.path)
                fillPath.close()
                canvas.drawPath(fillPath, lassoFillPaint)
            }
            canvas.drawPath(stroke.path, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractionEnabled) return false

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val newStroke = UserStroke(currentBrushMode)
                newStroke.moveTo(event.x, event.y)
                activeStroke = newStroke
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                activeStroke?.lineTo(event.x, event.y)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activeStroke?.let {
                    strokes.add(it)
                }
                activeStroke = null
                invalidate()
                dispatchStrokesUpdate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun dispatchStrokesUpdate() {
        if (width <= 0 || height <= 0) return
        val scaleX = 1.0f / width
        val scaleY = 1.0f / height
        val mpStrokes = strokes.map { it.toStroke(scaleX, scaleY) }
        onStrokesUpdatedListener?.invoke(mpStrokes)
    }

    fun setMaskResult(bitmap: Bitmap?) {
        maskBitmap = bitmap
        invalidate()
    }

    fun clearAll() {
        maskBitmap = null
        strokes.clear()
        activeStroke = null
        invalidate()
    }
}
