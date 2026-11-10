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

package com.google.mediapipe.examples.languagedetector

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

enum class MaxResultsOption(val label: String, val count: Int) {
    TOP_1("1", 1),
    TOP_3("3", 3),
    TOP_5("5", 5),
}

data class TextPreset(
    val id: String,
    val label: String,
    val text: String,
)

data class LanguageDetectorUiState(
    val inputText: String = DefaultPresets.first().text,
    val maxResultsOption: MaxResultsOption = MaxResultsOption.TOP_5,
    val isModelLoading: Boolean = true,
    val isDetecting: Boolean = false,
    val isReady: Boolean = false,
    val result: LanguageDetectorHelper.ResultBundle? = null,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean
        get() = isModelLoading || isDetecting

    val canDetect: Boolean
        get() = !isBusy && isReady && inputText.isNotBlank()
}

val DefaultPresets: List<TextPreset> =
    listOf(
        TextPreset(
            id = "chinese",
            label = "中文",
            text = "你好！我爱小猫",
        ),
        TextPreset(
            id = "english",
            label = "English",
            text =
                "MediaPipe offers cross-platform, customizable ML solutions for live and streaming media.",
        ),
        TextPreset(
            id = "french",
            label = "Français",
            text = "Bonjour tout le monde, comment allez-vous aujourd'hui ?",
        ),
        TextPreset(
            id = "spanish",
            label = "Español",
            text = "Hola, me encanta aprender nuevos idiomas con inteligencia artificial.",
        ),
        TextPreset(
            id = "japanese",
            label = "日本語",
            text = "こんにちは、今日はとても良い天気ですね。",
        ),
    )

/** ViewModel that owns the [LanguageDetectorHelper] off the main thread and manages UI state. */
class LanguageDetectorViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    // Serialize native MediaPipe calls onto a single background thread.
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(LanguageDetectorUiState())
    val uiState: StateFlow<LanguageDetectorUiState> = _uiState.asStateFlow()

    private var helper: LanguageDetectorHelper? = null
    private var activeJob: Job? = null

    private val listener =
        object : LanguageDetectorHelper.TextResultsListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        isDetecting = false,
                        isReady = false,
                        errorMessage = error,
                    )
                }
            }
        }

    init {
        prepareDetector()
    }

    fun updateInputText(text: String) {
        _uiState.update {
            if (it.inputText == text) it else it.copy(inputText = text, result = null)
        }
    }

    fun applyPreset(preset: TextPreset) {
        _uiState.update { it.copy(inputText = preset.text, result = null) }
    }

    fun selectMaxResults(option: MaxResultsOption) {
        if (_uiState.value.maxResultsOption == option) return
        _uiState.update { it.copy(maxResultsOption = option, result = null) }
        prepareDetector()
    }

    fun retryInitialization() {
        prepareDetector()
    }

    fun detect() {
        val snapshot = _uiState.value
        if (!snapshot.canDetect) return

        activeJob?.cancel()
        _uiState.update { it.copy(isDetecting = true, errorMessage = null) }
        activeJob =
            viewModelScope.launch {
                val bundle = withContext(engineDispatcher) { helper?.detect(snapshot.inputText) }
                _uiState.update {
                    if (bundle != null) {
                        it.copy(isDetecting = false, result = bundle)
                    } else {
                        it.copy(
                            isDetecting = false,
                            errorMessage = "Language detector is not initialized.",
                        )
                    }
                }
            }
    }

    private fun prepareDetector() {
        val targetMax = _uiState.value.maxResultsOption.count
        activeJob?.cancel()
        _uiState.update {
            it.copy(
                isModelLoading = true,
                isReady = false,
                errorMessage = null,
            )
        }
        activeJob =
            viewModelScope.launch {
                val ready =
                    withContext(engineDispatcher) {
                        val existing = helper
                        if (existing == null) {
                            val created =
                                LanguageDetectorHelper(
                                    context = context,
                                    maxResults = targetMax,
                                    currentDelegate = LanguageDetectorHelper.DELEGATE_CPU,
                                    listener = listener,
                                )
                            helper = created
                            created.isReady
                        } else {
                            existing.maxResults = targetMax
                            existing.currentDelegate = LanguageDetectorHelper.DELEGATE_CPU
                            existing.initDetector()
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
            existing?.clearLanguageDetector()
            engineExecutor.shutdown()
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return LanguageDetectorViewModel(appContext.applicationContext) as T
                }
            }
    }
}
