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

package com.google.mediapipe.examples.textclassifier

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

enum class ClassifierModel(
    val modelPath: String,
    val displayName: String,
    val description: String,
) {
    MOBILEBERT(
        modelPath = TextClassifierHelper.MOBILEBERT,
        displayName = "MobileBERT",
        description = "Contextual transformer classifier for high sentiment accuracy",
    ),
    WORD_VEC(
        modelPath = TextClassifierHelper.WORD_VEC,
        displayName = "WordVec",
        description = "Average WordVec · compact bag-of-words sentiment classifier",
    ),
}

data class TextPreset(
    val id: String,
    val label: String,
    val text: String,
)

data class TextClassifierUiState(
    val inputText: String = DefaultPresets.first().text,
    val model: ClassifierModel = ClassifierModel.MOBILEBERT,
    val isModelLoading: Boolean = true,
    val isClassifying: Boolean = false,
    val isReady: Boolean = false,
    val result: TextClassifierHelper.ResultBundle? = null,
    val errorMessage: String? = null,
) {
    val isBusy: Boolean
        get() = isModelLoading || isClassifying

    val canClassify: Boolean
        get() = !isBusy && isReady && inputText.isNotBlank()
}

val DefaultPresets: List<TextPreset> =
    listOf(
        TextPreset(
            id = "android",
            label = "Android",
            text =
                "Google has released 24 versions of the Android operating system since 2008 " +
                    "and continues to make substantial investments to develop, grow, and improve the OS.",
        ),
        TextPreset(
            id = "positive",
            label = "Positive",
            text =
                "I am so thrilled and grateful for this wonderful day, everything came together beautifully!",
        ),
        TextPreset(
            id = "negative",
            label = "Negative",
            text =
                "Unflinchingly bleak and desperate; a terribly dull, frustrating experience from start to finish.",
        ),
        TextPreset(
            id = "mixed",
            label = "Mixed",
            text =
                "The cinematography is breathtaking, though the pacing drags badly in the middle act.",
        ),
    )

/** ViewModel that owns the [TextClassifierHelper] off the main thread and manages UI state. */
class TextClassifierViewModel(appContext: Context) : ViewModel() {

    private val context = appContext.applicationContext

    // Serialize native MediaPipe calls onto a single background thread.
    private val engineExecutor = Executors.newSingleThreadExecutor()
    private val engineDispatcher = engineExecutor.asCoroutineDispatcher()

    private val _uiState = MutableStateFlow(TextClassifierUiState())
    val uiState: StateFlow<TextClassifierUiState> = _uiState.asStateFlow()

    private var helper: TextClassifierHelper? = null
    private var activeJob: Job? = null

    private val listener =
        object : TextClassifierHelper.TextResultsListener {
            override fun onError(error: String, errorCode: Int) {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        isClassifying = false,
                        isReady = false,
                        errorMessage = error,
                    )
                }
            }
        }

    init {
        prepareClassifier()
    }

    fun updateInputText(text: String) {
        _uiState.update {
            if (it.inputText == text) it else it.copy(inputText = text, result = null)
        }
    }

    fun applyPreset(preset: TextPreset) {
        _uiState.update { it.copy(inputText = preset.text, result = null) }
    }

    fun selectModel(model: ClassifierModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, result = null) }
        prepareClassifier()
    }

    fun retryInitialization() {
        prepareClassifier()
    }

    fun classify() {
        val snapshot = _uiState.value
        if (!snapshot.canClassify) return

        activeJob?.cancel()
        _uiState.update { it.copy(isClassifying = true, errorMessage = null) }
        activeJob =
            viewModelScope.launch {
                val bundle = withContext(engineDispatcher) { helper?.classify(snapshot.inputText) }
                _uiState.update {
                    if (bundle != null) {
                        it.copy(isClassifying = false, result = bundle)
                    } else {
                        it.copy(
                            isClassifying = false,
                            errorMessage = "Classifier is not initialized.",
                        )
                    }
                }
            }
    }

    private fun prepareClassifier() {
        val targetModel = _uiState.value.model
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
                                TextClassifierHelper(
                                    currentModel = targetModel.modelPath,
                                    context = context,
                                    currentDelegate = TextClassifierHelper.DELEGATE_CPU,
                                    listener = listener,
                                )
                            helper = created
                            created.isReady
                        } else {
                            existing.currentModel = targetModel.modelPath
                            existing.currentDelegate = TextClassifierHelper.DELEGATE_CPU
                            existing.initClassifier()
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
            existing?.clearTextClassifier()
            engineExecutor.shutdown()
        }
    }

    companion object {
        fun factory(appContext: Context): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TextClassifierViewModel(appContext.applicationContext) as T
                }
            }
    }
}
