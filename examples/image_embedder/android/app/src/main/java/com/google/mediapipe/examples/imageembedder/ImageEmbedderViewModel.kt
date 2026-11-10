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

package com.google.mediapipe.examples.imageembedder

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
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ImageEmbedderModel(val index: Int, val displayName: String, val shortLabel: String) {
    SMALL(ImageEmbedderHelper.MODEL_SMALL, "MobileNet V3 Small", "Small"),
    LARGE(ImageEmbedderHelper.MODEL_LARGE, "MobileNet V3 Large", "Large"),
}

enum class EmbedderDelegate(val index: Int, val displayName: String) {
    CPU(ImageEmbedderHelper.DELEGATE_CPU, "CPU"),
    GPU(ImageEmbedderHelper.DELEGATE_GPU, "GPU"),
}

enum class ImageSamplePreset(val id: String, val label: String, val firstAsset: String, val secondAsset: String) {
    SIMILAR(
        id = "similar",
        label = "Similar (Pizzas)",
        firstAsset = ImageEmbedderHelper.ASSET_AMERICAN_PIZZA,
        secondAsset = ImageEmbedderHelper.ASSET_ITALY_PIZZA,
    ),
    UNRELATED(
        id = "unrelated",
        label = "Unrelated (Pizza vs Mug)",
        firstAsset = ImageEmbedderHelper.ASSET_AMERICAN_PIZZA,
        secondAsset = ImageEmbedderHelper.ASSET_CUP,
    ),
}

data class ImageComparisonResult(
    val similarity: Double,
    val inferenceTimeMs: Long,
    val loadTimeMs: Long,
)

data class ImageEmbedderUiState(
    val model: ImageEmbedderModel = ImageEmbedderModel.SMALL,
    val delegate: EmbedderDelegate = EmbedderDelegate.CPU,
    val firstBitmap: Bitmap? = null,
    val secondBitmap: Bitmap? = null,
    val firstLabel: String = "American pizza",
    val secondLabel: String = "Italian pizza",
    val isModelReady: Boolean = false,
    val isLoadingModel: Boolean = true,
    val isComparing: Boolean = false,
    val result: ImageComparisonResult? = null,
    val errorMessage: String? = null,
    val errorCode: Int = ImageEmbedderHelper.OTHER_ERROR,
)

/** ViewModel that serializes image embedding work off the main thread and exposes UI state. */
class ImageEmbedderViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(ImageEmbedderUiState())
    val uiState: StateFlow<ImageEmbedderUiState> = _uiState.asStateFlow()

    private var helper: ImageEmbedderHelper? = null

    private val listener =
        object : ImageEmbedderHelper.EmbedderListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isLoadingModel = false,
                        isComparing = false,
                        isModelReady = false,
                        errorMessage = error,
                        errorCode = errorCode,
                    )
                }
            }
        }

    init {
        viewModelScope.launch {
            val pair =
                withContext(engineDispatcher) {
                    loadAssetBitmap(ImageEmbedderHelper.ASSET_AMERICAN_PIZZA) to
                        loadAssetBitmap(ImageEmbedderHelper.ASSET_ITALY_PIZZA)
                }
            _uiState.update {
                it.copy(
                    firstBitmap = pair.first,
                    secondBitmap = pair.second,
                )
            }
            initializeEmbedder()
        }
    }

    fun selectPreset(preset: ImageSamplePreset) {
        viewModelScope.launch {
            val pair =
                withContext(engineDispatcher) {
                    loadAssetBitmap(preset.firstAsset) to loadAssetBitmap(preset.secondAsset)
                }
            val labels =
                when (preset) {
                    ImageSamplePreset.SIMILAR -> "American pizza" to "Italian pizza"
                    ImageSamplePreset.UNRELATED -> "American pizza" to "Coffee mug"
                }
            _uiState.update {
                it.copy(
                    firstBitmap = pair.first,
                    secondBitmap = pair.second,
                    firstLabel = labels.first,
                    secondLabel = labels.second,
                    result = null,
                    errorMessage = null,
                )
            }
        }
    }

    fun setPickedImage(slot: Int, uri: Uri) {
        viewModelScope.launch {
            val bitmap = withContext(engineDispatcher) { decodeUriBitmap(uri) }
            if (bitmap == null) {
                _uiState.update { it.copy(errorMessage = "Failed to decode selected image.") }
                return@launch
            }
            _uiState.update { state ->
                when (slot) {
                    1 -> state.copy(firstBitmap = bitmap, firstLabel = "Custom photo 1", result = null, errorMessage = null)
                    else -> state.copy(secondBitmap = bitmap, secondLabel = "Custom photo 2", result = null, errorMessage = null)
                }
            }
        }
    }

    fun swapImages() {
        _uiState.update {
            it.copy(
                firstBitmap = it.secondBitmap,
                secondBitmap = it.firstBitmap,
                firstLabel = it.secondLabel,
                secondLabel = it.firstLabel,
                result = null,
            )
        }
    }

    fun selectModel(model: ImageEmbedderModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, result = null, errorMessage = null) }
        initializeEmbedder()
    }

    fun selectDelegate(delegate: EmbedderDelegate) {
        if (_uiState.value.delegate == delegate) return
        _uiState.update { it.copy(delegate = delegate, result = null, errorMessage = null) }
        initializeEmbedder()
    }

    fun switchToCpuDelegate() {
        selectDelegate(EmbedderDelegate.CPU)
    }

    fun retryInitialization() {
        initializeEmbedder()
    }

    fun compareImages() {
        val state = _uiState.value
        val img1 = state.firstBitmap ?: return
        val img2 = state.secondBitmap ?: return
        if (!state.isModelReady || state.isLoadingModel || state.isComparing) return

        _uiState.update { it.copy(isComparing = true, errorMessage = null) }
        viewModelScope.launch {
            val bundle = withContext(engineDispatcher) { helper?.embed(img1, img2) }
            _uiState.update { current ->
                if (bundle != null) {
                    current.copy(
                        isComparing = false,
                        result =
                            ImageComparisonResult(
                                similarity = bundle.similarity,
                                inferenceTimeMs = bundle.inferenceTime,
                                loadTimeMs = bundle.loadTimeMs,
                            ),
                    )
                } else {
                    current.copy(
                        isComparing = false,
                        errorMessage = current.errorMessage ?: "Unable to embed images.",
                    )
                }
            }
        }
    }

    private fun initializeEmbedder() {
        val snapshot = _uiState.value
        _uiState.update { it.copy(isLoadingModel = true, isModelReady = false, errorMessage = null) }
        viewModelScope.launch {
            val ready =
                withContext(engineDispatcher) {
                    val existing = helper
                    if (existing == null || existing.isClosed) {
                        val created =
                            ImageEmbedderHelper(
                                context = context,
                                currentDelegate = snapshot.delegate.index,
                                currentModel = snapshot.model.index,
                                listener = listener,
                            )
                        helper = created
                        !created.isClosed
                    } else {
                        existing.currentDelegate = snapshot.delegate.index
                        existing.currentModel = snapshot.model.index
                        existing.setupImageEmbedder() && !existing.isClosed
                    }
                }
            _uiState.update {
                it.copy(
                    isLoadingModel = false,
                    isModelReady = ready,
                )
            }
        }
    }

    private fun loadAssetBitmap(assetName: String): Bitmap? =
        context.assets.open(assetName).use { stream -> BitmapFactory.decodeStream(stream) }

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
        val existing = helper
        helper = null
        engineExecutor.execute {
            existing?.clearImageEmbedder()
            engineExecutor.shutdown()
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ImageEmbedderViewModel(appContext.applicationContext) as T
                }
            }
    }
}
