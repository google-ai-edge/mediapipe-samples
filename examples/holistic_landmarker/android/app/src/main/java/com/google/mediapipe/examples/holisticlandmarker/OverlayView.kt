package com.google.mediapipe.examples.holisticlandmarker

/*
 * Copyright 2024 The TensorFlow Authors. All Rights Reserved.
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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    private var results: HolisticLandmarkerResult? = null
    private var facePaint = Paint()
    private var rightHandPaint = Paint()
    private var leftHandPaint = Paint()
    private var posePaint = Paint()

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        initPaints()
    }

    fun clear() {
        results = null
        facePaint.reset()
        invalidate()
        initPaints()
    }

    //Set up properties for each Paint object
    private fun initPaints() {
        facePaint.color =
            ContextCompat.getColor(context!!, R.color.mp_color_primary)
        facePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        facePaint.style = Paint.Style.STROKE

        rightHandPaint.color =
            ContextCompat.getColor(context!!, R.color.color_right_hand)
        rightHandPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        rightHandPaint.style = Paint.Style.STROKE

        leftHandPaint.color =
            ContextCompat.getColor(context!!, R.color.color_left_hand)
        leftHandPaint.strokeWidth = LANDMARK_STROKE_WIDTH
        leftHandPaint.style = Paint.Style.STROKE

        posePaint.color =
            ContextCompat.getColor(context!!, R.color.color_pose)
        posePaint.strokeWidth = LANDMARK_STROKE_WIDTH
        posePaint.style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (results == null || results!!.faceLandmarks().isEmpty()) {
            clear()
            return
        }
        // draw segmentation mask if present
        if (results?.segmentationMask()?.isPresent == true) {
            val buffer = ByteBufferExtractor.extract(
                results?.segmentationMask()!!.get()
            )
            // convert bytebuffer to bitmap
            val bitmap = Bitmap.createBitmap(
                imageWidth,
                imageHeight,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            // scale
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scaleFactor).roundToInt(),
                (bitmap.height * scaleFactor).roundToInt(),
                false
            )
            // draw bitmap on canvas
            canvas.drawBitmap(
                scaledBitmap,
                0f,
                0f,
                null
            )
        }

        // draw pose landmarks
        results?.poseLandmarks()?.let { poseLandmarkerResult ->
            if (poseLandmarkerResult.isEmpty()) return@let
            PoseLandmarker.POSE_LANDMARKS.forEach {
                canvas.drawLine(
                    poseLandmarkerResult[it!!.start()]
                        .x() * imageWidth * scaleFactor,
                    poseLandmarkerResult[it.start()]
                        .y() * imageHeight * scaleFactor,
                    poseLandmarkerResult[it.end()]
                        .x() * imageWidth * scaleFactor,
                    poseLandmarkerResult[it.end()]
                        .y() * imageHeight * scaleFactor,
                    posePaint
                )
            }
        }

        // draw left hand landmarks
        results?.leftHandLandmarks()?.let { leftHandLandmarkerResult ->
            if (leftHandLandmarkerResult.isEmpty()) return@let
            HandLandmarker.HAND_CONNECTIONS.forEach {
                canvas.drawLine(
                    leftHandLandmarkerResult[it!!.start()]
                        .x() * imageWidth * scaleFactor,
                    leftHandLandmarkerResult[it.start()]
                        .y() * imageHeight * scaleFactor,
                    leftHandLandmarkerResult[it.end()]
                        .x() * imageWidth * scaleFactor,
                    leftHandLandmarkerResult[it.end()]
                        .y() * imageHeight * scaleFactor,
                    leftHandPaint
                )
            }
        }

        // draw right hand landmarks
        results?.rightHandLandmarks()?.let { rightHandLandmarkerResult ->
            if (rightHandLandmarkerResult.isEmpty()) return@let

            HandLandmarker.HAND_CONNECTIONS.forEach {
                canvas.drawLine(
                    rightHandLandmarkerResult[it!!.start()]
                        .x() * imageWidth * scaleFactor,
                    rightHandLandmarkerResult[it.start()]
                        .y() * imageHeight * scaleFactor,
                    rightHandLandmarkerResult[it.end()]
                        .x() * imageWidth * scaleFactor,
                    rightHandLandmarkerResult[it.end()]
                        .y() * imageHeight * scaleFactor,
                    rightHandPaint
                )
            }
        }

        // draw face landmarks
        results?.faceLandmarks()?.let { faceLandmarkerResult ->
            if (faceLandmarkerResult.isEmpty()) return@let

            FaceLandmarker.FACE_LANDMARKS_CONNECTORS.forEach {
                canvas.drawLine(
                    faceLandmarkerResult[it!!.start()]
                        .x() * imageWidth * scaleFactor,
                    faceLandmarkerResult[it.start()]
                        .y() * imageHeight * scaleFactor,
                    faceLandmarkerResult[it.end()]
                        .x() * imageWidth * scaleFactor,
                    faceLandmarkerResult[it.end()]
                        .y() * imageHeight * scaleFactor,
                    facePaint
                )
            }
        }
    }

    fun setResults(
        holisticLandmarkerResults: HolisticLandmarkerResult?,
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
