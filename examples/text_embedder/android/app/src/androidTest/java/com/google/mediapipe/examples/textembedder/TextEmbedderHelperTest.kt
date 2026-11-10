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

package com.google.mediapipe.examples.textembedder

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
class TextEmbedderHelperTest {

    companion object {
        private const val TAG = "TextEmbedderHelperTest"
        private const val TEXT_TEST_ONE =
            "Da Nang is a coastal city with a 60-km long shoreline. With smooth and sandy beaches " +
                "running across the coast, Da Nang beach is voted by Forbes (U.S.) to be 1 of 6 " +
                "most beautiful beaches in the world."
        private const val TEXT_TEST_TWO =
            "Da Nang is the commercial and educational centre of Central Vietnam and is the " +
                "largest city in the region. It has a well-sheltered, easily accessible port, " +
                "and its location on National Route 1 and the North–South Railway makes it a " +
                "transport hub."
        private const val TEXT_UNRELATED =
            "Quantum mechanics describes how subatomic particles behave at atomic scales."
        private const val EXPECTED_MOBILE_BERT_SIMILARITY = 0.9899
        private const val EXPECTED_MOBILE_BERT_DIMS = 512
        private const val EXPECTED_AVERAGE_WORD_DIMS = 16
    }

    @Test
    fun mobileBert_embeddingQualityAndRanking() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val recorder = LatencyRecorder("text_embedder_mobile_bert")
        recorder.putMetadata("model", TextEmbedderHelper.MODEL_MOBILE_BERT_PATH)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                TextEmbedderHelper(
                    context = context,
                    currentDelegate = TextEmbedderHelper.DELEGATE_CPU,
                    currentModel = TextEmbedderHelper.MODEL_MOBILE_BERT,
                )
            }
        try {
            assertTrue("MobileBERT failed to initialize", helper.isReady)

            // Warmup + golden related pair
            val related =
                recorder.measure("compare_related_ms") {
                    helper.compare(TEXT_TEST_ONE, TEXT_TEST_TWO)
                }
            assertNotNull(related)
            related!!
            Log.i(TAG, "MobileBERT related=$related")

            EmbeddingMetrics.assertSimilarityClose(
                expected = EXPECTED_MOBILE_BERT_SIMILARITY,
                actual = related.similarity,
                tolerance = 0.02,
                label = "mobileBert.daNang",
            )
            assertEquals(EXPECTED_MOBILE_BERT_DIMS, related.dimensions)

            // Identical text similarity should be ~1.0
            val identical = helper.compare(TEXT_TEST_ONE, TEXT_TEST_ONE)!!
            EmbeddingMetrics.assertSimilarityClose(1.0, identical.similarity, tolerance = 1e-4, label = "mobileBert.identical")

            // Symmetry check
            val reversed = helper.compare(TEXT_TEST_TWO, TEXT_TEST_ONE)!!
            EmbeddingMetrics.assertSimilarityClose(related.similarity, reversed.similarity, tolerance = 1e-5, label = "mobileBert.symmetry")

            // Unrelated pair must rank meaningfully below related pair
            val unrelated = helper.compare(TEXT_TEST_ONE, TEXT_UNRELATED)!!
            Log.i(TAG, "MobileBERT unrelated=$unrelated")
            assertTrue(
                "Expected related (${related.similarity}) > unrelated (${unrelated.similarity}) + 0.10",
                related.similarity > unrelated.similarity + 0.10,
            )

            // Verify shipped example presets produce sensible similarity ranking
            val presetsById = DefaultPresets.associateBy { it.id }
            val similarPreset = presetsById.getValue("similar")
            val unrelatedPreset = presetsById.getValue("unrelated")
            val similarPresetScore =
                helper.compare(similarPreset.firstText, similarPreset.secondText)!!.similarity
            val unrelatedPresetScore =
                helper.compare(unrelatedPreset.firstText, unrelatedPreset.secondText)!!.similarity
            assertTrue(
                "Expected preset 'similar' ($similarPresetScore) > 0.90",
                similarPresetScore > 0.90,
            )
            assertTrue(
                "Expected preset 'unrelated' ($unrelatedPresetScore) < 0.50",
                unrelatedPresetScore < 0.50,
            )

            // Raw vector sanity + record 5 steady-state comparisons for report-only timing
            val emb = helper.embed(TEXT_TEST_ONE)!!
            assertTrue(EmbeddingMetrics.l2Norm(emb.floatEmbedding()) > 0.0)
            repeat(5) {
                recorder.measure("compare_steady_ms") {
                    helper.compare(TEXT_TEST_ONE, TEXT_TEST_TWO)
                }
            }
            recorder.flush(context)
        } finally {
            helper.clearTextEmbedder()
        }
    }

    @Test
    fun averageWord_embeddingQualityAndRanking() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val recorder = LatencyRecorder("text_embedder_average_word")
        recorder.putMetadata("model", TextEmbedderHelper.MODEL_AVERAGE_WORD_PATH)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                TextEmbedderHelper(
                    context = context,
                    currentDelegate = TextEmbedderHelper.DELEGATE_CPU,
                    currentModel = TextEmbedderHelper.MODEL_AVERAGE_WORD,
                )
            }
        try {
            assertTrue("AverageWord failed to initialize", helper.isReady)

            val related =
                recorder.measure("compare_related_ms") {
                    helper.compare(TEXT_TEST_ONE, TEXT_TEST_TWO)
                }!!
            val unrelated = helper.compare(TEXT_TEST_ONE, TEXT_UNRELATED)!!
            val identical = helper.compare(TEXT_TEST_ONE, TEXT_TEST_ONE)!!
            Log.i(TAG, "AverageWord related=$related unrelated=$unrelated")

            assertEquals(1.0, identical.similarity, 1e-4)
            assertEquals(EXPECTED_AVERAGE_WORD_DIMS, related.dimensions)
            assertTrue(
                "Expected related (${related.similarity}) > unrelated (${unrelated.similarity})",
                related.similarity > unrelated.similarity,
            )

            repeat(5) {
                recorder.measure("compare_steady_ms") {
                    helper.compare(TEXT_TEST_ONE, TEXT_TEST_TWO)
                }
            }
            recorder.flush(context)
        } finally {
            helper.clearTextEmbedder()
        }
    }
}
