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

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriberResult
import java.util.Locale

/** Stateless Jetpack Compose screen for MediaPipe [AudioTranscriber] speech recognition. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioTranscriberScreen(
  state: AudioTranscriberUiState,
  onTranscribeSample: () -> Unit,
  onToggleRecording: () -> Unit,
  onClearTranscripts: () -> Unit,
  onSelectModelFile: () -> Unit,
  onPickAudioFile: () -> Unit = {},
  onSelectClipMode: (ClipTranscriptionMode) -> Unit = {},
  onSelectDelegate: (Int) -> Unit = {},
  onFallbackToCpu: () -> Unit = {},
  modifier: Modifier = Modifier,
) {
  var showSettings by rememberSaveable { mutableStateOf(false) }
  val delegateLabel =
    if (state.currentDelegate == AudioTranscriberHelper.DELEGATE_GPU) "GPU" else "CPU"
  val subtitle = "Parakeet TDT 0.6B · $delegateLabel · ${state.clipTranscriptionMode.label}"

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      MediaPipeTopBar(
        title = stringResource(R.string.app_name),
        subtitle = subtitle,
        onSettingsClick = { showSettings = true },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.errorMessage.isNullOrBlank()) {
        StatusErrorBanner(
          message = state.errorMessage,
          actionLabel =
            if (state.currentDelegate == AudioTranscriberHelper.DELEGATE_GPU) {
              "Use CPU"
            } else {
              null
            },
          onAction =
            if (state.currentDelegate == AudioTranscriberHelper.DELEGATE_GPU) {
              onFallbackToCpu
            } else {
              null
            },
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataBadge(
          label = "Status",
          value = state.statusMessage,
          modifier = Modifier.weight(1.4f).testTag("status_badge"),
        )
        MetadataBadge(
          label = "Segments",
          value = state.finalizedSegments.size.toString(),
          modifier = Modifier.weight(0.8f),
        )
        MetadataBadge(
          label = "Latency",
          value = state.lastLatencyMs?.let { "${it} ms" } ?: "--",
          modifier = Modifier.weight(0.9f),
        )
      }

      if (state.isRecording || state.isTranscribingSample) {
        AudioLevelMeter(audioLevel = state.audioLevel)
      }

      TranscriptCard(
        finalizedSegments = state.finalizedSegments,
        interimTranscript = state.interimTranscript,
        modifier = Modifier.weight(1f).fillMaxWidth().testTag("transcript_card"),
      )

      ControlButtonsRow(
        state = state,
        hasTranscriptContent =
          state.finalizedSegments.isNotEmpty() || state.interimTranscript.isNotEmpty(),
        onTranscribeSample = onTranscribeSample,
        onPickAudioFile = onPickAudioFile,
        onToggleRecording = onToggleRecording,
        onClearTranscripts = onClearTranscripts,
      )
    }
  }

  if (showSettings) {
    val clipModes = ClipTranscriptionMode.entries
    ModalBottomSheet(onDismissRequest = { showSettings = false }) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = stringResource(R.string.settings_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )
        Text(
          text = state.activeModelName,
          style = MaterialTheme.typography.bodySmall,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        SegmentedOptionRow(
          label = stringResource(R.string.delegate_label),
          options = listOf("CPU", "GPU"),
          selectedIndex = state.currentDelegate,
          enabled = !state.isRecording && !state.isTranscribingSample,
          onSelect = onSelectDelegate,
        )
        SegmentedOptionRow(
          label = stringResource(R.string.clip_mode_label),
          options = clipModes.map { it.label },
          selectedIndex = clipModes.indexOf(state.clipTranscriptionMode).coerceAtLeast(0),
          enabled = !state.isRecording && !state.isTranscribingSample,
          onSelect = { index -> onSelectClipMode(clipModes[index]) },
        )
        OutlinedButton(
          onClick = {
            showSettings = false
            onSelectModelFile()
          },
          enabled = !state.isRecording && !state.isTranscribingSample,
          modifier = Modifier.fillMaxWidth().testTag("load_model_button"),
        ) {
          Text(text = stringResource(R.string.load_model))
        }
        Button(
          onClick = { showSettings = false },
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(text = stringResource(R.string.done))
        }
        Spacer(modifier = Modifier.height(8.dp))
      }
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

  ElevatedCard(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(16.dp),
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
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
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
    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
    shape = RoundedCornerShape(10.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
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
  state: AudioTranscriberUiState,
  hasTranscriptContent: Boolean,
  onTranscribeSample: () -> Unit,
  onPickAudioFile: () -> Unit,
  onToggleRecording: () -> Unit,
  onClearTranscripts: () -> Unit,
) {
  val busy = state.isRecording || state.isTranscribingSample
  Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(
        onClick = onTranscribeSample,
        enabled = state.isModelReady && !busy,
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = Modifier.weight(1.4f).testTag("transcribe_sample_button"),
      ) {
        Text(
          text = stringResource(R.string.transcribe_sample),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      OutlinedButton(
        onClick = onPickAudioFile,
        enabled = state.isModelReady && !busy,
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(vertical = 14.dp),
        modifier = Modifier.weight(1f).testTag("pick_wav_button"),
      ) {
        Text(
          text = stringResource(R.string.pick_wav_file),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(
        onClick = onToggleRecording,
        enabled = state.isModelReady && !state.isTranscribingSample,
        colors =
          ButtonDefaults.buttonColors(
            containerColor =
              if (state.isRecording) {
                MaterialTheme.colorScheme.error
              } else {
                MaterialTheme.colorScheme.secondaryContainer
              },
            contentColor =
              if (state.isRecording) {
                MaterialTheme.colorScheme.onError
              } else {
                MaterialTheme.colorScheme.onSecondaryContainer
              },
          ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.weight(1f).testTag("record_mic_button"),
      ) {
        Text(
          text =
            stringResource(
              if (state.isRecording) R.string.stop_recording else R.string.start_recording
            ),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }

      OutlinedButton(
        onClick = onClearTranscripts,
        enabled = !busy && hasTranscriptContent,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.weight(1f).testTag("clear_transcript_button"),
      ) {
        Text(text = stringResource(R.string.clear_all), maxLines = 1)
      }
    }
  }
}
