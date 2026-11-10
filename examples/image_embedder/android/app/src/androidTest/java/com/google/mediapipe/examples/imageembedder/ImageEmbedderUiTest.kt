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

package com.google.mediapipe.examples.imageembedder

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
class ImageEmbedderUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForCompareReady(timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("compare_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitForSimilarityScore(timeoutMillis: Long = 20_000L): Double {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("similarity_score").fetchSemanticsNodes().isNotEmpty()
        }
        val text =
            composeRule
                .onNodeWithTag("similarity_score")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
                .joinToString("") { it.text }
                .trim()
        return text.toDouble()
    }

    @Test
    fun defaultPizzaPair_andUnrelatedPreset_displayExpectedRelativeSimilarity() {
        waitForCompareReady()

        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val pizzaScore = waitForSimilarityScore()
        composeRule.onNodeWithTag("metric_model").assertTextContains("Small", substring = true)

        composeRule.onNodeWithTag("preset_unrelated").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("result_card").assertDoesNotExist()

        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        val unrelatedScore = waitForSimilarityScore()
        assertTrue(
            "Expected similar pizzas ($pizzaScore) > unrelated pizza vs mug ($unrelatedScore)",
            pizzaScore > unrelatedScore,
        )
    }

    @Test
    fun switchModelInSettings_runsMobileNetV3Large() {
        waitForCompareReady()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Model_Large").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("MobileNet V3 Large", substring = true)
        waitForCompareReady()

        composeRule.onNodeWithTag("compare_button").performScrollTo().performClick()
        waitForSimilarityScore()
        composeRule.onNodeWithTag("metric_model").assertTextContains("Large", substring = true)
    }
}
