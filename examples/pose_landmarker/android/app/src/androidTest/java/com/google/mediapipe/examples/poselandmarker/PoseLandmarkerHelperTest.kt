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

package com.google.mediapipe.examples.poselandmarker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import com.google.mediapipe.framework.image.BitmapImageBuilder
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

/** Real-model instrumentation tests for [PoseLandmarkerHelper] across Lite, Full, and Heavy. */
@RunWith(AndroidJUnit4::class)
class PoseLandmarkerHelperTest {

    @Test
    fun detectImage_fullModel_returns33PoseLandmarks_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadSampleBitmap(context)

        val recorder = LatencyRecorder("pose_landmarker_full_cpu")
        recorder.putMetadata("model", PoseLandmarkerHelper.MODEL_FULL_PATH)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                PoseLandmarkerHelper(
                    context = context,
                    currentModel = PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL,
                    runningMode = RunningMode.IMAGE,
                )
            }
        try {
            assertFalse("PoseLandmarker Full failed to initialize", helper.isClosed)
            val bundle = recorder.measure("detect_image_ms") { helper.detectImage(bitmap) }
            assertNotNull(bundle)

            val result = bundle!!.results.first()
            assertEquals(1, result.landmarks().size)
            val landmarks = result.landmarks().first()
            assertEquals(33, landmarks.size)
            landmarks.forEach { lm ->
                assertTrue("Landmark x out of range: ${lm.x()}", lm.x() in 0f..1f)
                assertTrue("Landmark y out of range: ${lm.y()}", lm.y() in 0f..1f)
            }

            repeat(5) {
                recorder.measure("detect_steady_ms") { helper.detectImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearPoseLandmarker()
            bitmap.recycle()
        }
    }

    @Test
    fun detectImage_liteAndHeavyModels_return33PoseLandmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadSampleBitmap(context)
        try {
            listOf(
                PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE,
                PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_HEAVY,
            ).forEach { modelId ->
                val helper =
                    PoseLandmarkerHelper(
                        context = context,
                        currentModel = modelId,
                        runningMode = RunningMode.IMAGE,
                    )
                try {
                    assertFalse("Model $modelId failed to initialize", helper.isClosed)
                    val bundle = helper.detectImage(bitmap)
                    assertNotNull(bundle)
                    assertEquals(33, bundle!!.results.first().landmarks().first().size)
                } finally {
                    helper.clearPoseLandmarker()
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun detectLiveStream_returns33PoseLandmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<PoseLandmarkerHelper.ResultBundle?>()
        val errorRef = AtomicReference<String?>()

        val helper =
            PoseLandmarkerHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                poseLandmarkerHelperListener =
                    object : PoseLandmarkerHelper.LandmarkerListener {
                        override fun onError(error: String, errorCode: Int) {
                            errorRef.compareAndSet(null, error)
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
                            resultRef.compareAndSet(null, resultBundle)
                            latch.countDown()
                        }
                    },
            )
        val bitmap = loadSampleBitmap(context)
        try {
            assertFalse("PoseLandmarker LIVE_STREAM failed to initialize", helper.isClosed)
            helper.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())

            assertTrue("Timed out waiting for LIVE_STREAM callback", latch.await(15, TimeUnit.SECONDS))
            val err = errorRef.get()
            if (err != null) {
                throw AssertionError("Unexpected LIVE_STREAM error: $err")
            }

            val bundle = resultRef.get()
            assertNotNull(bundle)
            assertEquals(33, bundle!!.results.first().landmarks().first().size)
        } finally {
            helper.clearPoseLandmarker()
            bitmap.recycle()
        }
    }

    private fun loadSampleBitmap(context: Context): Bitmap =
        context.assets.open(PoseLandmarkerHelper.SAMPLE_IMAGE_ASSET).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) {
                "Failed to decode ${PoseLandmarkerHelper.SAMPLE_IMAGE_ASSET}"
            }
        }
}
