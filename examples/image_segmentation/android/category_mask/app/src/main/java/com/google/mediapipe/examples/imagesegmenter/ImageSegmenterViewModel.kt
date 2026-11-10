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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SegmenterTab {
    CAMERA,
    GALLERY,
}

enum class SegmentationModel(val index: Int, val displayName: String, val shortLabel: String) {
    DEEPLABV3(ImageSegmenterHelper.MODEL_DEEPLABV3, "DeepLabV3", "DeepLab"),
    HAIR_SEGMENTER(ImageSegmenterHelper.MODEL_HAIR_SEGMENTER, "Hair Segmenter", "Hair"),
    SELFIE_SEGMENTER(ImageSegmenterHelper.MODEL_SELFIE_SEGMENTER, "Selfie Segmenter", "Selfie"),
    SELFIE_MULTICLASS(ImageSegmenterHelper.MODEL_SELFIE_MULTICLASS, "Selfie Multiclass", "Multi"),
}

enum class SegmenterDelegate(val index: Int, val displayName: String) {
    CPU(ImageSegmenterHelper.DELEGATE_CPU, "CPU"),
    GPU(ImageSegmenterHelper.DELEGATE_GPU, "GPU"),
}

data class ImageSegmenterUiState(
    val selectedTab: SegmenterTab = SegmenterTab.CAMERA,
    val model: SegmentationModel = SegmentationModel.DEEPLABV3,
    val delegate: SegmenterDelegate = SegmenterDelegate.CPU,
    val isFrontCamera: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val isSegmentingGallery: Boolean = false,
    val cameraUnavailableMessage: String? = null,
    val liveResult: ImageSegmenterHelper.ResultBundle? = null,
    val galleryBitmap: Bitmap? = null,
    val galleryVideoUri: Uri? = null,
    val galleryResult: ImageSegmenterHelper.ResultBundle? = null,
    val errorMessage: String? = null,
    val errorCode: Int = ImageSegmenterHelper.OTHER_ERROR,
)

/** ViewModel coordinating category mask segmentation off the main thread. */
class ImageSegmenterViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    val cameraAnalyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = cameraAnalyzerExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(ImageSegmenterUiState())
    val uiState: StateFlow<ImageSegmenterUiState> = _uiState.asStateFlow()

    private var liveHelper: ImageSegmenterHelper? = null
    private var galleryJob: Job? = null
    private var videoOverlayJob: Job? = null
    private var lastGallerySampleUsed: Boolean = false
    private var lastGalleryMediaUri: Uri? = null

    private val listener =
        object : ImageSegmenterHelper.SegmenterListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isSegmentingGallery = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }

            override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
                _uiState.update { it.copy(liveResult = resultBundle) }
            }
        }

    fun selectTab(tab: SegmenterTab) {
        if (_uiState.value.selectedTab == tab) return
        videoOverlayJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                liveResult = if (tab != SegmenterTab.CAMERA) null else it.liveResult,
            )
        }
        if (tab == SegmenterTab.GALLERY) {
            releaseLiveSegmenterAsync()
        } else if (_uiState.value.hasCameraPermission) {
            prepareLiveSegmenter()
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        val changed = _uiState.value.hasCameraPermission != granted
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (changed && granted && _uiState.value.selectedTab == SegmenterTab.CAMERA) {
            prepareLiveSegmenter()
        } else if (!granted) {
            releaseLiveSegmenterAsync()
        }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, liveResult = null) }
    }

    fun setCameraUnavailable(message: String?) {
        _uiState.update { it.copy(cameraUnavailableMessage = message) }
    }

    fun selectModel(model: SegmentationModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, liveResult = null, errorMessage = null) }
        reapplySettings()
    }

    fun selectDelegate(delegate: SegmenterDelegate) {
        if (_uiState.value.delegate == delegate) return
        _uiState.update { it.copy(delegate = delegate, errorMessage = null) }
        reapplySettings()
    }

    fun switchToCpuDelegate() {
        selectDelegate(SegmenterDelegate.CPU)
    }

    fun retryInitialization() {
        _uiState.update { it.copy(errorMessage = null) }
        reapplySettings()
    }

    private fun reapplySettings() {
        val state = _uiState.value
        if (state.selectedTab == SegmenterTab.CAMERA && state.hasCameraPermission) {
            prepareLiveSegmenter()
        } else if (state.selectedTab == SegmenterTab.GALLERY) {
            when {
                lastGallerySampleUsed -> segmentSampleImage()
                lastGalleryMediaUri != null -> segmentGalleryUri(lastGalleryMediaUri!!)
            }
        }
    }

    fun prepareLiveSegmenter() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            withContext(engineDispatcher) {
                val existing = liveHelper
                if (existing == null || existing.isClosed) {
                    liveHelper = createHelper(snapshot, RunningMode.LIVE_STREAM, listener)
                } else {
                    existing.currentModel = snapshot.model.index
                    existing.currentDelegate = snapshot.delegate.index
                    existing.runningMode = RunningMode.LIVE_STREAM
                    existing.setupImageSegmenter()
                }
            }
        }
    }

    fun analyzeCameraFrame(imageProxy: ImageProxy) {
        val state = _uiState.value
        if (state.selectedTab != SegmenterTab.CAMERA || !state.hasCameraPermission) {
            imageProxy.close()
            return
        }
        val helper = liveHelper
        if (helper == null || helper.isClosed) {
            imageProxy.close()
            return
        }
        helper.segmentLiveStreamFrame(imageProxy, isFrontCamera = state.isFrontCamera)
    }

    fun releaseLiveSegmenterAsync() {
        cameraAnalyzerExecutor.execute {
            liveHelper?.clearImageSegmenter()
            liveHelper = null
        }
    }

    fun segmentSampleImage() {
        lastGallerySampleUsed = true
        lastGalleryMediaUri = null
        galleryJob?.cancel()
        videoOverlayJob?.cancel()
        val snapshot = _uiState.value
        _uiState.update {
            it.copy(
                isSegmentingGallery = true,
                galleryVideoUri = null,
                errorMessage = null,
            )
        }
        galleryJob =
            viewModelScope.launch {
                val decoded =
                    withContext(engineDispatcher) {
                        runCatching {
                            context.assets.open(ImageSegmenterHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }.getOrNull()
                    }
                if (decoded == null) {
                    _uiState.update {
                        it.copy(
                            isSegmentingGallery = false,
                            errorMessage = "Failed to load bundled sample photo.",
                        )
                    }
                    return@launch
                }
                _uiState.update { it.copy(galleryBitmap = decoded, galleryResult = null) }
                val bundle =
                    withContext(engineDispatcher) {
                        val helper = createHelper(snapshot, RunningMode.IMAGE, listener)
                        try {
                            helper.detectImage(decoded)
                        } finally {
                            helper.clearImageSegmenter()
                        }
                    }
                _uiState.update {
                    it.copy(isSegmentingGallery = false, galleryResult = bundle)
                }
            }
    }

    fun segmentGalleryUri(uri: Uri) {
        lastGallerySampleUsed = false
        lastGalleryMediaUri = uri
        galleryJob?.cancel()
        videoOverlayJob?.cancel()
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val snapshot = _uiState.value

        if (mimeType.startsWith("image/")) {
            _uiState.update {
                it.copy(
                    isSegmentingGallery = true,
                    galleryVideoUri = null,
                    errorMessage = null,
                )
            }
            galleryJob =
                viewModelScope.launch {
                    val bitmap = withContext(engineDispatcher) { decodeUriBitmap(uri) }
                    if (bitmap == null) {
                        _uiState.update {
                            it.copy(
                                isSegmentingGallery = false,
                                errorMessage = "Unable to decode selected image.",
                            )
                        }
                        return@launch
                    }
                    _uiState.update { it.copy(galleryBitmap = bitmap, galleryResult = null) }
                    val bundle =
                        withContext(engineDispatcher) {
                            val helper = createHelper(snapshot, RunningMode.IMAGE, listener)
                            try {
                                helper.detectImage(bitmap)
                            } finally {
                                helper.clearImageSegmenter()
                            }
                        }
                    _uiState.update {
                        it.copy(isSegmentingGallery = false, galleryResult = bundle)
                    }
                }
        } else if (mimeType.startsWith("video/")) {
            _uiState.update {
                it.copy(
                    isSegmentingGallery = true,
                    galleryBitmap = null,
                    galleryVideoUri = uri,
                    galleryResult = null,
                    errorMessage = null,
                )
            }
            galleryJob =
                viewModelScope.launch {
                    val videoBundle =
                        withContext(engineDispatcher) {
                            val helper = createHelper(snapshot, RunningMode.VIDEO, listener)
                            try {
                                helper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                            } finally {
                                helper.clearImageSegmenter()
                            }
                        }
                    if (videoBundle != null && videoBundle.results.isNotEmpty()) {
                        _uiState.update { it.copy(isSegmentingGallery = false) }
                        startVideoOverlayPlayback(videoBundle)
                    } else {
                        _uiState.update {
                            it.copy(
                                isSegmentingGallery = false,
                                errorMessage =
                                    it.errorMessage ?: "Failed to segment selected video.",
                            )
                        }
                    }
                }
        } else {
            _uiState.update { it.copy(errorMessage = "Unsupported media type.") }
        }
    }

    private fun createHelper(
        snapshot: ImageSegmenterUiState,
        runningMode: RunningMode,
        helperListener: ImageSegmenterHelper.SegmenterListener?,
    ): ImageSegmenterHelper =
        ImageSegmenterHelper(
            currentDelegate = snapshot.delegate.index,
            runningMode = runningMode,
            currentModel = snapshot.model.index,
            context = context,
            imageSegmenterListener = helperListener,
        )

    private fun startVideoOverlayPlayback(videoBundle: ImageSegmenterHelper.VideoResultBundle) {
        videoOverlayJob?.cancel()
        videoOverlayJob =
            viewModelScope.launch {
                for (frameBundle in videoBundle.results) {
                    _uiState.update { it.copy(galleryResult = frameBundle) }
                    delay(VIDEO_INTERVAL_MS)
                }
            }
    }

    @Suppress("DEPRECATION")
    private fun decodeUriBitmap(uri: Uri): Bitmap? =
        runCatching {
            val raw =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
            if (raw.config == Bitmap.Config.ARGB_8888) raw
            else raw.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()

    override fun onCleared() {
        super.onCleared()
        videoOverlayJob?.cancel()
        val existing = liveHelper
        liveHelper = null
        cameraAnalyzerExecutor.execute {
            existing?.clearImageSegmenter()
            cameraAnalyzerExecutor.shutdown()
        }
    }

    companion object {
        private const val VIDEO_INTERVAL_MS = 300L

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ImageSegmenterViewModel(appContext.applicationContext) as T
                }
            }
    }
}
