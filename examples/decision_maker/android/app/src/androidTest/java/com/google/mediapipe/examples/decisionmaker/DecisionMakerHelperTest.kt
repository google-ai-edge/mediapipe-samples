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

package com.google.mediapipe.examples.decisionmaker

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DecisionMakerHelperTest {

    companion object {
        private const val TAG = "DecisionMakerHelperTest"
    }

    @Test
    fun booleanChoiceAndScore_produceExpectedDecisionsAndLatencies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("decision_maker")
        recorder.putMetadata(
            "model",
            DecisionMakerHelper.MODEL_FILES[DecisionMakerHelper.MODEL_EMBEDDING_GEMMA],
        )
        recorder.putMetadata("delegate", "CPU")

        val helper =
            DecisionMakerHelper(
                context = context,
                currentDelegate = DecisionMakerHelper.DELEGATE_CPU,
                currentModel = DecisionMakerHelper.MODEL_EMBEDDING_GEMMA,
            )

        try {
            recorder.measure("model_load_ms") { helper.setupDecisionMaker() }
            assertTrue("DecisionMaker failed to initialize", helper.isReady)

            val pos =
                recorder.measure("evaluate_boolean_ms") {
                    helper.evaluateBoolean(
                        text = "This product is amazing, it works perfectly and I use it every day!",
                        condition = "The review is positive.",
                        threshold = 0.5f,
                    )
                }
            Log.i(TAG, "boolean positive=${pos.result.value} prob=${pos.result.probabilityTrue}")
            assertTrue("Expected positive review decision=true", pos.result.value)
            assertTrue(
                "Expected P(yes) > 0.65, got ${pos.result.probabilityTrue}",
                pos.result.probabilityTrue > 0.65f,
            )

            val neg =
                helper.evaluateBoolean(
                    text = "Terrible quality, broke on the first day and support ignored me.",
                    condition = "The review is positive.",
                    threshold = 0.5f,
                )
            Log.i(TAG, "boolean negative=${neg.result.value} prob=${neg.result.probabilityTrue}")
            assertFalse("Expected negative review decision=false", neg.result.value)
            assertTrue(
                "Expected P(yes) < 0.45 for negative review, got ${neg.result.probabilityTrue}",
                neg.result.probabilityTrue < 0.45f,
            )

            val choice =
                recorder.measure("evaluate_choice_ms") {
                    helper.evaluateChoice(
                        text = "My package never arrived even though tracking says delivered.",
                        options =
                            mapOf(
                                "shipping" to "Delivery problems, lost packages, tracking",
                                "billing" to "Payment issues, duplicate charges, invoices",
                                "technical" to "App crashes, login errors, bugs",
                            ),
                        instructions = "Which department should handle this ticket?",
                    )
                }
            Log.i(TAG, "choice=${choice.result.selectedKey} probs=${choice.result.probabilities}")
            assertEquals("shipping", choice.result.selectedKey)

            val score =
                recorder.measure("evaluate_score_ms") {
                    helper.evaluateScore(
                        text = "The support agent was friendly and fixed my issue fast.",
                        rubric =
                            listOf(
                                "Very dissatisfied: angry, problem not solved, terrible service",
                                "Dissatisfied: slow or unhelpful support, problem partly solved",
                                "Neutral: okay, average, nothing special",
                                "Satisfied: helpful support, problem solved",
                                "Very satisfied: excellent, fast, friendly support, delighted",
                            ),
                        instructions = "Rate how satisfied the customer is with the support experience.",
                    )
                }
            val probs = score.result.levelProbabilities
            val expectedScore = probs.withIndex().sumOf { (idx, p) -> (idx + 1) * p.toDouble() }
            Log.i(TAG, "score expected=$expectedScore probs=$probs")
            assertEquals(5, probs.size)
            assertTrue(
                "Expected satisfied review score > 3.5, got $expectedScore",
                expectedScore > 3.5,
            )

            repeat(5) {
                recorder.measure("evaluate_steady_ms") {
                    helper.evaluateBoolean(
                        text = "This product is amazing, it works perfectly and I use it every day!",
                        condition = "The review is positive.",
                        threshold = 0.5f,
                    )
                }
            }
            recorder.flush(context)
        } finally {
            helper.clearDecisionMaker()
        }
    }
}
