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

package com.google.mediapipe.examples.holisticlandmarker

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

/** Real-model instrumentation tests for [HolisticLandmarkerHelper]. */
@RunWith(AndroidJUnit4::class)
class HolisticLandmarkerHelperTest {

    @Test
    fun detectImage_returnsPoseAndFaceLandmarks_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadSampleBitmap(context)

        val recorder = LatencyRecorder("holistic_landmarker_cpu")
        recorder.putMetadata("model", HolisticLandmarkerHelper.MODEL_HOLISTIC_PATH)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                HolisticLandmarkerHelper(
                    context = context,
                    runningMode = RunningMode.IMAGE,
                )
            }
        try {
            assertFalse("HolisticLandmarker failed to initialize", helper.isClosed)
            val bundle = recorder.measure("detect_image_ms") { helper.detectImage(bitmap) }
            assertNotNull(bundle)

            val result = bundle!!.results.first()
            assertEquals(33, result.poseLandmarks().size)
            assertTrue(
                "Expected face landmarks, got ${result.faceLandmarks().size}",
                result.faceLandmarks().size >= 468,
            )
            val nose = result.poseLandmarks()[0]
            assertTrue("Nose x out of range: ${nose.x()}", nose.x() in 0f..1f)
            assertTrue("Nose y out of range: ${nose.y()}", nose.y() in 0f..1f)
            result.poseLandmarks().forEach { lm ->
                assertTrue("Pose x not finite: ${lm.x()}", lm.x().isFinite())
                assertTrue("Pose y not finite: ${lm.y()}", lm.y().isFinite())
            }

            repeat(5) {
                recorder.measure("detect_steady_ms") { helper.detectImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearHolisticLandmarker()
            bitmap.recycle()
        }
    }

    @Test
    fun detectLiveStream_returnsPoseAndFaceLandmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<HolisticLandmarkerHelper.ResultBundle?>()
        val errorRef = AtomicReference<String?>()

        val helper =
            HolisticLandmarkerHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                holisticLandmarkerHelperListener =
                    object : HolisticLandmarkerHelper.LandmarkerListener {
                        override fun onError(error: String, errorCode: Int) {
                            errorRef.compareAndSet(null, error)
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: HolisticLandmarkerHelper.ResultBundle) {
                            resultRef.compareAndSet(null, resultBundle)
                            latch.countDown()
                        }
                    },
            )
        val bitmap = loadSampleBitmap(context)
        try {
            assertFalse("HolisticLandmarker LIVE_STREAM failed to initialize", helper.isClosed)
            helper.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())

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
            val result = bundle!!.results.first()
            assertEquals(33, result.poseLandmarks().size)
            assertTrue(result.faceLandmarks().size >= 468)
        } finally {
            helper.clearHolisticLandmarker()
            bitmap.recycle()
        }
    }

    private fun loadSampleBitmap(context: Context): Bitmap =
        context.assets.open(HolisticLandmarkerHelper.SAMPLE_IMAGE_ASSET).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) {
                "Failed to decode ${HolisticLandmarkerHelper.SAMPLE_IMAGE_ASSET}"
            }
        }
}
