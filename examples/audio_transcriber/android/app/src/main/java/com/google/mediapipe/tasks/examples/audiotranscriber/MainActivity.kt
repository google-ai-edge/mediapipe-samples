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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriberResult
import com.google.mediapipe.tasks.examples.audiotranscriber.ui.theme.AudioTranscriberTheme
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private object MainActivityConstants {
  const val TAG = "AudioTranscriberMain"
  const val SIDELOAD_CACHE_SUBDIR = "sideloaded_models"
  const val DEFAULT_SIDELOADED_FILE_NAME = "sideloaded_model.litertlm"
}

/**
 * Copies the stream provided by [inputStreamProvider] into a file inside
 * `[cacheDir]/sideloaded_models/` with a sanitized `.litertlm` filename derived from [displayName].
 */
internal fun stageSideloadedModelStream(
  cacheDir: File,
  displayName: String?,
  inputStreamProvider: () -> InputStream?,
): File {
  val targetDir = File(cacheDir, MainActivityConstants.SIDELOAD_CACHE_SUBDIR)
  if (!targetDir.exists()) {
    targetDir.mkdirs()
  }

  val sanitizedBaseName =
    displayName?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.takeIf {
      it.isNotBlank()
    } ?: MainActivityConstants.DEFAULT_SIDELOADED_FILE_NAME
  val finalFileName =
    if (sanitizedBaseName.endsWith(".${AudioTranscriberViewModel.MODEL_EXTENSION}", true)) {
      sanitizedBaseName
    } else {
      "$sanitizedBaseName.${AudioTranscriberViewModel.MODEL_EXTENSION}"
    }

  val destinationFile = File(targetDir, finalFileName)
  val inputStream =
    inputStreamProvider() ?: throw IOException("Unable to open selected model document stream.")
  inputStream.use { input ->
    FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
  }
  return destinationFile
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
    Log.w(MainActivityConstants.TAG, "Failed to query display name for URI: $uri", e)
    uri.lastPathSegment
  }
}

/** Main activity for the MediaPipe AudioTranscriber Android sample app. */
class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      AudioTranscriberTheme {
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
              Log.e(MainActivityConstants.TAG, "Failed to stage selected model file", e)
              viewModel.onModelStagingFailed(e.message)
            }
          }
        }
      },
    )

  AudioTranscriberScreen(
    state = state,
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
  )
}

/** Stateless Jetpack Compose screen for live MediaPipe [AudioTranscriber] speech recognition. */
@Composable
fun AudioTranscriberScreen(
  state: AudioTranscriberUiState,
  onToggleRecording: () -> Unit,
  onClearTranscripts: () -> Unit,
  onSelectModelFile: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      HeaderSection(activeModelName = state.activeModelName)

      StatusPill(isRecording = state.isRecording, statusMessage = state.statusMessage)

      if (state.isRecording) {
        AudioLevelMeter(audioLevel = state.audioLevel)
      }

      TranscriptCard(
        finalizedSegments = state.finalizedSegments,
        interimTranscript = state.interimTranscript,
        modifier = Modifier.weight(1f).fillMaxWidth(),
      )

      ControlButtonsRow(
        isRecording = state.isRecording,
        hasTranscriptContent =
          state.finalizedSegments.isNotEmpty() || state.interimTranscript.isNotEmpty(),
        onToggleRecording = onToggleRecording,
        onClearTranscripts = onClearTranscripts,
        onSelectModelFile = onSelectModelFile,
      )
    }
  }
}

@Composable
private fun HeaderSection(activeModelName: String) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(4.dp),
    modifier = Modifier.padding(top = 8.dp),
  ) {
    Text(
      text = stringResource(R.string.app_name),
      style = MaterialTheme.typography.headlineSmall,
      fontWeight = FontWeight.Bold,
    )
    Text(
      text = stringResource(R.string.subtitle),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
      text = stringResource(R.string.model_label_format, activeModelName),
      style = MaterialTheme.typography.labelMedium,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun StatusPill(isRecording: Boolean, statusMessage: String) {
  Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      val indicatorColor =
        if (isRecording) {
          MaterialTheme.colorScheme.error
        } else {
          MaterialTheme.colorScheme.primary
        }
      Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(indicatorColor))
      Text(
        text = statusMessage,
        style = MaterialTheme.typography.labelMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun AudioLevelMeter(audioLevel: Float) {
  val animatedLevel by
    animateFloatAsState(targetValue = audioLevel.coerceIn(0.0f, 1.0f), label = "audioLevel")

  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = stringResource(R.string.audio_level_label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(
        text = String.format(Locale.US, "%d%%", (animatedLevel * 100).toInt()),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    LinearProgressIndicator(
      progress = { animatedLevel },
      modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(6.dp)),
    )
  }
}

@Composable
private fun TranscriptCard(
  finalizedSegments: List<AudioTranscriberResult.Segment>,
  interimTranscript: String,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  val totalItems = finalizedSegments.size + if (interimTranscript.isNotEmpty()) 1 else 0

  LaunchedEffect(finalizedSegments.size, interimTranscript) {
    if (totalItems > 0) {
      listState.animateScrollToItem(totalItems - 1)
    }
  }

  OutlinedCard(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
    Column(
      modifier = Modifier.fillMaxSize().padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = stringResource(R.string.transcript_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
      )

      if (finalizedSegments.isEmpty() && interimTranscript.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Text(
            text = stringResource(R.string.empty_transcript_prompt),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
          )
        }
      } else {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          contentPadding = PaddingValues(vertical = 4.dp),
          verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          items(items = finalizedSegments) { segment -> SegmentRow(segment = segment) }
          if (interimTranscript.isNotEmpty()) {
            item(key = "interim_transcript_row") {
              InterimSegmentRow(interimTranscript = interimTranscript)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun SegmentRow(segment: AudioTranscriberResult.Segment) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
      Text(
        text = String.format(Locale.US, "(%.1fs)", segment.durationSeconds),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = segment.transcript(),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
  }
}

@Composable
private fun InterimSegmentRow(interimTranscript: String) {
  Surface(
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
    shape = RoundedCornerShape(8.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.Top) {
      Text(
        text = stringResource(R.string.live_badge),
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(top = 2.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = interimTranscript,
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
  }
}

@Composable
private fun ControlButtonsRow(
  isRecording: Boolean,
  hasTranscriptContent: Boolean,
  onToggleRecording: () -> Unit,
  onClearTranscripts: () -> Unit,
  onSelectModelFile: () -> Unit,
) {
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Button(
      onClick = onToggleRecording,
      colors =
        ButtonDefaults.buttonColors(
          containerColor =
            if (isRecording) {
              MaterialTheme.colorScheme.error
            } else {
              MaterialTheme.colorScheme.primary
            }
        ),
      shape = RoundedCornerShape(28.dp),
      contentPadding = PaddingValues(vertical = 14.dp),
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text(
        text =
          stringResource(if (isRecording) R.string.stop_recording else R.string.start_recording),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
      )
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(
        onClick = onSelectModelFile,
        enabled = !isRecording,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.weight(1f),
      ) {
        Text(text = stringResource(R.string.load_model))
      }

      OutlinedButton(
        onClick = onClearTranscripts,
        enabled = !isRecording && hasTranscriptContent,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.weight(1f),
      ) {
        Text(text = stringResource(R.string.clear_all))
      }
    }
  }
}
