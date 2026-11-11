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
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.EmbeddingType
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextFormatContext
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextRole
import java.util.concurrent.Executors
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EmbedderModel(
    val index: Int,
    val displayName: String,
    val shortLabel: String,
    val dimensions: Int,
    val description: String,
    val supportsFormatContext: Boolean = false,
) {
    EMBEDDING_GEMMA(
        index = TextEmbedderHelper.MODEL_EMBEDDING_GEMMA,
        displayName = "Gemma 300M",
        shortLabel = "Gemma",
        dimensions = 768,
        description =
            "EmbeddingGemma 300M · 768d contextual embeddings with task-type formatting",
        supportsFormatContext = true,
    ),
    MOBILE_BERT(
        index = TextEmbedderHelper.MODEL_MOBILE_BERT,
        displayName = "MobileBERT",
        shortLabel = "MobileBERT",
        dimensions = 512,
        description = "Contextual 512d transformer embeddings for full sentences",
    ),
    UNIVERSAL_SENTENCE_ENCODER(
        index = TextEmbedderHelper.MODEL_UNIVERSAL_SENTENCE_ENCODER,
        displayName = "Universal",
        shortLabel = "Universal",
        dimensions = 100,
        description = "Universal Sentence Encoder · fast 100d sentence embeddings",
    ),
}

enum class EmbeddingContextMode(
    val id: String,
    val label: String,
    val description: String,
    val firstTaskType: EmbeddingType?,
    val firstRole: TextRole?,
    val secondTaskType: EmbeddingType?,
    val secondRole: TextRole?,
    val usesDocumentTitle: Boolean = false,
) {
    SIMILARITY(
        id = "similarity",
        label = "Similarity",
        description = "task: sentence similarity | query: <text> for both inputs",
        firstTaskType = EmbeddingType.SEMANTIC_SIMILARITY,
        firstRole = TextRole.QUERY,
        secondTaskType = EmbeddingType.SEMANTIC_SIMILARITY,
        secondRole = TextRole.QUERY,
    ),
    RETRIEVAL(
        id = "retrieval",
        label = "Query → Doc",
        description = "Asymmetric retrieval: Text A as query, Text B as titled document",
        firstTaskType = EmbeddingType.RETRIEVAL_QUERY,
        firstRole = TextRole.QUERY,
        secondTaskType = EmbeddingType.RETRIEVAL_DOCUMENT,
        secondRole = TextRole.DOCUMENT,
        usesDocumentTitle = true,
    ),
    QUESTION_ANSWERING(
        id = "qa",
        label = "Q & A",
        description = "Asymmetric Q&A: question query matched against answer passage",
        firstTaskType = EmbeddingType.QUESTION_ANSWERING,
        firstRole = TextRole.QUERY,
        secondTaskType = EmbeddingType.QUESTION_ANSWERING,
        secondRole = TextRole.DOCUMENT,
        usesDocumentTitle = true,
    ),
    CLUSTERING(
        id = "clustering",
        label = "Clustering",
        description = "task: clustering | query: <text> for grouping related passages",
        firstTaskType = EmbeddingType.CLUSTERING,
        firstRole = TextRole.QUERY,
        secondTaskType = EmbeddingType.CLUSTERING,
        secondRole = TextRole.QUERY,
    ),
    NONE(
        id = "none",
        label = "Plain text",
        description = "Embeds raw text without a TextFormatContext prompt wrapper",
        firstTaskType = null,
        firstRole = null,
        secondTaskType = null,
        secondRole = null,
    );

    fun buildFirstContext(): TextFormatContext? =
        buildContext(firstTaskType, firstRole, title = null)

    fun buildSecondContext(documentTitle: String): TextFormatContext? =
        buildContext(
            secondTaskType,
            secondRole,
            title = documentTitle.trim().takeIf { usesDocumentTitle && it.isNotEmpty() },
        )

    private fun buildContext(
        taskType: EmbeddingType?,
        role: TextRole?,
        title: String?,
    ): TextFormatContext? {
        if (taskType == null || role == null) return null
        return TextFormatContext.builder()
            .setTaskType(taskType)
            .setRole(role)
            .apply {
                if (!title.isNullOrEmpty()) {
                    setTitle(title)
                }
            }
            .build()
    }
}

data class TextPairPreset(
    val id: String,
    val label: String,
    val firstText: String,
    val secondText: String,
    val documentTitle: String = "",
    val preferredContext: EmbeddingContextMode = EmbeddingContextMode.SIMILARITY,
)

data class TextEmbedderUiState(
    val firstText: String = DefaultPresets.first().firstText,
    val secondText: String = DefaultPresets.first().secondText,
    val documentTitle: String = DefaultPresets.first().documentTitle,
    val model: EmbedderModel = EmbedderModel.EMBEDDING_GEMMA,
    val contextMode: EmbeddingContextMode = EmbeddingContextMode.SIMILARITY,
    val isModelLoading: Boolean = true,
    val isComparing: Boolean = false,
    val isReady: Boolean = false,
    val result: TextEmbedderHelper.ResultBundle? = null,
    val errorMessage: String? = null,
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
            id = "retrieval",
            label = "Query → Doc",
            firstText = "How does on-device ML keep user data private?",
            secondText =
                "On-device machine learning runs inference directly on the user's phone without sending personal text to remote servers.",
            documentTitle = "On-Device Privacy Architecture",
            preferredContext = EmbeddingContextMode.RETRIEVAL,
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

    fun updateDocumentTitle(title: String) {
        _uiState.update {
            if (it.documentTitle == title) it else it.copy(documentTitle = title, result = null)
        }
    }

    fun selectContextMode(mode: EmbeddingContextMode) {
        _uiState.update {
            if (it.contextMode == mode) it else it.copy(contextMode = mode, result = null)
        }
    }

    fun swapTexts() {
        _uiState.update {
            it.copy(firstText = it.secondText, secondText = it.firstText, result = null)
        }
    }

    fun applyPreset(preset: TextPairPreset) {
        _uiState.update {
            it.copy(
                firstText = preset.firstText,
                secondText = preset.secondText,
                documentTitle = preset.documentTitle,
                contextMode =
                    if (it.model.supportsFormatContext) preset.preferredContext
                    else it.contextMode,
                result = null,
            )
        }
    }

    fun selectModel(model: EmbedderModel) {
        if (_uiState.value.model == model) return
        _uiState.update { it.copy(model = model, result = null) }
        prepareEmbedder()
    }

    fun retryInitialization() {
        prepareEmbedder()
    }

    fun compare() {
        val snapshot = _uiState.value
        if (!snapshot.canCompare) return

        val firstCtx =
            if (snapshot.model.supportsFormatContext) snapshot.contextMode.buildFirstContext()
            else null
        val secondCtx =
            if (snapshot.model.supportsFormatContext) {
                snapshot.contextMode.buildSecondContext(snapshot.documentTitle)
            } else {
                null
            }

        activeJob?.cancel()
        _uiState.update { it.copy(isComparing = true, errorMessage = null) }
        activeJob =
            viewModelScope.launch {
                val bundle =
                    withContext(engineDispatcher) {
                        helper?.compare(
                            firstText = snapshot.firstText,
                            secondText = snapshot.secondText,
                            firstFormatContext = firstCtx,
                            secondFormatContext = secondCtx,
                        )
                    }
                _uiState.update {
                    if (bundle != null) {
                        it.copy(isComparing = false, result = bundle)
                    } else {
                        it.copy(
                            isComparing = false,
                            errorMessage = "Embedder is not initialized.",
                        )
                    }
                }
            }
    }

    private fun prepareEmbedder() {
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
                                TextEmbedderHelper(
                                    context = context,
                                    currentDelegate = TextEmbedderHelper.DELEGATE_CPU,
                                    currentModel = targetModel.index,
                                    listener = listener,
                                )
                            helper = created
                            created.isReady
                        } else {
                            existing.currentDelegate = TextEmbedderHelper.DELEGATE_CPU
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
