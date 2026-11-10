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

package com.google.mediapipe.examples.poselandmarker

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
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
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

enum class PoseModel(val index: Int, val displayName: String, val shortLabel: String) {
    LITE(PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_LITE, "Pose Lite", "Lite"),
    FULL(PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_FULL, "Pose Full", "Full"),
    HEAVY(PoseLandmarkerHelper.MODEL_POSE_LANDMARKER_HEAVY, "Pose Heavy", "Heavy"),
}

enum class LandmarkerDelegate(val index: Int, val displayName: String) {
    CPU(PoseLandmarkerHelper.DELEGATE_CPU, "CPU"),
    GPU(PoseLandmarkerHelper.DELEGATE_GPU, "GPU"),
}

data class PoseOverlay(
    val landmarks: List<Pair<Float, Float>>,
)

data class PoseLandmarkerFrame(
    val poses: List<PoseOverlay>,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class PoseLandmarkerUiState(
    val selectedTab: LandmarkerTab = LandmarkerTab.CAMERA,
    val model: PoseModel = PoseModel.FULL,
    val delegate: LandmarkerDelegate = LandmarkerDelegate.CPU,
    val minDetectionConfidence: Float = PoseLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
    val minTrackingConfidence: Float = PoseLandmarkerHelper.DEFAULT_POSE_TRACKING_CONFIDENCE,
    val minPresenceConfidence: Float = PoseLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
    val isFrontCamera: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val isDetectingGallery: Boolean = false,
    val cameraUnavailableMessage: String? = null,
    val liveResult: PoseLandmarkerFrame? = null,
    val galleryBitmap: Bitmap? = null,
    val galleryVideoUri: Uri? = null,
    val galleryResult: PoseLandmarkerFrame? = null,
    val errorMessage: String? = null,
    val errorCode: Int = PoseLandmarkerHelper.OTHER_ERROR,
)

/** ViewModel that serializes MediaPipe pose landmark work off the main thread. */
class PoseLandmarkerViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    val cameraAnalyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = cameraAnalyzerExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(PoseLandmarkerUiState())
    val uiState: StateFlow<PoseLandmarkerUiState> = _uiState.asStateFlow()

    private var liveHelper: PoseLandmarkerHelper? = null
    private var galleryJob: Job? = null
    private var videoOverlayJob: Job? = null
    private var lastGallerySampleUsed: Boolean = false
    private var lastGalleryMediaUri: Uri? = null

    private val listener =
        object : PoseLandmarkerHelper.LandmarkerListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isDetectingGallery = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }

            override fun onResults(resultBundle: PoseLandmarkerHelper.ResultBundle) {
                val frame = toPoseFrame(resultBundle, frameIndex = 0)
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

    fun selectModel(model: PoseModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, liveResult = null, errorMessage = null) }
        reapplySettings()
    }

    fun selectDelegate(delegate: LandmarkerDelegate) {
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
                    existing.currentModel = snapshot.model.index
                    existing.currentDelegate = snapshot.delegate.index
                    existing.minPoseDetectionConfidence = snapshot.minDetectionConfidence
                    existing.minPoseTrackingConfidence = snapshot.minTrackingConfidence
                    existing.minPosePresenceConfidence = snapshot.minPresenceConfidence
                    existing.runningMode = RunningMode.LIVE_STREAM
                    existing.setupPoseLandmarker()
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
            liveHelper?.clearPoseLandmarker()
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
                            context.assets.open(PoseLandmarkerHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            } ?: return@withContext null
                        val helper = createHelper(snapshot, RunningMode.IMAGE, listener)
                        try {
                            val bundle = helper.detectImage(bitmap)
                            bitmap to bundle?.let { toPoseFrame(it, frameIndex = 0) }
                        } finally {
                            helper.clearPoseLandmarker()
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
                                bitmap to bundle?.let { toPoseFrame(it, frameIndex = 0) }
                            } finally {
                                helper.clearPoseLandmarker()
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
                                    it.errorMessage ?: "Failed to detect pose landmarks in image.",
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
                                helper.clearPoseLandmarker()
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
                                    it.errorMessage ?: "Failed to detect pose landmarks in video.",
                            )
                        }
                    }
                }
        } else {
            _uiState.update { it.copy(errorMessage = "Unsupported media type.") }
        }
    }

    private fun createHelper(
        snapshot: PoseLandmarkerUiState,
        runningMode: RunningMode,
        helperListener: PoseLandmarkerHelper.LandmarkerListener?,
    ): PoseLandmarkerHelper =
        PoseLandmarkerHelper(
            minPoseDetectionConfidence = snapshot.minDetectionConfidence,
            minPoseTrackingConfidence = snapshot.minTrackingConfidence,
            minPosePresenceConfidence = snapshot.minPresenceConfidence,
            currentModel = snapshot.model.index,
            currentDelegate = snapshot.delegate.index,
            runningMode = runningMode,
            context = context,
            poseLandmarkerHelperListener = helperListener,
        )

    private fun startVideoOverlayPlayback(bundle: PoseLandmarkerHelper.ResultBundle) {
        videoOverlayJob?.cancel()
        videoOverlayJob =
            viewModelScope.launch {
                for (i in bundle.results.indices) {
                    _uiState.update { it.copy(galleryResult = toPoseFrame(bundle, i)) }
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
            existing?.clearPoseLandmarker()
            cameraAnalyzerExecutor.shutdown()
        }
    }

    companion object {
        private const val VIDEO_INTERVAL_MS = 300L

        internal fun toPoseFrame(
            bundle: PoseLandmarkerHelper.ResultBundle,
            frameIndex: Int,
        ): PoseLandmarkerFrame {
            val result: PoseLandmarkerResult? = bundle.results.getOrNull(frameIndex)
            val width = bundle.inputImageWidth.coerceAtLeast(1)
            val height = bundle.inputImageHeight.coerceAtLeast(1)
            val poses =
                result?.landmarks().orEmpty().map { poseLandmarks ->
                    PoseOverlay(
                        landmarks = poseLandmarks.map { lm -> (lm.x() * width) to (lm.y() * height) }
                    )
                }
            return PoseLandmarkerFrame(
                poses = poses,
                inferenceTimeMs = bundle.inferenceTime,
                imageWidth = width,
                imageHeight = height,
            )
        }

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PoseLandmarkerViewModel(appContext.applicationContext) as T
                }
            }
    }
}
