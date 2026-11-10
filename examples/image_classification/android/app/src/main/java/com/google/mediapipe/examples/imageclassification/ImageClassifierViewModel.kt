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

package com.google.mediapipe.examples.imageclassification

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

enum class ClassifierTab {
    CAMERA,
    GALLERY,
}

enum class ClassifierModel(val index: Int, val displayName: String, val shortLabel: String) {
    LITE0(ImageClassifierHelper.MODEL_EFFICIENTNETV0, "EfficientNet Lite0", "Lite0"),
    LITE2(ImageClassifierHelper.MODEL_EFFICIENTNETV2, "EfficientNet Lite2", "Lite2"),
}

enum class ClassifierDelegate(val index: Int, val displayName: String) {
    CPU(ImageClassifierHelper.DELEGATE_CPU, "CPU"),
    GPU(ImageClassifierHelper.DELEGATE_GPU, "GPU"),
}

data class ClassificationEntry(
    val label: String,
    val score: Float,
)

data class ClassificationFrame(
    val categories: List<ClassificationEntry>,
    val inferenceTimeMs: Long,
)

data class ImageClassifierUiState(
    val selectedTab: ClassifierTab = ClassifierTab.CAMERA,
    val model: ClassifierModel = ClassifierModel.LITE0,
    val delegate: ClassifierDelegate = ClassifierDelegate.CPU,
    val threshold: Float = ImageClassifierHelper.THRESHOLD_DEFAULT,
    val maxResults: Int = ImageClassifierHelper.MAX_RESULTS_DEFAULT,
    val isFrontCamera: Boolean = false,
    val hasCameraPermission: Boolean = false,
    val isClassifyingGallery: Boolean = false,
    val cameraUnavailableMessage: String? = null,
    val liveResult: ClassificationFrame? = null,
    val galleryBitmap: Bitmap? = null,
    val galleryVideoUri: Uri? = null,
    val galleryResult: ClassificationFrame? = null,
    val errorMessage: String? = null,
    val errorCode: Int = ImageClassifierHelper.OTHER_ERROR,
)

/** ViewModel that serializes MediaPipe image classification off the main thread and exposes UI state. */
class ImageClassifierViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    val cameraAnalyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = cameraAnalyzerExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(ImageClassifierUiState())
    val uiState: StateFlow<ImageClassifierUiState> = _uiState.asStateFlow()

    private var liveHelper: ImageClassifierHelper? = null
    private var galleryJob: Job? = null
    private var videoPlaybackJob: Job? = null
    private var lastGallerySampleUsed: Boolean = false
    private var lastGalleryMediaUri: Uri? = null

    private val listener =
        object : ImageClassifierHelper.ClassifierListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isClassifyingGallery = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }

            override fun onResults(resultBundle: ImageClassifierHelper.ResultBundle) {
                val frame = toClassificationFrame(resultBundle, frameIndex = 0)
                _uiState.update { it.copy(liveResult = frame) }
            }
        }

    fun selectTab(tab: ClassifierTab) {
        if (_uiState.value.selectedTab == tab) return
        videoPlaybackJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                liveResult = if (tab != ClassifierTab.CAMERA) null else it.liveResult,
            )
        }
        if (tab == ClassifierTab.GALLERY) {
            releaseLiveClassifierAsync()
        } else if (_uiState.value.hasCameraPermission) {
            prepareLiveClassifier()
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        val changed = _uiState.value.hasCameraPermission != granted
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (changed && granted && _uiState.value.selectedTab == ClassifierTab.CAMERA) {
            prepareLiveClassifier()
        } else if (!granted) {
            releaseLiveClassifierAsync()
        }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, liveResult = null) }
    }

    fun setCameraUnavailable(message: String?) {
        _uiState.update { it.copy(cameraUnavailableMessage = message) }
    }

    fun selectModel(model: ClassifierModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, liveResult = null, errorMessage = null) }
        reapplySettings()
    }

    fun selectDelegate(delegate: ClassifierDelegate) {
        if (_uiState.value.delegate == delegate) return
        _uiState.update { it.copy(delegate = delegate, liveResult = null, errorMessage = null) }
        reapplySettings()
    }

    fun selectMaxResults(maxResults: Int) {
        val clamped = maxResults.coerceIn(1, 3)
        if (_uiState.value.maxResults == clamped) return
        _uiState.update { it.copy(maxResults = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun updateThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.threshold == clamped) return
        _uiState.update { it.copy(threshold = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun switchToCpuDelegate() {
        selectDelegate(ClassifierDelegate.CPU)
    }

    fun retryInitialization() {
        _uiState.update { it.copy(errorMessage = null) }
        reapplySettings()
    }

    private fun reapplySettings() {
        val state = _uiState.value
        if (state.selectedTab == ClassifierTab.CAMERA && state.hasCameraPermission) {
            prepareLiveClassifier()
        } else if (state.selectedTab == ClassifierTab.GALLERY) {
            when {
                lastGallerySampleUsed -> classifySampleImage()
                lastGalleryMediaUri != null -> classifyGalleryUri(lastGalleryMediaUri!!)
            }
        }
    }

    fun prepareLiveClassifier() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            withContext(engineDispatcher) {
                val existing = liveHelper
                if (existing == null || existing.isClosed()) {
                    liveHelper =
                        ImageClassifierHelper(
                            threshold = snapshot.threshold,
                            maxResults = snapshot.maxResults,
                            currentDelegate = snapshot.delegate.index,
                            currentModel = snapshot.model.index,
                            runningMode = RunningMode.LIVE_STREAM,
                            context = context,
                            imageClassifierListener = listener,
                        )
                } else {
                    existing.threshold = snapshot.threshold
                    existing.maxResults = snapshot.maxResults
                    existing.currentDelegate = snapshot.delegate.index
                    existing.currentModel = snapshot.model.index
                    existing.runningMode = RunningMode.LIVE_STREAM
                    existing.setupImageClassifier()
                }
            }
        }
    }

    fun analyzeCameraFrame(imageProxy: ImageProxy) {
        val state = _uiState.value
        if (state.selectedTab != ClassifierTab.CAMERA || !state.hasCameraPermission) {
            imageProxy.close()
            return
        }
        val helper = liveHelper
        if (helper == null || helper.isClosed()) {
            imageProxy.close()
            return
        }
        helper.classifyLiveStreamFrame(imageProxy)
    }

    fun releaseLiveClassifierAsync() {
        cameraAnalyzerExecutor.execute {
            liveHelper?.clearImageClassifier()
            liveHelper = null
        }
    }

    fun classifySampleImage() {
        lastGallerySampleUsed = true
        lastGalleryMediaUri = null
        galleryJob?.cancel()
        videoPlaybackJob?.cancel()
        val snapshot = _uiState.value
        _uiState.update {
            it.copy(
                isClassifyingGallery = true,
                galleryVideoUri = null,
                galleryResult = null,
                errorMessage = null,
            )
        }
        galleryJob =
            viewModelScope.launch {
                val output =
                    withContext(engineDispatcher) {
                        val bitmap =
                            context.assets.open(ImageClassifierHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            } ?: return@withContext null
                        val helper =
                            ImageClassifierHelper(
                                threshold = snapshot.threshold,
                                maxResults = snapshot.maxResults,
                                currentDelegate = snapshot.delegate.index,
                                currentModel = snapshot.model.index,
                                runningMode = RunningMode.IMAGE,
                                context = context,
                                imageClassifierListener = listener,
                            )
                        try {
                            val bundle = helper.classifyImage(bitmap)
                            bitmap to bundle?.let { toClassificationFrame(it, frameIndex = 0) }
                        } finally {
                            helper.clearImageClassifier()
                        }
                    }
                if (output != null) {
                    _uiState.update { state ->
                        state.copy(
                            isClassifyingGallery = false,
                            galleryBitmap = output.first,
                            galleryResult = output.second,
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isClassifyingGallery = false,
                            errorMessage = state.errorMessage ?: "Failed to load sample image.",
                        )
                    }
                }
            }
    }

    fun classifyGalleryUri(uri: Uri) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        lastGallerySampleUsed = false
        lastGalleryMediaUri = uri
        galleryJob?.cancel()
        videoPlaybackJob?.cancel()
        val snapshot = _uiState.value

        if (mime.startsWith("image")) {
            _uiState.update {
                it.copy(
                    isClassifyingGallery = true,
                    galleryVideoUri = null,
                    galleryResult = null,
                    errorMessage = null,
                )
            }
            galleryJob =
                viewModelScope.launch {
                    val output =
                        withContext(engineDispatcher) {
                            val bitmap = decodeUriBitmap(uri) ?: return@withContext null
                            val helper =
                                ImageClassifierHelper(
                                    threshold = snapshot.threshold,
                                    maxResults = snapshot.maxResults,
                                    currentDelegate = snapshot.delegate.index,
                                    currentModel = snapshot.model.index,
                                    runningMode = RunningMode.IMAGE,
                                    context = context,
                                    imageClassifierListener = listener,
                                )
                            try {
                                val bundle = helper.classifyImage(bitmap)
                                bitmap to bundle?.let { toClassificationFrame(it, frameIndex = 0) }
                            } finally {
                                helper.clearImageClassifier()
                            }
                        }
                    if (output != null) {
                        _uiState.update {
                            it.copy(
                                isClassifyingGallery = false,
                                galleryBitmap = output.first,
                                galleryResult = output.second,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isClassifyingGallery = false,
                                errorMessage = it.errorMessage ?: "Failed to classify image.",
                            )
                        }
                    }
                }
        } else if (mime.startsWith("video")) {
            _uiState.update {
                it.copy(
                    isClassifyingGallery = true,
                    galleryBitmap = null,
                    galleryVideoUri = uri,
                    galleryResult = null,
                    errorMessage = null,
                )
            }
            galleryJob =
                viewModelScope.launch {
                    val bundle =
                        withContext(engineDispatcher) {
                            val helper =
                                ImageClassifierHelper(
                                    threshold = snapshot.threshold,
                                    maxResults = snapshot.maxResults,
                                    currentDelegate = snapshot.delegate.index,
                                    currentModel = snapshot.model.index,
                                    runningMode = RunningMode.VIDEO,
                                    context = context,
                                    imageClassifierListener = listener,
                                )
                            try {
                                helper.classifyVideoFile(uri, VIDEO_INTERVAL_MS)
                            } finally {
                                helper.clearImageClassifier()
                            }
                        }
                    if (bundle != null && bundle.results.isNotEmpty()) {
                        _uiState.update { it.copy(isClassifyingGallery = false) }
                        startVideoResultPlayback(bundle)
                    } else {
                        _uiState.update {
                            it.copy(
                                isClassifyingGallery = false,
                                errorMessage = it.errorMessage ?: "Failed to classify video.",
                            )
                        }
                    }
                }
        } else {
            _uiState.update { it.copy(errorMessage = "Unsupported media type.") }
        }
    }

    private fun startVideoResultPlayback(bundle: ImageClassifierHelper.ResultBundle) {
        videoPlaybackJob?.cancel()
        videoPlaybackJob =
            viewModelScope.launch {
                for (i in bundle.results.indices) {
                    _uiState.update { it.copy(galleryResult = toClassificationFrame(bundle, i)) }
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
        videoPlaybackJob?.cancel()
        val existing = liveHelper
        liveHelper = null
        cameraAnalyzerExecutor.execute {
            existing?.clearImageClassifier()
            cameraAnalyzerExecutor.shutdown()
        }
    }

    companion object {
        private const val VIDEO_INTERVAL_MS = 300L

        internal fun toClassificationFrame(
            bundle: ImageClassifierHelper.ResultBundle,
            frameIndex: Int,
        ): ClassificationFrame {
            val categories =
                bundle.results
                    .getOrNull(frameIndex)
                    ?.classificationResult()
                    ?.classifications()
                    ?.firstOrNull()
                    ?.categories()
                    .orEmpty()
                    .sortedByDescending { it.score() }
                    .map { cat ->
                        val name = cat.categoryName().ifBlank { "Unknown" }
                        ClassificationEntry(label = name, score = cat.score())
                    }
            return ClassificationFrame(
                categories = categories,
                inferenceTimeMs = bundle.inferenceTime,
            )
        }

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ImageClassifierViewModel(appContext.applicationContext) as T
                }
            }
    }
}
