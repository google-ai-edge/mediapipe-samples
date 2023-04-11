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
package com.mediapipe.example.interactivesegmentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.nio.ByteBuffer
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    private var maskBitmap: Bitmap? = null
    private var selectedPoint: Pair<Float, Float>? = null
    private var overlayColor: String = "#8012B5CB"
    private var selectionMarkerColor: String = "#FBBC04"
    private var selectionMarkerBorderColor: String = "#000000"
    private val selectPaint = Paint().apply {
        color = Color.parseColor(selectionMarkerColor)
    }
    private val borderPaint = Paint().apply {
        color = Color.parseColor(selectionMarkerBorderColor)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        maskBitmap?.let {
            canvas?.drawBitmap(it, 0f, 0f, null)
        }
        selectedPoint?.let {
            canvas?.drawCircle(it.first, it.second, 20f, borderPaint)
            canvas?.drawCircle(it.first, it.second, 15f, selectPaint)
        }
    }

    /**
     * Converts byteBuffer to mask bitmap
     * Scales the bitmap to match the view
     */
    fun setMaskResult(byteBuffer: ByteBuffer, maskWidth: Int, maskHeight: Int) {
        val pixels = IntArray(byteBuffer.capacity())
        for (i in pixels.indices) {
            val index = byteBuffer.get(i).toInt()
            val color = if (index == 0) Color.TRANSPARENT else Color.parseColor(overlayColor)
            pixels[i] = color
        }

        val bitmap = Bitmap.createBitmap(
            pixels,
            maskWidth,
            maskHeight,
            Bitmap.Config.ARGB_8888
        )

        // Assumes portrait for this sample, but scaling can be adjusted for landscape.
        // Code for selecting orientation excluded for sample simplicity
        val scaleFactor =
            min(width * 1f / bitmap.width, height * 1f / bitmap.height)
        val scaleWidth = (bitmap.width * scaleFactor).toInt()
        val scaleHeight = (bitmap.height * scaleFactor).toInt()
        maskBitmap =
            Bitmap.createScaledBitmap(bitmap, scaleWidth, scaleHeight, false)
        invalidate()
    }

    fun setSelectPosition(x: Float, y: Float) {
        selectedPoint = Pair(x, y)
        invalidate()
    }

    fun clearAll() {
        maskBitmap = null
        selectedPoint = null
        invalidate()
    }
}
