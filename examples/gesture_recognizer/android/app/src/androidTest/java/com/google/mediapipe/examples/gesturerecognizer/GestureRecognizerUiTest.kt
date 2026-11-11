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

package com.google.mediapipe.examples.gesturerecognizer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GestureRecognizerUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun galleryTrySample_recognizesThumbUp_andDisplaysOverlay() {
        composeRule.onNodeWithTag("tab_gallery").performClick()
        composeRule.onNodeWithTag("try_sample_button").performClick()

        composeRule.waitUntil(timeoutMillis = 20_000L) {
            composeRule.onAllNodesWithTag("gallery_metrics_row").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("gesture_overlay").assertIsDisplayed()
        composeRule.onNodeWithTag("metric_gesture_name").assertTextContains("Thumb_Up", substring = true)

        composeRule.onNodeWithTag("settings_button").performClick()
        composeRule.onNodeWithTag("settings_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_done_button").performClick()
    }

    @Test
    fun cameraTab_andGalleryTab_switchCleanly() {
        val hasPermCard =
            composeRule.onAllNodesWithTag("camera_permission_card").fetchSemanticsNodes().isNotEmpty()
        val hasPreview =
            composeRule.onAllNodesWithTag("camera_preview").fetchSemanticsNodes().isNotEmpty()
        assertTrue("Expected camera permission card or preview on launch", hasPermCard || hasPreview)

        composeRule.onNodeWithTag("tab_gallery").performClick()
        composeRule.onNodeWithTag("detection_canvas").assertIsDisplayed()

        composeRule.onNodeWithTag("tab_camera").performClick()
        val postSwitchCard =
            composeRule.onAllNodesWithTag("camera_permission_card").fetchSemanticsNodes().isNotEmpty()
        val postSwitchPreview =
            composeRule.onAllNodesWithTag("camera_preview").fetchSemanticsNodes().isNotEmpty()
        assertTrue(postSwitchCard || postSwitchPreview)
    }
}
