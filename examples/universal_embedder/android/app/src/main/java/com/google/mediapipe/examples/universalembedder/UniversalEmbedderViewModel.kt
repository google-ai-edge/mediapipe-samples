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

package com.google.mediapipe.examples.universalembedder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class UniversalDelegateOption(val id: Int, val label: String) {
    CPU(UniversalEmbedderHelper.DELEGATE_CPU, "CPU"),
    GPU(UniversalEmbedderHelper.DELEGATE_GPU, "GPU"),
}

enum class EmbedInputMode(val label: String) {
    TEXT("Text"),
    IMAGE("Image"),
}

data class SamplePhoto(
    val id: String,
    val label: String,
    val assetPath: String,
)

val BundledSamplePhotos: List<SamplePhoto> =
    listOf(
        SamplePhoto("cute_cat", "Cute Cat", "images/cute_cat.jpg"),
        SamplePhoto("sunny_beach", "Sunny Beach", "images/sunny_beach.jpg"),
        SamplePhoto("snowy_mountain", "Snowy Mountain", "images/snowy_mountain.jpg"),
        SamplePhoto("coffee_mug", "Coffee Mug", "images/coffee_mug.jpg"),
        SamplePhoto("fast_car", "Fast Car", "images/fast_car.jpg"),
        SamplePhoto("blue_sky", "Blue Sky", "images/blue_sky.jpg"),
        SamplePhoto("green_tree", "Green Tree", "images/green_tree.jpg"),
        SamplePhoto("open_book", "Open Book", "images/open_book.jpg"),
        SamplePhoto("red_apple", "Red Apple", "images/red_apple.jpg"),
        SamplePhoto("yellow_banana", "Yellow Banana", "images/yellow_banana.jpg"),
    )

data class EmbedSlotUi(
    val mode: EmbedInputMode,
    val text: String,
    val samplePhoto: SamplePhoto,
    val customBitmap: Bitmap? = null,
    val displayBitmap: Bitmap? = null,
) {
    val isReady: Boolean
        get() =
            when (mode) {
                EmbedInputMode.TEXT -> text.isNotBlank()
                EmbedInputMode.IMAGE -> (customBitmap ?: displayBitmap) != null
            }

    val activeBitmap: Bitmap?
        get() = customBitmap ?: displayBitmap
}

data class MultimodalPreset(
    val id: String,
    val label: String,
    val firstMode: EmbedInputMode,
    val firstText: String,
    val firstPhoto: SamplePhoto,
    val secondMode: EmbedInputMode,
    val secondText: String,
    val secondPhoto: SamplePhoto,
)

val DefaultMultimodalPresets: List<MultimodalPreset> =
    listOf(
        MultimodalPreset(
            id = "cross_modal",
            label = "Image ↔ Text",
            firstMode = EmbedInputMode.IMAGE,
            firstText = "A fluffy cat relaxing outdoors",
            firstPhoto = BundledSamplePhotos.first { it.id == "cute_cat" },
            secondMode = EmbedInputMode.TEXT,
            secondText = "a cute fluffy cat",
            secondPhoto = BundledSamplePhotos.first { it.id == "fast_car" },
        ),
        MultimodalPreset(
            id = "beach_match",
            label = "Beach ↔ Text",
            firstMode = EmbedInputMode.IMAGE,
            firstText = "A warm sandy beach by blue ocean waves",
            firstPhoto = BundledSamplePhotos.first { it.id == "sunny_beach" },
            secondMode = EmbedInputMode.TEXT,
            secondText = "warm sand on a sunny ocean beach",
            secondPhoto = BundledSamplePhotos.first { it.id == "snowy_mountain" },
        ),
        MultimodalPreset(
            id = "text_text",
            label = "Text ↔ Text",
            firstMode = EmbedInputMode.TEXT,
            firstText = "A golden retriever ran across the sunny sandy beach.",
            firstPhoto = BundledSamplePhotos.first { it.id == "sunny_beach" },
            secondMode = EmbedInputMode.TEXT,
            secondText = "A happy dog sprinted along the ocean shoreline on warm sand.",
            secondPhoto = BundledSamplePhotos.first { it.id == "cute_cat" },
        ),
        MultimodalPreset(
            id = "image_image",
            label = "Image ↔ Image",
            firstMode = EmbedInputMode.IMAGE,
            firstText = "Sunny ocean beach",
            firstPhoto = BundledSamplePhotos.first { it.id == "sunny_beach" },
            secondMode = EmbedInputMode.IMAGE,
            secondText = "Snowy mountain peak",
            secondPhoto = BundledSamplePhotos.first { it.id == "snowy_mountain" },
        ),
    )

data class UniversalEmbedderUiState(
    val firstSlot: EmbedSlotUi =
        EmbedSlotUi(
            mode = DefaultMultimodalPresets.first().firstMode,
            text = DefaultMultimodalPresets.first().firstText,
            samplePhoto = DefaultMultimodalPresets.first().firstPhoto,
        ),
    val secondSlot: EmbedSlotUi =
        EmbedSlotUi(
            mode = DefaultMultimodalPresets.first().secondMode,
            text = DefaultMultimodalPresets.first().secondText,
            samplePhoto = DefaultMultimodalPresets.first().secondPhoto,
        ),
    val delegateOption: UniversalDelegateOption =
        if (UniversalEmbedderHelper.isAndroidEmulator()) {
            UniversalDelegateOption.CPU
        } else {
            UniversalDelegateOption.GPU
        },
    val isModelLoading: Boolean = true,
    val isComparing: Boolean = false,
    val isReady: Boolean = false,
    val result: UniversalEmbedderHelper.ResultBundle? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
) {
    val isBusy: Boolean
        get() = isModelLoading || isComparing

    val canCompare: Boolean
        get() = !isBusy && isReady && firstSlot.isReady && secondSlot.isReady
}

class UniversalEmbedderViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(UniversalEmbedderUiState())
    val uiState: StateFlow<UniversalEmbedderUiState> = _uiState.asStateFlow()

    private val sampleThumbnailCache = mutableMapOf<String, Bitmap>()
    private var helper: UniversalEmbedderHelper? = null
    private var activeJob: Job? = null

    private var lastErrorMessage: String? = null

    private val listener =
        object : UniversalEmbedderHelper.EmbedderListener {
            override fun onError(error: String, errorCode: Int) {
                lastErrorMessage = error
            }
        }

    init {
        loadInitialSampleBitmaps()
        prepareEmbedder(allowGpuFallback = true)
    }

    private fun loadInitialSampleBitmaps() {
        val current = _uiState.value
        val firstBmp = loadSampleBitmap(current.firstSlot.samplePhoto)
        val secondBmp = loadSampleBitmap(current.secondSlot.samplePhoto)
        _uiState.update {
            it.copy(
                firstSlot = it.firstSlot.copy(displayBitmap = firstBmp),
                secondSlot = it.secondSlot.copy(displayBitmap = secondBmp),
            )
        }
    }

    private fun loadSampleBitmap(photo: SamplePhoto): Bitmap? {
        sampleThumbnailCache[photo.assetPath]?.let { return it }
        return runCatching {
                context.assets.open(photo.assetPath).use { stream ->
                    BitmapFactory.decodeStream(stream)?.let { raw ->
                        scaleDownBitmap(raw, maxSidePx = 768)
                    }
                }
            }
            .onSuccess { bmp ->
                if (bmp != null) sampleThumbnailCache[photo.assetPath] = bmp
            }
            .getOrNull()
    }

    fun selectPreset(preset: MultimodalPreset) {
        val firstBmp = loadSampleBitmap(preset.firstPhoto)
        val secondBmp = loadSampleBitmap(preset.secondPhoto)
        _uiState.update {
            it.copy(
                firstSlot =
                    EmbedSlotUi(
                        mode = preset.firstMode,
                        text = preset.firstText,
                        samplePhoto = preset.firstPhoto,
                        customBitmap = null,
                        displayBitmap = firstBmp,
                    ),
                secondSlot =
                    EmbedSlotUi(
                        mode = preset.secondMode,
                        text = preset.secondText,
                        samplePhoto = preset.secondPhoto,
                        customBitmap = null,
                        displayBitmap = secondBmp,
                    ),
                result = null,
                errorMessage = null,
            )
        }
    }

    fun setSlotMode(isFirst: Boolean, mode: EmbedInputMode) {
        updateSlot(isFirst) { slot ->
            val bmp = slot.displayBitmap ?: loadSampleBitmap(slot.samplePhoto)
            slot.copy(mode = mode, displayBitmap = bmp)
        }
    }

    fun updateSlotText(isFirst: Boolean, text: String) {
        updateSlot(isFirst) { slot -> slot.copy(text = text) }
    }

    fun selectSlotSamplePhoto(isFirst: Boolean, photo: SamplePhoto) {
        val bmp = loadSampleBitmap(photo)
        updateSlot(isFirst) { slot ->
            slot.copy(
                mode = EmbedInputMode.IMAGE,
                samplePhoto = photo,
                customBitmap = null,
                displayBitmap = bmp,
            )
        }
    }

    fun setSlotCustomImageUri(isFirst: Boolean, uri: Uri) {
        viewModelScope.launch {
            val decoded =
                withContext(engineDispatcher) {
                    runCatching {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.let { raw ->
                                    scaleDownBitmap(raw, maxSidePx = 1024)
                                }
                            }
                        }
                        .getOrNull()
                }
            if (decoded != null) {
                updateSlot(isFirst) { slot ->
                    slot.copy(mode = EmbedInputMode.IMAGE, customBitmap = decoded)
                }
            }
        }
    }

    fun swapSlots() {
        _uiState.update {
            it.copy(firstSlot = it.secondSlot, secondSlot = it.firstSlot, result = null)
        }
    }

    fun selectDelegate(option: UniversalDelegateOption) {
        if (_uiState.value.delegateOption == option || _uiState.value.isBusy) return
        _uiState.update {
            it.copy(delegateOption = option, result = null, errorMessage = null, noticeMessage = null)
        }
        prepareEmbedder(allowGpuFallback = false)
    }

    fun retryInitialization() {
        prepareEmbedder(allowGpuFallback = true)
    }

    fun compare() {
        val state = _uiState.value
        if (!state.canCompare) return
        val firstInput = toHelperInput(state.firstSlot) ?: return
        val secondInput = toHelperInput(state.secondSlot) ?: return

        activeJob?.cancel()
        _uiState.update { it.copy(isComparing = true, errorMessage = null) }
        activeJob =
            viewModelScope.launch {
                val bundle =
                    withContext(engineDispatcher) {
                        runCatching { helper?.compare(firstInput, secondInput) }
                            .onFailure { Log.e(TAG, "Comparison failed", it) }
                            .getOrNull()
                    }
                _uiState.update {
                    if (bundle != null) {
                        it.copy(isComparing = false, result = bundle)
                    } else {
                        it.copy(
                            isComparing = false,
                            errorMessage = lastErrorMessage ?: "Unable to compute embeddings.",
                        )
                    }
                }
            }
    }

    private fun toHelperInput(slot: EmbedSlotUi): UniversalEmbedderHelper.Input? =
        when (slot.mode) {
            EmbedInputMode.TEXT ->
                slot.text.trim().takeIf { it.isNotEmpty() }?.let {
                    UniversalEmbedderHelper.Input.Text(it)
                }
            EmbedInputMode.IMAGE ->
                slot.activeBitmap?.let { UniversalEmbedderHelper.Input.Image(it) }
        }

    private fun updateSlot(isFirst: Boolean, transform: (EmbedSlotUi) -> EmbedSlotUi) {
        _uiState.update { state ->
            if (isFirst) state.copy(firstSlot = transform(state.firstSlot), result = null)
            else state.copy(secondSlot = transform(state.secondSlot), result = null)
        }
    }

    private fun prepareEmbedder(allowGpuFallback: Boolean) {
        val targetDelegate = _uiState.value.delegateOption
        activeJob?.cancel()
        lastErrorMessage = null
        _uiState.update {
            it.copy(isModelLoading = true, isReady = false, errorMessage = null)
        }
        activeJob =
            viewModelScope.launch {
                val outcome =
                    withContext(engineDispatcher) {
                        val instance =
                            helper
                                ?: UniversalEmbedderHelper(
                                    context = context,
                                    currentDelegate = targetDelegate.id,
                                    listener = listener,
                                ).also { helper = it }
                        instance.currentDelegate = targetDelegate.id
                        if (instance.setupUniversalEmbedder()) {
                            Triple(true, targetDelegate, null as String?)
                        } else if (
                            allowGpuFallback && targetDelegate == UniversalDelegateOption.GPU
                        ) {
                            Log.w(TAG, "GPU initialization unavailable; falling back to CPU")
                            instance.currentDelegate = UniversalEmbedderHelper.DELEGATE_CPU
                            val cpuOk = instance.setupUniversalEmbedder()
                            Triple(
                                cpuOk,
                                if (cpuOk) UniversalDelegateOption.CPU else targetDelegate,
                                if (cpuOk) "GPU unavailable on this device — using CPU." else null,
                            )
                        } else {
                            Triple(false, targetDelegate, null as String?)
                        }
                    }

                val (ready, activeDelegate, notice) = outcome
                _uiState.update { state ->
                    state.copy(
                        delegateOption = activeDelegate,
                        isModelLoading = false,
                        isReady = ready,
                        noticeMessage = notice,
                        errorMessage = if (ready) null else lastErrorMessage ?: "Model failed to load.",
                    )
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        val existing = helper
        helper = null
        engineExecutor.execute {
            existing?.clearUniversalEmbedder()
            engineExecutor.shutdown()
        }
    }

    private fun scaleDownBitmap(bitmap: Bitmap, maxSidePx: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled =
            if (longest <= maxSidePx) bitmap
            else {
                val ratio = maxSidePx.toFloat() / longest
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * ratio).toInt().coerceAtLeast(1),
                    (bitmap.height * ratio).toInt().coerceAtLeast(1),
                    true,
                )
            }
        return if (scaled.config == Bitmap.Config.ARGB_8888) scaled
        else scaled.copy(Bitmap.Config.ARGB_8888, false)
    }

    companion object {
        private const val TAG = "UniversalEmbedderVM"

        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return UniversalEmbedderViewModel(context.applicationContext) as T
                }
            }
    }
}
