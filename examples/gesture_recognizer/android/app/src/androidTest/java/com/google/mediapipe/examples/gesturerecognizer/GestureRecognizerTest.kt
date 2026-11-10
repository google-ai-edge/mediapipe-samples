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
package com.google.mediapipe.examples.gesturerecognizer

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
import androidx.test.platform.app.InstrumentationRegistry
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

/** Instrumented tests for [GestureRecognizerHelper] across IMAGE, VIDEO, and LIVE_STREAM modes. */
@RunWith(AndroidJUnit4::class)
class GestureRecognizerTest {

    private companion object {
        private const val TAG = "GestureRecognizerTest"
        private const val TEST_IMAGE = "hand_thumb_up.jpg"
        private const val TEST_VIDEO = "test_video.mp4"

        private val expectedCategoriesForImageAndLiveStreamMode =
            listOf(Category.create(0.8105f, 0, "Thumb_Up", ""))

        private val expectedCategoryForVideoMode =
            listOf(Category.create(0.7876975f, 0, "Pointing_Up", ""))
    }

    @Test
    fun recognizerLiveStreamModeResultFallsWithinAcceptedRange() {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<GestureRecognizerHelper.ResultBundle?>()
        val errorRef = AtomicReference<String?>()

        val helper =
            GestureRecognizerHelper(
                context = ApplicationProvider.getApplicationContext(),
                runningMode = RunningMode.LIVE_STREAM,
                gestureRecognizerListener =
                    object : GestureRecognizerHelper.GestureRecognizerListener {
                        override fun onError(error: String, errorCode: Int) {
                            errorRef.compareAndSet(null, error)
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
                            resultRef.compareAndSet(null, resultBundle)
                            latch.countDown()
                        }
                    },
            )
        val bitmap = loadImage(TEST_IMAGE)
        try {
            assertFalse("GestureRecognizer LIVE_STREAM failed to initialize", helper.isClosed)
            helper.recognizeAsync(BitmapImageBuilder(bitmap).build(), SystemClock.uptimeMillis())

            assertTrue("Timed out waiting for LIVE_STREAM callback", latch.await(15, TimeUnit.SECONDS))
            val error = errorRef.get()
            if (error != null) {
                throw AssertionError("Unexpected LIVE_STREAM error: $error")
            }

            val bundle = resultRef.get()
            assertNotNull(bundle)
            val categories = bundle!!.results.first().gestures().first()
            assertTrue(categories.isNotEmpty())
            Log.i(
                TAG,
                "LiveStream gesture=${categories.first().categoryName()} score=${categories.first().score()}",
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().categoryName(),
                categories.first().categoryName(),
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().score(),
                categories.first().score(),
                0.05f,
            )
        } finally {
            helper.clearGestureRecognizer()
            bitmap.recycle()
        }
    }

    @Test
    fun recognizerVideoModeResultFallsWithinAcceptedRange() {
        val helper =
            GestureRecognizerHelper(
                context = ApplicationProvider.getApplicationContext(),
                runningMode = RunningMode.VIDEO,
            )
        val tempFile = createTempVideoFile(TEST_VIDEO)
        try {
            assertFalse("GestureRecognizer VIDEO failed to initialize", helper.isClosed)
            val videoUri: Uri = tempFile.toUri()
            val recognizerResult = helper.recognizeVideoFile(videoUri, 300)
            assertNotNull(recognizerResult)

            val hashMap = HashMap<String, Pair<Float, Int>>()
            recognizerResult!!.results.forEach { frameResult ->
                frameResult.gestures().firstOrNull()?.forEach { category ->
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
            assertTrue("Expected at least one gesture in video", actualAverageCategories.isNotEmpty())
            Log.i(
                TAG,
                "Video gesture=${actualAverageCategories.first().categoryName()} score=${actualAverageCategories.first().score()}",
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
                    0.06f,
                )
            }
        } finally {
            helper.clearGestureRecognizer()
            tempFile.delete()
        }
    }

    @Test
    fun recognizerImageModeResultFallsWithinAcceptedRange_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadImage(TEST_IMAGE)

        val recorder = LatencyRecorder("gesture_recognizer_image_cpu")
        recorder.putMetadata("model", "gesture_recognizer.task")
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                GestureRecognizerHelper(context = context, runningMode = RunningMode.IMAGE)
            }
        try {
            assertFalse("GestureRecognizer IMAGE failed to initialize", helper.isClosed)
            val bundle = recorder.measure("recognize_image_ms") { helper.recognizeImage(bitmap) }
            assertNotNull(bundle)

            val recognizerResult = bundle!!.results.first()
            assertEquals(21, recognizerResult.landmarks().first().size)
            val actualCategories = recognizerResult.gestures().first()
            assertTrue(actualCategories.isNotEmpty())
            Log.i(
                TAG,
                "Image gesture=${actualCategories.first().categoryName()} score=${actualCategories.first().score()}",
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().categoryName(),
                actualCategories.first().categoryName(),
            )
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode.first().score(),
                actualCategories.first().score(),
                0.05f,
            )

            repeat(5) {
                recorder.measure("recognize_steady_ms") { helper.recognizeImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearGestureRecognizer()
            bitmap.recycle()
        }
    }

    private fun loadImage(fileName: String): Bitmap {
        val assetManager: AssetManager = InstrumentationRegistry.getInstrumentation().context.assets
        return assetManager.open(fileName).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode $fileName" }
        }
    }

    private fun createTempVideoFile(videoName: String): File {
        val assetManager: AssetManager = InstrumentationRegistry.getInstrumentation().context.assets
        val file = File.createTempFile("test_video", ".mp4")
        assetManager.open(videoName).use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        return file
    }
}
