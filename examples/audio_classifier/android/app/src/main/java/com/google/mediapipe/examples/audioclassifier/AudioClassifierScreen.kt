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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioClassifierScreen(
    uiState: AudioClassifierUiState,
    onSelectInputMode: (ClassifierInputMode) -> Unit,
    onSelectClipSource: (SampleClipSource) -> Unit,
    onPickCustomAudio: () -> Unit,
    onClassifyClip: () -> Unit,
    onStopPlayback: () -> Unit,
    onToggleMicrophone: () -> Unit,
    onSelectWindow: (Int) -> Unit,
    onUpdateMaxResults: (Int) -> Unit,
    onUpdateThreshold: (Float) -> Unit,
    onUpdateOverlap: (OverlapOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.app_name),
                subtitle =
                    "YAMNet · Top ${uiState.maxResults} · ≥${String.format(Locale.US, "%.1f", uiState.threshold)}",
                onSettingsClick = { showSettings = true },
                settingsContentDescription = stringResource(R.string.cd_open_settings),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InputModeSelector(
                selectedMode = uiState.inputMode,
                onSelectMode = onSelectInputMode,
            )

            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                uiState.errorMessage?.let { message ->
                    StatusErrorBanner(
                        message = message,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = {
                            if (uiState.inputMode == ClassifierInputMode.CLIP) {
                                onClassifyClip()
                            } else {
                                onToggleMicrophone()
                            }
                        },
                    )
                }
            }

            when (uiState.inputMode) {
                ClassifierInputMode.CLIP ->
                    ClipControlsCard(
                        selectedSource = uiState.selectedSource,
                        isBusy = uiState.isBusy,
                        isPlaying = uiState.isPlaying,
                        onSelectClipSource = onSelectClipSource,
                        onPickCustomAudio = onPickCustomAudio,
                        onClassifyClip = onClassifyClip,
                        onStopPlayback = onStopPlayback,
                    )
                ClassifierInputMode.MICROPHONE ->
                    MicrophoneCard(
                        hasPermission = uiState.hasMicPermission,
                        isListening = uiState.isListening,
                        isBusy = uiState.isBusy,
                        onToggleMicrophone = onToggleMicrophone,
                    )
            }

            ResultsCard(
                uiState = uiState,
                onSelectWindow = onSelectWindow,
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            modifier = Modifier.testTag("settings_sheet"),
        ) {
            SettingsSheetContent(
                maxResults = uiState.maxResults,
                threshold = uiState.threshold,
                overlapOption = uiState.overlapOption,
                onUpdateMaxResults = onUpdateMaxResults,
                onUpdateThreshold = onUpdateThreshold,
                onUpdateOverlap = onUpdateOverlap,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun InputModeSelector(
    selectedMode: ClassifierInputMode,
    onSelectMode: (ClassifierInputMode) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = selectedMode == ClassifierInputMode.CLIP,
            onClick = { onSelectMode(ClassifierInputMode.CLIP) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_library_music_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            modifier = Modifier.testTag("mode_clip"),
        ) {
            Text(stringResource(R.string.tab_clip))
        }
        SegmentedButton(
            selected = selectedMode == ClassifierInputMode.MICROPHONE,
            onClick = { onSelectMode(ClassifierInputMode.MICROPHONE) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_record_voice_over_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            },
            modifier = Modifier.testTag("mode_mic"),
        ) {
            Text(stringResource(R.string.tab_mic))
        }
    }
}

@Composable
private fun ClipControlsCard(
    selectedSource: SampleClipSource,
    isBusy: Boolean,
    isPlaying: Boolean,
    onSelectClipSource: (SampleClipSource) -> Unit,
    onPickCustomAudio: () -> Unit,
    onClassifyClip: () -> Unit,
    onStopPlayback: () -> Unit,
) {
    val builtInSources = listOf(SampleClipSource.BundledSpeech, SampleClipSource.SyntheticTone)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.label_sample_clips),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                builtInSources.forEach { source ->
                    FilterChip(
                        selected = selectedSource.id == source.id,
                        onClick = { onSelectClipSource(source) },
                        label = { Text(source.label) },
                        modifier = Modifier.testTag("clip_${source.id}"),
                    )
                }
                if (selectedSource is SampleClipSource.CustomUri) {
                    FilterChip(
                        selected = true,
                        onClick = {},
                        label = { Text(selectedSource.label) },
                        modifier = Modifier.testTag("clip_custom"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = if (isPlaying) onStopPlayback else onClassifyClip,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier =
                        Modifier.weight(1f).heightIn(min = 48.dp).testTag("classify_button"),
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.status_classifying))
                    } else {
                        Icon(
                            painter =
                                painterResource(
                                    id =
                                        if (isPlaying) R.drawable.ic_stop
                                        else R.drawable.ic_play_arrow
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text =
                                stringResource(
                                    if (isPlaying) R.string.action_stop_playback
                                    else R.string.action_classify_clip
                                ),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                OutlinedButton(
                    onClick = onPickCustomAudio,
                    enabled = !isBusy,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.heightIn(min = 48.dp).testTag("pick_audio_button"),
                ) {
                    Text(stringResource(R.string.action_pick_audio))
                }
            }
        }
    }
}

@Composable
private fun MicrophoneCard(
    hasPermission: Boolean,
    isListening: Boolean,
    isBusy: Boolean,
    onToggleMicrophone: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.label_mic_stream),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text =
                    when {
                        !hasPermission -> stringResource(R.string.mic_permission_needed)
                        isListening -> stringResource(R.string.mic_active_hint)
                        else -> stringResource(R.string.mic_idle_hint)
                    },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = onToggleMicrophone,
                enabled = !isBusy,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            if (isListening) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.primary
                    ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("mic_toggle_button"),
            ) {
                Icon(
                    painter =
                        painterResource(
                            id = if (isListening) R.drawable.ic_stop else R.drawable.ic_mic
                        ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            when {
                                !hasPermission -> R.string.action_grant_mic
                                isListening -> R.string.action_stop_mic
                                else -> R.string.action_start_mic
                            }
                        ),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ResultsCard(
    uiState: AudioClassifierUiState,
    onSelectWindow: (Int) -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("result_card"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_top_categories),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                uiState.displayedCategories.firstOrNull()?.let { top ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = top.label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier =
                                Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    .testTag("top_category"),
                        )
                    }
                }
            }

            if (uiState.inputMode == ClassifierInputMode.CLIP && uiState.windows.size > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    uiState.windows.forEach { window ->
                        val sec = window.startMs / 1000f
                        FilterChip(
                            selected = window.windowIndex == uiState.activeWindowIndex,
                            onClick = { onSelectWindow(window.windowIndex) },
                            label = { Text(String.format(Locale.US, "%.1fs", sec)) },
                            modifier = Modifier.testTag("window_chip_${window.windowIndex}"),
                        )
                    }
                }
            }

            if (!uiState.hasResult) {
                Text(
                    text = stringResource(R.string.label_empty_results),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (uiState.displayedCategories.isEmpty()) {
                Text(
                    text = stringResource(R.string.label_no_sound_above_threshold),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    uiState.displayedCategories.forEach { category ->
                        CategoryProbabilityRow(category = category)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetadataBadge(
                    label = stringResource(R.string.label_inference_time),
                    value = uiState.inferenceTimeMs?.let { "$it ms" } ?: "--",
                    modifier = Modifier.weight(1f).testTag("metric_inference_time"),
                )
                MetadataBadge(
                    label = stringResource(R.string.label_windows),
                    value =
                        if (uiState.inputMode == ClassifierInputMode.CLIP && uiState.windows.isNotEmpty()) {
                            "${uiState.activeWindowIndex + 1}/${uiState.windows.size}"
                        } else {
                            "--"
                        },
                    modifier = Modifier.weight(1f).testTag("metric_windows"),
                )
                MetadataBadge(
                    label = stringResource(R.string.label_sample_rate),
                    value = uiState.sampleRateHz?.let { "${it / 1000} kHz" } ?: "16 kHz",
                    modifier = Modifier.weight(1f).testTag("metric_sample_rate"),
                )
            }
        }
    }
}

@Composable
private fun CategoryProbabilityRow(category: AudioCategoryUi) {
    val progress by
        animateFloatAsState(
            targetValue = category.score.coerceIn(0f, 1f),
            label = "score_${category.label}",
        )

    Column(
        modifier = Modifier.fillMaxWidth().testTag("category_row_${category.label}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = category.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = String.format(Locale.US, "%.2f", category.score),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        )
    }
}

@Composable
private fun SettingsSheetContent(
    maxResults: Int,
    threshold: Float,
    overlapOption: OverlapOption,
    onUpdateMaxResults: (Int) -> Unit,
    onUpdateThreshold: (Float) -> Unit,
    onUpdateOverlap: (OverlapOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val maxResultOptions = listOf(1, 2, 3, 4, 5)
    val overlaps = OverlapOption.entries

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        SegmentedOptionRow(
            label = stringResource(R.string.label_max_results),
            options = maxResultOptions.map { it.toString() },
            selectedIndex = maxResultOptions.indexOf(maxResults).coerceAtLeast(0),
            onSelect = { index -> onUpdateMaxResults(maxResultOptions[index]) },
        )

        SegmentedOptionRow(
            label = stringResource(R.string.label_overlap),
            options = overlaps.map { it.label },
            selectedIndex = overlaps.indexOf(overlapOption),
            onSelect = { index -> onUpdateOverlap(overlaps[index]) },
        )

        Text(
            text = stringResource(R.string.overlap_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.label_threshold),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = String.format(Locale.US, "%.2f", threshold),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("threshold_value"),
                )
            }
            Slider(
                value = threshold,
                onValueChange = { raw ->
                    val stepped = (raw * 10f).roundToInt() / 10f
                    onUpdateThreshold(stepped.coerceIn(0.1f, 0.8f))
                },
                valueRange = 0.1f..0.8f,
                steps = 6,
                modifier = Modifier.testTag("threshold_slider"),
            )
        }

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(R.string.label_delegate_cpu),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.delegate_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = onDismiss,
            modifier =
                Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("settings_done_button"),
        ) {
            Text(stringResource(R.string.action_done))
        }
    }
}
