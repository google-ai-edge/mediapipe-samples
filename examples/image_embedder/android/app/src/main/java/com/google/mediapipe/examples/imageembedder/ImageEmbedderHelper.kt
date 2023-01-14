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

package com.google.mediapipe.examples.imageembedder

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions

class ImageEmbedderHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_SMALL,
    var listener: EmbedderListener? = null
) {
    private var imageEmbedder: ImageEmbedder? = null

    init {
        setupImageEmbedder()
    }

    fun setupImageEmbedder() {
        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> {
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }
        when (currentModel) {
            MODEL_SMALL -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_SMALL_PATH)
            }
            MODEL_LARGE -> {
                baseOptionsBuilder.setModelAssetPath(MODEL_LARGE_PATH)
            }
        }
        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder =
                ImageEmbedderOptions.builder().setBaseOptions(baseOptions)
            val options = optionsBuilder.build()
            imageEmbedder = ImageEmbedder.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            listener?.onError(
                "Image embedder failed to initialize. See error logs for " +
                        "details"
            )
            Log.e(
                TAG,
                "Image embedder failed to load model with error: " + e.message
            )
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU
            listener?.onError(
                "Image embedder failed to initialize. See error logs for " +
                        "details", GPU_ERROR
            )
            Log.e(
                TAG,
                "Image embedder failed to load model with error: " + e.message
            )
        }
    }

    //  If both the vectors are aligned, the angle between them
    //  will be 0. cos 0 = 1. So, mathematically, this distance metric will
    //  be used to find the most similar image.
    fun embed(firstBitmap: Bitmap, secondBitmap: Bitmap): ResultBundle? {
        // Inference time is the difference between the system time at the start and finish of the
        // process
        val startTime = SystemClock.uptimeMillis()

        val firstMpImage = BitmapImageBuilder(firstBitmap).build()
        val secondMpImage = BitmapImageBuilder(secondBitmap).build()
        imageEmbedder?.let {
            val firstEmbed =
                it.embed(firstMpImage).embeddingResult().embeddings().first()
            val secondEmbed =
                it.embed(secondMpImage).embeddingResult().embeddings().first()
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            return ResultBundle(
                ImageEmbedder.cosineSimilarity(firstEmbed, secondEmbed),
                inferenceTimeMs
            )
        }
        return null
    }

    fun clearImageEmbedder() {
        imageEmbedder?.close()
        imageEmbedder = null
    }

    // Wraps results from inference, the time it takes for inference to be
    // performed.
    data class ResultBundle(
        val similarity: Double,
        val inferenceTime: Long,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_SMALL = 0
        const val MODEL_LARGE = 1
        const val MODEL_SMALL_PATH = "mobilenet_v3_small.tflite"
        const val MODEL_LARGE_PATH = "mobilenet_v3_large.tflite"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        private const val TAG = "ImageEmbedderHelper"
    }

    interface EmbedderListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }
}
