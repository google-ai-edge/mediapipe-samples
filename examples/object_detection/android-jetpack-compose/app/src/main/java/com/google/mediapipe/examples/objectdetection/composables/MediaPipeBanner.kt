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

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.objectdetection.R
import com.google.mediapipe.examples.objectdetection.ui.theme.Turquoise

// The MediaPipe banner displayed at the top of the app screens.

// For our purposes, it can show a back button on the left side
// and an options button on the right side when the corresponding
// callback functions are provided to it.

// It's intended to provide buttons to navigate between Home and Options screens

@Composable
fun MediaPipeBanner(
    onOptionsButtonClick: (() -> Unit)? = null,
    onBackButtonClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(Color(0xEEEEEEEE)),
    ) {
        if (onBackButtonClick != null) {
            IconButton(
                onClick = onBackButtonClick,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Backward arrow icon",
                    tint = Turquoise
                )
            }
        }
        Image(
            painter = painterResource(id = R.drawable.media_pipe_banner),
            contentDescription = "MediaPipe logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.align(Alignment.Center)
        )
        if (onOptionsButtonClick != null) {
            IconButton(
                onClick = onOptionsButtonClick,
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings icon",
                    tint = Turquoise
                )
            }
        }
    }
}