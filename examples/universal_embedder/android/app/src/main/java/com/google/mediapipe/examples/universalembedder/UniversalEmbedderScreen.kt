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

package com.google.mediapipe.examples.universalembedder

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.R as CommonUiR
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UniversalEmbedderScreen(
    uiState: UniversalEmbedderUiState,
    onSelectPreset: (MultimodalPreset) -> Unit,
    onSetSlotMode: (Boolean, EmbedInputMode) -> Unit,
    onUpdateSlotText: (Boolean, String) -> Unit,
    onSelectSamplePhoto: (Boolean, SamplePhoto) -> Unit,
    onCustomImagePicked: (Boolean, Uri) -> Unit,
    onSwapSlots: () -> Unit,
    onCompare: () -> Unit,
    onSelectDelegate: (UniversalDelegateOption) -> Unit,
    onRetryInitialization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var pickingFirstSlot by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                onCustomImagePicked(pickingFirstSlot, uri)
            }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.app_name),
                subtitle = "Gemma 2 440M · ${uiState.delegateOption.label}",
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
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

            if (uiState.noticeMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("delegate_notice"),
                ) {
                    Text(
                        text = uiState.noticeMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }

            PresetRow(
                firstSlot = uiState.firstSlot,
                secondSlot = uiState.secondSlot,
                onSelectPreset = { preset ->
                    focusManager.clearFocus()
                    onSelectPreset(preset)
                },
            )

            InputSlotCard(
                label = stringResource(R.string.label_item_a),
                slot = uiState.firstSlot,
                tagPrefix = "slot_a",
                onSelectMode = { mode -> onSetSlotMode(true, mode) },
                onTextChange = { text -> onUpdateSlotText(true, text) },
                onSelectPhoto = { photo -> onSelectSamplePhoto(true, photo) },
                onPickCustomImage = {
                    pickingFirstSlot = true
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )

            SwapDividerRow(onSwap = onSwapSlots)

            InputSlotCard(
                label = stringResource(R.string.label_item_b),
                slot = uiState.secondSlot,
                tagPrefix = "slot_b",
                onSelectMode = { mode -> onSetSlotMode(false, mode) },
                onTextChange = { text -> onUpdateSlotText(false, text) },
                onSelectPhoto = { photo -> onSelectSamplePhoto(false, photo) },
                onPickCustomImage = {
                    pickingFirstSlot = false
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )

            CompareButton(
                uiState = uiState,
                onClick = {
                    focusManager.clearFocus()
                    onCompare()
                },
            )

            AnimatedVisibility(
                visible = uiState.result != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                uiState.result?.let { result ->
                    SimilarityResultCard(
                        result = result,
                        delegateName = uiState.delegateOption.label,
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
                isBusy = uiState.isBusy,
                onSelectDelegate = onSelectDelegate,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun PresetRow(
    firstSlot: EmbedSlotUi,
    secondSlot: EmbedSlotUi,
    onSelectPreset: (MultimodalPreset) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.label_presets),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DefaultMultimodalPresets.forEach { preset ->
                val selected =
                    firstSlot.mode == preset.firstMode &&
                        secondSlot.mode == preset.secondMode &&
                        (firstSlot.mode == EmbedInputMode.IMAGE &&
                            firstSlot.samplePhoto.id == preset.firstPhoto.id ||
                            firstSlot.text == preset.firstText) &&
                        (secondSlot.mode == EmbedInputMode.IMAGE &&
                            secondSlot.samplePhoto.id == preset.secondPhoto.id ||
                            secondSlot.text == preset.secondText)
                FilterChip(
                    selected = selected,
                    onClick = { onSelectPreset(preset) },
                    label = { Text(preset.label) },
                    modifier = Modifier.testTag("preset_${preset.id}"),
                )
            }
        }
    }
}

@Composable
private fun InputSlotCard(
    label: String,
    slot: EmbedSlotUi,
    tagPrefix: String,
    onSelectMode: (EmbedInputMode) -> Unit,
    onTextChange: (String) -> Unit,
    onSelectPhoto: (SamplePhoto) -> Unit,
    onPickCustomImage: () -> Unit,
) {
    val modes = EmbedInputMode.entries

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )

                SingleChoiceSegmentedButtonRow {
                    modes.forEachIndexed { idx, mode ->
                        SegmentedButton(
                            selected = slot.mode == mode,
                            onClick = { onSelectMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = idx, count = modes.size),
                            modifier = Modifier.testTag("${tagPrefix}_mode_${mode.name.lowercase()}"),
                        ) {
                            Text(mode.label)
                        }
                    }
                }
            }

            if (slot.mode == EmbedInputMode.TEXT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    TextField(
                        value = slot.text,
                        onValueChange = onTextChange,
                        placeholder = {
                            Text(
                                text = stringResource(R.string.hint_text_input),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            )
                        },
                        minLines = 2,
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        modifier = Modifier.weight(1f).testTag("${tagPrefix}_text"),
                    )
                    if (slot.text.isNotEmpty()) {
                        IconButton(
                            onClick = { onTextChange("") },
                            modifier = Modifier.size(32.dp).testTag("${tagPrefix}_clear"),
                        ) {
                            Icon(
                                painter = painterResource(id = CommonUiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.cd_clear_text),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier.size(112.dp),
                    ) {
                        val bmp = slot.activeBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = slot.samplePhoto.label,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().testTag("${tagPrefix}_image"),
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text =
                                if (slot.customBitmap != null) "Selected photo"
                                else slot.samplePhoto.label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        OutlinedButton(
                            onClick = onPickCustomImage,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("${tagPrefix}_pick_photo"),
                        ) {
                            Text(stringResource(R.string.action_pick_photo))
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.label_sample_images),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BundledSamplePhotos.forEach { photo ->
                        val selected = slot.customBitmap == null && slot.samplePhoto.id == photo.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color =
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                            border =
                                if (selected) {
                                    BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                } else {
                                    null
                                },
                            modifier =
                                Modifier.clickable { onSelectPhoto(photo) }
                                    .testTag("${tagPrefix}_sample_${photo.id}"),
                        ) {
                            Text(
                                text = photo.label,
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                    if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwapDividerRow(onSwap: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        FilledTonalIconButton(
            onClick = onSwap,
            modifier = Modifier.size(36.dp).testTag("swap_button"),
        ) {
            Icon(
                painter = painterResource(id = CommonUiR.drawable.ic_swap_vert),
                contentDescription = stringResource(R.string.cd_swap_items),
                modifier = Modifier.size(20.dp),
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun CompareButton(uiState: UniversalEmbedderUiState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = uiState.canCompare,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("compare_button"),
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
                        else R.string.status_comparing
                    ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(
                text = stringResource(R.string.btn_compare),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SimilarityResultCard(
    result: UniversalEmbedderHelper.ResultBundle,
    delegateName: String,
) {
    val barTarget = result.similarity.toFloat().coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(targetValue = barTarget, label = "similarityBar")

    val (qualitativeLabel, badgeContainer, badgeContent) =
        when {
            result.similarity >= 0.55 ->
                Triple(
                    stringResource(R.string.similarity_very_high),
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
            result.similarity >= 0.30 ->
                Triple(
                    stringResource(R.string.similarity_moderate),
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
            else ->
                Triple(
                    stringResource(R.string.similarity_low),
                    MaterialTheme.colorScheme.surfaceVariant,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
        }

    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth().testTag("result_card"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.tv_similarity),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Surface(
                    color = badgeContainer,
                    contentColor = badgeContent,
                    shape = CircleShape,
                ) {
                    Text(
                        text = qualitativeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = String.format(Locale.US, "%.3f", result.similarity),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("similarity_score"),
                )
                Text(
                    text = "${(result.similarity.coerceIn(-1.0, 1.0) * 100).roundToInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetadataBadge(
                    label = stringResource(R.string.label_inference_time),
                    value = "${result.inferenceTime} ms",
                    modifier = Modifier.weight(1f).testTag("metric_inference_time"),
                )
                if (result.dimensions > 0) {
                    MetadataBadge(
                        label = stringResource(R.string.label_dimensions),
                        value = "${result.dimensions}d",
                        modifier = Modifier.weight(1f).testTag("metric_dimensions"),
                    )
                }
                MetadataBadge(
                    label = stringResource(R.string.label_delegate),
                    value = delegateName,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
    }
}

@Composable
private fun SettingsSheetContent(
    selectedDelegate: UniversalDelegateOption,
    isBusy: Boolean,
    onSelectDelegate: (UniversalDelegateOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val delegates = UniversalDelegateOption.entries

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )

        SegmentedOptionRow(
            label = stringResource(R.string.label_delegate),
            options = delegates.map { it.label },
            selectedIndex = delegates.indexOf(selectedDelegate),
            enabled = !isBusy,
            onSelect = { idx -> onSelectDelegate(delegates[idx]) },
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.delegate_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            )
        }

        FilledTonalButton(
            onClick = onDismiss,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().testTag("settings_done_button"),
        ) {
            Text(text = stringResource(R.string.action_done))
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}
