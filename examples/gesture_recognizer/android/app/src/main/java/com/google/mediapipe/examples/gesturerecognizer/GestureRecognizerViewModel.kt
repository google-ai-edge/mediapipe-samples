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

package com.google.mediapipe.examples.gesturerecognizer

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
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
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

enum class RecognizerTab {
    CAMERA,
    GALLERY,
}

enum class RecognizerDelegate(val index: Int, val displayName: String) {
    CPU(GestureRecognizerHelper.DELEGATE_CPU, "CPU"),
    GPU(GestureRecognizerHelper.DELEGATE_GPU, "GPU"),
}

data class RecognizedGesture(
    val name: String,
    val score: Float,
)

data class GestureHandOverlay(
    val landmarks: List<Pair<Float, Float>>,
)

data class GestureFrame(
    val hands: List<GestureHandOverlay>,
    val topGesture: RecognizedGesture?,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class GestureRecognizerUiState(
    val selectedTab: RecognizerTab = RecognizerTab.CAMERA,
    val delegate: RecognizerDelegate = RecognizerDelegate.CPU,
    val minDetectionConfidence: Float = GestureRecognizerHelper.DEFAULT_HAND_DETECTION_CONFIDENCE,
    val minTrackingConfidence: Float = GestureRecognizerHelper.DEFAULT_HAND_TRACKING_CONFIDENCE,
    val minPresenceConfidence: Float = GestureRecognizerHelper.DEFAULT_HAND_PRESENCE_CONFIDENCE,
    val isFrontCamera: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val isRecognizingGallery: Boolean = false,
    val cameraUnavailableMessage: String? = null,
    val liveResult: GestureFrame? = null,
    val galleryBitmap: Bitmap? = null,
    val galleryVideoUri: Uri? = null,
    val galleryResult: GestureFrame? = null,
    val errorMessage: String? = null,
    val errorCode: Int = GestureRecognizerHelper.OTHER_ERROR,
)

/** ViewModel that serializes MediaPipe gesture recognition work off the main thread. */
class GestureRecognizerViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    val cameraAnalyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = cameraAnalyzerExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(GestureRecognizerUiState())
    val uiState: StateFlow<GestureRecognizerUiState> = _uiState.asStateFlow()

    private var liveHelper: GestureRecognizerHelper? = null
    private var galleryJob: Job? = null
    private var videoOverlayJob: Job? = null
    private var lastGallerySampleUsed: Boolean = false
    private var lastGalleryMediaUri: Uri? = null

    private val listener =
        object : GestureRecognizerHelper.GestureRecognizerListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isRecognizingGallery = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }

            override fun onResults(resultBundle: GestureRecognizerHelper.ResultBundle) {
                val frame = toGestureFrame(resultBundle, frameIndex = 0)
                _uiState.update { it.copy(liveResult = frame) }
            }
        }

    fun selectTab(tab: RecognizerTab) {
        if (_uiState.value.selectedTab == tab) return
        videoOverlayJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                liveResult = if (tab != RecognizerTab.CAMERA) null else it.liveResult,
            )
        }
        if (tab == RecognizerTab.GALLERY) {
            releaseLiveRecognizerAsync()
        } else if (_uiState.value.hasCameraPermission) {
            prepareLiveRecognizer()
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        val changed = _uiState.value.hasCameraPermission != granted
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (changed && granted && _uiState.value.selectedTab == RecognizerTab.CAMERA) {
            prepareLiveRecognizer()
        } else if (!granted) {
            releaseLiveRecognizerAsync()
        }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, liveResult = null) }
    }

    fun setCameraUnavailable(message: String?) {
        _uiState.update { it.copy(cameraUnavailableMessage = message) }
    }

    fun selectDelegate(delegate: RecognizerDelegate) {
        if (_uiState.value.delegate == delegate) return
        _uiState.update { it.copy(delegate = delegate, errorMessage = null) }
        reapplySettings()
    }

    fun updateDetectionConfidence(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.minDetectionConfidence == clamped) return
        _uiState.update { it.copy(minDetectionConfidence = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun updateTrackingConfidence(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.minTrackingConfidence == clamped) return
        _uiState.update { it.copy(minTrackingConfidence = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun updatePresenceConfidence(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.minPresenceConfidence == clamped) return
        _uiState.update { it.copy(minPresenceConfidence = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun switchToCpuDelegate() {
        selectDelegate(RecognizerDelegate.CPU)
    }

    fun retryInitialization() {
        _uiState.update { it.copy(errorMessage = null) }
        reapplySettings()
    }

    private fun reapplySettings() {
        val state = _uiState.value
        if (state.selectedTab == RecognizerTab.CAMERA && state.hasCameraPermission) {
            prepareLiveRecognizer()
        } else if (state.selectedTab == RecognizerTab.GALLERY) {
            when {
                lastGallerySampleUsed -> recognizeSampleImage()
                lastGalleryMediaUri != null -> recognizeGalleryUri(lastGalleryMediaUri!!)
            }
        }
    }

    fun prepareLiveRecognizer() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            withContext(engineDispatcher) {
                val existing = liveHelper
                if (existing == null || existing.isClosed) {
                    liveHelper = createHelper(snapshot, RunningMode.LIVE_STREAM, listener)
                } else {
                    existing.currentDelegate = snapshot.delegate.index
                    existing.minHandDetectionConfidence = snapshot.minDetectionConfidence
                    existing.minHandTrackingConfidence = snapshot.minTrackingConfidence
                    existing.minHandPresenceConfidence = snapshot.minPresenceConfidence
                    existing.runningMode = RunningMode.LIVE_STREAM
                    existing.setupGestureRecognizer()
                }
            }
        }
    }

    fun analyzeCameraFrame(imageProxy: ImageProxy) {
        val state = _uiState.value
        if (state.selectedTab != RecognizerTab.CAMERA || !state.hasCameraPermission) {
            imageProxy.close()
            return
        }
        val helper = liveHelper
        if (helper == null || helper.isClosed) {
            imageProxy.close()
            return
        }
        helper.recognizeLiveStream(imageProxy, isFrontCamera = state.isFrontCamera)
    }

    fun releaseLiveRecognizerAsync() {
        cameraAnalyzerExecutor.execute {
            liveHelper?.clearGestureRecognizer()
            liveHelper = null
        }
    }

    fun recognizeSampleImage() {
        lastGallerySampleUsed = true
        lastGalleryMediaUri = null
        galleryJob?.cancel()
        videoOverlayJob?.cancel()
        val snapshot = _uiState.value
        _uiState.update {
            it.copy(
                isRecognizingGallery = true,
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
                            context.assets.open(GestureRecognizerHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            } ?: return@withContext null
                        val helper = createHelper(snapshot, RunningMode.IMAGE, listener)
                        try {
                            val bundle = helper.recognizeImage(bitmap)
                            bitmap to bundle?.let { toGestureFrame(it, frameIndex = 0) }
                        } finally {
                            helper.clearGestureRecognizer()
                        }
                    }
                if (output != null) {
                    _uiState.update { state ->
                        state.copy(
                            isRecognizingGallery = false,
                            galleryBitmap = output.first,
                            galleryResult = output.second,
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isRecognizingGallery = false,
                            errorMessage = state.errorMessage ?: "Failed to load sample image.",
                        )
                    }
                }
            }
    }

    fun recognizeGalleryUri(uri: Uri) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        lastGallerySampleUsed = false
        lastGalleryMediaUri = uri
        galleryJob?.cancel()
        videoOverlayJob?.cancel()
        val snapshot = _uiState.value

        if (mime.startsWith("image")) {
            _uiState.update {
                it.copy(
                    isRecognizingGallery = true,
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
                            val helper = createHelper(snapshot, RunningMode.IMAGE, listener)
                            try {
                                val bundle = helper.recognizeImage(bitmap)
                                bitmap to bundle?.let { toGestureFrame(it, frameIndex = 0) }
                            } finally {
                                helper.clearGestureRecognizer()
                            }
                        }
                    if (output != null) {
                        _uiState.update {
                            it.copy(
                                isRecognizingGallery = false,
                                galleryBitmap = output.first,
                                galleryResult = output.second,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isRecognizingGallery = false,
                                errorMessage =
                                    it.errorMessage ?: "Failed to recognize gesture in image.",
                            )
                        }
                    }
                }
        } else if (mime.startsWith("video")) {
            _uiState.update {
                it.copy(
                    isRecognizingGallery = true,
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
                            val helper = createHelper(snapshot, RunningMode.VIDEO, listener)
                            try {
                                helper.recognizeVideoFile(uri, VIDEO_INTERVAL_MS)
                            } finally {
                                helper.clearGestureRecognizer()
                            }
                        }
                    if (bundle != null && bundle.results.isNotEmpty()) {
                        _uiState.update { it.copy(isRecognizingGallery = false) }
                        startVideoOverlayPlayback(bundle)
                    } else {
                        _uiState.update {
                            it.copy(
                                isRecognizingGallery = false,
                                errorMessage =
                                    it.errorMessage ?: "Failed to recognize gesture in video.",
                            )
                        }
                    }
                }
        } else {
            _uiState.update { it.copy(errorMessage = "Unsupported media type.") }
        }
    }

    private fun createHelper(
        snapshot: GestureRecognizerUiState,
        runningMode: RunningMode,
        helperListener: GestureRecognizerHelper.GestureRecognizerListener?,
    ): GestureRecognizerHelper =
        GestureRecognizerHelper(
            minHandDetectionConfidence = snapshot.minDetectionConfidence,
            minHandTrackingConfidence = snapshot.minTrackingConfidence,
            minHandPresenceConfidence = snapshot.minPresenceConfidence,
            currentDelegate = snapshot.delegate.index,
            runningMode = runningMode,
            context = context,
            gestureRecognizerListener = helperListener,
        )

    private fun startVideoOverlayPlayback(bundle: GestureRecognizerHelper.ResultBundle) {
        videoOverlayJob?.cancel()
        videoOverlayJob =
            viewModelScope.launch {
                for (i in bundle.results.indices) {
                    _uiState.update { it.copy(galleryResult = toGestureFrame(bundle, i)) }
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
            existing?.clearGestureRecognizer()
            cameraAnalyzerExecutor.shutdown()
        }
    }

    companion object {
        private const val VIDEO_INTERVAL_MS = 300L

        internal fun toGestureFrame(
            bundle: GestureRecognizerHelper.ResultBundle,
            frameIndex: Int,
        ): GestureFrame {
            val result: GestureRecognizerResult? = bundle.results.getOrNull(frameIndex)
            val width = bundle.inputImageWidth.coerceAtLeast(1)
            val height = bundle.inputImageHeight.coerceAtLeast(1)
            val hands =
                result?.landmarks().orEmpty().map { landmarks ->
                    GestureHandOverlay(
                        landmarks = landmarks.map { lm -> (lm.x() * width) to (lm.y() * height) }
                    )
                }
            val topCategory =
                result?.gestures().orEmpty().flatten().maxByOrNull { it.score() }?.let { cat ->
                    RecognizedGesture(
                        name = cat.categoryName(),
                        score = cat.score(),
                    )
                }
            return GestureFrame(
                hands = hands,
                topGesture = topCategory,
                inferenceTimeMs = bundle.inferenceTime,
                imageWidth = width,
                imageHeight = height,
            )
        }

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return GestureRecognizerViewModel(appContext.applicationContext) as T
                }
            }
    }
}
