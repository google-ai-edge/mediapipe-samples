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

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextEmbedderUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForCompareReady(timeoutMillis: Long = 30_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("compare_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitForResultCard(timeoutMillis: Long = 30_000L): Double {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("similarity_score").fetchSemanticsNodes().isNotEmpty()
        }
        val textList =
            composeRule
                .onNodeWithTag("similarity_score")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
        val scoreText = textList.joinToString("") { it.text }
        return scoreText.toDouble()
    }

    private fun waitForResultCleared(timeoutMillis: Long = 5_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("result_card").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun defaultComparison_displaysScoreAndMetrics() {
        waitForCompareReady()
        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("Gemma 300M · 768d")

        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val score = waitForResultCard()

        assertTrue(
            "Expected positive similarity score for MediaPipe preset, got $score",
            score in 0.45..1.00,
        )
        composeRule.onNodeWithTag("result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_inference_time").assertTextContains("ms", substring = true)
        composeRule.onNodeWithTag("metric_dimensions").assertTextContains("768d", substring = true)
    }

    @Test
    fun retrievalContextAndCustomTexts_computesQualityRanking_andInvalidatesStaleResult() {
        waitForCompareReady()

        // Selecting Query -> Doc preset applies TextFormatContext retrieval mode and title input.
        composeRule.onNodeWithTag("preset_retrieval").performScrollTo().performClick()
        composeRule.onNodeWithTag("input_document_title").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val retrievalScore = waitForResultCard()
        assertTrue(
            "Expected positive Query -> Doc similarity for retrieval preset, got $retrievalScore",
            retrievalScore > 0.50,
        )

        composeRule.onNodeWithTag("context_similarity").performScrollTo().performClick()
        waitForResultCleared()

        val similarTextA =
            "The golden retriever ran across the sunny sandy beach toward the ocean."
        val similarTextB =
            "A happy dog sprinted along the ocean shoreline on the warm sand."
        val unrelatedTextB =
            "The federal reserve adjusted benchmark interest rates following the quarterly inflation report."

        composeRule.onNodeWithTag("input_text_a").performScrollTo().performTextReplacement(similarTextA)
        composeRule.onNodeWithTag("input_text_b").performScrollTo().performTextReplacement(similarTextB)

        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val similarScore = waitForResultCard()
        assertTrue(
            "Expected high similarity for related pair, got $similarScore",
            similarScore > 0.75,
        )

        // Editing an input must immediately drop any displayed result so stale scores are never shown.
        composeRule
            .onNodeWithTag("input_text_b")
            .performScrollTo()
            .performTextReplacement(unrelatedTextB)
        waitForResultCleared()

        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val unrelatedScore = waitForResultCard()
        assertTrue(
            "Expected related score ($similarScore) > unrelated score ($unrelatedScore) + 0.30",
            similarScore > unrelatedScore + 0.30,
        )
    }

    @Test
    fun presetAndSwap_updateInputsCorrectly() {
        waitForCompareReady()

        val similarPreset = DefaultPresets.first { it.id == "similar" }
        composeRule.onNodeWithTag("preset_similar").performScrollTo().performClick()
        composeRule.onNodeWithTag("input_text_a").assertTextContains(similarPreset.firstText)
        composeRule.onNodeWithTag("input_text_b").assertTextContains(similarPreset.secondText)

        composeRule.onNodeWithTag("swap_button").performScrollTo().performClick()
        composeRule.onNodeWithTag("input_text_a").assertTextContains(similarPreset.secondText)
        composeRule.onNodeWithTag("input_text_b").assertTextContains(similarPreset.firstText)
    }

    @Test
    fun switchModelInSettings_runsMobileBertAndUniversalEncoderEndToEnd() {
        waitForCompareReady()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Model_MobileBERT").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("MobileBERT · 512d")
        waitForCompareReady()
        composeRule.onNodeWithTag("preset_similar").performScrollTo().performClick()
        composeRule.onNodeWithTag("compare_button").assertIsEnabled().performScrollTo().performClick()
        val bertSimilarScore = waitForResultCard()
        assertTrue("Expected high MobileBERT similarity, got $bertSimilarScore", bertSimilarScore > 0.90)
        composeRule.onNodeWithTag("metric_dimensions").assertTextContains("512d", substring = true)

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Model_Universal").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("Universal · 100d")
        waitForCompareReady()

        composeRule.onNodeWithTag("preset_similar").performScrollTo().performClick()
        composeRule.onNodeWithTag("compare_button").assertIsEnabled().performScrollTo().performClick()
        val similarScore = waitForResultCard()
        assertTrue("Expected high USE similarity for Similar preset, got $similarScore", similarScore > 0.90)
        composeRule.onNodeWithTag("metric_dimensions").assertTextContains("100d", substring = true)

        composeRule.onNodeWithTag("preset_unrelated").performScrollTo().performClick()
        composeRule.onNodeWithTag("compare_button").assertIsEnabled().performScrollTo().performClick()
        val unrelatedScore = waitForResultCard()
        assertTrue(
            "Expected USE Similar ($similarScore) > Unrelated ($unrelatedScore) + 0.25",
            similarScore > unrelatedScore + 0.25,
        )
    }
}
