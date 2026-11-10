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

package com.google.mediapipe.examples.common.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val MediaPipeTealPrimary = Color(0xFF007F8B)
internal val MediaPipeTealVariant = Color(0xFF12B5CB)
internal val MediaPipeTealDark = Color(0xFF00676D)

private val MediaPipeLightColorScheme =
    lightColorScheme(
        primary = MediaPipeTealPrimary,
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD6F1F4),
        onPrimaryContainer = Color(0xFF004F56),
        secondary = MediaPipeTealDark,
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0F2F4),
        onSecondaryContainer = Color(0xFF004D54),
        tertiary = MediaPipeTealVariant,
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD0F4F9),
        onTertiaryContainer = Color(0xFF003B44),
        error = Color(0xFFB00020),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFCD8DF),
        onErrorContainer = Color(0xFF690010),
        background = Color(0xFFF2F4F5),
        onBackground = Color(0xFF1B2426),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1B2426),
        surfaceVariant = Color(0xFFEDF3F4),
        onSurfaceVariant = Color(0xFF5F6E71),
        outline = Color(0xFF7D8E91),
        outlineVariant = Color(0xFFDCE6E8),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFEDF3F4),
        surfaceContainerHigh = Color(0xFFEDF3F4),
        surfaceContainerHighest = Color(0xFFDCE6E8),
    )

private val MediaPipeDarkColorScheme =
    darkColorScheme(
        primary = MediaPipeTealVariant,
        onPrimary = Color(0xFF002B30),
        primaryContainer = Color(0xFF005C63),
        onPrimaryContainer = Color(0xFFB8F3FA),
        secondary = Color(0xFF8CD3DC),
        onSecondary = Color(0xFF00353B),
        secondaryContainer = Color(0xFF17444A),
        onSecondaryContainer = Color(0xFFCCEBF0),
        tertiary = Color(0xFF7ADCEE),
        onTertiary = Color(0xFF00363E),
        tertiaryContainer = Color(0xFF004F59),
        onTertiaryContainer = Color(0xFFC4F3FB),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E1415),
        onBackground = Color(0xFFE3ECED),
        surface = Color(0xFF151C1E),
        onSurface = Color(0xFFE3ECED),
        surfaceVariant = Color(0xFF263234),
        onSurfaceVariant = Color(0xFF9FB0B3),
        outline = Color(0xFF6B7B7E),
        outlineVariant = Color(0xFF283436),
        surfaceContainerLowest = Color(0xFF0B1011),
        surfaceContainerLow = Color(0xFF161F21),
        surfaceContainer = Color(0xFF1C2628),
        surfaceContainerHigh = Color(0xFF212D2F),
        surfaceContainerHighest = Color(0xFF2B383B),
    )

/**
 * Shared Material 3 theme for MediaPipe Android sample apps aligned with the Semantic Retriever
 * teal palette (`#007F8B` / `#12B5CB` / `#00676D`).
 *
 * Brand colors are used consistently by default (`dynamicColor = false`) so every MediaPipe sample
 * shares a cohesive visual identity across devices; callers may opt into Android 12+ wallpaper
 * dynamic color by setting `dynamicColor = true`.
 */
@Composable
fun MediaPipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> MediaPipeDarkColorScheme
            else -> MediaPipeLightColorScheme
        }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            // The top app bar uses a dark-to-bright MediaPipe teal gradient in both modes.
            insetsController.isAppearanceLightStatusBars = false
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
