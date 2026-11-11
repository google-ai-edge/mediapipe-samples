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

package com.google.mediapipe.examples.decisionmaker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.R as CommonUiR
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecisionMakerScreen(
    uiState: DecisionMakerUiState,
    onSelectQuestionType: (QuestionType) -> Unit,
    onInputTextChange: (String) -> Unit,
    onConditionChange: (String) -> Unit,
    onThresholdChange: (Float) -> Unit,
    onThresholdChangeFinished: () -> Unit,
    onInstructionsChange: (String) -> Unit,
    onAddItem: () -> Unit,
    onUpdateItemLabel: (Long, String) -> Unit,
    onUpdateItemDescription: (Long, String) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onEvaluate: () -> Unit,
    onSelectDelegate: (DecisionDelegateOption) -> Unit,
    onSelectModel: (DecisionModelOption) -> Unit,
    onRetryInitialization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val draft = uiState.activeDraft

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.app_name),
                subtitle = "${uiState.modelOption.shortLabel} · ${uiState.delegateOption.label}",
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
            QuestionTypeTabs(
                selectedType = uiState.questionType,
                onSelect = onSelectQuestionType,
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
                        onAction = onRetryInitialization,
                    )
                }
            }

            DecisionResultCard(
                outcome = uiState.outcome,
                modelLabel = uiState.modelOption.shortLabel,
                delegateLabel = uiState.delegateOption.label,
                isBusy = uiState.isBusy,
            )

            InputFieldCard(
                title = stringResource(R.string.label_input_text),
                value = draft.inputText,
                onValueChange = onInputTextChange,
                testTag = "input_text",
                minLines = 3,
            )

            if (uiState.questionType == QuestionType.BOOLEAN) {
                InputFieldCard(
                    title = stringResource(R.string.label_condition),
                    value = draft.condition,
                    onValueChange = onConditionChange,
                    testTag = "condition_text",
                    minLines = 2,
                )

                ThresholdCard(
                    threshold = draft.threshold,
                    onValueChange = onThresholdChange,
                    onValueChangeFinished = onThresholdChangeFinished,
                )
            } else {
                ItemsEditorSection(
                    questionType = uiState.questionType,
                    items = draft.items,
                    onAddItem = onAddItem,
                    onUpdateLabel = onUpdateItemLabel,
                    onUpdateDescription = onUpdateItemDescription,
                    onRemoveItem = onRemoveItem,
                )

                InputFieldCard(
                    title = stringResource(R.string.label_instructions),
                    value = draft.instructions,
                    onValueChange = onInstructionsChange,
                    testTag = "instructions_text",
                    minLines = 2,
                )
            }

            Button(
                onClick = {
                    focusManager.clearFocus()
                    onEvaluate()
                },
                enabled = uiState.canEvaluate,
                shape = RoundedCornerShape(16.dp),
                modifier =
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("evaluate_button"),
            ) {
                if (uiState.isBusy) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text =
                            stringResource(
                                if (uiState.isModelLoading) R.string.status_loading_model
                                else R.string.status_evaluating
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.btn_evaluate),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
                selectedDelegate = uiState.delegateOption,
                selectedModel = uiState.modelOption,
                isBusy = uiState.isBusy,
                onSelectDelegate = onSelectDelegate,
                onSelectModel = onSelectModel,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun QuestionTypeTabs(
    selectedType: QuestionType,
    onSelect: (QuestionType) -> Unit,
) {
    val types = QuestionType.entries
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selectedType,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                modifier = Modifier.testTag("tab_${type.name.lowercase()}"),
            ) {
                Text(type.label)
            }
        }
    }
}

@Composable
private fun DecisionResultCard(
    outcome: DecisionOutcomeUi?,
    modelLabel: String,
    delegateLabel: String,
    isBusy: Boolean,
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("result_card"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.label_result),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (outcome == null) {
                Text(
                    text =
                        stringResource(
                            if (isBusy) R.string.status_loading_model
                            else R.string.tv_result_placeholder
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("result_placeholder"),
                )
            } else {
                Text(
                    text = outcome.headline,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("result_headline"),
                )
                outcome.subtitle?.let { sub ->
                    Text(
                        text = sub,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    outcome.bars.forEach { bar -> ProbabilityBarRow(bar = bar) }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataBadge(
                        label = stringResource(R.string.label_inference_time),
                        value = "${outcome.inferenceTimeMs} ms",
                        modifier = Modifier.weight(1f).testTag("metric_inference_time"),
                    )
                    MetadataBadge(
                        label = stringResource(R.string.label_model),
                        value = "$modelLabel · $delegateLabel",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProbabilityBarRow(bar: DecisionBarUi) {
    val progress by
        animateFloatAsState(
            targetValue = bar.probability.coerceIn(0f, 1f),
            label = "prob_${bar.label}",
        )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = bar.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (bar.highlighted) FontWeight.Bold else FontWeight.Normal,
            )
            Text(
                text = String.format(Locale.US, "%.1f%%", progress * 100f),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            color =
                if (bar.highlighted) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            drawStopIndicator = {},
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
        )
    }
}

@Composable
private fun InputFieldCard(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    testTag: String,
    minLines: Int = 2,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp, start = 14.dp, end = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = { onValueChange("") },
                        modifier = Modifier.size(30.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = CommonUiR.drawable.ic_close),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
            TextField(
                value = value,
                onValueChange = onValueChange,
                minLines = minLines,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                modifier = Modifier.fillMaxWidth().testTag(testTag),
            )
        }
    }
}

@Composable
private fun ThresholdCard(
    threshold: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.title_threshold),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = String.format(Locale.US, "%.2f", threshold),
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = stringResource(R.string.helper_threshold),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = threshold,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                valueRange = 0.05f..0.95f,
                modifier = Modifier.testTag("threshold_slider"),
            )
        }
    }
}

@Composable
private fun ItemsEditorSection(
    questionType: QuestionType,
    items: List<QuestionItemUi>,
    onAddItem: () -> Unit,
    onUpdateLabel: (Long, String) -> Unit,
    onUpdateDescription: (Long, String) -> Unit,
    onRemoveItem: (Long) -> Unit,
) {
    val isScore = questionType == QuestionType.SCORE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(if (isScore) R.string.title_rubric else R.string.title_options),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(if (isScore) R.string.helper_rubric else R.string.helper_options),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        items.forEachIndexed { idx, item ->
            val badgeText =
                if (isScore) "${idx + 1}" else if (idx < 26) ('A' + idx).toString() else "${idx + 1}"

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                        modifier = Modifier.size(28.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedTextField(
                            value = item.label,
                            onValueChange = { onUpdateLabel(item.id, it) },
                            label = {
                                Text(
                                    stringResource(
                                        if (isScore) R.string.hint_level_label
                                        else R.string.hint_option_key
                                    )
                                )
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = item.description,
                            onValueChange = { onUpdateDescription(item.id, it) },
                            label = { Text(stringResource(R.string.hint_description)) },
                            minLines = 1,
                            maxLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    IconButton(
                        onClick = { onRemoveItem(item.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = CommonUiR.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_remove_item),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = onAddItem,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("add_item_button"),
        ) {
            Text(stringResource(if (isScore) R.string.btn_add_level else R.string.btn_add_option))
        }
    }
}

@Composable
private fun SettingsSheetContent(
    selectedDelegate: DecisionDelegateOption,
    selectedModel: DecisionModelOption,
    isBusy: Boolean,
    onSelectDelegate: (DecisionDelegateOption) -> Unit,
    onSelectModel: (DecisionModelOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val delegates = DecisionDelegateOption.entries
    val models = DecisionModelOption.entries

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
            label = stringResource(R.string.label_delegate),
            options = delegates.map { it.label },
            selectedIndex = delegates.indexOf(selectedDelegate),
            enabled = !isBusy,
            onSelect = { index -> onSelectDelegate(delegates[index]) },
        )

        SegmentedOptionRow(
            label = stringResource(R.string.label_model),
            options = models.map { it.shortLabel },
            selectedIndex = models.indexOf(selectedModel),
            enabled = !isBusy,
            onSelect = { index -> onSelectModel(models[index]) },
        )

        HorizontalDivider()

        Text(
            text = stringResource(R.string.model_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onDismiss,
            modifier =
                Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("settings_done_button"),
        ) {
            Text(stringResource(R.string.action_done))
        }
    }
}
