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
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real-model category mask instrumentation tests for [ImageSegmenterHelper]. */
@RunWith(AndroidJUnit4::class)
class ImageSegmentationTest {

    companion object {
        private const val EXPECTED_MASK_TOLERANCE = 0.01
        private const val INPUT_IMAGE = "input_image.jpeg"
        private const val EXPECTED_IMAGE = "expected_image.png"
    }

    @Test
    fun segmentationResultMatchesGoldenMask_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val testBitmap = loadTestAsset(INPUT_IMAGE)
        val expectedBitmap = loadTestAsset(EXPECTED_IMAGE)

        val recorder = LatencyRecorder("image_segmenter_category_mask_cpu")
        recorder.putMetadata("model", ImageSegmenterHelper.MODEL_DEEPLABV3_PATH)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                ImageSegmenterHelper(
                    context = context,
                    currentModel = ImageSegmenterHelper.MODEL_DEEPLABV3,
                    runningMode = RunningMode.IMAGE,
                )
            }
        try {
            assertFalse("DeepLabV3 failed to initialize", helper.isClosed)
            val mpImage = BitmapImageBuilder(testBitmap).build()
            val result = recorder.measure("detect_image_ms") { helper.segmentImageFile(mpImage) }
            assertNotNull(result)

            val byteBuffer = ByteBufferExtractor.extract(result!!.categoryMask().get())
            val outputMask = IntArray(byteBuffer.capacity())
            for (i in outputMask.indices) {
                val index = byteBuffer.get(i).toInt()
                outputMask[i] =
                    if (index in 1..20) {
                        ImageSegmenterHelper.toAlphaColor(ImageSegmenterHelper.LABEL_COLORS[index])
                    } else {
                        Color.TRANSPARENT
                    }
            }

            val maskBitmap =
                Bitmap.createBitmap(
                    outputMask,
                    mpImage.width,
                    mpImage.height,
                    Bitmap.Config.ARGB_8888,
                )
            val expectedPixels = getPixels(expectedBitmap)
            val actualPixels = getPixels(maskBitmap)
            assertEquals(expectedPixels.size, actualPixels.size)

            var inconsistentPixels = 0
            for (i in actualPixels.indices) {
                if (actualPixels[i] != expectedPixels[i]) {
                    inconsistentPixels++
                }
            }
            val mismatchRate = inconsistentPixels.toDouble() / actualPixels.size
            assertTrue(
                "Golden mask mismatch rate too high: $mismatchRate",
                mismatchRate < EXPECTED_MASK_TOLERANCE,
            )

            repeat(5) {
                recorder.measure("detect_steady_ms") { helper.detectImage(testBitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearImageSegmenter()
            testBitmap.recycle()
            expectedBitmap.recycle()
        }
    }

    @Test
    fun detectImage_secondaryModels_returnCategoryMasks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadTestAsset(INPUT_IMAGE)
        try {
            listOf(
                ImageSegmenterHelper.MODEL_HAIR_SEGMENTER,
                ImageSegmenterHelper.MODEL_SELFIE_SEGMENTER,
                ImageSegmenterHelper.MODEL_SELFIE_MULTICLASS,
            ).forEach { modelId ->
                val helper =
                    ImageSegmenterHelper(
                        context = context,
                        currentModel = modelId,
                        runningMode = RunningMode.IMAGE,
                    )
                try {
                    assertFalse("Model $modelId failed to initialize", helper.isClosed)
                    val bundle = helper.detectImage(bitmap)
                    assertNotNull("Null bundle for model $modelId", bundle)
                    assertTrue(bundle!!.maskWidth > 0 && bundle.maskHeight > 0)
                } finally {
                    helper.clearImageSegmenter()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun detectLiveStream_returnsCategoryMaskResult() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<ImageSegmenterHelper.ResultBundle?>()
        val errorRef = AtomicReference<String?>()

        val helper =
            ImageSegmenterHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                imageSegmenterListener =
                    object : ImageSegmenterHelper.SegmenterListener {
                        override fun onError(error: String, errorCode: Int) {
                            errorRef.compareAndSet(null, error)
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
                            resultRef.compareAndSet(null, resultBundle)
                            latch.countDown()
                        }
                    },
            )
        val bitmap = loadTestAsset(INPUT_IMAGE)
        try {
            assertFalse("LIVE_STREAM helper failed to initialize", helper.isClosed)
            helper.segmentAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())

            assertTrue(
                "Timed out waiting for LIVE_STREAM callback",
                latch.await(15, TimeUnit.SECONDS),
            )
            val err = errorRef.get()
            if (err != null) {
                throw AssertionError("Unexpected LIVE_STREAM error: $err")
            }

            val bundle = resultRef.get()
            assertNotNull(bundle)
            assertTrue(bundle!!.detectedCategories.isNotEmpty())
        } finally {
            helper.clearImageSegmenter()
            bitmap.recycle()
        }
    }

    private fun loadTestAsset(fileName: String): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open(fileName).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode $fileName" }
        }

    private fun getPixels(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }
}
