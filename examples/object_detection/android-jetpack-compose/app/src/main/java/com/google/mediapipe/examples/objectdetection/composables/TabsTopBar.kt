/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.mediapipe.examples.objectdetection.composables

import androidx.compose.material3.Icon
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import com.google.mediapipe.examples.objectdetection.R

// This composable is used in Home screen to navigate between camera view and gallery view

// It takes an index to display an indication of which tab we're currently in, and a function
// to set that index when a tab is tapped

@Composable
fun TabsTopBar(
    selectedTabIndex: Int,
    setSelectedTabIndex: (Int) -> Unit,
) {
    TabRow(selectedTabIndex = selectedTabIndex) {
        Tab(
            unselectedContentColor = Color.Gray,
            selected = selectedTabIndex == 0,
            onClick = {
                setSelectedTabIndex(0)
            },
            text = { Text("Camera") },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_photo_camera_24),
                    contentDescription = "Camera icon"
                )
            },
        )
        Tab(
            unselectedContentColor = Color.Gray,
            selected = selectedTabIndex == 1,
            onClick = {
                setSelectedTabIndex(1)
            },
            text = { Text("Gallery") },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_photo_library_24),
                    contentDescription = "Gallery icon"
                )
            },
        )
    }
}