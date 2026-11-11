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

package com.google.mediapipe.examples.audioclassifier

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
class AudioClassifierUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForTopCategory(timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("top_category").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForClassifyEnabled(timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("classify_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    @Test
    fun classifySpeechClip_displaysSpeechAndMetrics() {
        waitForClassifyEnabled()

        composeRule.onNodeWithTag("classify_button").performClick()
        waitForTopCategory()

        composeRule.onNodeWithTag("result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_category").assertTextContains("Speech", substring = true)
        composeRule.onNodeWithTag("metric_inference_time").assertTextContains("ms", substring = true)
        composeRule.onNodeWithTag("metric_sample_rate").assertTextContains("16 kHz")
    }

    @Test
    fun changeMaxResultsAndSwitchToToneClip_updatesResults() {
        waitForClassifyEnabled()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Max results_1").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("Top 1", substring = true)

        composeRule.onNodeWithTag("clip_tone").performClick()
        composeRule.onNodeWithTag("classify_button").performClick()
        waitForTopCategory()

        composeRule.onNodeWithTag("top_category").assertIsDisplayed()
    }
}
