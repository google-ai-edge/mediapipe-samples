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

package com.google.mediapipe.examples.universalembedder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.common.ui.MediaPipeTheme

/** Main activity for the MediaPipe Universal Embedder Android sample app. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaPipeTheme {
                val appContext = LocalContext.current.applicationContext
                val embedderViewModel: UniversalEmbedderViewModel =
                    viewModel(factory = UniversalEmbedderViewModel.factory(appContext))
                val uiState by embedderViewModel.uiState.collectAsStateWithLifecycle()

                UniversalEmbedderScreen(
                    uiState = uiState,
                    onSelectPreset = embedderViewModel::selectPreset,
                    onSetSlotMode = embedderViewModel::setSlotMode,
                    onUpdateSlotText = embedderViewModel::updateSlotText,
                    onSelectSamplePhoto = embedderViewModel::selectSlotSamplePhoto,
                    onCustomImagePicked = embedderViewModel::setSlotCustomImageUri,
                    onSwapSlots = embedderViewModel::swapSlots,
                    onCompare = embedderViewModel::compare,
                    onSelectDelegate = embedderViewModel::selectDelegate,
                    onRetryInitialization = embedderViewModel::retryInitialization,
                )
            }
        }
    }
}
