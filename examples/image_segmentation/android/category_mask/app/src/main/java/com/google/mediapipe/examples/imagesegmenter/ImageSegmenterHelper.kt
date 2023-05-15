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
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenterResult
import java.nio.ByteBuffer

class ImageSegmenterHelper(
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.IMAGE,
    var currentModel: Int = MODEL_DEEPLABV3,
    val context: Context,
    var imageSegmenterListener: SegmenterListener? = null
) {

    // For this example this needs to be a var so it can be reset on changes. If the Imagesegmenter
    // will not change, a lazy val would be preferable.
    private var imagesegmenter: ImageSegmenter? = null

    init {
        setupImageSegmenter()
    }

    // Segmenter must be closed when creating a new one to avoid returning results to a
    // non-existent object
    fun clearImageSegmenter() {
        imagesegmenter?.close()
        imagesegmenter = null
    }

    fun setListener(listener: SegmenterListener) {
        imageSegmenterListener = listener
    }

    fun clearListener() {
        imageSegmenterListener = null
    }

    // Return running status of image segmenter helper
    fun isClosed(): Boolean {
        return imagesegmenter == null
    }

    // Initialize the image segmenter using current settings on the
    // thread that is using it. CPU can be used with detectors
    // that are created on the main thread and used on a background thread, but
    // the GPU delegate needs to be used on the thread that initialized the
    // segmenter
    fun setupImageSegmenter() {
        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        when(currentModel) {
            MODEL_DEEPLABV3 -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_DEEPLABV3_PATH)
            }
            MODEL_HAIR_SEGMENTER -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_HAIR_SEGMENTER_PATH)
            }
            MODEL_SELFIE_SEGMENTER -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_SELFIE_SEGMENTER_PATH)
            }
            MODEL_SELFIE_MULTICLASS -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_SELFIE_MULTICLASS_PATH)
            }
        }

        if (imageSegmenterListener == null) {
            throw IllegalStateException(
                "ImageSegmenterListener must be set."
            )
        }

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = ImageSegmenter.ImageSegmenterOptions.builder()
                .setRunningMode(runningMode)
                .setBaseOptions(baseOptions)
                .setOutputCategoryMask(true)
                .setOutputConfidenceMasks(false)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnSegmentationResult)
                    .setErrorListener(this::returnSegmentationHelperError)
            }

            val options = optionsBuilder.build()
            imagesegmenter = ImageSegmenter.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            imageSegmenterListener?.onError(
                "Image segmenter failed to initialize. See error logs for details"
            )
            Log.e(
                TAG,
                "Image segmenter failed to load model with error: " + e.message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            imageSegmenterListener?.onError(
                "Image segmenter failed to initialize. See error logs for " + "details",
                GPU_ERROR
            )
            Log.e(
                TAG,
                "Image segmenter failed to load model with error: " + e.message
            )
        }
    }

    // Runs image segmentation on live streaming cameras frame-by-frame and
    // returns the results asynchronously to the caller.
    fun segmentLiveStreamFrame(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call segmentLiveStreamFrame" + " while not using RunningMode.LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()
        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )

        imageProxy.use {
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
        }

        // Used for rotating the frame image so it matches our models
        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())

            if(isFrontCamera) {
                postScale(
                    -1f,
                    1f,
                    imageProxy.width.toFloat(),
                    imageProxy.height.toFloat()
                )
            }
        }

        imageProxy.close()

        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer,
            0,
            0,
            bitmapBuffer.width,
            bitmapBuffer.height,
            matrix,
            true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()

        imagesegmenter?.segmentAsync(mpImage, frameTime)
    }

    // Runs image segmentation on single image and
    // returns the results asynchronously to the caller.
    fun segmentImageFile(mpImage: MPImage): ImageSegmenterResult? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call segmentImageFile" + " while not using RunningMode.IMAGE"
            )
        }
        return imagesegmenter?.segment(mpImage)
    }

    // Runs image segmentation on each video frame and
    // returns the results asynchronously to the caller.
    @kotlin.jvm.Throws(Exception::class)
    fun segmentVideoFile(mpImage: MPImage): ImageSegmenterResult? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call segmentVideoFile" + " while not using RunningMode.VIDEO"
            )
        }

        return imagesegmenter?.segmentForVideo(
            mpImage,
            SystemClock.uptimeMillis()
        )
    }

    // MPImage isn't necessary for this example, but the listener requires it
    private fun returnSegmentationResult(
        result: ImageSegmenterResult, image: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()

        val inferenceTime = finishTimeMs - result.timestampMs()

        // We only need the first mask for this sample because we are using
        // the OutputType CATEGORY_MASK, which only provides a single mask.
        val mpImage = result.categoryMask().get()

        imageSegmenterListener?.onResults(
            ResultBundle(
                ByteBufferExtractor.extract(mpImage),
                mpImage.width,
                mpImage.height,
                inferenceTime
            )
        )
    }

    // Return errors thrown during segmentation to this
    // ImageSegmenterHelper's caller
    private fun returnSegmentationHelperError(error: RuntimeException) {
        imageSegmenterListener?.onError(
            error.message ?: "An unknown error has occurred"
        )
    }

    // Wraps results from inference, the time it takes for inference to be
    // performed.
    data class ResultBundle(
        val results: ByteBuffer,
        val width: Int,
        val height: Int,
        val inferenceTime: Long,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1

        const val MODEL_DEEPLABV3 = 0
        const val MODEL_HAIR_SEGMENTER = 1
        const val MODEL_SELFIE_SEGMENTER = 2
        const val MODEL_SELFIE_MULTICLASS = 3

        const val MODEL_DEEPLABV3_PATH = "deeplabv3.tflite"
        const val MODEL_HAIR_SEGMENTER_PATH = "hair_segmenter.tflite"
        const val MODEL_SELFIE_MULTICLASS_PATH = "selfie_multiclass.tflite"
        const val MODEL_SELFIE_SEGMENTER_PATH = "selfie_segmenter.tflite"

        private const val TAG = "ImageSegmenterHelper"

        val labelColors = listOf(
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

    interface SegmenterListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
        fun onResults(resultBundle: ResultBundle)
    }
}
