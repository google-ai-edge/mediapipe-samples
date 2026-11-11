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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.vision.interactivesegmenter.Stroke
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class BrushMode(val labelRes: Int) {
    POSITIVE(R.string.brush_positive),
    NEGATIVE(R.string.brush_negative),
    LASSO(R.string.brush_lasso),
}

/** Stroke drawn by the user in normalized `[0f..1f]` image coordinates. */
data class DrawnStroke(
    val mode: BrushMode,
    val points: List<Pair<Float, Float>>,
) {
    fun toMediaPipeStroke(): Stroke {
        val mpMode =
            when (mode) {
                BrushMode.POSITIVE -> Stroke.BrushMode.POSITIVE
                BrushMode.NEGATIVE -> Stroke.BrushMode.NEGATIVE
                BrushMode.LASSO -> Stroke.BrushMode.LASSO
            }
        val keypoints = points.map { (x, y) -> NormalizedKeypoint.create(x, y) }
        return Stroke.builder()
            .setBrushMode(mpMode)
            .setPoints(keypoints)
            .setCompleted(true)
            .build()
    }
}

data class InteractiveSegmentationUiState(
    val brushMode: BrushMode = BrushMode.POSITIVE,
    val inputBitmap: Bitmap? = null,
    val strokes: List<DrawnStroke> = emptyList(),
    val result: InteractiveSegmentationHelper.ResultBundle? = null,
    val isSegmenting: Boolean = false,
    val errorMessage: String? = null,
)

/** ViewModel managing interactive segmentation state and serialized native inference calls. */
class InteractiveSegmentationViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    private val engineExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(InteractiveSegmentationUiState())
    val uiState: StateFlow<InteractiveSegmentationUiState> = _uiState.asStateFlow()

    private var helper: InteractiveSegmentationHelper? = null
    private var activeJob: Job? = null
    @Volatile private var requestGeneration: Long = 0L

    private val listener =
        object : InteractiveSegmentationHelper.InteractiveSegmentationListener {
            override fun onError(error: String) {
                _uiState.update { it.copy(isSegmenting = false, errorMessage = error) }
            }

            override fun onResults(result: InteractiveSegmentationHelper.ResultBundle?) = Unit
        }

    init {
        initializeSegmenter()
    }

    fun initializeSegmenter() {
        viewModelScope.launch {
            withContext(engineDispatcher) {
                if (helper == null || helper?.isClosed == true) {
                    helper = InteractiveSegmentationHelper(context, listener)
                } else {
                    helper?.setupInteractiveSegmenter()
                }
            }
        }
    }

    fun selectBrushMode(mode: BrushMode) {
        _uiState.update { it.copy(brushMode = mode) }
    }

    fun loadSampleImage() {
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isSegmenting = true,
                strokes = emptyList(),
                result = null,
                errorMessage = null,
            )
        }
        activeJob =
            viewModelScope.launch {
                val bitmap =
                    withContext(engineDispatcher) {
                        val segmenter = ensureHelper()
                        val raw =
                            runCatching {
                                context.assets
                                    .open(InteractiveSegmentationHelper.SAMPLE_IMAGE_ASSET)
                                    .use { stream -> BitmapFactory.decodeStream(stream) }
                            }.getOrNull()
                        raw?.let { decoded ->
                            val prepared = prepareBitmap(decoded)
                            if (prepared !== decoded) decoded.recycle()
                            segmenter.setInputImage(prepared)
                            prepared
                        }
                    }
                if (bitmap == null) {
                    _uiState.update {
                        it.copy(
                            isSegmenting = false,
                            errorMessage = "Failed to load sample image.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSegmenting = false,
                            inputBitmap = bitmap,
                            strokes = emptyList(),
                            result = null,
                        )
                    }
                }
            }
    }

    fun loadImageUri(uri: Uri) {
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isSegmenting = true,
                strokes = emptyList(),
                result = null,
                errorMessage = null,
            )
        }
        activeJob =
            viewModelScope.launch {
                val bitmap =
                    withContext(engineDispatcher) {
                        val segmenter = ensureHelper()
                        val decoded = decodeUriBitmap(uri) ?: return@withContext null
                        segmenter.setInputImage(decoded)
                        decoded
                    }
                if (bitmap == null) {
                    _uiState.update {
                        it.copy(
                            isSegmenting = false,
                            errorMessage = "Unable to decode selected image.",
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isSegmenting = false,
                            inputBitmap = bitmap,
                            strokes = emptyList(),
                            result = null,
                        )
                    }
                }
            }
    }

    fun addStroke(stroke: DrawnStroke) {
        if (_uiState.value.inputBitmap == null || stroke.points.isEmpty()) return
        val updatedStrokes = _uiState.value.strokes + stroke
        _uiState.update {
            it.copy(strokes = updatedStrokes, isSegmenting = true, errorMessage = null)
        }
        runSegmentation(updatedStrokes)
    }

    fun undoLastStroke() {
        val current = _uiState.value.strokes
        if (current.isEmpty()) return
        val updated = current.dropLast(1)
        if (updated.isEmpty()) {
            clearStrokes()
        } else {
            _uiState.update { it.copy(strokes = updated, isSegmenting = true, errorMessage = null) }
            runSegmentation(updated)
        }
    }

    fun clearStrokes() {
        requestGeneration++
        activeJob?.cancel()
        _uiState.update {
            it.copy(strokes = emptyList(), result = null, isSegmenting = false, errorMessage = null)
        }
    }

    fun retryInitialization() {
        _uiState.update { it.copy(errorMessage = null) }
        initializeSegmenter()
    }

    private fun runSegmentation(strokes: List<DrawnStroke>) {
        val gen = ++requestGeneration
        activeJob?.cancel()
        activeJob =
            viewModelScope.launch {
                val resultBundle =
                    withContext(engineDispatcher) {
                        val segmenter = ensureHelper()
                        segmenter.segment(strokes.map { it.toMediaPipeStroke() })
                    }
                if (gen == requestGeneration) {
                    _uiState.update {
                        it.copy(isSegmenting = false, result = resultBundle ?: it.result)
                    }
                }
            }
    }

    private fun ensureHelper(): InteractiveSegmentationHelper {
        val existing = helper
        if (existing != null && !existing.isClosed) return existing
        return InteractiveSegmentationHelper(context, listener).also { helper = it }
    }

    private fun prepareBitmap(raw: Bitmap): Bitmap {
        var scaled = raw
        if (raw.width > MAX_INPUT_WIDTH) {
            val scaleFactor = MAX_INPUT_WIDTH / raw.width.toFloat()
            scaled =
                Bitmap.createScaledBitmap(
                    raw,
                    MAX_INPUT_WIDTH,
                    (raw.height * scaleFactor).toInt().coerceAtLeast(1),
                    true,
                )
        }
        return if (scaled.config == Bitmap.Config.ARGB_8888) {
            scaled
        } else {
            scaled.copy(Bitmap.Config.ARGB_8888, false).also {
                if (scaled !== raw) scaled.recycle()
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
            prepareBitmap(raw).also { prepared ->
                if (prepared !== raw) raw.recycle()
            }
        }.getOrNull()

    override fun onCleared() {
        super.onCleared()
        val existing = helper
        helper = null
        engineExecutor.execute {
            existing?.clearInteractiveSegmenter()
            engineExecutor.shutdown()
        }
    }

    companion object {
        private const val MAX_INPUT_WIDTH = 640

        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return InteractiveSegmentationViewModel(appContext.applicationContext) as T
                }
            }
    }
}
