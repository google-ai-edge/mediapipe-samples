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

package com.mediapipe.example.interactivesegmentation

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractiveSegmentationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun loadSample_tapObject_andClearStrokes_worksEndToEnd() {
        composeRule.onNodeWithTag("try_sample_button").performClick()

        composeRule.waitUntil(timeoutMillis = 15_000L) {
            composeRule.onAllNodesWithTag("interactive_canvas").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap the corgi in the bundled cats_and_dogs.jpg sample image.
        composeRule.onNodeWithTag("interactive_canvas").performTouchInput {
            click(Offset(width * 0.62f, height * 0.52f))
        }

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithTag("metrics_row").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("metric_strokes").assertTextContains("1")
        composeRule.onNodeWithTag("metric_coverage").assertTextContains("%", substring = true)

        composeRule.onNodeWithTag("brush_negative").performClick()
        composeRule.onNodeWithTag("interactive_canvas").performTouchInput {
            click(Offset(width * 0.10f, height * 0.50f))
        }

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithTag("segmenting_indicator").fetchSemanticsNodes().isEmpty() &&
                runCatching {
                    composeRule.onNodeWithTag("metric_strokes").assertTextContains("2")
                    true
                }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("metric_strokes").assertTextContains("2")

        composeRule.onNodeWithTag("clear_strokes_button").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            composeRule.onAllNodesWithTag("metrics_row").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag("interactive_canvas").assertIsDisplayed()
    }
}
