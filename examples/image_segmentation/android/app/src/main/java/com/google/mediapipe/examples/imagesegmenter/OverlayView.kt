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
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.vision.core.RunningMode
import kotlin.math.max
import kotlin.math.min

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {
    companion object {

        private const val ALPHA_COLOR = 128
        private val labelColors = listOf(
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
            -16760704
        )
    }

    private var scaleBitmap: Bitmap? = null
    private var runningMode: RunningMode = RunningMode.IMAGE

    fun clear() {
        scaleBitmap = null
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        scaleBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
    }
    fun setRunningMode(runningMode: RunningMode){
        this.runningMode = runningMode
    }

    fun setResults(
        mpImage: MPImage,
    ) {
        // Create the mask bitmap with colors and the set of detected labels.
        // We only need the first mask for this sample because we are using
        // the OutputType CATEGORY_MASK, which only provides a single mask.
        val byteBuffer = ByteBufferExtractor.extract(mpImage)
        val pixels = IntArray(byteBuffer.capacity())
        for (i in pixels.indices) {
            val index = byteBuffer.get(i).toInt()
            val color =
                if (index in 1..20) labelColors[index].toColor() else Color.TRANSPARENT
            pixels[i] = color
        }

        val image = Bitmap.createBitmap(
            pixels,
            mpImage.width,
            mpImage.height,
            Bitmap.Config.ARGB_8888
        )

        val scaleFactor = when (runningMode) {
            RunningMode.IMAGE,
            RunningMode.VIDEO -> {
                min(width * 1f / mpImage.width, height * 1f / mpImage.height)
            }
            RunningMode.LIVE_STREAM -> {
                // PreviewView is in FILL_START mode. So we need to scale up the
                // landmarks to match with the size that the captured images will be
                // displayed.
                max(width * 1f / mpImage.width, height * 1f / mpImage.height)
            }
        }

        val scaleWidth = (mpImage.width * scaleFactor).toInt()
        val scaleHeight = (mpImage.height * scaleFactor).toInt()

        scaleBitmap = Bitmap.createScaledBitmap(
            image, scaleWidth, scaleHeight, false
        )
    }

    private fun Int.toColor(): Int {
        return Color.argb(
            ALPHA_COLOR, Color.red(this), Color.green(this), Color.blue(this)
        )
    }
}
