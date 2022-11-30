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
package com.google.mediapipe.examples.handlandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HandLandmarkerResult? = null
    private var linePaint = Paint()
    private var pointPaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        linePaint.reset()
        pointPaint.reset()
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
        results?.let { handLandmarkerResult ->
            val lines = mutableListOf<Float>()
            val points = mutableListOf<Float>()

            for (landmarks in handLandmarkerResult.landmarks()) {
                for (i in landmarkConnections.indices step 2) {
                    val startX =
                        landmarks[landmarkConnections[i]].x() * imageWidth * scaleFactor
                    val startY =
                        landmarks[landmarkConnections[i]].y() * imageHeight * scaleFactor
                    val endX =
                        landmarks[landmarkConnections[i + 1]].x() * imageWidth * scaleFactor
                    val endY =
                        landmarks[landmarkConnections[i + 1]].y() * imageHeight * scaleFactor
                    lines.add(startX)
                    lines.add(startY)
                    lines.add(endX)
                    lines.add(endY)
                    points.add(startX)
                    points.add(startY)
                }
                canvas.drawLines(lines.toFloatArray(), linePaint)
                canvas.drawPoints(points.toFloatArray(), pointPaint)
            }
        }
    }

    fun setResults(
        handLandmarkerResults: HandLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = handLandmarkerResults

        this.imageHeight = imageHeight
        this.imageWidth = imageWidth

        scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / imageWidth, height * 1f / imageHeight)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / imageWidth, height * 1f / imageHeight)
            }
        }
        invalidate()
    }

    companion object {
        private const val LANDMARK_STROKE_WIDTH = 8F

        // This list defines the lines that are drawn when visualizing the hand landmark detection
        // results. These lines connect:
        // landmarkConnections[2*n] and landmarkConnections[2*n+1]
        private val landmarkConnections = listOf(
            HandLandmarkerHelper.WRIST,
            HandLandmarkerHelper.THUMB_CMC,
            HandLandmarkerHelper.THUMB_CMC,
            HandLandmarkerHelper.THUMB_MCP,
            HandLandmarkerHelper.THUMB_MCP,
            HandLandmarkerHelper.THUMB_IP,
            HandLandmarkerHelper.THUMB_IP,
            HandLandmarkerHelper.THUMB_TIP,
            HandLandmarkerHelper.WRIST,
            HandLandmarkerHelper.INDEX_FINGER_MCP,
            HandLandmarkerHelper.INDEX_FINGER_MCP,
            HandLandmarkerHelper.INDEX_FINGER_PIP,
            HandLandmarkerHelper.INDEX_FINGER_PIP,
            HandLandmarkerHelper.INDEX_FINGER_DIP,
            HandLandmarkerHelper.INDEX_FINGER_DIP,
            HandLandmarkerHelper.INDEX_FINGER_TIP,
            HandLandmarkerHelper.INDEX_FINGER_MCP,
            HandLandmarkerHelper.MIDDLE_FINGER_MCP,
            HandLandmarkerHelper.MIDDLE_FINGER_MCP,
            HandLandmarkerHelper.MIDDLE_FINGER_PIP,
            HandLandmarkerHelper.MIDDLE_FINGER_PIP,
            HandLandmarkerHelper.MIDDLE_FINGER_DIP,
            HandLandmarkerHelper.MIDDLE_FINGER_DIP,
            HandLandmarkerHelper.MIDDLE_FINGER_TIP,
            HandLandmarkerHelper.MIDDLE_FINGER_MCP,
            HandLandmarkerHelper.RING_FINGER_MCP,
            HandLandmarkerHelper.RING_FINGER_MCP,
            HandLandmarkerHelper.RING_FINGER_PIP,
            HandLandmarkerHelper.RING_FINGER_PIP,
            HandLandmarkerHelper.RING_FINGER_DIP,
            HandLandmarkerHelper.RING_FINGER_DIP,
            HandLandmarkerHelper.RING_FINGER_TIP,
            HandLandmarkerHelper.RING_FINGER_MCP,
            HandLandmarkerHelper.PINKY_MCP,
            HandLandmarkerHelper.WRIST,
            HandLandmarkerHelper.PINKY_MCP,
            HandLandmarkerHelper.PINKY_MCP,
            HandLandmarkerHelper.PINKY_PIP,
            HandLandmarkerHelper.PINKY_PIP,
            HandLandmarkerHelper.PINKY_DIP,
            HandLandmarkerHelper.PINKY_DIP,
            HandLandmarkerHelper.PINKY_TIP
        )
    }
}
