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

package com.google.mediapipe.tasks.examples.audiotranscriber

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.examples.common.ui.MediaPipeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "AudioTranscriberMain"

/** Main activity for the MediaPipe AudioTranscriber Android sample app. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MediaPipeTheme {
        val appContext = LocalContext.current.applicationContext
        val transcriberViewModel: AudioTranscriberViewModel =
          viewModel(factory = AudioTranscriberViewModel.factory(appContext))
        AudioTranscriberRoute(viewModel = transcriberViewModel)
      }
    }
  }
}

@Composable
private fun AudioTranscriberRoute(viewModel: AudioTranscriberViewModel) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val state by viewModel.uiState.collectAsState()

  val permissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { isGranted ->
        if (isGranted) {
          viewModel.startRecording()
        } else {
          viewModel.onMicrophonePermissionDenied()
        }
      },
    )

  val openModelLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
      onResult = { uri: Uri? ->
        if (uri != null) {
          coroutineScope.launch {
            try {
              val stagedFile =
                withContext(Dispatchers.IO) {
                  val displayName = resolveUriDisplayName(context, uri)
                  stageSideloadedModelStream(
                    cacheDir = context.cacheDir,
                    displayName = displayName,
                    inputStreamProvider = { context.contentResolver.openInputStream(uri) },
                  )
                }
              viewModel.loadModel(stagedFile)
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              Log.e(TAG, "Failed to stage selected model file", e)
              viewModel.onModelStagingFailed(e.message)
            }
          }
        }
      },
    )

  val openAudioLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.OpenDocument(),
      onResult = { uri: Uri? ->
        if (uri != null) {
          viewModel.transcribeAudioUri(uri)
        }
      },
    )

  AudioTranscriberScreen(
    state = state,
    onTranscribeSample = viewModel::transcribeSampleClip,
    onToggleRecording = {
      if (state.isRecording) {
        viewModel.stopRecording()
      } else {
        val permissionStatus =
          ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
          viewModel.startRecording()
        } else {
          permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
      }
    },
    onClearTranscripts = viewModel::clearTranscripts,
    onSelectModelFile = { openModelLauncher.launch(arrayOf("*/*")) },
    onPickAudioFile = { openAudioLauncher.launch(arrayOf("audio/*")) },
    onSelectClipMode = viewModel::setClipTranscriptionMode,
    onSelectDelegate = viewModel::setDelegate,
    onFallbackToCpu = viewModel::fallbackToCpu,
  )
}

private fun resolveUriDisplayName(context: Context, uri: Uri): String? {
  return try {
    context.contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
          cursor.getString(nameIndex)
        } else {
          uri.lastPathSegment
        }
      }
  } catch (e: Exception) {
    Log.w(TAG, "Failed to query display name for URI: $uri", e)
    uri.lastPathSegment
  }
}
