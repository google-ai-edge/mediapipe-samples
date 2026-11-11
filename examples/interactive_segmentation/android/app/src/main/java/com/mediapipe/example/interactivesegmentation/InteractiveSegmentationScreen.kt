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

package com.mediapipe.example.interactivesegmentation

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as ComposeStroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import kotlin.math.min
import kotlin.math.roundToInt

private val PositiveColor = Color(0xFF4CAF50)
private val NegativeColor = Color(0xFFE53935)
private val LassoColor = Color(0xFF2196F3)

@Composable
fun InteractiveSegmentationScreen(
    uiState: InteractiveSegmentationUiState,
    onSelectBrushMode: (BrushMode) -> Unit,
    onAddStroke: (DrawnStroke) -> Unit,
    onUndoStroke: () -> Unit,
    onClearStrokes: () -> Unit,
    onTrySample: () -> Unit,
    onPickImage: () -> Unit,
    onTakePhoto: () -> Unit,
    onRetryInitialization: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            MediaPipeTopBar(
                title = stringResource(R.string.app_name),
                subtitle = stringResource(R.string.subtitle_magic_touch),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
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

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("interactive_canvas_card"),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val bitmap = uiState.inputBitmap
                    if (bitmap != null) {
                        InteractiveDrawingCanvas(
                            bitmap = bitmap,
                            maskBitmap = uiState.result?.maskBitmap,
                            brushMode = uiState.brushMode,
                            strokes = uiState.strokes,
                            onStrokeCompleted = onAddStroke,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.baseline_image_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = stringResource(R.string.empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    if (uiState.strokes.isNotEmpty()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                TextButton(
                                    onClick = onUndoStroke,
                                    enabled = !uiState.isSegmenting,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("undo_stroke_button"),
                                ) {
                                    Text(stringResource(R.string.action_undo_stroke), maxLines = 1)
                                }
                                TextButton(
                                    onClick = onClearStrokes,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("clear_strokes_button"),
                                ) {
                                    Text(stringResource(R.string.action_clear_strokes), maxLines = 1)
                                }
                            }
                        }
                    }

                    if (uiState.isSegmenting) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.testTag("segmenting_indicator"),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(stringResource(R.string.status_segmenting))
                            }
                        }
                    }
                }
            }

            if (uiState.inputBitmap != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrushMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.brushMode == mode,
                            onClick = { onSelectBrushMode(mode) },
                            label = {
                                Text(
                                    text = stringResource(mode.labelRes),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                            modifier = Modifier.weight(1f).testTag("brush_${mode.name.lowercase()}"),
                        )
                    }
                }
            }

            if (uiState.result != null) {
                val result = uiState.result
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("metrics_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetadataBadge(
                        label = stringResource(R.string.label_inference_time),
                        value = "${result.inferenceTimeMs} ms",
                        modifier = Modifier.weight(1f).testTag("metric_inference_time"),
                    )
                    MetadataBadge(
                        label = stringResource(R.string.label_coverage),
                        value = "${result.selectedPercent}%",
                        modifier = Modifier.weight(1f).testTag("metric_coverage"),
                    )
                    MetadataBadge(
                        label = stringResource(R.string.label_strokes),
                        value = uiState.strokes.size.toString(),
                        modifier = Modifier.weight(0.8f).testTag("metric_strokes"),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onTrySample,
                    enabled = !uiState.isSegmenting,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).testTag("try_sample_button"),
                ) {
                    Text(
                        text = stringResource(R.string.action_try_sample),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                OutlinedButton(
                    onClick = onTakePhoto,
                    enabled = !uiState.isSegmenting,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    modifier = Modifier.testTag("take_photo_button"),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_camera_alt_24),
                        contentDescription = stringResource(R.string.action_take_photo),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Button(
                    onClick = onPickImage,
                    enabled = !uiState.isSegmenting,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f).testTag("pick_image_button"),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.baseline_image_24),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.action_pick_image),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private data class FittedImageBounds(
    val offsetX: Float,
    val offsetY: Float,
    val drawWidth: Float,
    val drawHeight: Float,
) {
    fun toNormalizedOrNull(px: Float, py: Float): Pair<Float, Float>? {
        if (drawWidth <= 0f || drawHeight <= 0f) return null
        if (px < offsetX || px > offsetX + drawWidth || py < offsetY || py > offsetY + drawHeight) {
            return null
        }
        return ((px - offsetX) / drawWidth).coerceIn(0f, 1f) to
            ((py - offsetY) / drawHeight).coerceIn(0f, 1f)
    }

    fun toCanvasOffset(normX: Float, normY: Float): Offset =
        Offset(offsetX + normX * drawWidth, offsetY + normY * drawHeight)
}

@Composable
private fun InteractiveDrawingCanvas(
    bitmap: Bitmap,
    maskBitmap: Bitmap?,
    brushMode: BrushMode,
    strokes: List<DrawnStroke>,
    onStrokeCompleted: (DrawnStroke) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bgImage = remember(bitmap) { bitmap.asImageBitmap() }
    val maskImage = remember(maskBitmap) { maskBitmap?.asImageBitmap() }
    val activePoints = remember { mutableStateListOf<Pair<Float, Float>>() }
    var activeBrushMode by remember { mutableStateOf(brushMode) }
    var latestBounds by remember { mutableStateOf<FittedImageBounds?>(null) }

    Canvas(
        modifier =
            modifier
                .clipToBounds()
                .testTag("interactive_canvas")
                .pointerInput(bitmap, brushMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val bounds = latestBounds ?: return@awaitEachGesture
                        val startNorm = bounds.toNormalizedOrNull(down.position.x, down.position.y)
                            ?: return@awaitEachGesture
                        down.consume()
                        activeBrushMode = brushMode
                        activePoints.clear()
                        activePoints.add(startNorm)

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            bounds.toNormalizedOrNull(change.position.x, change.position.y)?.let { pt ->
                                activePoints.add(pt)
                            }
                            change.consume()
                        }

                        if (activePoints.isNotEmpty()) {
                            onStrokeCompleted(
                                DrawnStroke(mode = activeBrushMode, points = activePoints.toList())
                            )
                        }
                        activePoints.clear()
                    }
                }
    ) {
        val imgW = bitmap.width.toFloat().coerceAtLeast(1f)
        val imgH = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = min(size.width / imgW, size.height / imgH)
        val drawW = (imgW * scale).coerceAtLeast(1f)
        val drawH = (imgH * scale).coerceAtLeast(1f)
        val offsetX = (size.width - drawW) / 2f
        val offsetY = (size.height - drawH) / 2f
        val bounds = FittedImageBounds(offsetX, offsetY, drawW, drawH)
        latestBounds = bounds

        val dstOffset = IntOffset(offsetX.roundToInt(), offsetY.roundToInt())
        val dstSize = IntSize(drawW.roundToInt().coerceAtLeast(1), drawH.roundToInt().coerceAtLeast(1))

        drawImage(image = bgImage, dstOffset = dstOffset, dstSize = dstSize)
        maskImage?.let {
            drawImage(image = it, dstOffset = dstOffset, dstSize = dstSize)
        }

        strokes.forEach { stroke ->
            drawStrokeOverlay(stroke.mode, stroke.points, bounds)
        }
        if (activePoints.isNotEmpty()) {
            drawStrokeOverlay(activeBrushMode, activePoints, bounds)
        }
    }
}

private fun DrawScope.drawStrokeOverlay(
    mode: BrushMode,
    points: List<Pair<Float, Float>>,
    bounds: FittedImageBounds,
) {
    if (points.isEmpty()) return
    val color =
        when (mode) {
            BrushMode.POSITIVE -> PositiveColor
            BrushMode.NEGATIVE -> NegativeColor
            BrushMode.LASSO -> LassoColor
        }

    if (points.size == 1) {
        val center = bounds.toCanvasOffset(points[0].first, points[0].second)
        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = center)
        drawCircle(color = color, radius = 6.dp.toPx(), center = center)
        return
    }

    val path = Path()
    val first = bounds.toCanvasOffset(points[0].first, points[0].second)
    path.moveTo(first.x, first.y)
    for (i in 1 until points.size) {
        val pt = bounds.toCanvasOffset(points[i].first, points[i].second)
        path.lineTo(pt.x, pt.y)
    }
    if (mode == BrushMode.LASSO) {
        val fillPath = Path().apply {
            addPath(path)
            close()
        }
        drawPath(path = fillPath, color = LassoColor.copy(alpha = 0.16f))
    }
    drawPath(
        path = path,
        color = color.copy(alpha = 0.92f),
        style = ComposeStroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
