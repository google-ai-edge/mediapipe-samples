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

package com.google.mediapipe.tasks.examples.audiotranscriber.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MediaPipeTeal = Color(0xFF00796B)
private val MediaPipeTealDark = Color(0xFF004D40)
private val MediaPipeTealLight = Color(0xFFB2DFDB)
private val MediaPipeAccent = Color(0xFF0288D1)

private val AppColorScheme =
  lightColorScheme(
    primary = MediaPipeTeal,
    onPrimary = Color.White,
    primaryContainer = MediaPipeTealLight,
    onPrimaryContainer = MediaPipeTealDark,
    secondary = MediaPipeAccent,
    onSecondary = Color.White,
  )

/** Material 3 Compose theme for the MediaPipe AudioTranscriber Android sample app. */
@Composable
fun AudioTranscriberTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = AppColorScheme, content = content)
}
