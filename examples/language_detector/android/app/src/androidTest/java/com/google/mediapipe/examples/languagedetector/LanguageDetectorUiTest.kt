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

package com.google.mediapipe.examples.languagedetector

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
class LanguageDetectorUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun waitForDetectReady(timeoutMillis: Long = 20_000L) {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            val nodes = composeRule.onAllNodesWithTag("detect_button").fetchSemanticsNodes()
            nodes.isNotEmpty() && !nodes.first().config.contains(SemanticsProperties.Disabled)
        }
    }

    private fun waitForTopCode(timeoutMillis: Long = 20_000L): String {
        composeRule.waitUntil(timeoutMillis = timeoutMillis) {
            composeRule.onAllNodesWithTag("top_language_code").fetchSemanticsNodes().isNotEmpty()
        }
        val textList =
            composeRule
                .onNodeWithTag("top_language_code")
                .fetchSemanticsNode()
                .config[SemanticsProperties.Text]
        return textList.joinToString("") { it.text }
    }

    @Test
    fun defaultDetection_displaysChineseAndMetrics() {
        waitForDetectReady()

        composeRule.onNodeWithTag("detect_button").performScrollTo().performClick()
        val topCode = waitForTopCode()

        assertEquals("ZH", topCode)
        composeRule.onNodeWithTag("result_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("top_language").assertTextContains("Chinese")
        composeRule.onNodeWithTag("metric_inference_time").assertTextContains("ms", substring = true)
        composeRule.onNodeWithTag("metric_top_code").assertTextContains("zh", substring = true)
    }

    @Test
    fun customTexts_detectFrenchAndSpanish_andInvalidateStaleResult() {
        waitForDetectReady()

        val frenchText = "Bonjour tout le monde, comment allez-vous aujourd'hui ?"
        val spanishText = "Hola, me encanta aprender nuevos idiomas con inteligencia artificial."

        composeRule.onNodeWithTag("input_text").performTextReplacement(frenchText)
        composeRule.onNodeWithTag("detect_button").performScrollTo().performClick()
        assertEquals("FR", waitForTopCode())
        composeRule.onNodeWithTag("top_language").assertTextContains("French")

        // Editing input must immediately clear any displayed result so stale detections aren't shown.
        composeRule
            .onNodeWithTag("input_text")
            .performScrollTo()
            .performTextReplacement(spanishText)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("result_card").assertDoesNotExist()

        composeRule.onNodeWithTag("detect_button").performScrollTo().performClick()
        assertEquals("ES", waitForTopCode())
        composeRule.onNodeWithTag("top_language").assertTextContains("Spanish")
    }

    @Test
    fun presetSelection_updatesInputText() {
        waitForDetectReady()

        val japanesePreset = DefaultPresets.first { it.id == "japanese" }
        val englishPreset = DefaultPresets.first { it.id == "english" }

        composeRule.onNodeWithTag("preset_japanese").performClick()
        composeRule.onNodeWithTag("input_text").assertTextContains(japanesePreset.text)

        composeRule.onNodeWithTag("preset_english").performClick()
        composeRule.onNodeWithTag("input_text").assertTextContains(englishPreset.text)
    }

    @Test
    fun changeMaxResultsInSettings_updatesSubtitleAndDetects() {
        waitForDetectReady()

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("option_Max results_1").performClick()
        composeRule.onNodeWithTag("settings_done_button").performClick()

        composeRule.onNodeWithTag("top_bar_subtitle").assertTextContains("Top 1 · CPU")
        waitForDetectReady()

        composeRule.onNodeWithTag("preset_french").performClick()
        composeRule.onNodeWithTag("detect_button").assertIsEnabled().performScrollTo().performClick()
        assertEquals("FR", waitForTopCode())
        composeRule.onNodeWithTag("language_row_fr").assertIsDisplayed()
    }
}
