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
package com.google.mediapipe.examples.imageclassification

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
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult
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
class ImageClassificationTest {
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
        Category.create(0.54f, 0, "red wine", ""),
        Category.create(0.10546875f, 1, "wine bottle", "")
    )

    private val expectedCategoryForVideoMode = listOf(
        Category.create(0.40564904f, 0, "laptop", ""),
        Category.create(0.20930989f, 1, "notebook", ""),
        Category.create(0.1328125f, 2, "iPod", ""),
    )

    /**
     * Verify that the result returned from the Image Classifier Helper with
     * LIVE_STREAM mode is within the acceptable range to the expected result.
     */
    @Test
    fun classificationResultsFromLiveStreamModeFallsWithinAcceptedRange() {
        var classifierResult: ImageClassifierResult? = null
        val imageClassifierHelper = ImageClassifierHelper(
            context = ApplicationProvider.getApplicationContext(),
            runningMode = RunningMode.LIVE_STREAM,
            threshold = 0.1f,
            imageClassifierListener =
            object : ImageClassifierHelper.ClassifierListener {
                override fun onError(error: String, errorCode: Int) {
                    println(error)

                    // Release the lock
                    lock.withLock {
                        condition.signal()
                    }
                }


                override fun onResults(resultBundle: ImageClassifierHelper.ResultBundle) {
                    classifierResult = resultBundle.results.first()

                    // Release the lock and start verifying the result
                    lock.withLock {
                        condition.signal()
                    }
                }
            })

        val bitmap = loadImage(TEST_IMAGE_NAME)
        val mpImage = BitmapImageBuilder(bitmap).build()

        // Run the image classification with the test image.
        imageClassifierHelper.classifyAsync(
            mpImage,
            0,
            SystemClock.uptimeMillis()
        )

        // Lock to wait the imageClassifier return the value.
        lock.withLock {
            condition.await()
        }

        // Verify that the classification result is not null.
        assertNotNull(classifierResult)

        val actualCategories =
            classifierResult!!.classificationResult().classifications().first()
                .categories()

        for (i in actualCategories.indices) {
            // Verify that the categories are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].categoryName(),
                actualCategories[i].categoryName()
            )

            // Verify that the scores are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].score(),
                actualCategories[i].score(), 0.01f
            )
        }
    }

    /**
     * Verify that the result returned from Image Classifier Helper with
     * VIDEO mode is within the acceptable range to the expected result.
     * The result is the average of all frames.
     */
    @Test
    fun classificationResultsFromVideoModeFallsWithinAcceptedRange() {
        val imageClassifierHelper = ImageClassifierHelper(
            context = ApplicationProvider.getApplicationContext(),
            runningMode = RunningMode.VIDEO,
            threshold = 0.1f,
        )

        val videoUri = getVideoUri(TEST_VIDEO_NAME)

        // Run the image classification with the test video.
        val classificationResult = imageClassifierHelper.classifyVideoFile(
            videoUri,
            300
        )

        // Verify that the classification result is not null.
        assertNotNull(classificationResult)

        // Average scores of all frames.
        val hashMap = HashMap<String, Pair<Float, Int>>()
        classificationResult!!.results.forEach { frameResult ->
            frameResult.classificationResult().classifications().first()
                .categories().forEach {
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
            min(
                actualAverageCategories.size, expectedCategoryForVideoMode.size
            )

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
     * Verify that the result returned from the Image Classifier Helper with
     * IMAGE mode is within the acceptable range to the expected result.
     */
    @Test
    fun classificationResultsFromImageModeFallsWithinAcceptedRange() {
        val imageClassifierHelper = ImageClassifierHelper(
            context = ApplicationProvider.getApplicationContext(),
            runningMode = RunningMode.IMAGE,
            threshold = 0.1f
        )

        val bitmap = loadImage(TEST_IMAGE_NAME)

        // Run the image classification with the test image.
        val classificationResult =
            imageClassifierHelper.classifyImage(bitmap!!)?.results?.first()

        // Verify that the classification result is not null.
        assertNotNull(classificationResult)

        val actualCategories =
            classificationResult!!.classificationResult().classifications()
                .first()
                .categories()

        for (i in actualCategories.indices) {
            // Verify that the categories are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].categoryName(),
                actualCategories[i].categoryName()
            )

            // Verify that the scores are correct.
            assertEquals(
                expectedCategoriesForImageAndLiveStreamMode[i].score(),
                actualCategories[i].score(), 0.01f
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
