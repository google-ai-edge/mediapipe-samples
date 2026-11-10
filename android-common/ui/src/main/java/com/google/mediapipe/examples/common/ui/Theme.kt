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
import android.os.Build
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

private val MediaPipeLightColorScheme =
    lightColorScheme(
        primary = Color(0xFF006973),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF9AF0FA),
        onPrimaryContainer = Color(0xFF001F23),
        secondary = Color(0xFF4A6366),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFCDE7EB),
        onSecondaryContainer = Color(0xFF051F22),
        tertiary = Color(0xFF505E7D),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFD7E2FF),
        onTertiaryContainer = Color(0xFF0B1B36),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        background = Color(0xFFF5FAFB),
        onBackground = Color(0xFF171D1E),
        surface = Color(0xFFF5FAFB),
        onSurface = Color(0xFF171D1E),
        surfaceVariant = Color(0xFFDAE4E6),
        onSurfaceVariant = Color(0xFF3F484A),
        outline = Color(0xFF6F797A),
        outlineVariant = Color(0xFFBEC8CA),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFEFF5F5),
        surfaceContainer = Color(0xFFE9EFF0),
        surfaceContainerHigh = Color(0xFFE3E9EA),
        surfaceContainerHighest = Color(0xFFDDE4E4),
    )

private val MediaPipeDarkColorScheme =
    darkColorScheme(
        primary = Color(0xFF7DD4DF),
        onPrimary = Color(0xFF00363C),
        primaryContainer = Color(0xFF004F56),
        onPrimaryContainer = Color(0xFF9AF0FA),
        secondary = Color(0xFFB1CBCF),
        onSecondary = Color(0xFF1C3438),
        secondaryContainer = Color(0xFF334B4E),
        onSecondaryContainer = Color(0xFFCDE7EB),
        tertiary = Color(0xFFB8C6EA),
        onTertiary = Color(0xFF21304C),
        tertiaryContainer = Color(0xFF384664),
        onTertiaryContainer = Color(0xFFD7E2FF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF0E1415),
        onBackground = Color(0xFFDEE3E4),
        surface = Color(0xFF0E1415),
        onSurface = Color(0xFFDEE3E4),
        surfaceVariant = Color(0xFF3F484A),
        onSurfaceVariant = Color(0xFFBEC8CA),
        outline = Color(0xFF899294),
        outlineVariant = Color(0xFF3F484A),
        surfaceContainerLowest = Color(0xFF090F10),
        surfaceContainerLow = Color(0xFF171D1E),
        surfaceContainer = Color(0xFF1B2122),
        surfaceContainerHigh = Color(0xFF252B2C),
        surfaceContainerHighest = Color(0xFF303637),
    )

/**
 * Shared Material 3 theme for MediaPipe Android sample apps.
 *
 * Uses dynamic color on Android 12+ when enabled and falls back to a Material 3 light/dark
 * palette built around the MediaPipe teal brand color.
 */
@Composable
fun MediaPipeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
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
            insetsController.isAppearanceLightStatusBars = !darkTheme
            insetsController.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
