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

package com.google.mediapipe.examples.imageembedder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.common.ui.MediaPipeTheme

/** Main activity for the MediaPipe Image Embedder Android sample app. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaPipeTheme {
                val context = LocalContext.current
                val embedderViewModel: ImageEmbedderViewModel =
                    viewModel(factory = ImageEmbedderViewModel.factory(context.applicationContext))
                val uiState by embedderViewModel.uiState.collectAsStateWithLifecycle()
                var pendingSlot by rememberSaveable { mutableIntStateOf(1) }

                val imagePickerLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            embedderViewModel.setPickedImage(pendingSlot, uri)
                        }
                    }

                ImageEmbedderScreen(
                    uiState = uiState,
                    onPickImage = { slot ->
                        pendingSlot = slot
                        imagePickerLauncher.launch(arrayOf("image/*"))
                    },
                    onSelectPreset = embedderViewModel::selectPreset,
                    onSwapImages = embedderViewModel::swapImages,
                    onCompare = embedderViewModel::compareImages,
                    onSelectModel = embedderViewModel::selectModel,
                    onSelectDelegate = embedderViewModel::selectDelegate,
                    onUseCpuDelegate = embedderViewModel::switchToCpuDelegate,
                    onRetryInitialization = embedderViewModel::retryInitialization,
                )
            }
        }
    }
}
