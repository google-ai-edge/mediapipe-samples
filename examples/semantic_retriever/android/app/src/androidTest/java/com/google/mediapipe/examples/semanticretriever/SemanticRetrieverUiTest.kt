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

package com.google.mediapipe.examples.semanticretriever

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
class SemanticRetrieverUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForIndexReady(timeoutMillis: Long = 240_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("search_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    @Test
    fun sampleGallerySearch_displaysRankedImagesAndMetrics() {
        waitForIndexReady()
        composeRule.onNodeWithTag("indexed_status_text").assertTextContains("10 images indexed")

        composeRule.onNodeWithTag("suggestion_cat").performScrollTo().performClick()
        composeRule.onNodeWithTag("search_button").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 120_000L) {
            composeRule.onAllNodesWithTag("result_card_1").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("result_card_1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("metric_search_time").assertTextContains("ms", substring = true)
    }

    @Test
    fun switchStoreAndDelegateInSettings_updatesSubtitleAndIndexes() {
        waitForIndexReady()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Delegate_CPU").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("CPU", substring = true)
        waitForIndexReady()
    }
}
