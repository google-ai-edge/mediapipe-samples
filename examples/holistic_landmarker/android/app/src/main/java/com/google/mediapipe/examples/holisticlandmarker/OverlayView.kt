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
package com.google.mediapipe.examples.holisticlandmarker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var results: HolisticLandmarkerResult? = null
    private var pointPaint = Paint()
    private var poseLinePaint = Paint()
    private var faceLinePaint = Paint()
    private var leftHandLinePaint = Paint()
    private var rightHandLinePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        pointPaint.reset()
        poseLinePaint.reset()
        faceLinePaint.reset()
        leftHandLinePaint.reset()
        rightHandLinePaint.reset()
        invalidate()
        initPaints()
    }

    private fun initPaints() {
        pointPaint.color = Color.YELLOW
        pointPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        pointPaint.style = Paint.Style.FILL

        poseLinePaint.color = ContextCompat.getColor(context!!, R.color.mp_color_primary)
        poseLinePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        poseLinePaint.style = Paint.Style.STROKE

        faceLinePaint.color = Color.CYAN
        faceLinePaint.strokeWidth = LANDMARK_STROKE_WIDTH / 2f
        faceLinePaint.style = Paint.Style.STROKE

        leftHandLinePaint.color = Color.GREEN
        leftHandLinePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        leftHandLinePaint.style = Paint.Style.STROKE

        rightHandLinePaint.color = Color.MAGENTA
        rightHandLinePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        rightHandLinePaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        results?.let { holisticResult ->

            // Calculate scaled image dimensions & offsets to center the image on the canvas
            val scaledImageWidth = imageWidth * scaleFactor
            val scaledImageHeight = imageHeight * scaleFactor
            val offsetX = (width - scaledImageWidth) / 2f
            val offsetY = (height - scaledImageHeight) / 2f

            // 1. Draw Pose Landmarks & Connections
            val poseLandmarks = holisticResult.poseLandmarks()
            if (poseLandmarks.isNotEmpty()) {
                for (landmark in poseLandmarks) {
                    canvas.drawPoint(
                        landmark.x() * imageWidth * scaleFactor + offsetX,
                        landmark.y() * imageHeight * scaleFactor + offsetY,
                        pointPaint
                    )
                }

                PoseLandmarker.POSE_LANDMARKS.forEach { connection ->
                    val start = poseLandmarks.getOrNull(connection.start())
                    val end = poseLandmarks.getOrNull(connection.end())
                    if (start != null && end != null) {
                        canvas.drawLine(
                            start.x() * imageWidth * scaleFactor + offsetX,
                            start.y() * imageHeight * scaleFactor + offsetY,
                            end.x() * imageWidth * scaleFactor + offsetX,
                            end.y() * imageHeight * scaleFactor + offsetY,
                            poseLinePaint
                        )
                    }
                }
            }

            // 2. Draw Face Landmarks & Connectors
            val faceLandmarks = holisticResult.faceLandmarks()
            if (faceLandmarks.isNotEmpty()) {
                for (landmark in faceLandmarks) {
                    canvas.drawPoint(
                        landmark.x() * imageWidth * scaleFactor + offsetX,
                        landmark.y() * imageHeight * scaleFactor + offsetY,
                        pointPaint
                    )
                }

                FaceLandmarker.FACE_LANDMARKS_CONNECTORS.filterNotNull().forEach { connector ->
                    val start = faceLandmarks.getOrNull(connector.start())
                    val end = faceLandmarks.getOrNull(connector.end())
                    if (start != null && end != null) {
                        canvas.drawLine(
                            start.x() * imageWidth * scaleFactor + offsetX,
                            start.y() * imageHeight * scaleFactor + offsetY,
                            end.x() * imageWidth * scaleFactor + offsetX,
                            end.y() * imageHeight * scaleFactor + offsetY,
                            faceLinePaint
                        )
                    }
                }
            }

            // 3. Draw Left Hand Landmarks & Connections
            val leftHandLandmarks = holisticResult.leftHandLandmarks()
            if (leftHandLandmarks.isNotEmpty()) {
                for (landmark in leftHandLandmarks) {
                    canvas.drawPoint(
                        landmark.x() * imageWidth * scaleFactor + offsetX,
                        landmark.y() * imageHeight * scaleFactor + offsetY,
                        pointPaint
                    )
                }

                HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                    val start = leftHandLandmarks.getOrNull(connection.start())
                    val end = leftHandLandmarks.getOrNull(connection.end())
                    if (start != null && end != null) {
                        canvas.drawLine(
                            start.x() * imageWidth * scaleFactor + offsetX,
                            start.y() * imageHeight * scaleFactor + offsetY,
                            end.x() * imageWidth * scaleFactor + offsetX,
                            end.y() * imageHeight * scaleFactor + offsetY,
                            leftHandLinePaint
                        )
                    }
                }
            }

            // 4. Draw Right Hand Landmarks & Connections
            val rightHandLandmarks = holisticResult.rightHandLandmarks()
            if (rightHandLandmarks.isNotEmpty()) {
                for (landmark in rightHandLandmarks) {
                    canvas.drawPoint(
                        landmark.x() * imageWidth * scaleFactor + offsetX,
                        landmark.y() * imageHeight * scaleFactor + offsetY,
                        pointPaint
                    )
                }

                HandLandmarker.HAND_CONNECTIONS.forEach { connection ->
                    val start = rightHandLandmarks.getOrNull(connection.start())
                    val end = rightHandLandmarks.getOrNull(connection.end())
                    if (start != null && end != null) {
                        canvas.drawLine(
                            start.x() * imageWidth * scaleFactor + offsetX,
                            start.y() * imageHeight * scaleFactor + offsetY,
                            end.x() * imageWidth * scaleFactor + offsetX,
                            end.y() * imageHeight * scaleFactor + offsetY,
                            rightHandLinePaint
                        )
                    }
                }
            }
        }
    }

    fun setResults(
        holisticLandmarkerResults: HolisticLandmarkerResult,
        imageHeight: Int,
        imageWidth: Int,
        runningMode: RunningMode = RunningMode.IMAGE
    ) {
        results = holisticLandmarkerResults

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
    }
}
