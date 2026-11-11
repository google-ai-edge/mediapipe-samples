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
    var listener: EmbedderListener? = null,
) {
    private var imageEmbedder: ImageEmbedder? = null
    private var lastLoadTimeMs: Long = 0L

    val isClosed: Boolean
        get() = imageEmbedder == null

    init {
        setupImageEmbedder()
    }

    fun setupImageEmbedder(): Boolean {
        clearImageEmbedder()
        val startTime = SystemClock.uptimeMillis()

        val baseOptionsBuilder = BaseOptions.builder()
        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
            else -> baseOptionsBuilder.setDelegate(Delegate.CPU)
        }
        when (currentModel) {
            MODEL_SMALL -> baseOptionsBuilder.setModelAssetPath(MODEL_SMALL_PATH)
            MODEL_LARGE -> baseOptionsBuilder.setModelAssetPath(MODEL_LARGE_PATH)
            else -> baseOptionsBuilder.setModelAssetPath(MODEL_SMALL_PATH)
        }
        try {
            val baseOptions = baseOptionsBuilder.build()
            val options = ImageEmbedderOptions.builder().setBaseOptions(baseOptions).build()
            imageEmbedder = ImageEmbedder.createFromOptions(context, options)
            lastLoadTimeMs = SystemClock.uptimeMillis() - startTime
            return true
        } catch (e: IllegalStateException) {
            listener?.onError("Image embedder failed to initialize. See error logs for details")
            Log.e(TAG, "Image embedder failed to load model with error: ${e.message}")
        } catch (e: RuntimeException) {
            // This occurs if the model being used does not support GPU.
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            listener?.onError(
                "Image embedder failed to initialize. See error logs for details",
                errorCode,
            )
            Log.e(TAG, "Image embedder failed to load model with error: ${e.message}")
        }
        return false
    }

    // If both vectors are aligned, the angle between them is 0 and cos(0) = 1.
    fun embed(firstBitmap: Bitmap, secondBitmap: Bitmap): ResultBundle? {
        val embedder = imageEmbedder ?: return null
        val startTime = SystemClock.uptimeMillis()

        val firstSafe =
            if (firstBitmap.config == Bitmap.Config.ARGB_8888) firstBitmap
            else firstBitmap.copy(Bitmap.Config.ARGB_8888, false)
        val secondSafe =
            if (secondBitmap.config == Bitmap.Config.ARGB_8888) secondBitmap
            else secondBitmap.copy(Bitmap.Config.ARGB_8888, false)

        return try {
            val firstMpImage = BitmapImageBuilder(firstSafe).build()
            val secondMpImage = BitmapImageBuilder(secondSafe).build()
            val firstEmbed =
                embedder.embed(firstMpImage).embeddingResult().embeddings().firstOrNull()
                    ?: return null
            val secondEmbed =
                embedder.embed(secondMpImage).embeddingResult().embeddings().firstOrNull()
                    ?: return null
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            ResultBundle(
                similarity = ImageEmbedder.cosineSimilarity(firstEmbed, secondEmbed),
                inferenceTime = inferenceTimeMs,
                loadTimeMs = lastLoadTimeMs,
            )
        } finally {
            if (firstSafe !== firstBitmap) firstSafe.recycle()
            if (secondSafe !== secondBitmap) secondSafe.recycle()
        }
    }

    fun clearImageEmbedder() {
        imageEmbedder?.close()
        imageEmbedder = null
    }

    /** Wraps cosine similarity and timing measurements from comparing two images. */
    data class ResultBundle(
        val similarity: Double,
        val inferenceTime: Long,
        val loadTimeMs: Long = 0L,
    )

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val MODEL_SMALL = 0
        const val MODEL_LARGE = 1
        const val MODEL_SMALL_PATH = "mobilenet_v3_small.tflite"
        const val MODEL_LARGE_PATH = "mobilenet_v3_large.tflite"
        const val ASSET_AMERICAN_PIZZA = "american_pizza.jpg"
        const val ASSET_ITALY_PIZZA = "italy_pizza.png"
        const val ASSET_CUP = "cup.png"
        const val OTHER_ERROR = 0
        const val GPU_ERROR = 1
        private const val TAG = "ImageEmbedderHelper"
    }

    interface EmbedderListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }
}
