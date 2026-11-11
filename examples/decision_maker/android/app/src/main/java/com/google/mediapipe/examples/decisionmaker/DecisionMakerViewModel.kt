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

package com.google.mediapipe.examples.decisionmaker

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.decision.BooleanResult
import com.google.mediapipe.tasks.decision.ChoiceResult
import com.google.mediapipe.tasks.decision.ScoreResult
import java.util.Locale
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class QuestionType(val label: String) {
    BOOLEAN("Boolean"),
    CHOICE("Choice"),
    SCORE("Score"),
}

enum class DecisionDelegateOption(val id: Int, val label: String) {
    CPU(DecisionMakerHelper.DELEGATE_CPU, "CPU"),
    GPU(DecisionMakerHelper.DELEGATE_GPU, "GPU"),
}

enum class DecisionModelOption(val id: Int, val displayName: String, val shortLabel: String) {
    EMBEDDING_GEMMA(DecisionMakerHelper.MODEL_EMBEDDING_GEMMA, "EmbeddingGemma 270M", "Gemma 270M"),
    LAYA(DecisionMakerHelper.MODEL_LAYA, "Laya S256", "Laya"),
    GLINER(DecisionMakerHelper.MODEL_GLINER, "GLiNER S256", "GLiNER"),
}

data class QuestionItemUi(
    val id: Long,
    val label: String,
    val description: String = "",
)

data class QuestionDraftUi(
    val inputText: String,
    val condition: String = "",
    val threshold: Float = 0.5f,
    val items: List<QuestionItemUi> = emptyList(),
    val instructions: String = "",
)

data class DecisionBarUi(
    val label: String,
    val probability: Float,
    val highlighted: Boolean,
)

data class DecisionOutcomeUi(
    val headline: String,
    val subtitle: String?,
    val bars: List<DecisionBarUi>,
    val inferenceTimeMs: Long,
)

private fun defaultDelegateOption(): DecisionDelegateOption =
    if (DecisionMakerHelper.isAndroidEmulator()) {
        DecisionDelegateOption.CPU
    } else {
        DecisionDelegateOption.GPU
    }

data class DecisionMakerUiState(
    val questionType: QuestionType = QuestionType.BOOLEAN,
    val drafts: Map<QuestionType, QuestionDraftUi> = defaultDrafts(),
    val delegateOption: DecisionDelegateOption = defaultDelegateOption(),
    val modelOption: DecisionModelOption = DecisionModelOption.EMBEDDING_GEMMA,
    val isModelLoading: Boolean = true,
    val isEvaluating: Boolean = false,
    val outcome: DecisionOutcomeUi? = null,
    val errorMessage: String? = null,
    val noticeMessage: String? = null,
) {
    val activeDraft: QuestionDraftUi
        get() = drafts.getValue(questionType)

    val isBusy: Boolean
        get() = isModelLoading || isEvaluating

    val canEvaluate: Boolean
        get() {
            if (isBusy || activeDraft.inputText.isBlank()) return false
            if (questionType != QuestionType.BOOLEAN) {
                return activeDraft.items.count { it.label.isNotBlank() } >= 2
            }
            return activeDraft.condition.isNotBlank()
        }
}

private var nextItemId = 1L

private fun item(label: String, description: String = ""): QuestionItemUi =
    QuestionItemUi(id = nextItemId++, label = label, description = description)

fun defaultDrafts(): Map<QuestionType, QuestionDraftUi> =
    mapOf(
        QuestionType.BOOLEAN to
            QuestionDraftUi(
                inputText = "This product is amazing, it works perfectly and I use it every day!",
                condition = "The review is positive.",
                threshold = 0.5f,
            ),
        QuestionType.CHOICE to
            QuestionDraftUi(
                inputText = "My package never arrived even though tracking says delivered.",
                items =
                    listOf(
                        item("shipping", "Delivery problems, lost packages, tracking"),
                        item("billing", "Payment issues, duplicate charges, invoices"),
                        item("technical", "App crashes, login errors, bugs"),
                    ),
                instructions = "Which department should handle this ticket?",
            ),
        QuestionType.SCORE to
            QuestionDraftUi(
                inputText = "The support agent was friendly and fixed my issue fast.",
                items =
                    listOf(
                        item("Very dissatisfied", "angry, problem not solved, terrible service"),
                        item(
                            "Dissatisfied",
                            "slow or unhelpful support, problem partly solved",
                        ),
                        item("Neutral", "okay, average, nothing special"),
                        item("Satisfied", "helpful support, problem solved"),
                        item("Very satisfied", "excellent, fast, friendly support, delighted"),
                    ),
                instructions = "Rate how satisfied the customer is with the support experience.",
            ),
    )

class DecisionMakerViewModel(application: Application) : AndroidViewModel(application) {

    private val defaultDelegate = defaultDelegateOption()
    private val _uiState = MutableStateFlow(DecisionMakerUiState(delegateOption = defaultDelegate))
    val uiState: StateFlow<DecisionMakerUiState> = _uiState.asStateFlow()

    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private val helper =
        DecisionMakerHelper(
            context = application,
            currentDelegate = defaultDelegate.id,
            currentModel = DecisionMakerHelper.MODEL_EMBEDDING_GEMMA,
        )

    init {
        setupEngine(allowGpuFallback = defaultDelegate == DecisionDelegateOption.GPU)
    }

    fun selectQuestionType(type: QuestionType) {
        if (_uiState.value.questionType == type) return
        _uiState.update {
            it.copy(
                questionType = type,
                outcome = null,
                errorMessage = null,
            )
        }
    }

    fun updateInputText(value: String) = updateActiveDraft { it.copy(inputText = value) }

    fun updateCondition(value: String) = updateActiveDraft { it.copy(condition = value) }

    fun updateThreshold(value: Float) {
        _uiState.update { state ->
            val current = state.drafts.getValue(QuestionType.BOOLEAN)
            state.copy(
                drafts = state.drafts + (QuestionType.BOOLEAN to current.copy(threshold = value))
            )
        }
    }

    fun updateInstructions(value: String) = updateActiveDraft { it.copy(instructions = value) }

    fun addItem() {
        updateActiveDraft { draft -> draft.copy(items = draft.items + item("", "")) }
    }

    fun updateItemLabel(id: Long, label: String) {
        updateActiveDraft { draft ->
            draft.copy(items = draft.items.map { if (it.id == id) it.copy(label = label) else it })
        }
    }

    fun updateItemDescription(id: Long, description: String) {
        updateActiveDraft { draft ->
            draft.copy(
                items = draft.items.map { if (it.id == id) it.copy(description = description) else it }
            )
        }
    }

    fun removeItem(id: Long) {
        updateActiveDraft { draft ->
            draft.copy(items = draft.items.filterNot { it.id == id })
        }
    }

    fun selectDelegate(option: DecisionDelegateOption) {
        if (_uiState.value.delegateOption == option || _uiState.value.isBusy) return
        helper.currentDelegate = option.id
        _uiState.update {
            it.copy(
                delegateOption = option,
                isModelLoading = true,
                outcome = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
        setupEngine(allowGpuFallback = false)
    }

    fun selectModel(option: DecisionModelOption) {
        if (_uiState.value.modelOption == option || _uiState.value.isBusy) return
        helper.currentModel = option.id
        _uiState.update {
            it.copy(
                modelOption = option,
                isModelLoading = true,
                outcome = null,
                errorMessage = null,
                noticeMessage = null,
            )
        }
        setupEngine(allowGpuFallback = true)
    }

    fun retryInitialization() {
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isModelLoading = true, errorMessage = null, noticeMessage = null) }
        setupEngine(allowGpuFallback = true)
    }

    fun evaluate() {
        val state = _uiState.value
        if (!state.canEvaluate) return
        val type = state.questionType
        val draft = state.drafts.getValue(type)

        _uiState.update { it.copy(isEvaluating = true, errorMessage = null) }

        viewModelScope.launch {
            try {
                val outcome =
                    withContext(dispatcher) {
                        when (type) {
                            QuestionType.BOOLEAN -> {
                                val res =
                                    helper.evaluateBoolean(
                                        draft.inputText.trim(),
                                        draft.condition.trim(),
                                        draft.threshold,
                                    )
                                formatBoolean(res.result, draft.threshold, res.inferenceTime)
                            }
                            QuestionType.CHOICE -> {
                                val named =
                                    draft.items
                                        .map { it.label.trim() to it.description.trim() }
                                        .filter { it.first.isNotEmpty() }
                                val options = named.associate { it.first to it.second }
                                val res =
                                    helper.evaluateChoice(
                                        draft.inputText.trim(),
                                        options,
                                        draft.instructions.trim(),
                                    )
                                formatChoice(res.result, options, res.inferenceTime)
                            }
                            QuestionType.SCORE -> {
                                val named =
                                    draft.items
                                        .map { it.label.trim() to it.description.trim() }
                                        .filter { it.first.isNotEmpty() }
                                val rubric =
                                    named.map { (name, desc) ->
                                        if (desc.isEmpty()) name else "$name: $desc"
                                    }
                                val res =
                                    helper.evaluateScore(
                                        draft.inputText.trim(),
                                        rubric,
                                        draft.instructions.trim(),
                                    )
                                formatScore(
                                    res.result,
                                    named.map { it.first },
                                    res.inferenceTime,
                                )
                            }
                        }
                    }

                _uiState.update {
                    it.copy(
                        isEvaluating = false,
                        outcome = outcome,
                    )
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Evaluation failed", t)
                _uiState.update {
                    it.copy(
                        isEvaluating = false,
                        errorMessage = t.message ?: "Evaluation failed.",
                    )
                }
            }
        }
    }

    private fun updateActiveDraft(transform: (QuestionDraftUi) -> QuestionDraftUi) {
        _uiState.update { state ->
            val current = state.drafts.getValue(state.questionType)
            state.copy(
                drafts = state.drafts + (state.questionType to transform(current)),
                outcome = null,
                errorMessage = null,
            )
        }
    }

    private fun setupEngine(allowGpuFallback: Boolean) {
        viewModelScope.launch {
            val error =
                withContext(dispatcher) {
                    runCatching { helper.setupDecisionMaker() }.exceptionOrNull()
                }
            if (error == null) {
                _uiState.update {
                    it.copy(isModelLoading = false, errorMessage = null)
                }
                return@launch
            }
            Log.w(TAG, "Failed to setup DecisionMaker on ${helper.currentDelegate}", error)
            if (
                allowGpuFallback &&
                    helper.currentDelegate == DecisionMakerHelper.DELEGATE_GPU &&
                    error !is DecisionMakerHelper.ModelNotFoundException
            ) {
                helper.currentDelegate = DecisionMakerHelper.DELEGATE_CPU
                val cpuError =
                    withContext(dispatcher) {
                        runCatching { helper.setupDecisionMaker() }.exceptionOrNull()
                    }
                _uiState.update {
                    it.copy(
                        delegateOption = DecisionDelegateOption.CPU,
                        isModelLoading = false,
                        noticeMessage =
                            if (cpuError == null) "GPU unavailable; switched to CPU." else null,
                        errorMessage = cpuError?.message,
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isModelLoading = false,
                        errorMessage = error.message ?: "Failed to initialize DecisionMaker.",
                    )
                }
            }
        }
    }

    private fun formatBoolean(
        result: BooleanResult,
        threshold: Float,
        inferenceTimeMs: Long,
    ): DecisionOutcomeUi =
        DecisionOutcomeUi(
            headline = if (result.value) "Yes" else "No",
            subtitle = String.format(Locale.US, "Yes when P(yes) ≥ %.2f", threshold),
            bars =
                listOf(
                    DecisionBarUi("Yes", result.probabilityTrue, result.value),
                    DecisionBarUi("No", 1f - result.probabilityTrue, !result.value),
                ),
            inferenceTimeMs = inferenceTimeMs,
        )

    private fun formatChoice(
        result: ChoiceResult,
        options: Map<String, String>,
        inferenceTimeMs: Long,
    ): DecisionOutcomeUi =
        DecisionOutcomeUi(
            headline = result.selectedKey,
            subtitle = options[result.selectedKey]?.takeIf { it.isNotEmpty() },
            bars =
                result.probabilities.entries
                    .sortedByDescending { it.value }
                    .map { (key, prob) ->
                        DecisionBarUi(key, prob, key == result.selectedKey)
                    },
            inferenceTimeMs = inferenceTimeMs,
        )

    private fun formatScore(
        result: ScoreResult,
        levels: List<String>,
        inferenceTimeMs: Long,
    ): DecisionOutcomeUi {
        val probs = result.levelProbabilities
        val expected = probs.withIndex().sumOf { (idx, p) -> (idx + 1) * p.toDouble() }
        val bestIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
        return DecisionOutcomeUi(
            headline = String.format(Locale.US, "%.2f / %d", expected, probs.size),
            subtitle = levels.getOrNull(bestIdx)?.let { "Most likely: $it" },
            bars =
                probs.mapIndexed { idx, p ->
                    DecisionBarUi("${idx + 1} · ${levels.getOrElse(idx) { "" }}", p, idx == bestIdx)
                },
            inferenceTimeMs = inferenceTimeMs,
        )
    }

    override fun onCleared() {
        super.onCleared()
        executor.execute { helper.clearDecisionMaker() }
        executor.shutdown()
    }

    companion object {
        private const val TAG = "DecisionMakerVM"
    }
}
