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

package com.google.mediapipe.tasks.audio.texttospeech.examples

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import java.util.Locale

/** Stateful Compose Material 3 screen for on-device Kokoro-82M speech synthesis. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextToSpeechScreen(
  state: TtsUiState,
  onSelectPreset: (Int) -> Unit,
  onTextChange: (String) -> Unit,
  onSelectLanguage: (String) -> Unit,
  onSelectVoice: (String) -> Unit,
  onSpeak: () -> Unit,
  onStream: () -> Unit,
  onReplay: () -> Unit,
  onStop: () -> Unit,
  onSelectDelegateMode: (TtsDelegateMode) -> Unit,
  onSelectSpeakingRate: (Float) -> Unit,
  onFallbackToCpu: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showSettings by rememberSaveable { mutableStateOf(false) }
  var rateSliderValue by remember(state.speakingRate) { mutableStateOf(state.speakingRate) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      MediaPipeTopBar(
        title = stringResource(R.string.app_name),
        subtitle = "Kokoro 82M · ${state.activeDelegateLabel}",
        onSettingsClick = { showSettings = true },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .verticalScroll(rememberScrollState())
          .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      if (!state.errorMessage.isNullOrBlank()) {
        StatusErrorBanner(
          message = state.errorMessage,
          actionLabel = if (state.delegateMode == TtsDelegateMode.GPU) "Use CPU" else null,
          onAction = if (state.delegateMode == TtsDelegateMode.GPU) onFallbackToCpu else null,
        )
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetadataBadge(
          label = "Status",
          value = state.statusBadge,
          modifier = Modifier.weight(1.1f).testTag("tts_status_badge"),
        )
        MetadataBadge(
          label = "Audio",
          value = state.durationSeconds?.let { String.format(Locale.US, "%.2fs", it) } ?: "--",
          modifier = Modifier.weight(0.95f).testTag("tts_duration_badge"),
        )
        MetadataBadge(
          label = "Latency",
          value = state.latencyMs?.let { "$it ms" } ?: "--",
          modifier = Modifier.weight(0.95f).testTag("tts_latency_badge"),
        )
      }

      Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          text = "Sample utterances",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          itemsIndexed(TextToSpeechHelper.PRESETS) { index, preset ->
            FilterChip(
              selected = index == state.selectedPresetIndex,
              onClick = { onSelectPreset(index) },
              enabled = !state.isBusy,
              label = { Text(preset.chipLabel, maxLines = 1) },
            )
          }
        }
      }

      OutlinedTextField(
        value = state.textInput,
        onValueChange = onTextChange,
        label = { Text("Text to speak") },
        minLines = 4,
        maxLines = 6,
        enabled = !state.isBusy,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("tts_text_input"),
      )

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LanguageDropdown(
          languages = state.availableLanguages,
          selectedTag = state.selectedLanguageTag,
          enabled = state.isEngineReady && !state.isBusy,
          onSelect = onSelectLanguage,
          modifier = Modifier.weight(0.9f).testTag("tts_language_selector"),
        )
        VoiceDropdown(
          voices = state.voicesForLanguage,
          selectedVoiceId = state.selectedVoiceId,
          enabled = state.isEngineReady && !state.isBusy,
          onSelect = onSelectVoice,
          modifier = Modifier.weight(1.1f).testTag("tts_voice_selector"),
        )
      }

      val telemetrySummary = buildTelemetryLine(state)
      if (telemetrySummary != null) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier.fillMaxWidth().testTag("tts_telemetry_card"),
        ) {
          Text(
            text = telemetrySummary,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
          )
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
          onClick = onSpeak,
          enabled = state.isEngineReady && !state.isBusy,
          shape = RoundedCornerShape(26.dp),
          contentPadding = PaddingValues(vertical = 14.dp),
          modifier = Modifier.weight(1f).testTag("tts_speak_button"),
        ) {
          Text(
            text = "Speak",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
        FilledTonalButton(
          onClick = onStream,
          enabled = state.isEngineReady && !state.isBusy,
          shape = RoundedCornerShape(26.dp),
          contentPadding = PaddingValues(vertical = 14.dp),
          modifier = Modifier.weight(1f).testTag("tts_stream_button"),
        ) {
          Text(
            text = "Stream",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
          )
        }
      }

      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(
          onClick = onReplay,
          enabled = state.isEngineReady && !state.isBusy && state.hasReplayableAudio,
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.weight(1f).testTag("tts_replay_button"),
        ) {
          Text(text = "Replay")
        }
        Button(
          onClick = onStop,
          enabled = state.isBusy,
          colors =
            ButtonDefaults.buttonColors(
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
            ),
          shape = RoundedCornerShape(24.dp),
          modifier = Modifier.weight(1f).testTag("tts_stop_button"),
        ) {
          Text(text = "Stop")
        }
      }
    }
  }

  if (showSettings) {
    ModalBottomSheet(onDismissRequest = { showSettings = false }) {
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "Engine & Voice Settings",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold,
        )

        SegmentedOptionRow(
          label = "Delegate",
          options = TtsDelegateMode.entries.map { it.label },
          selectedIndex = TtsDelegateMode.entries.indexOf(state.delegateMode),
          enabled = !state.isBusy,
          onSelect = { idx -> onSelectDelegateMode(TtsDelegateMode.entries[idx]) },
        )

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "Speaking rate",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = String.format(Locale.US, "%.2fx", rateSliderValue),
              style = MaterialTheme.typography.labelLarge,
              fontFamily = FontFamily.Monospace,
            )
          }
          Slider(
            value = rateSliderValue,
            onValueChange = { rateSliderValue = it },
            onValueChangeFinished = { onSelectSpeakingRate(rateSliderValue) },
            valueRange = 0.75f..1.4f,
            enabled = !state.isBusy,
          )
        }

        if (state.logLines.isNotEmpty()) {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
              text = "Engine log",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(
              color = MaterialTheme.colorScheme.surfaceContainerHigh,
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Text(
                text = state.logLines.takeLast(8).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(10.dp),
              )
            }
          }
        }

        Button(
          onClick = { showSettings = false },
          modifier = Modifier.fillMaxWidth().testTag("settings_done_button"),
        ) {
          Text("Done")
        }
        Spacer(modifier = Modifier.height(8.dp))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageDropdown(
  languages: List<Pair<String, String>>,
  selectedTag: String,
  enabled: Boolean,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedLabel =
    languages.firstOrNull { it.first == selectedTag }?.second
      ?: TextToSpeechHelper.languageDisplayName(selectedTag)

  ExposedDropdownMenuBox(
    expanded = expanded && enabled,
    onExpandedChange = { if (enabled) expanded = !expanded },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      enabled = enabled,
      singleLine = true,
      label = { Text("Language") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
      shape = RoundedCornerShape(12.dp),
      modifier =
        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
      languages.forEach { (tag, label) ->
        DropdownMenuItem(
          text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
          onClick = {
            expanded = false
            onSelect(tag)
          },
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoiceDropdown(
  voices: List<com.google.mediapipe.tasks.audio.texttospeech.VoiceInfo>,
  selectedVoiceId: String,
  enabled: Boolean,
  onSelect: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by remember { mutableStateOf(false) }
  val selectedVoice = voices.firstOrNull { it.voiceId == selectedVoiceId }
  val selectedLabel = selectedVoice?.let { TextToSpeechHelper.voiceDisplayName(it) } ?: selectedVoiceId

  ExposedDropdownMenuBox(
    expanded = expanded && enabled,
    onExpandedChange = { if (enabled) expanded = !expanded },
    modifier = modifier,
  ) {
    OutlinedTextField(
      value = selectedLabel,
      onValueChange = {},
      readOnly = true,
      enabled = enabled,
      singleLine = true,
      label = { Text("Voice") },
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
      shape = RoundedCornerShape(12.dp),
      modifier =
        Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
    )
    ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
      voices.forEach { voice ->
        DropdownMenuItem(
          text = {
            Text(
              TextToSpeechHelper.voiceDisplayName(voice),
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          },
          onClick = {
            expanded = false
            onSelect(voice.voiceId)
          },
        )
      }
    }
  }
}

private fun buildTelemetryLine(state: TtsUiState): String? {
  val duration = state.durationSeconds ?: return null
  val latency = state.latencyMs ?: return null
  val parts = mutableListOf<String>()
  parts.add(state.selectedVoiceId)
  parts.add(String.format(Locale.US, "%.2fs / %d ms", duration, latency))
  state.rtf?.takeIf { !it.isNaN() }?.let {
    parts.add(String.format(Locale.US, "RTF %.2fx", it))
  }
  state.ttfaMs?.let { ttfa ->
    val chunks = state.chunkCount ?: 0
    parts.add("$chunks chunks (TTFA $ttfa ms)")
  }
  state.lastWavPath?.let { parts.add(it) }
  return parts.joinToString(" · ")
}
