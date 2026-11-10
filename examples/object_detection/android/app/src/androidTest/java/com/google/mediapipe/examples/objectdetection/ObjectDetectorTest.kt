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
package com.google.mediapipe.examples.objectdetection

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

/** Instrumented test for [ObjectDetectorHelper] across IMAGE, VIDEO, and LIVE_STREAM modes. */
@RunWith(AndroidJUnit4::class)
class ObjectDetectorTest {

    companion object {
        private const val TAG = "ObjectDetectorTest"
        private const val TEST_IMAGE_NAME = "test_image.jpg"
        private const val TEST_VIDEO_NAME = "test_video.mp4"

        private val expectedCategoriesForImageAndLiveStreamMode =
            listOf(
                Category.create(0.74924266f, 0, "bottle", ""),
                Category.create(0.7368947f, 1, "pizza", ""),
                Category.create(0.69547653f, 2, "dining table", ""),
            )

        private val expectedCategoryForVideoMode =
            listOf(
                Category.create(0.65084565f, 0, "person", ""),
                Category.create(0.53448033f, 1, "cell phone", ""),
            )
    }

    @Test
    fun detectionResultsFromLiveStreamModeFallsWithinAcceptedRange() {
        val latch = CountDownLatch(1)
        val resultRef = AtomicReference<ObjectDetectorHelper.ResultBundle?>()
        val errorRef = AtomicReference<String?>()

        val objectDetectorHelper =
            ObjectDetectorHelper(
                context = ApplicationProvider.getApplicationContext(),
                runningMode = RunningMode.LIVE_STREAM,
                objectDetectorListener =
                    object : ObjectDetectorHelper.DetectorListener {
                        override fun onError(error: String, errorCode: Int) {
                            errorRef.compareAndSet(null, error)
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
                            resultRef.compareAndSet(null, resultBundle)
                            latch.countDown()
                        }
                    },
            )
        val bitmap = loadImage(TEST_IMAGE_NAME)
        try {
            assertFalse("ObjectDetector LIVE_STREAM failed to initialize", objectDetectorHelper.isClosed)
            val mpImage = BitmapImageBuilder(bitmap).build()
            objectDetectorHelper.detectAsync(mpImage, SystemClock.uptimeMillis())

            assertTrue("Timed out waiting for LIVE_STREAM callback", latch.await(15, TimeUnit.SECONDS))
            val error = errorRef.get()
            if (error != null) {
                throw AssertionError("Unexpected LIVE_STREAM error: $error")
            }

            val bundle = resultRef.get()
            assertNotNull(bundle)
            val detections = bundle!!.results.first().detections()
            assertTrue("Expected at least one detection", detections.isNotEmpty())
            val actualLabels = detections.mapNotNull { it.categories().firstOrNull()?.categoryName() }
            val actualScores = detections.mapNotNull { it.categories().firstOrNull()?.score() }
            Log.i(TAG, "LiveStream Lite0 detections=$actualLabels scores=$actualScores")

            val expectedSize = min(detections.size, expectedCategoriesForImageAndLiveStreamMode.size)
            for (i in 0 until expectedSize) {
                val category = detections[i].categories().first()
                assertEquals(
                    expectedCategoriesForImageAndLiveStreamMode[i].categoryName(),
                    category.categoryName(),
                )
                assertEquals(
                    expectedCategoriesForImageAndLiveStreamMode[i].score(),
                    category.score(),
                    0.08f,
                )
            }
        } finally {
            objectDetectorHelper.clearObjectDetector()
            bitmap.recycle()
        }
    }

    @Test
    fun detectionResultsFromVideoModeFallsWithinAcceptedRange() {
        val objectDetectorHelper =
            ObjectDetectorHelper(
                context = ApplicationProvider.getApplicationContext(),
                runningMode = RunningMode.VIDEO,
            )
        val tempFile = createTempVideoFile(TEST_VIDEO_NAME)
        try {
            assertFalse("ObjectDetector VIDEO failed to initialize", objectDetectorHelper.isClosed)
            val videoUri: Uri = tempFile.toUri()
            val detectionResult = objectDetectorHelper.detectVideoFile(videoUri, 300)
            assertNotNull(detectionResult)

            val hashMap = HashMap<String, Pair<Float, Int>>()
            detectionResult!!.results.forEach { frameResult ->
                frameResult.detections().forEach { detection ->
                    detection.categories().firstOrNull()?.let { category ->
                        val prev = hashMap[category.categoryName()]
                        if (prev != null) {
                            hashMap[category.categoryName()] =
                                Pair(prev.first + category.score(), prev.second + 1)
                        } else {
                            hashMap[category.categoryName()] = Pair(category.score(), 1)
                        }
                    }
                }
            }
            val actualAverageCategories =
                hashMap
                    .map { Category.create(it.value.first / it.value.second, 0, it.key, "") }
                    .sortedByDescending { it.score() }
            Log.i(
                TAG,
                "Video top avg categories=${actualAverageCategories.take(3).map { "${it.categoryName()}:${it.score()}" }}",
            )
            assertTrue("Expected video detections", actualAverageCategories.isNotEmpty())
            val minSize = min(actualAverageCategories.size, expectedCategoryForVideoMode.size)
            for (i in 0 until minSize) {
                assertEquals(
                    expectedCategoryForVideoMode[i].categoryName(),
                    actualAverageCategories[i].categoryName(),
                )
                assertEquals(
                    expectedCategoryForVideoMode[i].score(),
                    actualAverageCategories[i].score(),
                    0.08f,
                )
            }
        } finally {
            objectDetectorHelper.clearObjectDetector()
            tempFile.delete()
        }
    }

    @Test
    fun detectionResultsFromImageModeFallsWithinAcceptedRange_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadImage(TEST_IMAGE_NAME)

        val recorder = LatencyRecorder("object_detector_lite0_cpu")
        recorder.putMetadata("model", ObjectDetectorHelper.MODEL_EFFICIENTDETV0_NAME)
        recorder.putMetadata("delegate", "CPU")

        val objectDetectorHelper =
            recorder.measure("model_load_ms") {
                ObjectDetectorHelper(context = context, runningMode = RunningMode.IMAGE)
            }
        try {
            assertFalse("ObjectDetector Lite0 IMAGE failed to initialize", objectDetectorHelper.isClosed)
            val bundle = recorder.measure("detect_image_ms") { objectDetectorHelper.detectImage(bitmap) }
            assertNotNull(bundle)
            val detections = bundle!!.results.first().detections()
            assertTrue(detections.isNotEmpty())
            val expectedSize = min(detections.size, expectedCategoriesForImageAndLiveStreamMode.size)
            for (i in 0 until expectedSize) {
                val category = detections[i].categories().first()
                assertEquals(
                    expectedCategoriesForImageAndLiveStreamMode[i].categoryName(),
                    category.categoryName(),
                )
                assertEquals(
                    expectedCategoriesForImageAndLiveStreamMode[i].score(),
                    category.score(),
                    0.08f,
                )
            }

            repeat(5) {
                recorder.measure("detect_steady_ms") { objectDetectorHelper.detectImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            objectDetectorHelper.clearObjectDetector()
            bitmap.recycle()
        }
    }

    @Test
    fun efficientDetLite2_detectsSampleImage_andRecordsLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadImage(TEST_IMAGE_NAME)

        val recorder = LatencyRecorder("object_detector_lite2_cpu")
        recorder.putMetadata("model", ObjectDetectorHelper.MODEL_EFFICIENTDETV2_NAME)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                ObjectDetectorHelper(
                    context = context,
                    currentModel = ObjectDetectorHelper.MODEL_EFFICIENTDETV2,
                    runningMode = RunningMode.IMAGE,
                )
            }
        try {
            assertFalse("ObjectDetector Lite2 failed to initialize", helper.isClosed)
            val bundle = recorder.measure("detect_image_ms") { helper.detectImage(bitmap) }
            assertNotNull(bundle)
            val labels =
                bundle!!.results.first().detections().mapNotNull { it.categories().firstOrNull()?.categoryName() }
            Log.i(TAG, "Image Lite2 labels=$labels")
            assertTrue("Expected Lite2 to detect bottle or wine glass: $labels", labels.contains("bottle"))

            repeat(5) {
                recorder.measure("detect_steady_ms") { helper.detectImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearObjectDetector()
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
