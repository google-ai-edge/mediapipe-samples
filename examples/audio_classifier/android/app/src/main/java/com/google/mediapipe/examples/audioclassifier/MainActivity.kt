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

package com.google.mediapipe.examples.audioclassifier

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mediapipe.examples.common.ui.MediaPipeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: AudioClassifierViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MediaPipeTheme {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(Unit) {
                    val granted =
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                    viewModel.updateHasMicPermission(granted)
                }

                val micPermissionLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { granted ->
                            viewModel.updateHasMicPermission(granted)
                            if (granted) {
                                viewModel.startMicrophone()
                            }
                        },
                    )

                val audioPickerLauncher =
                    rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.GetContent(),
                        onResult = { uri: Uri? ->
                            if (uri != null) {
                                val displayName = resolveDisplayName(uri) ?: "Custom audio"
                                viewModel.selectCustomAudioUri(uri, displayName)
                            }
                        },
                    )

                AudioClassifierScreen(
                    uiState = uiState,
                    onSelectInputMode = viewModel::selectInputMode,
                    onSelectClipSource = viewModel::selectClipSource,
                    onPickCustomAudio = { audioPickerLauncher.launch("audio/*") },
                    onClassifyClip = { viewModel.classifySelectedClip(playAudio = true) },
                    onStopPlayback = viewModel::stopPlaybackOnly,
                    onToggleMicrophone = {
                        if (!uiState.hasMicPermission) {
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        } else if (uiState.isListening) {
                            viewModel.stopMicrophone()
                        } else {
                            viewModel.startMicrophone()
                        }
                    },
                    onSelectWindow = viewModel::selectWindow,
                    onUpdateMaxResults = viewModel::updateMaxResults,
                    onUpdateThreshold = viewModel::updateThreshold,
                    onUpdateOverlap = viewModel::updateOverlap,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPauseLifecycle()
    }

    private fun resolveDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && it.moveToFirst()) it.getString(idx) else null
        } ?: uri.lastPathSegment
}
