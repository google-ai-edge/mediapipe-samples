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

package com.google.mediapipe.examples.textembedder

import android.content.Context
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

enum class EmbedderModel(val index: Int, val displayName: String, val description: String) {
    MOBILE_BERT(
        index = TextEmbedderHelper.MODEL_MOBILE_BERT,
        displayName = "MobileBERT",
        description = "Contextual 512d transformer embeddings for full sentences",
    ),
    AVERAGE_WORD(
        index = TextEmbedderHelper.MODEL_AVERAGE_WORD,
        displayName = "Average Word",
        description = "Fast lightweight 16d word-average embeddings",
    ),
}

enum class EmbedderDelegate(val index: Int, val displayName: String) {
    CPU(TextEmbedderHelper.DELEGATE_CPU, "CPU"),
    GPU(TextEmbedderHelper.DELEGATE_GPU, "GPU"),
}

data class TextPairPreset(
    val id: String,
    val label: String,
    val firstText: String,
    val secondText: String,
)

data class TextEmbedderUiState(
    val firstText: String = DefaultPresets.first().firstText,
    val secondText: String = DefaultPresets.first().secondText,
    val model: EmbedderModel = EmbedderModel.MOBILE_BERT,
    val delegate: EmbedderDelegate = EmbedderDelegate.CPU,
    val isModelLoading: Boolean = true,
    val isComparing: Boolean = false,
    val isReady: Boolean = false,
    val result: TextEmbedderHelper.ResultBundle? = null,
    val errorMessage: String? = null,
    val errorIsGpuFallback: Boolean = false,
) {
    val isBusy: Boolean
        get() = isModelLoading || isComparing

    val canCompare: Boolean
        get() = !isBusy && isReady && firstText.isNotBlank() && secondText.isNotBlank()
}

val DefaultPresets: List<TextPairPreset> =
    listOf(
        TextPairPreset(
            id = "mediapipe",
            label = "MediaPipe",
            firstText =
                "MediaPipe offers cross-platform, customizable ML solutions for live and streaming media.",
            secondText =
                "I use MediaPipe to create amazing things with text, audio, and visual content.",
        ),
        TextPairPreset(
            id = "similar",
            label = "Similar",
            firstText =
                "The golden retriever ran across the sunny sandy beach toward the ocean.",
            secondText =
                "A happy dog sprinted along the ocean shoreline on the warm sand.",
        ),
        TextPairPreset(
            id = "unrelated",
            label = "Unrelated",
            firstText =
                "The golden retriever ran across the sunny sandy beach toward the ocean.",
            secondText =
                "The federal reserve adjusted benchmark interest rates following the quarterly inflation report.",
        ),
        TextPairPreset(
            id = "identical",
            label = "Identical",
            firstText = "On-device machine learning keeps user data private and responsive.",
            secondText = "On-device machine learning keeps user data private and responsive.",
        ),
    )

/** ViewModel that owns the [TextEmbedderHelper] off the main thread and manages UI state. */
class TextEmbedderViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    // Serialize native MediaPipe calls onto a single background thread.
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(TextEmbedderUiState())
    val uiState: StateFlow<TextEmbedderUiState> = _uiState.asStateFlow()

    private var helper: TextEmbedderHelper? = null
    private var activeJob: Job? = null

    private val listener =
        object : TextEmbedderHelper.EmbedderListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        isComparing = false,
                        isReady = false,
                        errorMessage = error,
                        errorIsGpuFallback = errorCode == TextEmbedderHelper.GPU_ERROR,
                    )
                }
            }
        }

    init {
        prepareEmbedder()
    }

    fun updateFirstText(text: String) {
        _uiState.update {
            if (it.firstText == text) it else it.copy(firstText = text, result = null)
        }
    }

    fun updateSecondText(text: String) {
        _uiState.update {
            if (it.secondText == text) it else it.copy(secondText = text, result = null)
        }
    }

    fun swapTexts() {
        _uiState.update {
            it.copy(firstText = it.secondText, secondText = it.firstText, result = null)
        }
    }

    fun applyPreset(preset: TextPairPreset) {
        _uiState.update {
            it.copy(firstText = preset.firstText, secondText = preset.secondText, result = null)
        }
    }

    fun selectModel(model: EmbedderModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, result = null) }
        prepareEmbedder()
    }

    fun selectDelegate(delegate: EmbedderDelegate) {
        if (_uiState.value.delegate == delegate) return
        _uiState.update { it.copy(delegate = delegate, result = null) }
        prepareEmbedder()
    }

    fun switchToCpuAndRetry() {
        _uiState.update {
            it.copy(
                delegate = EmbedderDelegate.CPU,
                errorMessage = null,
                errorIsGpuFallback = false,
                result = null,
            )
        }
        prepareEmbedder()
    }

    fun retryInitialization() {
        prepareEmbedder()
    }

    fun compare() {
        val snapshot = _uiState.value
        if (!snapshot.canCompare) return

        activeJob?.cancel()
        _uiState.update { it.copy(isComparing = true, errorMessage = null, errorIsGpuFallback = false) }
        activeJob =
            viewModelScope.launch {
                val bundle =
                    withContext(engineDispatcher) {
                        helper?.compare(snapshot.firstText, snapshot.secondText)
                    }
                _uiState.update {
                    if (bundle != null) {
                        it.copy(isComparing = false, result = bundle)
                    } else {
                        it.copy(
                            isComparing = false,
                            errorMessage = "Embedder is not initialized.",
                            errorIsGpuFallback = false,
                        )
                    }
                }
            }
    }

    private fun prepareEmbedder() {
        val targetModel = _uiState.value.model
        val targetDelegate = _uiState.value.delegate
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isModelLoading = true,
                isReady = false,
                errorMessage = null,
                errorIsGpuFallback = false,
            )
        }
        activeJob =
            viewModelScope.launch {
                val ready =
                    withContext(engineDispatcher) {
                        val existing = helper
                        if (existing == null) {
                            val created =
                                TextEmbedderHelper(
                                    context = context,
                                    currentDelegate = targetDelegate.index,
                                    currentModel = targetModel.index,
                                    listener = listener,
                                )
                            helper = created
                            created.isReady
                        } else {
                            existing.currentDelegate = targetDelegate.index
                            existing.currentModel = targetModel.index
                            existing.setupTextEmbedder()
                        }
                    }
                _uiState.update { state ->
                    state.copy(isModelLoading = false, isReady = ready)
                }
            }
    }

    override fun onCleared() {
        super.onCleared()
        val existing = helper
        helper = null
        engineExecutor.execute {
            existing?.clearTextEmbedder()
            engineExecutor.shutdown()
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TextEmbedderViewModel(context.applicationContext) as T
                }
            }
    }
}
