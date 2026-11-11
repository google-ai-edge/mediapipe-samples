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

package com.google.mediapipe.examples.facelandmarker

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
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
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

enum class LandmarkerTab {
    CAMERA,
    GALLERY,
}

enum class LandmarkerDelegate(val index: Int, val displayName: String) {
    CPU(FaceLandmarkerHelper.DELEGATE_CPU, "CPU"),
    GPU(FaceLandmarkerHelper.DELEGATE_GPU, "GPU"),
}

data class BlendshapeScore(
    val name: String,
    val score: Float,
)

data class FaceMeshOverlay(
    val landmarks: List<Pair<Float, Float>>,
)

data class FaceLandmarkerFrame(
    val faces: List<FaceMeshOverlay>,
    val topBlendshapes: List<BlendshapeScore>,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class FaceLandmarkerUiState(
    val selectedTab: LandmarkerTab = LandmarkerTab.CAMERA,
    val delegate: LandmarkerDelegate = LandmarkerDelegate.CPU,
    val maxFaces: Int = FaceLandmarkerHelper.DEFAULT_NUM_FACES,
    val minDetectionConfidence: Float = FaceLandmarkerHelper.DEFAULT_FACE_DETECTION_CONFIDENCE,
    val minTrackingConfidence: Float = FaceLandmarkerHelper.DEFAULT_FACE_TRACKING_CONFIDENCE,
    val minPresenceConfidence: Float = FaceLandmarkerHelper.DEFAULT_FACE_PRESENCE_CONFIDENCE,
    val isFrontCamera: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val isDetectingGallery: Boolean = false,
    val cameraUnavailableMessage: String? = null,
    val liveResult: FaceLandmarkerFrame? = null,
    val galleryBitmap: Bitmap? = null,
    val galleryVideoUri: Uri? = null,
    val galleryResult: FaceLandmarkerFrame? = null,
    val errorMessage: String? = null,
    val errorCode: Int = FaceLandmarkerHelper.OTHER_ERROR,
)

/** ViewModel that serializes MediaPipe face landmark work off the main thread and exposes UI state. */
class FaceLandmarkerViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    val cameraAnalyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = cameraAnalyzerExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(FaceLandmarkerUiState())
    val uiState: StateFlow<FaceLandmarkerUiState> = _uiState.asStateFlow()

    private var liveHelper: FaceLandmarkerHelper? = null
    private var galleryJob: Job? = null
    private var videoOverlayJob: Job? = null
    private var lastGallerySampleUsed: Boolean = false
    private var lastGalleryMediaUri: Uri? = null

    private val listener =
        object : FaceLandmarkerHelper.LandmarkerListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isDetectingGallery = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }

            override fun onResults(resultBundle: FaceLandmarkerHelper.ResultBundle) {
                val frame =
                    toLandmarkerFrame(
                        result = resultBundle.result,
                        inferenceTimeMs = resultBundle.inferenceTime,
                        width = resultBundle.inputImageWidth,
                        height = resultBundle.inputImageHeight,
                    )
                _uiState.update { it.copy(liveResult = frame) }
            }
        }

    fun selectTab(tab: LandmarkerTab) {
        if (_uiState.value.selectedTab == tab) return
        videoOverlayJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                liveResult = if (tab != LandmarkerTab.CAMERA) null else it.liveResult,
            )
        }
        if (tab == LandmarkerTab.GALLERY) {
            releaseLiveLandmarkerAsync()
        } else if (_uiState.value.hasCameraPermission) {
            prepareLiveLandmarker()
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        val changed = _uiState.value.hasCameraPermission != granted
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (changed && granted && _uiState.value.selectedTab == LandmarkerTab.CAMERA) {
            prepareLiveLandmarker()
        } else if (!granted) {
            releaseLiveLandmarkerAsync()
        }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, liveResult = null) }
    }

    fun setCameraUnavailable(message: String?) {
        _uiState.update { it.copy(cameraUnavailableMessage = message) }
    }

    fun selectDelegate(delegate: LandmarkerDelegate) {
        if (_uiState.value.delegate == delegate) return
        _uiState.update { it.copy(delegate = delegate, errorMessage = null) }
        reapplySettings()
    }

    fun selectMaxFaces(maxFaces: Int) {
        val clamped = maxFaces.coerceIn(1, FaceLandmarkerHelper.MAX_FACES_LIMIT)
        if (_uiState.value.maxFaces == clamped) return
        _uiState.update { it.copy(maxFaces = clamped, errorMessage = null) }
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
        selectDelegate(LandmarkerDelegate.CPU)
    }

    fun retryInitialization() {
        _uiState.update { it.copy(errorMessage = null) }
        reapplySettings()
    }

    private fun reapplySettings() {
        val state = _uiState.value
        if (state.selectedTab == LandmarkerTab.CAMERA && state.hasCameraPermission) {
            prepareLiveLandmarker()
        } else if (state.selectedTab == LandmarkerTab.GALLERY) {
            when {
                lastGallerySampleUsed -> detectSampleImage()
                lastGalleryMediaUri != null -> detectGalleryUri(lastGalleryMediaUri!!)
            }
        }
    }

    fun prepareLiveLandmarker() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            withContext(engineDispatcher) {
                val existing = liveHelper
                if (existing == null || existing.isClosed) {
                    liveHelper = createHelper(snapshot, RunningMode.LIVE_STREAM, listener)
                } else {
                    existing.currentDelegate = snapshot.delegate.index
                    existing.maxNumFaces = snapshot.maxFaces
                    existing.minFaceDetectionConfidence = snapshot.minDetectionConfidence
                    existing.minFaceTrackingConfidence = snapshot.minTrackingConfidence
                    existing.minFacePresenceConfidence = snapshot.minPresenceConfidence
                    existing.runningMode = RunningMode.LIVE_STREAM
                    existing.setupFaceLandmarker()
                }
            }
        }
    }

    fun analyzeCameraFrame(imageProxy: ImageProxy) {
        val state = _uiState.value
        if (state.selectedTab != LandmarkerTab.CAMERA || !state.hasCameraPermission) {
            imageProxy.close()
            return
        }
        val helper = liveHelper
        if (helper == null || helper.isClosed) {
            imageProxy.close()
            return
        }
        helper.detectLiveStream(imageProxy, isFrontCamera = state.isFrontCamera)
    }

    fun releaseLiveLandmarkerAsync() {
        cameraAnalyzerExecutor.execute {
            liveHelper?.clearFaceLandmarker()
            liveHelper = null
        }
    }

    fun detectSampleImage() {
        lastGallerySampleUsed = true
        lastGalleryMediaUri = null
        galleryJob?.cancel()
        videoOverlayJob?.cancel()
        val snapshot = _uiState.value
        _uiState.update {
            it.copy(
                isDetectingGallery = true,
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
                            context.assets.open(FaceLandmarkerHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            } ?: return@withContext null
                        val helper = createHelper(snapshot, RunningMode.IMAGE, listener)
                        try {
                            val bundle = helper.detectImage(bitmap)
                            bitmap to
                                bundle?.let {
                                    toLandmarkerFrame(
                                        result = it.result,
                                        inferenceTimeMs = it.inferenceTime,
                                        width = it.inputImageWidth,
                                        height = it.inputImageHeight,
                                    )
                                }
                        } finally {
                            helper.clearFaceLandmarker()
                        }
                    }
                if (output != null) {
                    _uiState.update { state ->
                        state.copy(
                            isDetectingGallery = false,
                            galleryBitmap = output.first,
                            galleryResult = output.second,
                        )
                    }
                } else {
                    _uiState.update { state ->
                        state.copy(
                            isDetectingGallery = false,
                            errorMessage = state.errorMessage ?: "Failed to load sample image.",
                        )
                    }
                }
            }
    }

    fun detectGalleryUri(uri: Uri) {
        val mime = context.contentResolver.getType(uri).orEmpty()
        lastGallerySampleUsed = false
        lastGalleryMediaUri = uri
        galleryJob?.cancel()
        videoOverlayJob?.cancel()
        val snapshot = _uiState.value

        if (mime.startsWith("image")) {
            _uiState.update {
                it.copy(
                    isDetectingGallery = true,
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
                                val bundle = helper.detectImage(bitmap)
                                bitmap to
                                    bundle?.let {
                                        toLandmarkerFrame(
                                            result = it.result,
                                            inferenceTimeMs = it.inferenceTime,
                                            width = it.inputImageWidth,
                                            height = it.inputImageHeight,
                                        )
                                    }
                            } finally {
                                helper.clearFaceLandmarker()
                            }
                        }
                    if (output != null) {
                        _uiState.update {
                            it.copy(
                                isDetectingGallery = false,
                                galleryBitmap = output.first,
                                galleryResult = output.second,
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isDetectingGallery = false,
                                errorMessage =
                                    it.errorMessage ?: "Failed to detect face landmarks in image.",
                            )
                        }
                    }
                }
        } else if (mime.startsWith("video")) {
            _uiState.update {
                it.copy(
                    isDetectingGallery = true,
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
                                helper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                            } finally {
                                helper.clearFaceLandmarker()
                            }
                        }
                    if (bundle != null && bundle.results.isNotEmpty()) {
                        _uiState.update { it.copy(isDetectingGallery = false) }
                        startVideoOverlayPlayback(bundle)
                    } else {
                        _uiState.update {
                            it.copy(
                                isDetectingGallery = false,
                                errorMessage =
                                    it.errorMessage ?: "Failed to detect face landmarks in video.",
                            )
                        }
                    }
                }
        } else {
            _uiState.update { it.copy(errorMessage = "Unsupported media type.") }
        }
    }

    private fun createHelper(
        snapshot: FaceLandmarkerUiState,
        runningMode: RunningMode,
        helperListener: FaceLandmarkerHelper.LandmarkerListener?,
    ): FaceLandmarkerHelper =
        FaceLandmarkerHelper(
            minFaceDetectionConfidence = snapshot.minDetectionConfidence,
            minFaceTrackingConfidence = snapshot.minTrackingConfidence,
            minFacePresenceConfidence = snapshot.minPresenceConfidence,
            maxNumFaces = snapshot.maxFaces,
            currentDelegate = snapshot.delegate.index,
            runningMode = runningMode,
            context = context,
            faceLandmarkerHelperListener = helperListener,
        )

    private fun startVideoOverlayPlayback(bundle: FaceLandmarkerHelper.VideoResultBundle) {
        videoOverlayJob?.cancel()
        videoOverlayJob =
            viewModelScope.launch {
                for (result in bundle.results) {
                    _uiState.update {
                        it.copy(
                            galleryResult =
                                toLandmarkerFrame(
                                    result = result,
                                    inferenceTimeMs = bundle.inferenceTime,
                                    width = bundle.inputImageWidth,
                                    height = bundle.inputImageHeight,
                                )
                        )
                    }
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
            existing?.clearFaceLandmarker()
            cameraAnalyzerExecutor.shutdown()
        }
    }

    companion object {
        private const val VIDEO_INTERVAL_MS = 300L

        internal fun toLandmarkerFrame(
            result: FaceLandmarkerResult,
            inferenceTimeMs: Long,
            width: Int,
            height: Int,
        ): FaceLandmarkerFrame {
            val safeW = width.coerceAtLeast(1)
            val safeH = height.coerceAtLeast(1)
            val faces =
                result.faceLandmarks().map { landmarks ->
                    FaceMeshOverlay(
                        landmarks = landmarks.map { lm -> (lm.x() * safeW) to (lm.y() * safeH) }
                    )
                }
            val firstBlendshapes =
                if (result.faceBlendshapes().isPresent) {
                    result.faceBlendshapes().get().firstOrNull().orEmpty()
                } else {
                    emptyList()
                }
            val topBlendshapes =
                firstBlendshapes
                    .filter { it.categoryName() != "_neutral" }
                    .sortedByDescending { it.score() }
                    .take(3)
                    .map { BlendshapeScore(name = it.categoryName(), score = it.score()) }

            return FaceLandmarkerFrame(
                faces = faces,
                topBlendshapes = topBlendshapes,
                inferenceTimeMs = inferenceTimeMs,
                imageWidth = safeW,
                imageHeight = safeH,
            )
        }

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FaceLandmarkerViewModel(appContext.applicationContext) as T
                }
            }
    }
}
