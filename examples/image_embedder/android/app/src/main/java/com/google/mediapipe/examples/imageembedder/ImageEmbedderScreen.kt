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

package com.google.mediapipe.examples.imageembedder

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.R as CommonUiR
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImageEmbedderScreen(
    uiState: ImageEmbedderUiState,
    onPickImage: (Int) -> Unit,
    onSelectPreset: (ImageSamplePreset) -> Unit,
    onSwapImages: () -> Unit,
    onCompare: () -> Unit,
    onSelectModel: (ImageEmbedderModel) -> Unit,
    onSelectDelegate: (EmbedderDelegate) -> Unit,
    onUseCpuDelegate: () -> Unit,
    onRetryInitialization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.app_name),
                subtitle = "${uiState.model.displayName} · ${uiState.delegate.displayName}",
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
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                uiState.errorMessage?.let { message ->
                    val isGpuError = uiState.errorCode == ImageEmbedderHelper.GPU_ERROR
                    StatusErrorBanner(
                        message = message,
                        actionLabel =
                            stringResource(
                                if (isGpuError) R.string.action_use_cpu else R.string.action_retry
                            ),
                        onAction = if (isGpuError) onUseCpuDelegate else onRetryInitialization,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Sample pairs",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ImageSamplePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = false,
                            onClick = { onSelectPreset(preset) },
                            label = { Text(preset.label) },
                            modifier = Modifier.testTag("preset_${preset.id}"),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ImageSlotCard(
                    slotTitle = stringResource(R.string.label_image_one),
                    imageLabel = uiState.firstLabel,
                    bitmap = uiState.firstBitmap,
                    onClick = { onPickImage(1) },
                    modifier = Modifier.weight(1f).testTag("image_slot_1"),
                )
                FilledTonalIconButton(
                    onClick = onSwapImages,
                    modifier = Modifier.size(36.dp).testTag("swap_button"),
                ) {
                    Icon(
                        painter = painterResource(id = CommonUiR.drawable.ic_swap_vert),
                        contentDescription = stringResource(R.string.cd_swap_images),
                        modifier = Modifier.size(18.dp),
                    )
                }
                ImageSlotCard(
                    slotTitle = stringResource(R.string.label_image_two),
                    imageLabel = uiState.secondLabel,
                    bitmap = uiState.secondBitmap,
                    onClick = { onPickImage(2) },
                    modifier = Modifier.weight(1f).testTag("image_slot_2"),
                )
            }

            Button(
                onClick = onCompare,
                enabled =
                    uiState.isModelReady &&
                        !uiState.isLoadingModel &&
                        !uiState.isComparing &&
                        uiState.firstBitmap != null &&
                        uiState.secondBitmap != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("compare_button"),
            ) {
                if (uiState.isLoadingModel || uiState.isComparing) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        stringResource(
                            if (uiState.isLoadingModel) R.string.status_loading_model
                            else R.string.status_comparing
                        )
                    )
                } else {
                    Text(
                        text = stringResource(R.string.action_compare),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.result != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                uiState.result?.let { res ->
                    ImageSimilarityResultCard(
                        result = res,
                        model = uiState.model,
                        delegate = uiState.delegate,
                    )
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            modifier = Modifier.testTag("settings_sheet"),
        ) {
            SettingsSheetContent(
                selectedModel = uiState.model,
                selectedDelegate = uiState.delegate,
                onSelectModel = onSelectModel,
                onSelectDelegate = onSelectDelegate,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun ImageSlotCard(
    slotTitle: String,
    imageLabel: String,
    bitmap: Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = slotTitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = imageLabel,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = stringResource(R.string.hint_pick_image),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = imageLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ImageSimilarityResultCard(
    result: ImageComparisonResult,
    model: ImageEmbedderModel,
    delegate: EmbedderDelegate,
) {
    val similarityClamped = result.similarity.toFloat().coerceIn(0f, 1f)
    val similarityPct = (similarityClamped * 100f).coerceIn(0f, 100f)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag("result_card"),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = stringResource(R.string.label_similarity),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = String.format(Locale.US, "%.2f", result.similarity),
                    style = MaterialTheme.typography.headlineMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("similarity_score"),
                )
            }

            LinearProgressIndicator(
                progress = { similarityPct / 100f },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round,
            )

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
                    label = stringResource(R.string.label_model),
                    value = model.shortLabel,
                    modifier = Modifier.weight(1f).testTag("metric_model"),
                )
                MetadataBadge(
                    label = stringResource(R.string.label_delegate),
                    value = delegate.displayName,
                    modifier = Modifier.weight(0.9f),
                )
            }
        }
    }
}

@Composable
private fun SettingsSheetContent(
    selectedModel: ImageEmbedderModel,
    selectedDelegate: EmbedderDelegate,
    onSelectModel: (ImageEmbedderModel) -> Unit,
    onSelectDelegate: (EmbedderDelegate) -> Unit,
    onDismiss: () -> Unit,
) {
    val models = ImageEmbedderModel.entries
    val delegates = EmbedderDelegate.entries

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
            label = stringResource(R.string.label_model),
            options = models.map { it.shortLabel },
            selectedIndex = models.indexOf(selectedModel),
            onSelect = { index -> onSelectModel(models[index]) },
        )

        HorizontalDivider()

        SegmentedOptionRow(
            label = stringResource(R.string.label_delegate),
            options = delegates.map { it.displayName },
            selectedIndex = delegates.indexOf(selectedDelegate),
            onSelect = { index -> onSelectDelegate(delegates[index]) },
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
