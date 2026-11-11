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

package com.google.mediapipe.examples.textproofreader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.R as CommonUiR
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import com.google.mediapipe.tasks.text.textproofreader.TextProofreaderResult.CorrectionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextProofreaderScreen(
    uiState: TextProofreaderUiState,
    onInputTextChange: (String) -> Unit,
    onApplyPreset: (ProofreaderPreset) -> Unit,
    onProofread: (Boolean) -> Unit,
    onApplyCorrectedToInput: () -> Unit,
    onSelectMaxTokens: (MaxTokensOption) -> Unit,
    onRetryInitialization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.app_name),
                subtitle = "Gemma 200M · ${uiState.maxTokensOption.label} tok · CPU",
                onSettingsClick = { showSettings = true },
                settingsContentDescription = stringResource(R.string.cd_open_settings),
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                uiState.errorMessage?.let { message ->
                    StatusErrorBanner(
                        message = message,
                        actionLabel = stringResource(R.string.action_retry),
                        onAction = onRetryInitialization,
                    )
                }
            }

            PresetSelectorRow(
                currentText = uiState.inputText,
                onApplyPreset = { preset ->
                    focusManager.clearFocus()
                    onApplyPreset(preset)
                },
            )

            TextInputCard(
                text = uiState.inputText,
                onTextChange = onInputTextChange,
            )

            ActionButtonsRow(
                uiState = uiState,
                onProofread = { streaming ->
                    focusManager.clearFocus()
                    onProofread(streaming)
                },
            )

            AnimatedVisibility(
                visible = uiState.streamingText.isNotEmpty() || uiState.result != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                ProofreaderResultCard(
                    streamingText = uiState.streamingText,
                    result = uiState.result,
                    onApplyCorrectedToInput = onApplyCorrectedToInput,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            modifier = Modifier.testTag("settings_sheet"),
        ) {
            SettingsSheetContent(
                selectedOption = uiState.maxTokensOption,
                isBusy = uiState.isBusy,
                onSelectMaxTokens = onSelectMaxTokens,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun PresetSelectorRow(
    currentText: String,
    onApplyPreset: (ProofreaderPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.label_examples),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DefaultProofreaderPresets.forEach { preset ->
                FilterChip(
                    selected = currentText == preset.text,
                    onClick = { onApplyPreset(preset) },
                    label = { Text(preset.label) },
                    modifier = Modifier.testTag("preset_${preset.id}"),
                )
            }
        }
    }
}

@Composable
private fun TextInputCard(
    text: String,
    onTextChange: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 16.dp, end = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.label_input_text),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${text.length} chars",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (text.isNotEmpty()) {
                        IconButton(
                            onClick = { onTextChange("") },
                            modifier = Modifier.size(32.dp).testTag("clear_text"),
                        ) {
                            Icon(
                                painter = painterResource(id = CommonUiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.cd_clear_text),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.hint_input_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                minLines = 3,
                maxLines = 6,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                modifier = Modifier.fillMaxWidth().testTag("input_text"),
            )
        }
    }
}

@Composable
private fun ActionButtonsRow(
    uiState: TextProofreaderUiState,
    onProofread: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = { onProofread(false) },
            enabled = uiState.canProofread,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.weight(1f).heightIn(min = 50.dp).testTag("proofread_button"),
        ) {
            if (uiState.isBusy) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text =
                        stringResource(
                            if (uiState.isModelLoading) R.string.status_loading_model
                            else R.string.status_proofreading
                        ),
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = stringResource(R.string.action_proofread),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        OutlinedButton(
            onClick = { onProofread(true) },
            enabled = uiState.canProofread,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.heightIn(min = 50.dp).testTag("stream_button"),
        ) {
            Text(
                text = stringResource(R.string.action_stream),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ProofreaderResultCard(
    streamingText: String,
    result: TextProofreaderHelper.ResultBundle?,
    onApplyCorrectedToInput: () -> Unit,
) {
    val displayedText = result?.proofreadText ?: streamingText
    val darkTheme = isSystemInDarkTheme()

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
                    text = stringResource(R.string.label_proofread_result),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (result != null && result.proofreadText.isNotBlank()) {
                    TextButton(
                        onClick = onApplyCorrectedToInput,
                        modifier = Modifier.testTag("apply_fix_button"),
                    ) {
                        Text(stringResource(R.string.action_use_corrected))
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = displayedText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(14.dp).testTag("proofread_output_text"),
                )
            }

            if (result != null) {
                Text(
                    text = stringResource(R.string.label_highlighted_diff),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (result.corrections.isEmpty()) {
                        Text(
                            text = stringResource(R.string.label_no_edits_needed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(14.dp),
                        )
                    } else {
                        Text(
                            text = buildDiffAnnotatedString(result.corrections, darkTheme),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(14.dp).testTag("diff_output_text"),
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataBadge(
                        label = stringResource(R.string.label_inference_time),
                        value = "${result.inferenceTimeMs} ms",
                        modifier = Modifier.weight(1f).testTag("metric_inference_time"),
                    )
                    MetadataBadge(
                        label = stringResource(R.string.label_edits),
                        value = "${result.editCount}",
                        modifier = Modifier.weight(1f).testTag("metric_edit_count"),
                    )
                }
            }
        }
    }
}

private fun buildDiffAnnotatedString(
    corrections: List<TextProofreaderHelper.CorrectionUi>,
    darkTheme: Boolean,
): AnnotatedString = buildAnnotatedString {
    val insertFg = if (darkTheme) Color(0xFF9DF2A8) else Color(0xFF0A5C18)
    val insertBg = if (darkTheme) Color(0xFF1B4323) else Color(0xFFDCF6DF)
    val deleteFg = if (darkTheme) Color(0xFFFFB4AB) else Color(0xFF8C1D18)
    val deleteBg = if (darkTheme) Color(0xFF4D1A18) else Color(0xFFFDE2E0)

    corrections.forEach { correction ->
        when (correction.type) {
            CorrectionType.INSERTION -> {
                withStyle(
                    SpanStyle(
                        color = insertFg,
                        background = insertBg,
                        fontWeight = FontWeight.SemiBold,
                    )
                ) {
                    append(correction.text)
                }
            }
            CorrectionType.DELETION -> {
                withStyle(
                    SpanStyle(
                        color = deleteFg,
                        background = deleteBg,
                        textDecoration = TextDecoration.LineThrough,
                    )
                ) {
                    append(correction.text)
                }
            }
            CorrectionType.SAME -> append(correction.text)
        }
    }
}

@Composable
private fun SettingsSheetContent(
    selectedOption: MaxTokensOption,
    isBusy: Boolean,
    onSelectMaxTokens: (MaxTokensOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = MaxTokensOption.entries

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )

        SegmentedOptionRow(
            label = stringResource(R.string.label_max_tokens),
            options = options.map { it.label },
            selectedIndex = options.indexOf(selectedOption),
            enabled = !isBusy,
            onSelect = { index -> onSelectMaxTokens(options[index]) },
        )

        Text(
            text = stringResource(R.string.max_tokens_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()

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
                    text = stringResource(R.string.label_accelerator_cpu),
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
