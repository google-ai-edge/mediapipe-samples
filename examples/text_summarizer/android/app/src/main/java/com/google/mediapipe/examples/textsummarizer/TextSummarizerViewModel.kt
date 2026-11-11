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

package com.google.mediapipe.examples.textsummarizer

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizer.TextSummarizerOptions.Mode
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SummarizerPreset(
    val id: String,
    val label: String,
    val text: String,
)

val DefaultSummarizerPresets =
    listOf(
        SummarizerPreset(
            id = "mediapipe",
            label = "On-device AI",
            text =
                "MediaPipe Tasks provides cross-platform, customizable on-device machine learning solutions for mobile, web, and desktop developers. Instead of sending raw audio, camera frames, or sensitive documents to remote cloud servers, apps can run quantized LiteRT and LiteRT-LM models directly on phone hardware. Running models locally lowers network bandwidth costs, keeps private user data offline, and ensures predictable sub-second latency even in airplane mode or poor connectivity. Developers can swap quantized bundles tailored to CPU, GPU, or NPU accelerators while preserving a single Kotlin or Swift application codebase across devices.",
        ),
        SummarizerPreset(
            id = "meeting",
            label = "Project sync",
            text =
                "During Tuesday's mobile engineering review, the team finalized the Compose Material 3 migration across all fourteen computer vision samples and scheduled regression testing on Pixel and Galaxy devices. Priya will update the continuous integration matrix to verify downloaded model SHA-256 digests before emulator tests run, while Marcus will audit dark-theme contrast ratios and keyboard resizing behavior prior to the release branch cut on Friday.",
        ),
        SummarizerPreset(
            id = "weather",
            label = "Solar storm",
            text =
                "A severe geomagnetic storm reached Earth yesterday afternoon after two coronal mass ejections merged in interplanetary space. Forecasters at the Space Weather Prediction Center reported vivid auroras visible much farther south than usual across North America and Europe. Grid operators temporarily switched high-latitude transformers into protective monitoring modes, while aviation routers adjusted polar flight communications as a standard precaution.",
        ),
    )

enum class SummaryModeOption(val mode: Mode, val label: String) {
    KEYPOINTS(Mode.KEYPOINTS, "Keypoints"),
    TLDR(Mode.TLDR, "TL;DR"),
}

enum class MaxTokensOption(val count: Int, val label: String) {
    COMPACT(512, "512"),
    BALANCED(1024, "1024"),
    EXTENDED(2048, "2048"),
}

data class TextSummarizerUiState(
    val inputText: String = DefaultSummarizerPresets.first().text,
    val modeOption: SummaryModeOption = SummaryModeOption.KEYPOINTS,
    val maxTokensOption: MaxTokensOption = MaxTokensOption.EXTENDED,
    val isModelLoading: Boolean = true,
    val isSummarizing: Boolean = false,
    val streamingText: String = "",
    val result: TextSummarizerHelper.ResultBundle? = null,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean
        get() = isModelLoading || isSummarizing

    val canSummarize: Boolean
        get() = !isBusy && inputText.isNotBlank()
}

class TextSummarizerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(TextSummarizerUiState())
    val uiState: StateFlow<TextSummarizerUiState> = _uiState.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val helper = TextSummarizerHelper(application, initOnCreate = false)

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

    fun applyPreset(preset: SummarizerPreset) {
        _uiState.update {
            it.copy(
                inputText = preset.text,
                streamingText = "",
                result = null,
                errorMessage = null,
            )
        }
    }

    fun selectMode(option: SummaryModeOption) {
        if (_uiState.value.modeOption == option || _uiState.value.isBusy) return
        _uiState.update {
            it.copy(
                modeOption = option,
                isModelLoading = true,
                streamingText = "",
                result = null,
                errorMessage = null,
            )
        }
        helper.mode = option.mode
        initializeEngine()
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

    fun summarize(streaming: Boolean = false) {
        val snapshot = _uiState.value
        val text = snapshot.inputText.trim()
        if (snapshot.isBusy || text.isEmpty()) return

        _uiState.update {
            it.copy(
                isSummarizing = true,
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
                            val deferred = CompletableDeferred<TextSummarizerHelper.ResultBundle>()
                            helper.summarizeStreaming(
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
                            isSummarizing = false,
                            streamingText = "",
                            result = outcome,
                        )
                    }
                } else {
                    val outcome = withContext(dispatcher) { helper.summarize(text) }
                    _uiState.update {
                        it.copy(
                            isSummarizing = false,
                            result = outcome,
                        )
                    }
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Summarization error; reinitializing engine", t)
                _uiState.update {
                    it.copy(
                        isSummarizing = false,
                        streamingText = "",
                        errorMessage = t.message ?: "Summarization failed; resetting model.",
                    )
                }
                withContext(dispatcher) { helper.initSummarizer() }
            }
        }
    }

    private fun initializeEngine() {
        viewModelScope.launch {
            val ready = withContext(dispatcher) { helper.initSummarizer() }
            _uiState.update {
                it.copy(
                    isModelLoading = false,
                    errorMessage =
                        if (ready) null else "Failed to initialize TextSummarizer model.",
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        executor.execute { helper.clearSummarizer() }
        executor.shutdown()
    }

    companion object {
        private const val TAG = "TextSummarizerVM"
    }
}
