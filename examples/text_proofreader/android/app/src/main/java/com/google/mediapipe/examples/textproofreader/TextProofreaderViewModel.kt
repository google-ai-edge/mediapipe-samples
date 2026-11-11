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

package com.google.mediapipe.examples.textproofreader

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ProofreaderPreset(
    val id: String,
    val label: String,
    val text: String,
)

val DefaultProofreaderPresets =
    listOf(
        ProofreaderPreset(
            id = "grammar",
            label = "Grammar errors",
            text = "I seen him yesterday at store, and he dont want no help from nobody.",
        ),
        ProofreaderPreset(
            id = "tense",
            label = "Spelling & tense",
            text = "She go to school evry day and always forgeting her homework on desk.",
        ),
        ProofreaderPreset(
            id = "email",
            label = "Meeting note",
            text = "Please send me the report by tommorow morning so we can reviewed it together.",
        ),
        ProofreaderPreset(
            id = "clean",
            label = "Clean sentence",
            text = "MediaPipe helps developers build fast, on-device AI experiences on Android.",
        ),
    )

enum class MaxTokensOption(val count: Int, val label: String) {
    COMPACT(512, "512"),
    BALANCED(1024, "1024"),
    EXTENDED(2048, "2048"),
}

data class TextProofreaderUiState(
    val inputText: String = DefaultProofreaderPresets.first().text,
    val maxTokensOption: MaxTokensOption = MaxTokensOption.EXTENDED,
    val isModelLoading: Boolean = true,
    val isProofreading: Boolean = false,
    val streamingText: String = "",
    val result: TextProofreaderHelper.ResultBundle? = null,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean
        get() = isModelLoading || isProofreading

    val canProofread: Boolean
        get() = !isBusy && inputText.isNotBlank()
}

class TextProofreaderViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TextProofreaderUiState())
    val uiState: StateFlow<TextProofreaderUiState> = _uiState.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val helper = TextProofreaderHelper(application, initOnCreate = false)

    init {
        initializeEngine()
    }

    fun updateInputText(text: String) {
        if (_uiState.value.inputText == text) return
        _uiState.update {
            it.copy(
                inputText = text,
                streamingText = "",
                result = null,
                errorMessage = null,
            )
        }
    }

    fun applyPreset(preset: ProofreaderPreset) {
        _uiState.update {
            it.copy(
                inputText = preset.text,
                streamingText = "",
                result = null,
                errorMessage = null,
            )
        }
    }

    fun applyCorrectedToInput() {
        val corrected = _uiState.value.result?.proofreadText?.trim().orEmpty()
        if (corrected.isEmpty()) return
        _uiState.update {
            it.copy(
                inputText = corrected,
                streamingText = "",
                result = null,
            )
        }
    }

    fun selectMaxTokens(option: MaxTokensOption) {
        if (_uiState.value.maxTokensOption == option || _uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                maxTokensOption = option,
                isModelLoading = true,
                streamingText = "",
                result = null,
                errorMessage = null,
            )
        }
        helper.maxNumTokens = option.count
        initializeEngine()
    }

    fun retryInitialization() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isModelLoading = true, errorMessage = null) }
        initializeEngine()
    }

    fun proofread(streaming: Boolean = false) {
        val snapshot = _uiState.value
        val text = snapshot.inputText.trim()
        if (snapshot.isBusy || text.isEmpty()) return

        _uiState.update {
            it.copy(
                isProofreading = true,
                streamingText = "",
                result = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                if (streaming) {
                    val outcome =
                        withContext(dispatcher) {
                            val deferred =
                                CompletableDeferred<TextProofreaderHelper.ResultBundle>()
                            helper.proofreadStreaming(
                                text = text,
                                onChunk = { chunk ->
                                    _uiState.update { cur ->
                                        cur.copy(streamingText = cur.streamingText + chunk)
                                    }
                                },
                                onComplete = { bundle -> deferred.complete(bundle) },
                                onError = { err -> deferred.completeExceptionally(err) },
                            )
                            deferred.await()
                        }
                    _uiState.update {
                        it.copy(
                            isProofreading = false,
                            streamingText = "",
                            result = outcome,
                        )
                    }
                } else {
                    val outcome = withContext(dispatcher) { helper.proofread(text) }
                    _uiState.update {
                        it.copy(
                            isProofreading = false,
                            result = outcome,
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Proofreading error; reinitializing engine", t)
                _uiState.update {
                    it.copy(
                        isProofreading = false,
                        streamingText = "",
                        errorMessage = t.message ?: "Proofreading failed; resetting model.",
                    )
                }
                withContext(dispatcher) { helper.initProofreader() }
            }
        }
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            val ready = withContext(dispatcher) { helper.initProofreader() }
            _uiState.update {
                it.copy(
                    isModelLoading = false,
                    errorMessage =
                        if (ready) null else "Failed to initialize TextProofreader model.",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        executor.execute { helper.clearProofreader() }
        executor.shutdown()
    }

    companion object {
        private const val TAG = "TextProofreaderVM"
    }
}
