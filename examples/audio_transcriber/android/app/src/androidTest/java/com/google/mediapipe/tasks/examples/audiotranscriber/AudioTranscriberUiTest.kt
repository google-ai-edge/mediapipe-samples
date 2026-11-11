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

package com.google.mediapipe.tasks.examples.audiotranscriber

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioTranscriberUiTest {

  @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun transcribeSampleClip_populatesTranscriptAndClearsCleanly() {
    // Wait until Parakeet TDT finishes loading and Status displays "Ready".
    composeRule.waitUntil(timeoutMillis = 90_000L) {
      composeRule
        .onAllNodesWithText(AudioTranscriberViewModel.STATUS_READY)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeRule.onNodeWithTag("transcribe_sample_button").assertIsEnabled().performClick()

    composeRule.waitUntil(timeoutMillis = 90_000L) {
      composeRule
        .onAllNodesWithText(AudioTranscriberViewModel.STATUS_SAMPLE_COMPLETE)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeRule
      .onNodeWithText("tomorrow", substring = true, ignoreCase = true)
      .assertIsDisplayed()

    composeRule.onNodeWithTag("clear_transcript_button").assertIsEnabled().performClick()

    composeRule.waitUntil(timeoutMillis = 15_000L) {
      composeRule
        .onAllNodesWithText(AudioTranscriberViewModel.STATUS_CLEARED)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }
    composeRule.onNodeWithTag("clear_transcript_button").assertIsNotEnabled()
  }

  @Test
  fun settingsSheet_displaysDelegateAndLoadModelOptions() {
    composeRule.waitUntil(timeoutMillis = 90_000L) {
      composeRule
        .onAllNodesWithText(AudioTranscriberViewModel.STATUS_READY)
        .fetchSemanticsNodes()
        .isNotEmpty()
    }

    composeRule.onNodeWithTag("settings_button").performClick()
    composeRule.onNodeWithText("Delegate").assertIsDisplayed()
    composeRule.onNodeWithTag("option_Delegate_CPU").assertIsDisplayed()
    composeRule.onNodeWithTag("option_Delegate_GPU").assertIsDisplayed()
    composeRule.onNodeWithTag("load_model_button").assertIsDisplayed()
    composeRule.onNodeWithText("Done").performClick()
  }
}
