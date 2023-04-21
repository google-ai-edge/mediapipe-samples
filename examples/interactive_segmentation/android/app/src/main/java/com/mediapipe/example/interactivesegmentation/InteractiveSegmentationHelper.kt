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
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter.RegionOfInterest
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
                InteractiveSegmenter.InteractiveSegmenterOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setOutputCategoryMask(true)
                    .setOutputConfidenceMasks(false)
                    .setResultListener(this::returnSegmeneterResults)
                    .setErrorListener(this::returnSegmenterError)

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
    }

    fun isInputImageAssigned(): Boolean {
        return inputImage != null
    }

    /**
     * Runs segmentation on an image using a custom ROI (region of interest)
     */
    fun segment(normX: Float, normY: Float) {
        clear()
        setupInteractiveSegmenter()
        inputImage?.let {
            val roi = RegionOfInterest.create(
                NormalizedKeypoint.create(
                    normX * it.width,
                    normY * it.height
                )
            )
            val mpImage = BitmapImageBuilder(it).build()
            interactiveSegmenter?.segmentWithResultListener(mpImage, roi)
        }
    }

    /**
     * Returns the result of segmentation as a ByteBuffer
     * @returns {ResultBundle|null} The segmented bitmap data as a ByteBuffer, or null if there are no result.
     */
    private fun returnSegmeneterResults(
        result: ImageSegmenterResult,
        mpImage: MPImage
    ) {
        // Extract first MPImage and convert to byte buffer to display
        val byteBuffer =
            ByteBufferExtractor.extract(result.categoryMask().get())

        val resultBundle =
            ResultBundle(byteBuffer, mpImage.width, mpImage.height)
        listener.onResults(resultBundle)
    }

    private fun returnSegmenterError(error: RuntimeException) {
        listener.onError(error.message.toString())
    }

    companion object {
        private const val TAG = "InteractiveSegmentationHelper"
        private const val MP_INTERACTIVE_SEGMENTATION_MODEL =
            "interactive_segmentation_model.tflite"
    }

    data class ResultBundle(
        val byteBuffer: ByteBuffer,
        val maskWidth: Int,
        val maskHeight: Int
    )

    interface InteractiveSegmentationListener {
        fun onError(error: String)
        fun onResults(result: ResultBundle?)
    }
}
