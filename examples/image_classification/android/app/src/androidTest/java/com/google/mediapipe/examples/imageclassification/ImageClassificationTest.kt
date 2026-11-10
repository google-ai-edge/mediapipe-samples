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

package com.google.mediapipe.examples.imageclassification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.examples.common.testing.EmbeddingMetrics
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageclassifier.ImageClassifierResult
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageClassificationTest {

    companion object {
        private const val TAG = "ImageClassificationTest"
        private const val TEST_IMAGE_NAME = "test_image.jpg"
        private const val TEST_VIDEO_NAME = "test_video.mp4"
    }

    @Test
    fun efficientNetLite0_imageAndLiveStreamModes_matchGoldenCategories() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadTestBitmap(TEST_IMAGE_NAME)
        val recorder = LatencyRecorder("image_classifier_lite0_cpu")
        recorder.putMetadata("model", ImageClassifierHelper.MODEL_EFFICIENTNETV0_FILE)
        recorder.putMetadata("delegate", "CPU")

        val imageHelper =
            recorder.measure("model_load_ms") {
                ImageClassifierHelper(
                    context = context,
                    runningMode = RunningMode.IMAGE,
                    currentModel = ImageClassifierHelper.MODEL_EFFICIENTNETV0,
                    currentDelegate = ImageClassifierHelper.DELEGATE_CPU,
                    threshold = 0.1f,
                    maxResults = 3,
                )
            }
        try {
            assertFalse("Lite0 failed to initialize in IMAGE mode", imageHelper.isClosed())
            val bundle = recorder.measure("classify_image_ms") { imageHelper.classifyImage(bitmap) }
            assertNotNull(bundle)
            val categories = bundle!!.results.first().classificationResult().classifications().first().categories()
            Log.i(TAG, "Lite0 IMAGE categories=${categories.map { "${it.categoryName()}@${it.score()}" }}")
            assertTrue("Expected red wine top category", categories.isNotEmpty())
            assertEquals("red wine", categories[0].categoryName())
            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.413,
                actual = categories[0].score().toDouble(),
                tolerance = 0.03,
                label = "lite0.top0.score",
            )

            repeat(5) {
                recorder.measure("classify_steady_ms") { imageHelper.classifyImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            imageHelper.clearImageClassifier()
        }

        val latch = CountDownLatch(1)
        var asyncResult: ImageClassifierResult? = null
        var asyncError: String? = null
        val liveHelper =
            ImageClassifierHelper(
                context = context,
                runningMode = RunningMode.LIVE_STREAM,
                currentModel = ImageClassifierHelper.MODEL_EFFICIENTNETV0,
                currentDelegate = ImageClassifierHelper.DELEGATE_CPU,
                threshold = 0.1f,
                maxResults = 3,
                imageClassifierListener =
                    object : ImageClassifierHelper.ClassifierListener {
                        override fun onError(error: String, errorCode: Int) {
                            asyncError = error
                            latch.countDown()
                        }

                        override fun onResults(resultBundle: ImageClassifierHelper.ResultBundle) {
                            asyncResult = resultBundle.results.firstOrNull()
                            latch.countDown()
                        }
                    },
            )
        try {
            assertFalse("Lite0 failed to initialize in LIVE_STREAM mode", liveHelper.isClosed())
            liveHelper.classifyAsync(BitmapImageBuilder(bitmap).build(), 0, SystemClock.uptimeMillis())
            assertTrue("Timed out waiting for LIVE_STREAM result", latch.await(15, TimeUnit.SECONDS))
            assertEquals(null, asyncError)
            assertNotNull(asyncResult)
            val liveCats = asyncResult!!.classificationResult().classifications().first().categories()
            assertEquals("red wine", liveCats[0].categoryName())
            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.413,
                actual = liveCats[0].score().toDouble(),
                tolerance = 0.03,
                label = "lite0.live.top0.score",
            )
        } finally {
            liveHelper.clearImageClassifier()
            bitmap.recycle()
        }
    }

    @Test
    fun efficientNetLite0_videoMode_returnsExpectedTopCategory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val helper =
            ImageClassifierHelper(
                context = context,
                runningMode = RunningMode.VIDEO,
                threshold = 0.1f,
                maxResults = 3,
            )
        val tempFile = copyTestAssetToTempFile(TEST_VIDEO_NAME, ".mp4")
        try {
            val bundle = helper.classifyVideoFile(tempFile.toUri(), 300L)
            assertNotNull("Video classification returned null", bundle)
            val avgByLabel = mutableMapOf<String, MutableList<Float>>()
            bundle!!.results.forEach { frame ->
                frame.classificationResult().classifications().firstOrNull()?.categories()?.forEach { cat ->
                    avgByLabel.getOrPut(cat.categoryName()) { mutableListOf() }.add(cat.score())
                }
            }
            val ranked =
                avgByLabel
                    .map { (name, scores) -> name to scores.sum() / bundle.results.size }
                    .sortedByDescending { it.second }
            Log.i(TAG, "Lite0 VIDEO ranked=$ranked")
            assertTrue("Expected video classifications", ranked.isNotEmpty())
            assertEquals("laptop", ranked[0].first)
        } finally {
            helper.clearImageClassifier()
            tempFile.delete()
        }
    }

    @Test
    fun efficientNetLite2_imageMode_classifiesSampleAndLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = loadTestBitmap(TEST_IMAGE_NAME)
        val recorder = LatencyRecorder("image_classifier_lite2_cpu")
        recorder.putMetadata("model", ImageClassifierHelper.MODEL_EFFICIENTNETV2_FILE)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                ImageClassifierHelper(
                    context = context,
                    runningMode = RunningMode.IMAGE,
                    currentModel = ImageClassifierHelper.MODEL_EFFICIENTNETV2,
                    currentDelegate = ImageClassifierHelper.DELEGATE_CPU,
                    threshold = 0.1f,
                    maxResults = 3,
                )
            }
        try {
            assertFalse("Lite2 failed to initialize on CPU", helper.isClosed())
            val bundle = recorder.measure("classify_image_ms") { helper.classifyImage(bitmap) }!!
            val categories = bundle.results.first().classificationResult().classifications().first().categories()
            Log.i(TAG, "Lite2 IMAGE categories=${categories.map { "${it.categoryName()}@${it.score()}" }}")
            assertTrue("Expected top category for Lite2", categories.isNotEmpty())
            assertEquals("red wine", categories[0].categoryName())
            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.254,
                actual = categories[0].score().toDouble(),
                tolerance = 0.03,
                label = "lite2.top0.score",
            )

            // Also verify bundled sample_cup.png classifies above default 0.5 threshold on Lite0.
            context.assets.open(ImageClassifierHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                val sampleBitmap = requireNotNull(BitmapFactory.decodeStream(stream))
                val sampleHelper =
                    ImageClassifierHelper(
                        context = context,
                        runningMode = RunningMode.IMAGE,
                        currentModel = ImageClassifierHelper.MODEL_EFFICIENTNETV0,
                        threshold = ImageClassifierHelper.THRESHOLD_DEFAULT,
                    )
                try {
                    val sampleResult = sampleHelper.classifyImage(sampleBitmap)!!
                    val sampleCats = sampleResult.results.first().classificationResult().classifications().first().categories()
                    Log.i(TAG, "Lite0 sample_cup categories=${sampleCats.map { "${it.categoryName()}@${it.score()}" }}")
                    assertTrue("Expected coffee mug for sample_cup", sampleCats.isNotEmpty())
                    assertEquals("coffee mug", sampleCats[0].categoryName())
                    EmbeddingMetrics.assertSimilarityClose(
                        expected = 0.573,
                        actual = sampleCats[0].score().toDouble(),
                        tolerance = 0.03,
                        label = "lite0.sample_cup.score",
                    )
                } finally {
                    sampleHelper.clearImageClassifier()
                    sampleBitmap.recycle()
                }
            }

            repeat(5) {
                recorder.measure("classify_steady_ms") { helper.classifyImage(bitmap) }
            }
            recorder.flush(context)
        } finally {
            helper.clearImageClassifier()
            bitmap.recycle()
        }
    }

    private fun loadTestBitmap(fileName: String): Bitmap =
        InstrumentationRegistry.getInstrumentation().context.assets.open(fileName).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode $fileName" }
        }

    private fun copyTestAssetToTempFile(assetName: String, suffix: String): File {
        val file = File.createTempFile("mp_test_", suffix)
        InstrumentationRegistry.getInstrumentation().context.assets.open(assetName).use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file
    }
}
