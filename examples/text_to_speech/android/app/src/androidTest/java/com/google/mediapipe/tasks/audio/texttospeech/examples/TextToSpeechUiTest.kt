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

package com.google.mediapipe.tasks.audio.texttospeech.examples

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextToSpeechUiTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun speakAndSettings_synthesizeSpeechAndDisplayTelemetry() {
    composeRule.waitUntil(timeoutMillis = 240_000L) {
      composeRule.onAllNodesWithText("Ready").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("tts_text_input").assertIsDisplayed().performTextReplacement("Hello Kokoro.")
    composeRule.onNodeWithTag("tts_speak_button").assertIsEnabled().performClick()

    composeRule.waitUntil(timeoutMillis = 240_000L) {
      composeRule.onAllNodesWithTag("tts_telemetry_card").fetchSemanticsNodes().isNotEmpty() &&
        composeRule.onAllNodesWithText("Done").fetchSemanticsNodes().isNotEmpty()
    }

    composeRule.onNodeWithTag("tts_telemetry_card").assertIsDisplayed()
    composeRule.onNodeWithTag("tts_replay_button").assertIsEnabled()

    composeRule.onNodeWithTag("settings_button").performClick()
    composeRule.onNodeWithText("Engine & Voice Settings").assertIsDisplayed()
    composeRule.onNodeWithTag("option_Delegate_Auto").assertIsDisplayed()
    composeRule.onNodeWithTag("option_Delegate_CPU").assertIsDisplayed()
    composeRule.onNodeWithTag("option_Delegate_GPU").assertIsDisplayed()
    composeRule.onNodeWithTag("settings_done_button").performClick()
  }
}
