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

package com.google.mediapipe.examples.semanticretriever

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemanticRetrieverScreen(
    uiState: SemanticRetrieverUiState,
    onQueryChange: (String) -> Unit,
    onApplySuggestion: (QuerySuggestion) -> Unit,
    onSearch: () -> Unit,
    onReindexSamples: () -> Unit,
    onClearIndex: () -> Unit,
    onAddCustomPhoto: (Uri) -> Unit,
    onSelectDelegate: (RetrieverDelegateOption) -> Unit,
    onSelectStore: (VectorStoreOption) -> Unit,
    onRetryInitialization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                onAddCustomPhoto(uri)
            }
        }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.app_name),
                subtitle = "${uiState.storeOption.label} · ${uiState.delegateOption.label}",
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

            IndexStatusCard(
                uiState = uiState,
                onReindexSamples = onReindexSamples,
                onClearIndex = onClearIndex,
                onAddCustomPhoto = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )

            SuggestionRow(
                currentQuery = uiState.query,
                onApplySuggestion = { suggestion ->
                    focusManager.clearFocus()
                    onApplySuggestion(suggestion)
                },
            )

            QueryInputCard(
                query = uiState.query,
                onQueryChange = onQueryChange,
            )

            SearchButton(
                uiState = uiState,
                onClick = {
                    focusManager.clearFocus()
                    onSearch()
                },
            )

            if (uiState.lastSearchTimeMs != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataBadge(
                        label = stringResource(R.string.label_search_time),
                        value = "${uiState.lastSearchTimeMs} ms",
                        modifier = Modifier.weight(1f).testTag("metric_search_time"),
                    )
                    MetadataBadge(
                        label = stringResource(R.string.label_indexed_items),
                        value = "${uiState.indexedCount} images",
                        modifier = Modifier.weight(1f).testTag("metric_indexed_count"),
                    )
                    MetadataBadge(
                        label = stringResource(R.string.label_vector_store),
                        value = uiState.storeOption.label,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (uiState.matches.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.label_results),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                uiState.matches.forEachIndexed { rank, match ->
                    MatchResultCard(
                        rank = rank + 1,
                        match = match,
                    )
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.empty_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
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
                selectedStore = uiState.storeOption,
                isBusy = uiState.isBusy,
                onSelectDelegate = onSelectDelegate,
                onSelectStore = onSelectStore,
                onDismiss = { showSettings = false },
            )
        }
    }
}

@Composable
private fun IndexStatusCard(
    uiState: SemanticRetrieverUiState,
    onReindexSamples: () -> Unit,
    onClearIndex: () -> Unit,
    onAddCustomPhoto: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text =
                            if (uiState.isIndexing) {
                                stringResource(
                                    R.string.status_indexing_format,
                                    uiState.indexingProgress,
                                    uiState.indexingTotal.coerceAtLeast(1),
                                )
                            } else {
                                "${uiState.indexedCount} images indexed"
                            },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("indexed_status_text"),
                    )
                    Text(
                        text = "${uiState.storeOption.label} · 768d vector store",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedButton(
                    onClick = onAddCustomPhoto,
                    enabled = !uiState.isBusy,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_photo_button"),
                ) {
                    Text(stringResource(R.string.action_add_photo))
                }
            }

            if (uiState.isIndexing) {
                LinearProgressIndicator(
                    progress = {
                        uiState.indexingProgress.toFloat() / uiState.indexingTotal.coerceAtLeast(1)
                    },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = onReindexSamples,
                        enabled = !uiState.isBusy,
                        modifier = Modifier.testTag("reindex_button"),
                    ) {
                        Text(stringResource(R.string.action_reindex))
                    }
                    if (uiState.indexedCount > 0) {
                        TextButton(
                            onClick = onClearIndex,
                            enabled = !uiState.isBusy,
                            modifier = Modifier.testTag("clear_index_button"),
                        ) {
                            Text(stringResource(R.string.action_clear_index))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    currentQuery: String,
    onApplySuggestion: (QuerySuggestion) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = stringResource(R.string.label_query_suggestions),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DefaultQuerySuggestions.forEach { suggestion ->
                FilterChip(
                    selected = currentQuery.trim().equals(suggestion.query, ignoreCase = true),
                    onClick = { onApplySuggestion(suggestion) },
                    label = { Text(suggestion.label) },
                    modifier = Modifier.testTag("suggestion_${suggestion.id}"),
                )
            }
        }
    }
}

@Composable
private fun QueryInputCard(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = stringResource(R.string.hint_search_query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors =
                    TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                modifier = Modifier.weight(1f).testTag("query_input"),
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(32.dp).testTag("clear_query_button"),
                ) {
                    Icon(
                        painter = painterResource(id = CommonUiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.cd_clear_query),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchButton(uiState: SemanticRetrieverUiState, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = uiState.canSearch,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("search_button"),
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
                    when {
                        uiState.isModelLoading -> stringResource(R.string.status_loading_model)
                        uiState.isIndexing ->
                            stringResource(
                                R.string.status_indexing_format,
                                uiState.indexingProgress,
                                uiState.indexingTotal.coerceAtLeast(1),
                            )
                        else -> stringResource(R.string.status_searching)
                    },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(
                text = stringResource(R.string.btn_search),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MatchResultCard(
    rank: Int,
    match: SearchMatchUi,
) {
    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().testTag("result_card_$rank"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(72.dp),
            ) {
                val bmp = match.thumbnail
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = match.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "#$rank",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "#$rank · ${match.label}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape,
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.3f", match.score),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { match.score.toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    drawStopIndicator = {},
                )
                Text(
                    text = match.sourceTag,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SettingsSheetContent(
    selectedDelegate: RetrieverDelegateOption,
    selectedStore: VectorStoreOption,
    isBusy: Boolean,
    onSelectDelegate: (RetrieverDelegateOption) -> Unit,
    onSelectStore: (VectorStoreOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val delegates = RetrieverDelegateOption.entries
    val stores = VectorStoreOption.entries

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
            label = stringResource(R.string.label_vector_store),
            options = stores.map { it.label },
            selectedIndex = stores.indexOf(selectedStore),
            enabled = !isBusy,
            onSelect = { idx -> onSelectStore(stores[idx]) },
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
                text = stringResource(R.string.settings_note),
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
