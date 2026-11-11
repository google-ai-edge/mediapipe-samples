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

package com.google.mediapipe.examples.facelandmarker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
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

/** Real-model instrumentation tests for [FaceLandmarkerHelper]. */
@RunWith(AndroidJUnit4::class)
class FaceLandmarkerHelperTest {

    companion object {
        private const val TAG = "FaceLandmarkerHelperTest"
    }

    @Test
    fun detectImage_returns478FaceLandmarks_andBlendshapes_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadSampleBitmap(context)

        val recorder = LatencyRecorder("face_landmarker_image_cpu")
        recorder.putMetadata("model", "face_landmarker.task")
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                FaceLandmarkerHelper(context = context, runningMode = RunningMode.IMAGE)
            }
        try {
            assertFalse("FaceLandmarker IMAGE failed to initialize", helper.isClosed)
            val bundle = recorder.measure("detect_image_ms") { helper.detectImage(bitmap) }
            assertNotNull(bundle)

            val result = bundle!!.result
            assertEquals(1, result.faceLandmarks().size)
            val landmarks = result.faceLandmarks().first()
            assertEquals(478, landmarks.size)
            landmarks.forEach { lm ->
                assertTrue("Landmark x out of range: ${lm.x()}", lm.x() in 0f..1f)
                assertTrue("Landmark y out of range: ${lm.y()}", lm.y() in 0f..1f)
            }

            assertTrue("Expected face blendshapes output", result.faceBlendshapes().isPresent)
            val blendshapes = result.faceBlendshapes().get().first()
            assertTrue("Expected 52 blendshape categories", blendshapes.size >= 52)
            val topNonNeutral =
                blendshapes
                    .filter { it.categoryName() != "_neutral" }
                    .sortedByDescending { it.score() }
                    .take(3)
            Log.i(
                TAG,
                "Image top blendshapes=${topNonNeutral.map { "${it.categoryName()}:${it.score()}" }}",
            )
            assertTrue("Expected non-zero top blendshape", topNonNeutral.first().score() > 0.05f)

            repeat(5) {
                recorder.measure("detect_steady_ms") { helper.detectImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearFaceLandmarker()
            bitmap.recycle()
        }
    }

    @Test
    fun detectLiveStream_returns478FaceLandmarks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<FaceLandmarkerHelper.ResultBundle?>()
        val errorRef = AtomicReference<String?>()

        val helper =
            FaceLandmarkerHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                faceLandmarkerHelperListener =
                    object : FaceLandmarkerHelper.LandmarkerListener {
                        override fun onError(error: String, errorCode: Int) {
                            errorRef.compareAndSet(null, error)
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
                            resultRef.compareAndSet(null, resultBundle)
                            latch.countDown()
                        }
                    },
            )
        val bitmap = loadSampleBitmap(context)
        try {
            assertFalse("FaceLandmarker LIVE_STREAM failed to initialize", helper.isClosed)
            helper.detectAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())

            assertTrue("Timed out waiting for LIVE_STREAM callback", latch.await(15, TimeUnit.SECONDS))
            val err = errorRef.get()
            if (err != null) {
                throw AssertionError("Unexpected LIVE_STREAM error: $err")
            }

            val bundle = resultRef.get()
            assertNotNull(bundle)
            assertEquals(1, bundle!!.result.faceLandmarks().size)
            assertEquals(478, bundle.result.faceLandmarks().first().size)
        } finally {
            helper.clearFaceLandmarker()
            bitmap.recycle()
        }
    }

    private fun loadSampleBitmap(context: Context): Bitmap =
        context.assets.open(FaceLandmarkerHelper.SAMPLE_IMAGE_ASSET).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) {
                "Failed to decode ${FaceLandmarkerHelper.SAMPLE_IMAGE_ASSET}"
            }
        }
}
