/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real-model instrumentation tests for [InteractiveSegmentationHelper]. */
@RunWith(AndroidJUnit4::class)
class InteractiveSegmentationHelperTest {

    companion object {
        private const val INPUT_IMAGE = "cat.jpg"
        private const val EXPECTED_MASK_IMAGE = "expected_result.png"
        private const val MIN_IOU_THRESHOLD = 0.70
    }

    @Test
    fun segmentPositiveStroke_isolatesCatForeground_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catBitmap = loadTestAsset(INPUT_IMAGE)
        val expectedBitmap = loadTestAsset(EXPECTED_MASK_IMAGE)

        val recorder = LatencyRecorder("interactive_segmentation_cpu")
        recorder.putMetadata(
            "model",
            InteractiveSegmentationHelper.MP_INTERACTIVE_SEGMENTATION_MODEL,
        )
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") { InteractiveSegmentationHelper(context = context) }
        try {
            assertFalse("InteractiveSegmentationHelper failed to initialize", helper.isClosed)
            helper.setInputImage(catBitmap)

            val catStroke =
                Stroke.builder()
                    .setBrushMode(Stroke.BrushMode.POSITIVE)
                    .setPoints(listOf(NormalizedKeypoint.create(0.354f, 0.642f)))
                    .setCompleted(true)
                    .build()

            val result = recorder.measure("detect_image_ms") { helper.segment(listOf(catStroke)) }
            assertNotNull(result)
            val bundle = result!!
            assertEquals(catBitmap.width, bundle.maskWidth)
            assertEquals(catBitmap.height, bundle.maskHeight)

            val expectedPixels = getPixels(expectedBitmap)
            val actualPixels = getPixels(bundle.maskBitmap)
            assertEquals(expectedPixels.size, actualPixels.size)

            var intersection = 0
            var union = 0
            for (i in actualPixels.indices) {
                val actualFg = Color.alpha(actualPixels[i]) > 0
                val expectedFg = Color.alpha(expectedPixels[i]) > 0
                if (actualFg && expectedFg) intersection++
                if (actualFg || expectedFg) union++
            }
            val iou = if (union > 0) intersection.toDouble() / union else 0.0
            assertTrue(
                "Expected cat segmentation IoU >= $MIN_IOU_THRESHOLD, got $iou",
                iou >= MIN_IOU_THRESHOLD,
            )

            repeat(5) {
                recorder.measure("detect_steady_ms") { helper.segment(listOf(catStroke)) }
            }
            recorder.flush(context)
        } finally {
            helper.clearInteractiveSegmenter()
            catBitmap.recycle()
            expectedBitmap.recycle()
        }
    }

    @Test
    fun segmentPositiveAndNegativeStrokes_returnsBoundedMask() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val catBitmap = loadTestAsset(INPUT_IMAGE)
        val helper = InteractiveSegmentationHelper(context = context)
        try {
            assertFalse("InteractiveSegmentationHelper failed to initialize", helper.isClosed)
            helper.setInputImage(catBitmap)

            val positiveCat =
                Stroke.builder()
                    .setBrushMode(Stroke.BrushMode.POSITIVE)
                    .setPoints(listOf(NormalizedKeypoint.create(0.354f, 0.642f)))
                    .setCompleted(true)
                    .build()
            val negativeBackground =
                Stroke.builder()
                    .setBrushMode(Stroke.BrushMode.NEGATIVE)
                    .setPoints(listOf(NormalizedKeypoint.create(0.85f, 0.50f)))
                    .setCompleted(true)
                    .build()

            val bundle = helper.segment(listOf(positiveCat, negativeBackground))
            assertNotNull(bundle)
            assertTrue("Expected selected % in 1..60, got ${bundle!!.selectedPercent}", bundle.selectedPercent in 1..60)
        } finally {
            helper.clearInteractiveSegmenter()
            catBitmap.recycle()
        }
    }

    private fun loadTestAsset(fileName: String): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open(fileName).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode $fileName" }
        }

    private fun getPixels(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels
    }
}
