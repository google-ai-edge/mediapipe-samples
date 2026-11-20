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
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder.ImageEmbedderOptions

class ImageEmbedderHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_CPU,
    var currentModel: Int = MODEL_SMALL,
    var l2Normalize: Boolean = false,
    var quantize: Boolean = false,
    var runningMode: RunningMode = RunningMode.IMAGE,
    var listener: EmbedderListener? = null,
) {
    private var imageEmbedder: ImageEmbedder? = null
    private var lastLoadTimeMs: Long = 0L
    private var nextVideoTimestampMs: Long = 0L

    val isClosed: Boolean
        get() = imageEmbedder == null

    init {
        setupImageEmbedder()
    }

    fun setupImageEmbedder(): Boolean {
        clearImageEmbedder()
        nextVideoTimestampMs = 0L
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
            val options =
                ImageEmbedderOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setL2Normalize(l2Normalize)
                    .setQuantize(quantize)
                    .setRunningMode(runningMode)
                    .build()
            imageEmbedder = ImageEmbedder.createFromOptions(context, options)
            lastLoadTimeMs = SystemClock.uptimeMillis() - startTime
            return true
        } catch (e: IllegalStateException) {
            val detail = e.message ?: "IllegalStateException"
            Log.e(TAG, "Image embedder failed to load model with error: $detail", e)
            listener?.onError("Image embedder failed to initialize: $detail")
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            Log.e(TAG, "Image embedder failed to load model with error: $detail", e)
            listener?.onError(
                if (errorCode == GPU_ERROR) {
                    "This model or device does not support GPU acceleration: $detail"
                } else {
                    "Image embedder failed to initialize: $detail"
                },
                errorCode,
            )
        }
        return false
    }

    // If both vectors are aligned, the angle between them is 0 and cos(0) = 1.
    fun embed(firstBitmap: Bitmap, secondBitmap: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException(
                "Attempting to call embed while not using RunningMode.IMAGE"
            )
        }
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
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            Log.e(TAG, "Image embedder failed to embed images with error: $detail", e)
            listener?.onError("Image embedder failed: $detail", errorCode)
            null
        } finally {
            if (firstSafe !== firstBitmap) firstSafe.recycle()
            if (secondSafe !== secondBitmap) secondSafe.recycle()
        }
    }

    /** Extracts frame 0 of each video URI and embeds both frames in [RunningMode.VIDEO]. */
    fun embedVideo(firstVideoUri: Uri, secondVideoUri: Uri): ResultBundle? {
        val firstFrame = extractVideoFrame(context, firstVideoUri)
        if (firstFrame == null) {
            listener?.onError("Failed to extract frame from first video.")
            return null
        }
        val secondFrame = extractVideoFrame(context, secondVideoUri)
        if (secondFrame == null) {
            firstFrame.recycle()
            listener?.onError("Failed to extract frame from second video.")
            return null
        }
        return try {
            embedVideoFrames(firstFrame, secondFrame)
        } finally {
            firstFrame.recycle()
            secondFrame.recycle()
        }
    }

    /** Embeds two frames in [RunningMode.VIDEO] with strictly increasing timestamps. */
    fun embedVideoFrames(firstFrame: Bitmap, secondFrame: Bitmap): ResultBundle? {
        if (runningMode != RunningMode.VIDEO) {
            throw IllegalArgumentException(
                "Attempting to call embedVideoFrames while not using RunningMode.VIDEO"
            )
        }
        val embedder = imageEmbedder ?: return null
        val startTime = SystemClock.uptimeMillis()

        val firstSafe =
            if (firstFrame.config == Bitmap.Config.ARGB_8888) firstFrame
            else firstFrame.copy(Bitmap.Config.ARGB_8888, false)
        val secondSafe =
            if (secondFrame.config == Bitmap.Config.ARGB_8888) secondFrame
            else secondFrame.copy(Bitmap.Config.ARGB_8888, false)

        return try {
            val firstMpImage = BitmapImageBuilder(firstSafe).build()
            val secondMpImage = BitmapImageBuilder(secondSafe).build()
            val firstTimestampMs = nextVideoTimestampMs
            nextVideoTimestampMs += VIDEO_FRAME_STEP_MS
            val secondTimestampMs = nextVideoTimestampMs
            nextVideoTimestampMs += VIDEO_FRAME_STEP_MS
            val firstEmbed =
                embedder
                    .embedForVideo(firstMpImage, firstTimestampMs)
                    .embeddingResult()
                    .embeddings()
                    .firstOrNull()
                    ?: return null
            val secondEmbed =
                embedder
                    .embedForVideo(secondMpImage, secondTimestampMs)
                    .embeddingResult()
                    .embeddings()
                    .firstOrNull()
                    ?: return null
            val inferenceTimeMs = SystemClock.uptimeMillis() - startTime
            ResultBundle(
                similarity = ImageEmbedder.cosineSimilarity(firstEmbed, secondEmbed),
                inferenceTime = inferenceTimeMs,
                loadTimeMs = lastLoadTimeMs,
            )
        } catch (e: RuntimeException) {
            val detail = e.message ?: "RuntimeException"
            val errorCode = if (currentDelegate == DELEGATE_GPU) GPU_ERROR else OTHER_ERROR
            Log.e(TAG, "Image embedder failed to embed video frames with error: $detail", e)
            listener?.onError("Image embedder failed in video mode: $detail", errorCode)
            null
        } finally {
            if (firstSafe !== firstFrame) firstSafe.recycle()
            if (secondSafe !== secondFrame) secondSafe.recycle()
        }
    }

    fun clearImageEmbedder() {
        try {
            imageEmbedder?.close()
        } catch (e: RuntimeException) {
            Log.e(TAG, "Error closing image embedder: ${e.message}", e)
        } finally {
            imageEmbedder = null
        }
    }

    /** Wraps cosine similarity and timing measurements from comparing two images or video frames. */
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
        private const val VIDEO_FRAME_STEP_MS = 33L
        private const val TAG = "ImageEmbedderHelper"

        fun extractVideoFrame(context: Context, videoUri: Uri, timeUs: Long = 0L): Bitmap? {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(context, videoUri)
                val raw =
                    retriever.getFrameAtTime(timeUs)
                        ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                        ?: return null
                if (raw.config == Bitmap.Config.ARGB_8888) {
                    raw
                } else {
                    raw.copy(Bitmap.Config.ARGB_8888, false).also { raw.recycle() }
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Failed to extract video frame from $videoUri: ${e.message}", e)
                null
            } finally {
                runCatching { retriever.release() }
            }
        }
    }

    interface EmbedderListener {
        fun onError(error: String, errorCode: Int = OTHER_ERROR)
    }
}
