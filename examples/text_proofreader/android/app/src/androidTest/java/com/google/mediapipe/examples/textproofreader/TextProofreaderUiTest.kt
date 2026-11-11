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

package com.google.mediapipe.examples.textproofreader

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextProofreaderUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForProofreadReady(timeoutMillis: Long = 60_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("proofread_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitForResultCard(timeoutMillis: Long = 60_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("metric_inference_time").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun proofreadSpellingPreset_rendersCorrectedTextAndMetrics() {
        waitForProofreadReady()

        composeRule.onNodeWithTag("preset_tense").performScrollTo().performClick()
        composeRule.onNodeWithTag("proofread_button").performScrollTo().performClick()
        waitForResultCard()

        composeRule.onNodeWithTag("result_card").performScrollTo().assertIsDisplayed()
        composeRule
            .onNodeWithTag("proofread_output_text")
            .assertTextContains("every day", substring = true, ignoreCase = true)
        composeRule.onNodeWithTag("metric_inference_time").assertTextContains("ms", substring = true)
    }

    @Test
    fun updateSettingsAndEditText_clearsStaleResult() {
        waitForProofreadReady()

        composeRule.onNodeWithTag("preset_email").performScrollTo().performClick()
        composeRule.onNodeWithTag("proofread_button").performScrollTo().performClick()
        waitForResultCard()

        composeRule.onNodeWithTag("preset_clean").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithTag("result_card").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("result_card").assertDoesNotExist()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Max tokens_1024").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("1024", substring = true)
        waitForProofreadReady()
    }
}
