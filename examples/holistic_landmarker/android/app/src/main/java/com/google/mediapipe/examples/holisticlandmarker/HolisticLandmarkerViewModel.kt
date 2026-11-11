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

package com.google.mediapipe.examples.holisticlandmarker

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
import com.google.mediapipe.tasks.vision.holisticlandmarker.HolisticLandmarkerResult
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
    CPU(HolisticLandmarkerHelper.DELEGATE_CPU, "CPU"),
    GPU(HolisticLandmarkerHelper.DELEGATE_GPU, "GPU"),
}

data class HolisticLandmarkerFrame(
    val faceLandmarks: List<Pair<Float, Float>>,
    val poseLandmarks: List<Pair<Float, Float>>,
    val leftHandLandmarks: List<Pair<Float, Float>>,
    val rightHandLandmarks: List<Pair<Float, Float>>,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
) {
    val handsCount: Int
        get() = (if (leftHandLandmarks.isNotEmpty()) 1 else 0) +
            (if (rightHandLandmarks.isNotEmpty()) 1 else 0)
}

data class HolisticLandmarkerUiState(
    val selectedTab: LandmarkerTab = LandmarkerTab.CAMERA,
    val delegate: LandmarkerDelegate = LandmarkerDelegate.CPU,
    val minFaceDetectionConfidence: Float =
        HolisticLandmarkerHelper.DEFAULT_FACE_DETECTION_CONFIDENCE,
    val minFacePresenceConfidence: Float =
        HolisticLandmarkerHelper.DEFAULT_FACE_PRESENCE_CONFIDENCE,
    val minPoseDetectionConfidence: Float =
        HolisticLandmarkerHelper.DEFAULT_POSE_DETECTION_CONFIDENCE,
    val minPosePresenceConfidence: Float =
        HolisticLandmarkerHelper.DEFAULT_POSE_PRESENCE_CONFIDENCE,
    val minHandLandmarksConfidence: Float =
        HolisticLandmarkerHelper.DEFAULT_HAND_LANDMARKS_CONFIDENCE,
    val isFrontCamera: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val isDetectingGallery: Boolean = false,
    val cameraUnavailableMessage: String? = null,
    val liveResult: HolisticLandmarkerFrame? = null,
    val galleryBitmap: Bitmap? = null,
    val galleryVideoUri: Uri? = null,
    val galleryResult: HolisticLandmarkerFrame? = null,
    val errorMessage: String? = null,
    val errorCode: Int = HolisticLandmarkerHelper.OTHER_ERROR,
)

/** ViewModel that serializes MediaPipe holistic landmark work off the main thread. */
class HolisticLandmarkerViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    val cameraAnalyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = cameraAnalyzerExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(HolisticLandmarkerUiState())
    val uiState: StateFlow<HolisticLandmarkerUiState> = _uiState.asStateFlow()

    private var liveHelper: HolisticLandmarkerHelper? = null
    private var galleryJob: Job? = null
    private var videoOverlayJob: Job? = null
    private var lastGallerySampleUsed: Boolean = false
    private var lastGalleryMediaUri: Uri? = null

    private val listener =
        object : HolisticLandmarkerHelper.LandmarkerListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isDetectingGallery = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }

            override fun onResults(resultBundle: HolisticLandmarkerHelper.ResultBundle) {
                val frame = toHolisticFrame(resultBundle, frameIndex = 0)
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

    fun updateFaceDetectionConfidence(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.minFaceDetectionConfidence == clamped) return
        _uiState.update { it.copy(minFaceDetectionConfidence = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun updatePoseDetectionConfidence(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.minPoseDetectionConfidence == clamped) return
        _uiState.update { it.copy(minPoseDetectionConfidence = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun updateHandLandmarksConfidence(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.minHandLandmarksConfidence == clamped) return
        _uiState.update { it.copy(minHandLandmarksConfidence = clamped, errorMessage = null) }
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
                    existing.minFaceDetectionConfidence = snapshot.minFaceDetectionConfidence
                    existing.minFacePresenceConfidence = snapshot.minFacePresenceConfidence
                    existing.minPoseDetectionConfidence = snapshot.minPoseDetectionConfidence
                    existing.minPosePresenceConfidence = snapshot.minPosePresenceConfidence
                    existing.minHandLandmarksConfidence = snapshot.minHandLandmarksConfidence
                    existing.runningMode = RunningMode.LIVE_STREAM
                    existing.setupHolisticLandmarker()
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
            liveHelper?.clearHolisticLandmarker()
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
                errorMessage = null,
            )
        }
        galleryJob =
            viewModelScope.launch {
                val decoded =
                    withContext(engineDispatcher) {
                        runCatching {
                            context.assets.open(HolisticLandmarkerHelper.SAMPLE_IMAGE_ASSET).use {
                                stream ->
                                BitmapFactory.decodeStream(stream)
                            }
                        }.getOrNull()
                    }
                if (decoded == null) {
                    _uiState.update {
                        it.copy(
                            isDetectingGallery = false,
                            errorMessage = "Failed to load bundled sample portrait.",
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
                            helper.clearHolisticLandmarker()
                        }
                    }
                _uiState.update {
                    it.copy(
                        isDetectingGallery = false,
                        galleryResult = bundle?.let { b -> toHolisticFrame(b, frameIndex = 0) },
                    )
                }
            }
    }

    fun detectGalleryUri(uri: Uri) {
        lastGallerySampleUsed = false
        lastGalleryMediaUri = uri
        galleryJob?.cancel()
        videoOverlayJob?.cancel()
        val mimeType = context.contentResolver.getType(uri) ?: ""
        val snapshot = _uiState.value

        if (mimeType.startsWith("image/")) {
            _uiState.update {
                it.copy(
                    isDetectingGallery = true,
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
                                isDetectingGallery = false,
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
                                helper.clearHolisticLandmarker()
                            }
                        }
                    _uiState.update {
                        it.copy(
                            isDetectingGallery = false,
                            galleryResult =
                                bundle?.let { b -> toHolisticFrame(b, frameIndex = 0) },
                        )
                    }
                }
        } else if (mimeType.startsWith("video/")) {
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
                                helper.clearHolisticLandmarker()
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
                                    it.errorMessage
                                        ?: "Failed to detect holistic landmarks in video.",
                            )
                        }
                    }
                }
        } else {
            _uiState.update { it.copy(errorMessage = "Unsupported media type.") }
        }
    }

    private fun createHelper(
        snapshot: HolisticLandmarkerUiState,
        runningMode: RunningMode,
        helperListener: HolisticLandmarkerHelper.LandmarkerListener?,
    ): HolisticLandmarkerHelper =
        HolisticLandmarkerHelper(
            minFaceDetectionConfidence = snapshot.minFaceDetectionConfidence,
            minFacePresenceConfidence = snapshot.minFacePresenceConfidence,
            minPoseDetectionConfidence = snapshot.minPoseDetectionConfidence,
            minPosePresenceConfidence = snapshot.minPosePresenceConfidence,
            minHandLandmarksConfidence = snapshot.minHandLandmarksConfidence,
            currentDelegate = snapshot.delegate.index,
            runningMode = runningMode,
            context = context,
            holisticLandmarkerHelperListener = helperListener,
        )

    private fun startVideoOverlayPlayback(bundle: HolisticLandmarkerHelper.ResultBundle) {
        videoOverlayJob?.cancel()
        videoOverlayJob =
            viewModelScope.launch {
                for (i in bundle.results.indices) {
                    _uiState.update { it.copy(galleryResult = toHolisticFrame(bundle, i)) }
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
            existing?.clearHolisticLandmarker()
            cameraAnalyzerExecutor.shutdown()
        }
    }

    companion object {
        private const val VIDEO_INTERVAL_MS = 300L

        internal fun toHolisticFrame(
            bundle: HolisticLandmarkerHelper.ResultBundle,
            frameIndex: Int,
        ): HolisticLandmarkerFrame {
            val result: HolisticLandmarkerResult? = bundle.results.getOrNull(frameIndex)
            val width = bundle.inputImageWidth.coerceAtLeast(1)
            val height = bundle.inputImageHeight.coerceAtLeast(1)
            fun mapLandmarks(
                landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>?
            ): List<Pair<Float, Float>> =
                landmarks.orEmpty().map { lm -> (lm.x() * width) to (lm.y() * height) }

            return HolisticLandmarkerFrame(
                faceLandmarks = mapLandmarks(result?.faceLandmarks()),
                poseLandmarks = mapLandmarks(result?.poseLandmarks()),
                leftHandLandmarks = mapLandmarks(result?.leftHandLandmarks()),
                rightHandLandmarks = mapLandmarks(result?.rightHandLandmarks()),
                inferenceTimeMs = bundle.inferenceTime,
                imageWidth = width,
                imageHeight = height,
            )
        }

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return HolisticLandmarkerViewModel(appContext.applicationContext) as T
                }
            }
    }
}
