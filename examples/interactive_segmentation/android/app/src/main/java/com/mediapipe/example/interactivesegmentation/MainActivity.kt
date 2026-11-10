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
package com.mediapipe.example.interactivesegmentation

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.common.ui.MediaPipeTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Main activity for the MediaPipe Interactive Segmentation sample app. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MediaPipeTheme {
                val context = LocalContext.current
                val segmenterViewModel: InteractiveSegmentationViewModel =
                    viewModel(
                        factory =
                            InteractiveSegmentationViewModel.factory(context.applicationContext)
                    )
                val uiState by segmenterViewModel.uiState.collectAsStateWithLifecycle()
                var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }

                val pickImageLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
                        uri ->
                        if (uri != null) {
                            segmenterViewModel.loadImageUri(uri)
                        }
                    }

                val takePhotoLauncher =
                    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
                        success ->
                        val captured = pendingPhotoUri
                        if (success && captured != null) {
                            segmenterViewModel.loadImageUri(captured)
                        }
                    }

                InteractiveSegmentationScreen(
                    uiState = uiState,
                    onSelectBrushMode = segmenterViewModel::selectBrushMode,
                    onAddStroke = segmenterViewModel::addStroke,
                    onUndoStroke = segmenterViewModel::undoLastStroke,
                    onClearStrokes = segmenterViewModel::clearStrokes,
                    onTrySample = segmenterViewModel::loadSampleImage,
                    onPickImage = { pickImageLauncher.launch(arrayOf("image/*")) },
                    onTakePhoto = {
                        val uri = createTempPhotoUri(context)
                        pendingPhotoUri = uri
                        takePhotoLauncher.launch(uri)
                    },
                    onRetryInitialization = segmenterViewModel::retryInitialization,
                )
            }
        }
    }

    private fun createTempPhotoUri(context: Context): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val photoFile = File(context.cacheDir, "JPEG_$timestamp.jpg")
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            photoFile,
        )
    }
}
