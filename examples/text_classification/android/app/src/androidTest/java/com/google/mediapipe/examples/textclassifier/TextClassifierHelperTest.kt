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

package com.google.mediapipe.examples.textclassifier

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
class TextClassifierHelperTest {

    companion object {
        private const val TAG = "TextClassifierHelperTest"
        private const val NEGATIVE_GOLDEN_TEXT = "unflinchingly bleak and desperate"
        private const val EXPECTED_BERT_NEGATIVE_SCORE = 0.9558
        private const val EXPECTED_BERT_POSITIVE_SCORE = 0.0442
        private const val POSITIVE_TEXT =
            "I am so thrilled and grateful for this wonderful day, everything came together beautifully!"
        private const val NEGATIVE_TEXT =
            "The movie was terrible, boring, and a complete waste of time."
    }

    @Test
    fun mobileBert_classificationQualityAndRanking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("text_classification_mobilebert")
        recorder.putMetadata("model", TextClassifierHelper.MOBILEBERT)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                TextClassifierHelper(
                    currentModel = TextClassifierHelper.MOBILEBERT,
                    context = context,
                    currentDelegate = TextClassifierHelper.DELEGATE_CPU,
                )
            }
        try {
            assertTrue("MobileBERT classifier failed to initialize", helper.isReady)

            val bleakResult =
                recorder.measure("classify_negative_ms") {
                    helper.classify(NEGATIVE_GOLDEN_TEXT)
                }
            assertNotNull(bleakResult)
            bleakResult!!
            Log.i(TAG, "MobileBERT bleak=$bleakResult")

            assertEquals(2, bleakResult.categories.size)
            val bleakNeg = bleakResult.categories.first { it.displayLabel == "Negative" }
            val bleakPos = bleakResult.categories.first { it.displayLabel == "Positive" }
            assertEquals("Negative", bleakResult.categories.first().displayLabel)
            EmbeddingMetrics.assertSimilarityClose(
                expected = EXPECTED_BERT_NEGATIVE_SCORE,
                actual = bleakNeg.score.toDouble(),
                tolerance = 0.02,
                label = "mobileBert.bleakNegative",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = EXPECTED_BERT_POSITIVE_SCORE,
                actual = bleakPos.score.toDouble(),
                tolerance = 0.02,
                label = "mobileBert.bleakPositive",
            )
            EmbeddingMetrics.assertSimilarityClose(
                expected = 1.0,
                actual = bleakNeg.score.toDouble() + bleakPos.score.toDouble(),
                tolerance = 1e-4,
                label = "mobileBert.scoreSum",
            )

            val posResult = helper.classify(POSITIVE_TEXT)!!
            assertEquals("Positive", posResult.categories.first().displayLabel)
            assertTrue(
                "Expected positive > 0.95 for clearly positive text, got ${posResult.categories.first().score}",
                posResult.categories.first().score > 0.95f,
            )

            val negResult = helper.classify(NEGATIVE_TEXT)!!
            assertEquals("Negative", negResult.categories.first().displayLabel)
            assertTrue(
                "Expected negative > 0.95 for clearly negative text, got ${negResult.categories.first().score}",
                negResult.categories.first().score > 0.95f,
            )

            val presetsById = DefaultPresets.associateBy { it.id }
            val androidPresetScore = helper.classify(presetsById.getValue("android").text)!!
            assertEquals("Positive", androidPresetScore.categories.first().displayLabel)
            assertTrue(androidPresetScore.categories.first().score > 0.90f)

            repeat(5) {
                recorder.measure("classify_steady_ms") {
                    helper.classify(POSITIVE_TEXT)
                }
            }
            recorder.flush(context)
        } finally {
            helper.clearTextClassifier()
        }
    }

    @Test
    fun wordVec_classificationQualityAndRanking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("text_classification_wordvec")
        recorder.putMetadata("model", TextClassifierHelper.WORD_VEC)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                TextClassifierHelper(
                    currentModel = TextClassifierHelper.WORD_VEC,
                    context = context,
                    currentDelegate = TextClassifierHelper.DELEGATE_CPU,
                )
            }
        try {
            assertTrue("WordVec classifier failed to initialize", helper.isReady)

            val posResult =
                recorder.measure("classify_positive_ms") {
                    helper.classify(POSITIVE_TEXT)
                }!!
            val negResult = helper.classify(NEGATIVE_TEXT)!!
            Log.i(TAG, "WordVec pos=$posResult neg=$negResult")

            assertEquals(2, posResult.categories.size)
            assertEquals("Positive", posResult.categories.first().displayLabel)
            assertTrue(
                "Expected WordVec Positive > 0.85, got ${posResult.categories.first().score}",
                posResult.categories.first().score > 0.85f,
            )

            assertEquals("Negative", negResult.categories.first().displayLabel)
            assertTrue(
                "Expected WordVec Negative > 0.95, got ${negResult.categories.first().score}",
                negResult.categories.first().score > 0.95f,
            )

            val sumPos = posResult.categories.sumOf { it.score.toDouble() }
            EmbeddingMetrics.assertSimilarityClose(
                expected = 1.0,
                actual = sumPos,
                tolerance = 1e-4,
                label = "wordVec.scoreSum",
            )

            repeat(5) {
                recorder.measure("classify_steady_ms") {
                    helper.classify(POSITIVE_TEXT)
                }
            }
            recorder.flush(context)
        } finally {
            helper.clearTextClassifier()
        }
    }
}
