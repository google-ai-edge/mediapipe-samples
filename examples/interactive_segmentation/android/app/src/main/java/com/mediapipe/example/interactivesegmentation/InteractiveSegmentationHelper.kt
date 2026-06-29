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
import android.graphics.Color
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenterOptions
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import java.nio.ByteBuffer

class InteractiveSegmentationHelper(
    private val context: Context,
    private val listener: InteractiveSegmentationListener
) {

    private var interactiveSegmenter: InteractiveSegmenter? = null
    private var inputImage: Bitmap? = null

    init {
        setupInteractiveSegmenter()
    }

    fun clear() {
        interactiveSegmenter?.close()
        interactiveSegmenter = null
    }

    private fun setupInteractiveSegmenter() {
        val baseOptionBuilder = BaseOptions.builder()
            .setModelAssetPath(MP_INTERACTIVE_SEGMENTATION_MODEL)

        try {
            val baseOptions = baseOptionBuilder.build()
            val optionsBuilder =
                InteractiveSegmenterOptions.builder()
                    .setBaseOptions(baseOptions)

            val options = optionsBuilder.build()
            interactiveSegmenter =
                InteractiveSegmenter.createFromOptions(context, options)

        } catch (e: IllegalStateException) {
            listener.onError(
                "Interactive segmentation failed to initialize. See error logs for details"
            )
            Log.e(
                TAG,
                "MP Task Vision failed to load the task with error: " + e.message
            )
        } catch (e: RuntimeException) {
            listener.onError(
                "Interactive segmentation failed to initialize. See error logs for details"
            )
            Log.e(
                TAG,
                "MP Task Vision failed to load the task with error: " + e.message
            )
        }
    }

    /**
     * Prepares input bitmap for segmentation
     */
    fun setInputImage(bitmap: Bitmap) {
        inputImage = bitmap
        try {
            interactiveSegmenter?.setImage(BitmapImageBuilder(bitmap).build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set image on segmenter: " + e.message)
        }
    }

    fun isInputImageAssigned(): Boolean {
        return inputImage != null
    }

    /**
     * Runs segmentation on an image using provided strokes
     */
    fun segment(strokes: List<Stroke>) {
        if (inputImage == null || interactiveSegmenter == null) return
        try {
            val mpImage = interactiveSegmenter?.segment(strokes) ?: return
            val buffer = ByteBufferExtractor.extract(mpImage).asFloatBuffer()
            val width = mpImage.width
            val height = mpImage.height
            val pixels = IntArray(width * height)
            buffer.rewind()
            val floatArray = FloatArray(width * height)
            buffer.get(floatArray)
            for (i in 0 until width * height) {
                val bufferValue = floatArray[i]
                val alpha = (bufferValue * 255).toInt()
                pixels[i] = Color.argb(alpha, 255, 255, 255)
            }
            val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            maskBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            
            val resultBundle = ResultBundle(maskBitmap, width, height)
            listener.onResults(resultBundle)
        } catch (e: Exception) {
            listener.onError("Segmentation failed: " + e.message)
        }
    }

    companion object {
        private const val TAG = "InteractiveSegmentationHelper"
        private const val MP_INTERACTIVE_SEGMENTATION_MODEL =
            "interactive_segmentation.task"
    }

    data class ResultBundle(
        val maskBitmap: Bitmap,
        val maskWidth: Int,
        val maskHeight: Int
    )

    interface InteractiveSegmentationListener {
        fun onError(error: String)
        fun onResults(result: ResultBundle?)
    }
}
