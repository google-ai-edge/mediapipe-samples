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

package com.google.mediapipe.examples.facedetection

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.EmbeddingMetrics
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import com.google.mediapipe.tasks.vision.core.RunningMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FaceDetectorHelperTest {

    companion object {
        private const val TAG = "FaceDetectorHelperTest"
    }

    @Test
    fun blazeFaceShortRange_cpuDetectionGoldenAndThreshold() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap =
            context.assets.open(FaceDetectorHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        assertNotNull("Failed to decode sample_faces.jpg", bitmap)
        bitmap!!

        val recorder = LatencyRecorder("face_detector_short_range_cpu")
        recorder.putMetadata("model", FaceDetectorHelper.MODEL_SHORT_RANGE_NAME)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                FaceDetectorHelper(
                    threshold = FaceDetectorHelper.THRESHOLD_DEFAULT,
                    currentDelegate = FaceDetectorHelper.DELEGATE_CPU,
                    currentModel = FaceDetectorHelper.MODEL_SHORT_RANGE,
                    runningMode = RunningMode.IMAGE,
                    context = context,
                )
            }
        try {
            assertFalse("FaceDetector failed to initialize on CPU", helper.isClosed)

            val bundle =
                recorder.measure("detect_image_ms") {
                    helper.detectImage(bitmap)
                }
            assertNotNull(bundle)
            bundle!!
            assertEquals(bitmap.width, bundle.inputImageWidth)
            assertEquals(bitmap.height, bundle.inputImageHeight)

            val detections = bundle.results.single().detections()
            Log.i(
                TAG,
                "CPU detections=${detections.size} ms=${bundle.inferenceTime} boxes=${
                    detections.map { "${it.boundingBox()}@${it.categories().firstOrNull()?.score()}" }
                }",
            )
            assertEquals("Expected 2 faces in sample_faces.jpg", 2, detections.size)

            val sorted = detections.sortedByDescending { it.categories().firstOrNull()?.score() ?: 0f }
            val first = sorted[0]
            val second = sorted[1]

            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.973,
                actual = first.categories().first().score().toDouble(),
                tolerance = 0.03,
                label = "face0.score",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.925,
                actual = second.categories().first().score().toDouble(),
                tolerance = 0.03,
                label = "face1.score",
            )

            EmbeddingMetrics.assertSimilarityClose(
                expected = 126.0,
                actual = first.boundingBox().left.toDouble(),
                tolerance = 12.0,
                label = "face0.left",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 100.0,
                actual = first.boundingBox().top.toDouble(),
                tolerance = 12.0,
                label = "face0.top",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 616.0,
                actual = second.boundingBox().left.toDouble(),
                tolerance = 12.0,
                label = "face1.left",
            )

            val overlayFrame = FaceDetectorViewModel.toDetectionFrame(bundle, frameIndex = 0)
            assertEquals(2, overlayFrame.detections.size)
            assertTrue(overlayFrame.detections.all { it.keypoints.size == 6 })

            repeat(5) {
                recorder.measure("detect_steady_ms") {
                    helper.detectImage(bitmap)
                }
            }
            recorder.flush(context)

            helper.threshold = 0.95f
            assertTrue("Failed to re-init with threshold=0.95", helper.setupFaceDetector())
            val strictBundle = helper.detectImage(bitmap)!!
            assertEquals(
                "Expected only the 0.973 face at threshold 0.95",
                1,
                strictBundle.results.single().detections().size,
            )
        } finally {
            helper.clearFaceDetector()
            bitmap.recycle()
        }
    }

    @Test
    fun blazeFaceFullRange_cpuDetectionAndLatency() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap =
            context.assets.open(FaceDetectorHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        assertNotNull("Failed to decode sample_faces.jpg", bitmap)
        bitmap!!

        val recorder = LatencyRecorder("face_detector_full_range_cpu")
        recorder.putMetadata("model", FaceDetectorHelper.MODEL_FULL_RANGE_NAME)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                FaceDetectorHelper(
                    threshold = FaceDetectorHelper.THRESHOLD_DEFAULT,
                    currentDelegate = FaceDetectorHelper.DELEGATE_CPU,
                    currentModel = FaceDetectorHelper.MODEL_FULL_RANGE,
                    runningMode = RunningMode.IMAGE,
                    context = context,
                )
            }
        try {
            assertFalse("FaceDetector Full-Range failed to initialize on CPU", helper.isClosed)

            val bundle =
                recorder.measure("detect_image_ms") {
                    helper.detectImage(bitmap)
                }
            assertNotNull(bundle)
            bundle!!
            assertEquals(bitmap.width, bundle.inputImageWidth)
            assertEquals(bitmap.height, bundle.inputImageHeight)

            val detections = bundle.results.single().detections()
            Log.i(
                TAG,
                "FullRange CPU detections=${detections.size} ms=${bundle.inferenceTime} boxes=${
                    detections.map { "${it.boundingBox()}@${it.categories().firstOrNull()?.score()}" }
                }",
            )
            assertEquals("Expected 2 faces in sample_faces.jpg with Full-Range", 2, detections.size)

            val sorted = detections.sortedByDescending { it.categories().firstOrNull()?.score() ?: 0f }
            val first = sorted[0]
            val second = sorted[1]

            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.951,
                actual = first.categories().first().score().toDouble(),
                tolerance = 0.03,
                label = "full_range.face0.score",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.887,
                actual = second.categories().first().score().toDouble(),
                tolerance = 0.03,
                label = "full_range.face1.score",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 93.0,
                actual = first.boundingBox().left.toDouble(),
                tolerance = 12.0,
                label = "full_range.face0.left",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 74.0,
                actual = first.boundingBox().top.toDouble(),
                tolerance = 12.0,
                label = "full_range.face0.top",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 659.0,
                actual = second.boundingBox().left.toDouble(),
                tolerance = 12.0,
                label = "full_range.face1.left",
            )

            val overlayFrame = FaceDetectorViewModel.toDetectionFrame(bundle, frameIndex = 0)
            assertEquals(2, overlayFrame.detections.size)
            assertTrue(overlayFrame.detections.all { it.keypoints.size == 6 })

            repeat(5) {
                recorder.measure("detect_steady_ms") {
                    helper.detectImage(bitmap)
                }
            }
            recorder.flush(context)

            helper.threshold = 0.92f
            assertTrue("Failed to re-init Full-Range with threshold=0.92", helper.setupFaceDetector())
            val strictBundle = helper.detectImage(bitmap)!!
            assertEquals(
                "Expected only the 0.951 face at threshold 0.92",
                1,
                strictBundle.results.single().detections().size,
            )
        } finally {
            helper.clearFaceDetector()
            bitmap.recycle()
        }
    }
}
