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

package com.google.mediapipe.examples.imagesegmenter

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.widget.VideoView
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mediapipe.examples.common.ui.MediaPipeTopBar
import com.google.mediapipe.examples.common.ui.MetadataBadge
import com.google.mediapipe.examples.common.ui.SegmentedOptionRow
import com.google.mediapipe.examples.common.ui.StatusErrorBanner
import java.util.concurrent.ExecutorService
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageSegmenterScreen(
    uiState: ImageSegmenterUiState,
    analyzerExecutor: ExecutorService,
    onSelectTab: (SegmenterTab) -> Unit,
    onRequestCameraPermission: () -> Unit,
    onFlipCamera: () -> Unit,
    onCameraFrame: (ImageProxy) -> Unit,
    onCameraUnavailable: (String?) -> Unit,
    onPickMedia: () -> Unit,
    onTrySample: () -> Unit,
    onSelectModel: (SegmentationModel) -> Unit,
    onSelectDelegate: (SegmenterDelegate) -> Unit,
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
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.selectedTab == SegmenterTab.CAMERA,
                    onClick = { onSelectTab(SegmenterTab.CAMERA) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_photo_camera_24),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.menu_camera)) },
                    modifier = Modifier.testTag("tab_camera"),
                )
                NavigationBarItem(
                    selected = uiState.selectedTab == SegmenterTab.GALLERY,
                    onClick = { onSelectTab(SegmenterTab.GALLERY) },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_photo_library_24),
                            contentDescription = null,
                        )
                    },
                    label = { Text(stringResource(R.string.menu_gallery)) },
                    modifier = Modifier.testTag("tab_gallery"),
                )
            }
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
                    val isGpuError = uiState.errorCode == ImageSegmenterHelper.GPU_ERROR
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

            when (uiState.selectedTab) {
                SegmenterTab.CAMERA -> {
                    CameraSegmentationPane(
                        uiState = uiState,
                        analyzerExecutor = analyzerExecutor,
                        onRequestCameraPermission = onRequestCameraPermission,
                        onFlipCamera = onFlipCamera,
                        onCameraFrame = onCameraFrame,
                        onCameraUnavailable = onCameraUnavailable,
                        modifier = Modifier.weight(1f),
                    )
                }
                SegmenterTab.GALLERY -> {
                    GallerySegmentationPane(
                        uiState = uiState,
                        onPickMedia = onPickMedia,
                        onTrySample = onTrySample,
                        modifier = Modifier.weight(1f),
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
private fun CameraSegmentationPane(
    uiState: ImageSegmenterUiState,
    analyzerExecutor: ExecutorService,
    onRequestCameraPermission: () -> Unit,
    onFlipCamera: () -> Unit,
    onCameraFrame: (ImageProxy) -> Unit,
    onCameraUnavailable: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!uiState.hasCameraPermission) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("camera_permission_card"),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_photo_camera_24),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.camera_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.camera_permission_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(
                        onClick = onRequestCameraPermission,
                        modifier = Modifier.testTag("grant_camera_button"),
                    ) {
                        Text(stringResource(R.string.action_grant_camera))
                    }
                }
            }
        } else {
            Surface(
                color = Color.Black,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(24.dp)),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    CameraPreviewWithAnalyzer(
                        isFrontCamera = uiState.isFrontCamera,
                        analyzerExecutor = analyzerExecutor,
                        onFrame = onCameraFrame,
                        onCameraUnavailable = onCameraUnavailable,
                    )
                    uiState.liveResult?.let { result ->
                        MaskOverlayCanvas(
                            backgroundBitmap = null,
                            maskBitmap = result.maskBitmap,
                            targetWidth = result.inputWidth,
                            targetHeight = result.inputHeight,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    uiState.cameraUnavailableMessage?.let { msg ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.92f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataBadge(
                    label = stringResource(R.string.label_inference_time),
                    value = uiState.liveResult?.let { "${it.inferenceTime} ms" } ?: "—",
                    modifier = Modifier.weight(1f).testTag("camera_inference_time"),
                )
                MetadataBadge(
                    label = stringResource(R.string.label_segments),
                    value = uiState.liveResult?.detectedCategories?.size?.toString() ?: "0",
                    modifier = Modifier.weight(0.8f).testTag("camera_segment_count"),
                )
                FilledTonalButton(
                    onClick = onFlipCamera,
                    modifier = Modifier.testTag("flip_camera_button"),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_flip_camera_24),
                        contentDescription = stringResource(R.string.action_switch_camera),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (uiState.isFrontCamera) R.string.camera_lens_front
                            else R.string.camera_lens_back
                        )
                    )
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPreviewWithAnalyzer(
    isFrontCamera: Boolean,
    analyzerExecutor: ExecutorService,
    onFrame: (ImageProxy) -> Unit,
    onCameraUnavailable: (String?) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            runCatching { cameraProvider = future.get() }
                .onFailure { onCameraUnavailable("Camera unavailable: ${it.message}") }
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose { cameraProvider?.unbindAll() }
    }

    LaunchedEffect(cameraProvider, isFrontCamera) {
        val provider = cameraProvider ?: return@LaunchedEffect
        val preferredSelector =
            if (isFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA
            else CameraSelector.DEFAULT_BACK_CAMERA
        val selector =
            when {
                runCatching { provider.hasCamera(preferredSelector) }.getOrDefault(false) ->
                    preferredSelector
                runCatching { provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }
                    .getOrDefault(false) -> CameraSelector.DEFAULT_BACK_CAMERA
                runCatching { provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }
                    .getOrDefault(false) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> {
                    onCameraUnavailable("No camera hardware available on this device.")
                    return@LaunchedEffect
                }
            }

        onCameraUnavailable(null)
        provider.unbindAll()

        @Suppress("DEPRECATION")
        val preview =
            Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        @Suppress("DEPRECATION")
        val analysis =
            ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { it.setAnalyzer(analyzerExecutor, onFrame) }

        try {
            provider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
        } catch (exc: Exception) {
            Log.e(ImageSegmenterHelper.TAG, "CameraX bind failed", exc)
            onCameraUnavailable("Failed to open camera: ${exc.message}")
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize().testTag("camera_preview"),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GallerySegmentationPane(
    uiState: ImageSegmenterUiState,
    onPickMedia: () -> Unit,
    onTrySample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.weight(1f).fillMaxWidth().testTag("detection_canvas"),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when {
                    uiState.galleryBitmap != null -> {
                        MaskOverlayCanvas(
                            backgroundBitmap = uiState.galleryBitmap,
                            maskBitmap = uiState.galleryResult?.maskBitmap,
                            targetWidth = uiState.galleryBitmap.width,
                            targetHeight = uiState.galleryBitmap.height,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    uiState.galleryVideoUri != null -> {
                        VideoPlaybackView(
                            uri = uiState.galleryVideoUri,
                            modifier = Modifier.fillMaxSize(),
                        )
                        uiState.galleryResult?.let { result ->
                            MaskOverlayCanvas(
                                backgroundBitmap = null,
                                maskBitmap = result.maskBitmap,
                                targetWidth = result.inputWidth,
                                targetHeight = result.inputHeight,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    else -> {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                painter =
                                    painterResource(id = R.drawable.ic_baseline_photo_library_24),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                text = stringResource(R.string.gallery_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.gallery_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                if (uiState.isSegmentingGallery) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Text(stringResource(R.string.status_segmenting))
                        }
                    }
                }
            }
        }

        if (uiState.galleryResult != null) {
            val result = uiState.galleryResult
            if (result.detectedCategories.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().testTag("legend_row"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    result.detectedCategories.forEach { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier =
                                        Modifier.size(10.dp)
                                            .clip(CircleShape)
                                            .background(Color(item.argbColor))
                                )
                                Text(
                                    text = item.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().testTag("gallery_metrics_row"),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetadataBadge(
                    label = stringResource(R.string.label_inference_time),
                    value = "${result.inferenceTime} ms",
                    modifier = Modifier.weight(1f).testTag("metric_inference_time"),
                )
                MetadataBadge(
                    label = stringResource(R.string.label_segments),
                    value = result.detectedCategories.size.toString(),
                    modifier = Modifier.weight(0.8f).testTag("metric_segments"),
                )
                MetadataBadge(
                    label = stringResource(R.string.label_model),
                    value = uiState.model.shortLabel,
                    modifier = Modifier.weight(1f).testTag("metric_model"),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onTrySample,
                enabled = !uiState.isSegmentingGallery,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("try_sample_button"),
            ) {
                Text(stringResource(R.string.action_try_sample))
            }
            Button(
                onClick = onPickMedia,
                enabled = !uiState.isSegmentingGallery,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f).testTag("pick_media_button"),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_add_24),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.action_pick_media))
            }
        }
    }
}

@Composable
private fun VideoPlaybackView(uri: Uri, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { context ->
            VideoView(context).apply {
                setVideoURI(uri)
                setOnPreparedListener { mp ->
                    mp.setVolume(0f, 0f)
                    start()
                }
            }
        },
        update = { view -> view.setVideoURI(uri) },
        modifier = modifier,
    )
}

@Composable
private fun MaskOverlayCanvas(
    backgroundBitmap: Bitmap?,
    maskBitmap: Bitmap?,
    targetWidth: Int,
    targetHeight: Int,
    modifier: Modifier = Modifier,
) {
    val bgImage = remember(backgroundBitmap) { backgroundBitmap?.asImageBitmap() }
    val maskImage = remember(maskBitmap) { maskBitmap?.asImageBitmap() }

    Canvas(modifier = modifier.testTag("segmentation_overlay")) {
        val w = targetWidth.toFloat().coerceAtLeast(1f)
        val h = targetHeight.toFloat().coerceAtLeast(1f)
        val scale = min(size.width / w, size.height / h)
        val drawW = (w * scale).roundToInt().coerceAtLeast(1)
        val drawH = (h * scale).roundToInt().coerceAtLeast(1)
        val offsetX = ((size.width - drawW) / 2f).roundToInt()
        val offsetY = ((size.height - drawH) / 2f).roundToInt()
        val dstOffset = IntOffset(offsetX, offsetY)
        val dstSize = IntSize(drawW, drawH)

        bgImage?.let {
            drawImage(image = it, dstOffset = dstOffset, dstSize = dstSize)
        }
        maskImage?.let {
            drawImage(image = it, dstOffset = dstOffset, dstSize = dstSize)
        }
    }
}

@Composable
private fun SettingsSheetContent(
    selectedModel: SegmentationModel,
    selectedDelegate: SegmenterDelegate,
    onSelectModel: (SegmentationModel) -> Unit,
    onSelectDelegate: (SegmenterDelegate) -> Unit,
    onDismiss: () -> Unit,
) {
    val models = SegmentationModel.entries
    val delegates = SegmenterDelegate.entries

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
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
            onSelect = { idx -> onSelectModel(models[idx]) },
        )

        SegmentedOptionRow(
            label = stringResource(R.string.label_delegate),
            options = delegates.map { it.displayName },
            selectedIndex = delegates.indexOf(selectedDelegate),
            onSelect = { idx -> onSelectDelegate(delegates[idx]) },
        )

        HorizontalDivider()

        Button(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("settings_done_button"),
        ) {
            Text(stringResource(R.string.action_done))
        }
    }
}
