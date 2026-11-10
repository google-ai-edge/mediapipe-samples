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
package com.google.mediapipe.examples.handlandmarker

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Instrumentation tests for [HandLandmarkerHelper] across IMAGE, VIDEO, and LIVE_STREAM modes. */
@RunWith(AndroidJUnit4::class)
class HandLandmarkerHelperTest {

    companion object {
        private const val TAG = "HandLandmarkerHelperTest"
        private const val TEST_IMAGE = "test_image.jpg"
        private const val TEST_VIDEO = "test_video.mp4"

        private val expectedCategoriesForImageAndLiveStreamMode =
            listOf(Category.create(0.7034111f, 0, "Right", ""))

        private val expectedCategoryForVideoMode =
            listOf(Category.create(0.9509236f, 0, "Right", ""))
    }

    @Test
    fun landmarkerLiveStreamModeResultFallsWithinAcceptedRange() {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<HandLandmarkerHelper.ResultBundle?>()
        val errorRef = AtomicReference<String?>()

        val handLandmarkerHelper =
            HandLandmarkerHelper(
                context = ApplicationProvider.getApplicationContext(),
                runningMode = RunningMode.LIVE_STREAM,
                handLandmarkerHelperListener =
                    object : HandLandmarkerHelper.LandmarkerListener {
                        override fun onError(error: String, errorCode: Int) {
                            errorRef.compareAndSet(null, error)
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: HandLandmarkerHelper.ResultBundle) {
                            resultRef.compareAndSet(null, resultBundle)
                            latch.countDown()
                        }
                    },
            )
        val bitmap = loadImage(TEST_IMAGE)
        try {
            assertFalse("HandLandmarker failed to initialize", handLandmarkerHelper.isClosed)
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarkerHelper.detectAsync(mpImage, SystemClock.uptimeMillis())

            assertTrue("Timed out waiting for LIVE_STREAM callback", latch.await(15, TimeUnit.SECONDS))
            val error = errorRef.get()
            if (error != null) {
                throw AssertionError("Unexpected LIVE_STREAM error: $error")
            }

            val bundle = resultRef.get()
            assertNotNull(bundle)
            val firstResult = bundle!!.results.first()
            assertEquals(21, firstResult.landmarks().first().size)
            val actualCategories = firstResult.handedness().first()
            assertTrue(actualCategories.isNotEmpty())
            Log.i(
                TAG,
                "LiveStream handedness=${actualCategories.first().categoryName()} score=${actualCategories.first().score()}",
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().categoryName(),
                actualCategories.first().categoryName(),
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().score(),
                actualCategories.first().score(),
                0.02f,
            )
        } finally {
            handLandmarkerHelper.clearHandLandmarker()
            bitmap.recycle()
        }
    }

    @Test
    fun landmarkerVideoModeResultFallsWithinAcceptedRange() {
        val handLandmarkerHelper =
            HandLandmarkerHelper(
                context = ApplicationProvider.getApplicationContext(),
                runningMode = RunningMode.VIDEO,
            )
        val tempFile = createTempVideoFile(TEST_VIDEO)
        try {
            assertFalse("HandLandmarker VIDEO mode failed to initialize", handLandmarkerHelper.isClosed)
            val videoUri: Uri = tempFile.toUri()
            val landmarkerResult = handLandmarkerHelper.detectVideoFile(videoUri, 300)

            assertNotNull(landmarkerResult)

            val hashMap = HashMap<String, Pair<Float, Int>>()
            landmarkerResult!!.results.forEach { frameResult ->
                frameResult.handedness().firstOrNull()?.forEach { category ->
                    val prev = hashMap[category.categoryName()]
                    if (prev != null) {
                        hashMap[category.categoryName()] =
                            Pair(prev.first + category.score(), prev.second + 1)
                    } else {
                        hashMap[category.categoryName()] = Pair(category.score(), 1)
                    }
                }
            }
            val actualAverageCategories =
                hashMap
                    .map { Category.create(it.value.first / it.value.second, 0, it.key, "") }
                    .sortedByDescending { it.score() }

            assertTrue("Expected at least one handedness category in video", actualAverageCategories.isNotEmpty())
            Log.i(
                TAG,
                "Video avg handedness=${actualAverageCategories.first().categoryName()} score=${actualAverageCategories.first().score()}",
            )
            val minSize = min(actualAverageCategories.size, expectedCategoryForVideoMode.size)
            for (i in 0 until minSize) {
                assertEquals(
                    expectedCategoryForVideoMode[i].categoryName(),
                    actualAverageCategories[i].categoryName(),
                )
                assertEquals(
                    expectedCategoryForVideoMode[i].score(),
                    actualAverageCategories[i].score(),
                    0.05f,
                )
            }
        } finally {
            handLandmarkerHelper.clearHandLandmarker()
            tempFile.delete()
        }
    }

    @Test
    fun landmarkerImageModeResultFallsWithinAcceptedRange_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadImage(TEST_IMAGE)

        val recorder = LatencyRecorder("hand_landmarker_image_cpu")
        recorder.putMetadata("model", "hand_landmarker.task")
        recorder.putMetadata("delegate", "CPU")

        val handLandmarkerHelper =
            recorder.measure("model_load_ms") {
                HandLandmarkerHelper(context = context, runningMode = RunningMode.IMAGE)
            }
        try {
            assertFalse("HandLandmarker IMAGE mode failed to initialize", handLandmarkerHelper.isClosed)
            val bundle = recorder.measure("detect_image_ms") { handLandmarkerHelper.detectImage(bitmap) }
            assertNotNull(bundle)

            val landmarkerResult = bundle!!.results.first()
            val landmarks = landmarkerResult.landmarks().first()
            assertEquals(21, landmarks.size)
            landmarks.forEach { lm ->
                assertTrue("Landmark x out of range: ${lm.x()}", lm.x() in 0f..1f)
                assertTrue("Landmark y out of range: ${lm.y()}", lm.y() in 0f..1f)
            }

            val actualCategories = landmarkerResult.handedness().first()
            assertTrue(actualCategories.isNotEmpty())
            Log.i(
                TAG,
                "Image handedness=${actualCategories.first().categoryName()} score=${actualCategories.first().score()}",
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().categoryName(),
                actualCategories.first().categoryName(),
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().score(),
                actualCategories.first().score(),
                0.02f,
            )

            repeat(5) {
                recorder.measure("detect_steady_ms") { handLandmarkerHelper.detectImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            handLandmarkerHelper.clearHandLandmarker()
            bitmap.recycle()
        }
    }

    @Test
    fun landmarkerImageMode_lowDetectionThreshold_andTwoHands_detectsSampleHands() {
        val handLandmarkerHelper =
            HandLandmarkerHelper(
                context = ApplicationProvider.getApplicationContext(),
                minHandDetectionConfidence = 0.2f,
                minHandPresenceConfidence = 0.2f,
                maxNumHands = 2,
                runningMode = RunningMode.IMAGE,
            )
        val bitmap = loadImage(TEST_IMAGE)
        try {
            val bundle = handLandmarkerHelper.detectImage(bitmap)
            assertNotNull(bundle)
            val result = bundle!!.results.first()
            val handCount = result.landmarks().size
            val labels = result.handedness().mapNotNull { it.firstOrNull()?.categoryName() }
            Log.i(TAG, "Two-hand detection on $TEST_IMAGE (thresh=0.2): count=$handCount, handednesses=$labels")
            assertTrue("Expected at least 1 hand detected in $TEST_IMAGE", handCount >= 1)
            result.landmarks().forEach { hand -> assertEquals(21, hand.size) }
        } finally {
            handLandmarkerHelper.clearHandLandmarker()
            bitmap.recycle()
        }
    }

    private fun loadImage(fileName: String): Bitmap {
        val assetManager: AssetManager = InstrumentationRegistryHelper.assets()
        return assetManager.open(fileName).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode $fileName" }
        }
    }

    private fun createTempVideoFile(videoName: String): File {
        val assetManager: AssetManager = InstrumentationRegistryHelper.assets()
        val file = File.createTempFile("test_video", ".mp4")
        assetManager.open(videoName).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }

    private object InstrumentationRegistryHelper {
        fun assets(): AssetManager =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
    }
}
