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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextClassifierUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForClassifyReady(timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("classify_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitForResultCard(timeoutMillis: Long = 20_000L): String {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("top_category").fetchSemanticsNodes().isNotEmpty()
        }
        val textList =
            composeRule
                .onNodeWithTag("top_category")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
        return textList.joinToString("") { it.text }
    }

    @Test
    fun defaultClassification_displaysSentimentAndMetrics() {
        waitForClassifyReady()

        composeRule.onNodeWithTag("classify_button").performScrollTo().performClick()
        val topLabel = waitForResultCard()

        assertEquals("Positive", topLabel)
        composeRule.onNodeWithTag("result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_inference_time").assertTextContains("ms", substring = true)
        composeRule.onNodeWithTag("metric_model").assertTextContains("MobileBERT", substring = true)
    }

    @Test
    fun customTexts_classifyPositiveAndNegative_andInvalidateStaleResult() {
        waitForClassifyReady()

        val positiveInput =
            "I am so thrilled and grateful for this wonderful day, everything came together beautifully!"
        val negativeInput =
            "The movie was terrible, boring, and a complete waste of time."

        composeRule.onNodeWithTag("input_text").performTextReplacement(positiveInput)
        composeRule.onNodeWithTag("classify_button").performScrollTo().performClick()
        val posLabel = waitForResultCard()
        assertEquals("Positive", posLabel)

        // Editing input must immediately drop displayed results so stale predictions never remain.
        composeRule
            .onNodeWithTag("input_text")
            .performScrollTo()
            .performTextReplacement(negativeInput)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("result_card").assertDoesNotExist()

        composeRule.onNodeWithTag("classify_button").performScrollTo().performClick()
        val negLabel = waitForResultCard()
        assertEquals("Negative", negLabel)
    }

    @Test
    fun presetSelection_updatesInputText() {
        waitForClassifyReady()

        val negativePreset = DefaultPresets.first { it.id == "negative" }
        val positivePreset = DefaultPresets.first { it.id == "positive" }

        composeRule.onNodeWithTag("preset_negative").performClick()
        composeRule.onNodeWithTag("input_text").assertTextContains(negativePreset.text)

        composeRule.onNodeWithTag("preset_positive").performClick()
        composeRule.onNodeWithTag("input_text").assertTextContains(positivePreset.text)
    }

    @Test
    fun switchModelInSettings_runsWordVecEndToEnd() {
        waitForClassifyReady()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Model_WordVec").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("WordVec")
        waitForClassifyReady()

        composeRule.onNodeWithTag("preset_negative").performClick()
        composeRule.onNodeWithTag("classify_button").assertIsEnabled().performScrollTo().performClick()
        val negLabel = waitForResultCard()
        assertEquals("Negative", negLabel)
        composeRule.onNodeWithTag("metric_model").assertTextContains("WordVec", substring = true)

        composeRule.onNodeWithTag("preset_positive").performClick()
        composeRule.onNodeWithTag("classify_button").assertIsEnabled().performScrollTo().performClick()
        val posLabel = waitForResultCard()
        assertEquals("Positive", posLabel)
    }
}
