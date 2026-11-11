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

/** Real-model confidence mask instrumentation tests for [ImageSegmenterHelper]. */
@RunWith(AndroidJUnit4::class)
class ImageSegmentationTest {

    companion object {
        private const val INPUT_IMAGE = "input_image.jpeg"
    }

    @Test
    fun detectImage_deepLabV3_returnsValidConfidenceMasks_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadTestAsset(INPUT_IMAGE)

        val recorder = LatencyRecorder("image_segmenter_confidence_mask_cpu")
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
            val result =
                recorder.measure("detect_image_ms") {
                    helper.segmentImageFile(BitmapImageBuilder(bitmap).build())
                }
            assertNotNull(result)

            val masks = result!!.confidenceMasks().orElse(null)
            assertNotNull(masks)
            assertEquals(21, masks!!.size)

            val backgroundBuffer = ByteBufferExtractor.extract(masks[0]).asFloatBuffer()
            assertEquals(bitmap.width * bitmap.height, backgroundBuffer.capacity())
            var hasHighBackground = false
            var hasLowBackgroundForeground = false
            for (i in 0 until backgroundBuffer.capacity()) {
                val conf = backgroundBuffer.get(i)
                assertTrue("Confidence out of [0,1]: $conf", conf in 0f..1f)
                if (conf > 0.8f) hasHighBackground = true
                if (conf < 0.2f) hasLowBackgroundForeground = true
            }
            assertTrue("Expected confident background region", hasHighBackground)
            assertTrue("Expected confident foreground region", hasLowBackgroundForeground)

            val bundle = helper.detectImage(bitmap)
            assertNotNull(bundle)
            assertTrue("Expected foreground % in (5, 95)", bundle!!.foregroundPercent in 5..95)

            repeat(5) {
                recorder.measure("detect_steady_ms") { helper.detectImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearImageSegmenter()
            bitmap.recycle()
        }
    }

    @Test
    fun detectImage_secondaryModels_returnConfidenceMasks() {
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
                    assertTrue(bundle!!.maskCount >= 1)
                    assertTrue(bundle.maskWidth > 0 && bundle.maskHeight > 0)
                } finally {
                    helper.clearImageSegmenter()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun detectLiveStream_returnsConfidenceMaskResult() {
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
            assertEquals(21, bundle!!.maskCount)
        } finally {
            helper.clearImageSegmenter()
            bitmap.recycle()
        }
    }

    private fun loadTestAsset(fileName: String): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open(fileName).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode $fileName" }
        }
}
