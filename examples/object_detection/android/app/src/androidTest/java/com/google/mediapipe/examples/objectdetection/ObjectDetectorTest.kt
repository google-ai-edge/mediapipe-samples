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

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.objectdetector.ObjectDetectorResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.min

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ObjectDetectorTest {
    companion object {
        private const val TEST_IMAGE_NAME = "test_image.jpg"
        private const val TEST_VIDEO_NAME = "test_video.mp4"
        private lateinit var lock: ReentrantLock
        private lateinit var condition: Condition

        @BeforeClass
        @JvmStatic
        fun setup() {
            lock = ReentrantLock()
            condition = lock.newCondition()
        }
    }


    private val expectedCategoriesForImageAndLiveStreamMode = listOf(
        Category.create(0.73828125f, 0, "bottle", ""),
        Category.create(0.70703125f, 1, "wine glass", ""),
        Category.create(0.66796875f, 2, "pizza", "")
    )

    private val expectedCategoryForVideoMode = listOf(
        Category.create(0.64570314f, 0, "laptop", ""),
        Category.create(0.62890625f, 1, "person", ""),
        Category.create(0.5859375f, 2, "cell phone", ""),
    )

    /**
     * Verify that the result returned from the Object Detector Helper with
     * LIVE_STREAM mode is within the acceptable range to the expected result.
     */
    @Test
    fun detectionResultsFromLiveStreamModeFallsWithinAcceptedRange() {
        var detectionResult: ObjectDetectorResult? = null
        val objectDetectorHelper = ObjectDetectorHelper(
            context = ApplicationProvider.getApplicationContext(),
            runningMode = RunningMode.LIVE_STREAM,
            objectDetectorListener =
            object : ObjectDetectorHelper.DetectorListener {
                override fun onError(error: String, errorCode: Int) {
                    println(error)

                    // Release the lock
                    lock.withLock {
                        condition.signal()
                    }
                }

                override fun onResults(resultBundle: ObjectDetectorHelper.ResultBundle) {
                    detectionResult = resultBundle.results.first()

                    // Release the lock and start verifying the result
                    lock.withLock {
                        condition.signal()
                    }
                }
            })

        val bitmap = loadImage(TEST_IMAGE_NAME)
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Run the object detection with the test image.
        objectDetectorHelper.detectAsync(mpImage, SystemClock.uptimeMillis())

        // Lock to wait the objectDetectorHelper return the value.
        lock.withLock {
            condition.await()
        }

        // Verify that the detection result is not null.
        assertNotNull(detectionResult)

        val actualCategories =
            detectionResult!!.detections().first().categories()

        for (i in actualCategories.indices) {
            // Verify that the categories are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].categoryName(),
                actualCategories[i].categoryName()
            )

            // Verify that the scores are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].score(),
                actualCategories[i].score(), 0.05f
            )
        }
    }

    /**
     * Verify that the result returned from Object Detector Helper with
     * VIDEO mode is within the acceptable range to the expected result.
     * The result is the average of all frames.
     */
    @Test
    fun detectionResultsFromVideoModeFallsWithinAcceptedRange() {
        val objectDetectorHelper = ObjectDetectorHelper(
            context = ApplicationProvider.getApplicationContext(),
            runningMode = RunningMode.VIDEO
        )

        val videoUri = getVideoUri(TEST_VIDEO_NAME)

        // Run the object detection with the test video.
        val detectionResult = objectDetectorHelper.detectVideoFile(
            videoUri,
            300
        )

        // Verify that the detection result is not null.
        assertNotNull(detectionResult)

        // Average scores of all frames.
        val hashMap = HashMap<String, Pair<Float, Int>>()
        detectionResult!!.results.forEach { frameResult ->
            frameResult.detections().first().categories().forEach {
                if (hashMap.containsKey(it.categoryName())) {
                    hashMap[it.categoryName()] = Pair(
                        hashMap[it.categoryName()]!!.first + it.score(),
                        hashMap[it.categoryName()]!!.second + 1
                    )
                } else {
                    hashMap[it.categoryName()] = Pair(it.score(), 1)
                }
            }
        }
        val actualAverageCategories = hashMap.map {
            val averageScore = it.value.first / it.value.second
            Category.create(averageScore, 0, it.key, "")
        }.toList().sortedByDescending { it.score() }

        val minSize =
            min(actualAverageCategories.size, expectedCategoryForVideoMode.size)

        for (i in 0 until minSize) {
            // Verify that the categories are correct.
            assertEquals(
                expectedCategoryForVideoMode[i].categoryName(),
                actualAverageCategories[i].categoryName()
            )

            // Verify that the scores are correct.
            assertEquals(
                expectedCategoryForVideoMode[i].score(),
                actualAverageCategories[i].score(), 0.05f
            )
        }
    }

    /**
     * Verify that the result returned from the Object Detector Helper with
     * IMAGE mode is within the acceptable range to the expected result.
     */
    @Test
    fun detectionResultsFromImageModeFallsWithinAcceptedRange() {
        val objectDetectorHelper = ObjectDetectorHelper(
            context = ApplicationProvider.getApplicationContext(),
            runningMode = RunningMode.IMAGE
        )

        val bitmap = loadImage(TEST_IMAGE_NAME)

        // Run the object detection with the test image.
        val detectionResult =
            objectDetectorHelper.detectImage(bitmap!!)?.results?.first()

        // Verify that the detection result is not null.
        assertNotNull(detectionResult)

        val actualCategories =
            detectionResult!!.detections().first().categories()

        for (i in actualCategories.indices) {
            // Verify that the categories are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].categoryName(),
                actualCategories[i].categoryName()
            )

            // Verify that the scores are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].score(),
                actualCategories[i].score(), 0.05f
            )
        }
    }

    @Throws(Exception::class)
    private fun loadImage(fileName: String): Bitmap? {
        val assetManager: AssetManager =
            InstrumentationRegistry.getInstrumentation().context.assets
        val inputStream: InputStream = assetManager.open(fileName)
        return BitmapFactory.decodeStream(inputStream)
    }

    @Throws(Exception::class)
    private fun getVideoUri(videoName: String): Uri {
        val assetManager: AssetManager =
            InstrumentationRegistry.getInstrumentation().context.assets
        val file = File.createTempFile("test_video", ".mp4")
        val output = FileOutputStream(file)
        val inputStream: InputStream = assetManager.open(videoName)
        inputStream.copyTo(output)
        return file.toUri()
    }
}
