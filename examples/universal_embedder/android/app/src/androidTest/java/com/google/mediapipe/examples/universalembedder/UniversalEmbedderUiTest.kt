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

package com.google.mediapipe.examples.universalembedder

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UniversalEmbedderUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForCompareReady(timeoutMillis: Long = 60_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("compare_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitForResultScore(timeoutMillis: Long = 30_000L): Double {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("similarity_score").fetchSemanticsNodes().isNotEmpty()
        }
        val textList =
            composeRule
                .onNodeWithTag("similarity_score")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
        return textList.joinToString("") { it.text }.toDouble()
    }

    private fun waitForResultCleared() {
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("result_card").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun defaultCrossModalAndTextPresets_displayExpectedScores() {
        waitForCompareReady()

        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val crossModalScore = waitForResultScore()
        assertTrue(
            "Expected positive Image<->Text score for cute_cat preset, got $crossModalScore",
            crossModalScore > 0.25,
        )
        composeRule.onNodeWithTag("metric_inference_time").assertTextContains("ms", substring = true)

        composeRule.onNodeWithTag("preset_text_text").performScrollTo().performClick()
        waitForResultCleared()
        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val textPairScore = waitForResultScore()
        assertTrue(
            "Expected high Text<->Text score for dog/beach preset, got $textPairScore",
            textPairScore > 0.60,
        )
    }

    @Test
    fun switchDelegateInSettings_reloadsCleanly() {
        waitForCompareReady()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Delegate_CPU").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("CPU", substring = true)
        waitForCompareReady()
    }
}
