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

package com.google.mediapipe.examples.imageclassification

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.common.ui.MediaPipeTheme

/** Main activity for the MediaPipe Image Classifier Android sample app. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaPipeTheme {
                val context = LocalContext.current
                val classifierViewModel: ImageClassifierViewModel =
                    viewModel(factory = ImageClassifierViewModel.factory(context.applicationContext))
                val uiState by classifierViewModel.uiState.collectAsStateWithLifecycle()

                val permissionLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                        classifierViewModel.updateCameraPermission(granted)
                    }

                val mediaPickerLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                        if (uri != null) {
                            classifierViewModel.classifyGalleryUri(uri)
                        }
                    }

                LaunchedEffect(Unit) {
                    val granted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    classifierViewModel.updateCameraPermission(granted)
                }

                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                    val granted =
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    classifierViewModel.updateCameraPermission(granted)
                }

                ImageClassifierScreen(
                    uiState = uiState,
                    analyzerExecutor = classifierViewModel.cameraAnalyzerExecutor,
                    onSelectTab = classifierViewModel::selectTab,
                    onRequestCameraPermission = {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    },
                    onFlipCamera = classifierViewModel::toggleCameraLens,
                    onCameraFrame = classifierViewModel::analyzeCameraFrame,
                    onCameraUnavailable = classifierViewModel::setCameraUnavailable,
                    onPickMedia = { mediaPickerLauncher.launch(arrayOf("image/*", "video/*")) },
                    onTrySample = classifierViewModel::classifySampleImage,
                    onSelectModel = classifierViewModel::selectModel,
                    onSelectDelegate = classifierViewModel::selectDelegate,
                    onSelectMaxResults = classifierViewModel::selectMaxResults,
                    onUpdateThreshold = classifierViewModel::updateThreshold,
                    onUseCpuDelegate = classifierViewModel::switchToCpuDelegate,
                    onRetryInitialization = classifierViewModel::retryInitialization,
                )
            }
        }
    }
}
