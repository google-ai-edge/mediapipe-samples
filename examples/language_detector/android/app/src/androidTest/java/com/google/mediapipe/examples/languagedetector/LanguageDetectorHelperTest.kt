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

package com.google.mediapipe.examples.languagedetector

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.EmbeddingMetrics
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanguageDetectorHelperTest {

    companion object {
        private const val TAG = "LanguageDetectorHelperTest"
    }

    @Test
    fun languageDetector_multilingualGoldenAndOptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("language_detector")
        recorder.putMetadata("model", LanguageDetectorHelper.MODEL_DETECTOR)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                LanguageDetectorHelper(
                    context = context,
                    maxResults = LanguageDetectorHelper.MAX_RESULTS_DEFAULT,
                    currentDelegate = LanguageDetectorHelper.DELEGATE_CPU,
                )
            }
        try {
            assertTrue("LanguageDetector failed to initialize", helper.isReady)

            val expectedSamples =
                listOf(
                    Triple("你好！我爱小猫", "zh", 0.9970),
                    Triple(
                        "MediaPipe offers cross-platform, customizable ML solutions for live and streaming media.",
                        "en",
                        0.9995,
                    ),
                    Triple(
                        "Bonjour tout le monde, comment allez-vous aujourd'hui ?",
                        "fr",
                        0.9999,
                    ),
                    Triple(
                        "Hola, me encanta aprender nuevos idiomas con inteligencia artificial.",
                        "es",
                        0.9948,
                    ),
                    Triple("こんにちは、今日はとても良い天気ですね。", "ja", 0.9992),
                )

            expectedSamples.forEachIndexed { idx, (input, expectedCode, expectedProb) ->
                val bundle =
                    if (idx == 0) {
                        recorder.measure("detect_chinese_ms") { helper.detect(input) }
                    } else {
                        helper.detect(input)
                    }
                assertNotNull(bundle)
                bundle!!
                Log.i(TAG, "sample[$idx] code=${bundle.predictions.firstOrNull()} ms=${bundle.inferenceTime}")

                assertTrue(
                    "Expected at most ${LanguageDetectorHelper.MAX_RESULTS_DEFAULT} predictions, got ${bundle.predictions.size}",
                    bundle.predictions.size in 1..LanguageDetectorHelper.MAX_RESULTS_DEFAULT,
                )
                val top = bundle.predictions.first()
                assertEquals("Unexpected top language code for '$input'", expectedCode, top.languageCode)
                EmbeddingMetrics.assertSimilarityClose(
                    expected = expectedProb,
                    actual = top.probability.toDouble(),
                    tolerance = 0.02,
                    label = "lang.$expectedCode",
                )
            }

            assertEquals("Chinese", LanguageDetectorHelper.resolveLanguageName("zh"))
            assertEquals("English", LanguageDetectorHelper.resolveLanguageName("en"))
            assertEquals("French", LanguageDetectorHelper.resolveLanguageName("fr"))
            assertEquals("Unknown", LanguageDetectorHelper.resolveLanguageName("unknown"))

            repeat(5) {
                recorder.measure("detect_steady_ms") {
                    helper.detect(expectedSamples[1].first)
                }
            }
            recorder.flush(context)

            helper.maxResults = 1
            assertTrue("Failed to re-init with maxResults=1", helper.initDetector())
            val single = helper.detect("Hallo Welt, das ist ein schöner Tag zum Programmieren.")!!
            assertEquals(1, single.predictions.size)
            assertEquals("de", single.predictions.first().languageCode)
            assertTrue(single.predictions.first().probability > 0.95f)
        } finally {
            helper.clearLanguageDetector()
        }
    }
}
