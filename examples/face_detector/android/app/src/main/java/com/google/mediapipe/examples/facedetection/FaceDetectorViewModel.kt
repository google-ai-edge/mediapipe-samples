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

package com.google.mediapipe.examples.facedetection

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
import com.google.mediapipe.tasks.vision.facedetector.FaceDetectorResult
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

enum class DetectorTab {
    CAMERA,
    GALLERY,
}

enum class DetectorModel(val index: Int, val displayName: String, val shortLabel: String) {
    SHORT_RANGE(FaceDetectorHelper.MODEL_SHORT_RANGE, "BlazeFace (Short)", "Short-Range"),
    FULL_RANGE(FaceDetectorHelper.MODEL_FULL_RANGE, "BlazeFace (Full)", "Full-Range"),
}

enum class DetectorDelegate(val index: Int, val displayName: String) {
    CPU(FaceDetectorHelper.DELEGATE_CPU, "CPU"),
    GPU(FaceDetectorHelper.DELEGATE_GPU, "GPU"),
}

data class FaceDetectionOverlay(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val score: Float,
    val label: String,
    val keypoints: List<Pair<Float, Float>>,
)

data class DetectionFrame(
    val detections: List<FaceDetectionOverlay>,
    val inferenceTimeMs: Long,
    val imageWidth: Int,
    val imageHeight: Int,
)

data class FaceDetectorUiState(
    val selectedTab: DetectorTab = DetectorTab.CAMERA,
    val model: DetectorModel = DetectorModel.SHORT_RANGE,
    val delegate: DetectorDelegate = DetectorDelegate.CPU,
    val threshold: Float = FaceDetectorHelper.THRESHOLD_DEFAULT,
    val isFrontCamera: Boolean = true,
    val hasCameraPermission: Boolean = false,
    val isDetectingGallery: Boolean = false,
    val cameraUnavailableMessage: String? = null,
    val liveResult: DetectionFrame? = null,
    val galleryBitmap: Bitmap? = null,
    val galleryVideoUri: Uri? = null,
    val galleryResult: DetectionFrame? = null,
    val errorMessage: String? = null,
    val errorCode: Int = FaceDetectorHelper.OTHER_ERROR,
)

/** ViewModel that serializes MediaPipe face detection work off the main thread and exposes UI state. */
class FaceDetectorViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    val cameraAnalyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = cameraAnalyzerExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(FaceDetectorUiState())
    val uiState: StateFlow<FaceDetectorUiState> = _uiState.asStateFlow()

    private var liveHelper: FaceDetectorHelper? = null
    private var galleryJob: Job? = null
    private var videoOverlayJob: Job? = null
    private var lastGallerySampleUsed: Boolean = false
    private var lastGalleryMediaUri: Uri? = null

    private val listener =
        object : FaceDetectorHelper.DetectorListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isDetectingGallery = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }

            override fun onResults(resultBundle: FaceDetectorHelper.ResultBundle) {
                val frame = toDetectionFrame(resultBundle, frameIndex = 0)
                _uiState.update { it.copy(liveResult = frame) }
            }
        }

    fun selectTab(tab: DetectorTab) {
        if (_uiState.value.selectedTab == tab) return
        videoOverlayJob?.cancel()
        _uiState.update {
            it.copy(
                selectedTab = tab,
                errorMessage = null,
                liveResult = if (tab != DetectorTab.CAMERA) null else it.liveResult,
            )
        }
        if (tab == DetectorTab.GALLERY) {
            releaseLiveDetectorAsync()
        } else if (_uiState.value.hasCameraPermission) {
            prepareLiveDetector()
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        val changed = _uiState.value.hasCameraPermission != granted
        _uiState.update { it.copy(hasCameraPermission = granted) }
        if (changed && granted && _uiState.value.selectedTab == DetectorTab.CAMERA) {
            prepareLiveDetector()
        } else if (!granted) {
            releaseLiveDetectorAsync()
        }
    }

    fun toggleCameraLens() {
        _uiState.update { it.copy(isFrontCamera = !it.isFrontCamera, liveResult = null) }
    }

    fun setCameraUnavailable(message: String?) {
        _uiState.update { it.copy(cameraUnavailableMessage = message) }
    }

    fun selectModel(model: DetectorModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, liveResult = null, errorMessage = null) }
        reapplySettings()
    }

    fun selectDelegate(delegate: DetectorDelegate) {
        if (_uiState.value.delegate == delegate) return
        _uiState.update { it.copy(delegate = delegate, errorMessage = null) }
        reapplySettings()
    }

    fun updateThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.9f)
        if (_uiState.value.threshold == clamped) return
        _uiState.update { it.copy(threshold = clamped, errorMessage = null) }
        reapplySettings()
    }

    fun switchToCpuDelegate() {
        selectDelegate(DetectorDelegate.CPU)
    }

    fun retryInitialization() {
        _uiState.update { it.copy(errorMessage = null) }
        reapplySettings()
    }

    private fun reapplySettings() {
        val state = _uiState.value
        if (state.selectedTab == DetectorTab.CAMERA && state.hasCameraPermission) {
            prepareLiveDetector()
        } else if (state.selectedTab == DetectorTab.GALLERY) {
            when {
                lastGallerySampleUsed -> detectSampleImage()
                lastGalleryMediaUri != null -> detectGalleryUri(lastGalleryMediaUri!!)
            }
        }
    }

    fun prepareLiveDetector() {
        val snapshot = _uiState.value
        viewModelScope.launch {
            withContext(engineDispatcher) {
                val existing = liveHelper
                if (existing == null || existing.isClosed) {
                    liveHelper =
                        FaceDetectorHelper(
                            threshold = snapshot.threshold,
                            currentDelegate = snapshot.delegate.index,
                            currentModel = snapshot.model.index,
                            runningMode = RunningMode.LIVE_STREAM,
                            context = context,
                            faceDetectorListener = listener,
                        )
                } else {
                    existing.threshold = snapshot.threshold
                    existing.currentDelegate = snapshot.delegate.index
                    existing.currentModel = snapshot.model.index
                    existing.runningMode = RunningMode.LIVE_STREAM
                    existing.setupFaceDetector()
                }
            }
        }
    }

    fun analyzeCameraFrame(imageProxy: ImageProxy) {
        val state = _uiState.value
        if (state.selectedTab != DetectorTab.CAMERA || !state.hasCameraPermission) {
            imageProxy.close()
            return
        }
        val helper = liveHelper
        if (helper == null || helper.isClosed) {
            imageProxy.close()
            return
        }
        helper.detectLivestreamFrame(imageProxy, isFrontCamera = state.isFrontCamera)
    }

    fun releaseLiveDetectorAsync() {
        cameraAnalyzerExecutor.execute {
            liveHelper?.clearFaceDetector()
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
                            context.assets.open(FaceDetectorHelper.SAMPLE_IMAGE_ASSET).use { stream ->
                                BitmapFactory.decodeStream(stream)
                            } ?: return@withContext null
                        val helper =
                            FaceDetectorHelper(
                                threshold = snapshot.threshold,
                                currentDelegate = snapshot.delegate.index,
                                currentModel = snapshot.model.index,
                                runningMode = RunningMode.IMAGE,
                                context = context,
                                faceDetectorListener = listener,
                            )
                        try {
                            val bundle = helper.detectImage(bitmap)
                            bitmap to bundle?.let { toDetectionFrame(it, frameIndex = 0) }
                        } finally {
                            helper.clearFaceDetector()
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
                            val helper =
                                FaceDetectorHelper(
                                    threshold = snapshot.threshold,
                                    currentDelegate = snapshot.delegate.index,
                                    currentModel = snapshot.model.index,
                                    runningMode = RunningMode.IMAGE,
                                    context = context,
                                    faceDetectorListener = listener,
                                )
                            try {
                                val bundle = helper.detectImage(bitmap)
                                bitmap to bundle?.let { toDetectionFrame(it, frameIndex = 0) }
                            } finally {
                                helper.clearFaceDetector()
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
                                errorMessage = it.errorMessage ?: "Failed to detect faces in image.",
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
                            val helper =
                                FaceDetectorHelper(
                                    threshold = snapshot.threshold,
                                    currentDelegate = snapshot.delegate.index,
                                    currentModel = snapshot.model.index,
                                    runningMode = RunningMode.VIDEO,
                                    context = context,
                                    faceDetectorListener = listener,
                                )
                            try {
                                helper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                            } finally {
                                helper.clearFaceDetector()
                            }
                        }
                    if (bundle != null && bundle.results.isNotEmpty()) {
                        _uiState.update { it.copy(isDetectingGallery = false) }
                        startVideoOverlayPlayback(bundle)
                    } else {
                        _uiState.update {
                            it.copy(
                                isDetectingGallery = false,
                                errorMessage = it.errorMessage ?: "Failed to detect faces in video.",
                            )
                        }
                    }
                }
        } else {
            _uiState.update { it.copy(errorMessage = "Unsupported media type.") }
        }
    }

    private fun startVideoOverlayPlayback(bundle: FaceDetectorHelper.ResultBundle) {
        videoOverlayJob?.cancel()
        videoOverlayJob =
            viewModelScope.launch {
                for (i in bundle.results.indices) {
                    _uiState.update { it.copy(galleryResult = toDetectionFrame(bundle, i)) }
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
            existing?.clearFaceDetector()
            cameraAnalyzerExecutor.shutdown()
        }
    }

    companion object {
        private const val VIDEO_INTERVAL_MS = 300L

        internal fun toDetectionFrame(
            bundle: FaceDetectorHelper.ResultBundle,
            frameIndex: Int,
        ): DetectionFrame {
            val result: FaceDetectorResult? = bundle.results.getOrNull(frameIndex)
            val width = bundle.inputImageWidth.coerceAtLeast(1)
            val height = bundle.inputImageHeight.coerceAtLeast(1)
            val overlays =
                result?.detections().orEmpty().map { detection ->
                    val box = detection.boundingBox()
                    val category = detection.categories().firstOrNull()
                    val score = category?.score() ?: 0f
                    val label =
                        category?.categoryName()?.takeIf { it.isNotBlank() }?.replaceFirstChar {
                            it.uppercase()
                        } ?: "Face"
                    val kpList =
                        if (detection.keypoints().isPresent) detection.keypoints().get()
                        else emptyList()
                    val kps = kpList.map { kp -> (kp.x() * width) to (kp.y() * height) }
                    FaceDetectionOverlay(
                        left = box.left,
                        top = box.top,
                        right = box.right,
                        bottom = box.bottom,
                        score = score,
                        label = label,
                        keypoints = kps,
                    )
                }
            return DetectionFrame(
                detections = overlays,
                inferenceTimeMs = bundle.inferenceTime,
                imageWidth = width,
                imageHeight = height,
            )
        }

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return FaceDetectorViewModel(appContext.applicationContext) as T
                }
            }
    }
}
