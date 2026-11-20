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

package com.google.mediapipe.examples.validation.adapters

import com.google.mediapipe.examples.decisionmaker.BooleanOutcomeData
import com.google.mediapipe.examples.decisionmaker.ChoiceOutcomeData
import com.google.mediapipe.examples.decisionmaker.DecisionDelegateOption
import com.google.mediapipe.examples.decisionmaker.DecisionMakerHelper
import com.google.mediapipe.examples.decisionmaker.DecisionMakerViewModel
import com.google.mediapipe.examples.decisionmaker.DecisionModelOption
import com.google.mediapipe.examples.decisionmaker.QuestionType
import com.google.mediapipe.examples.decisionmaker.ScoreOutcomeData
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import java.io.File
import org.json.JSONObject

/** Drives the decision maker sample in both `schema` (`evaluateJson` triage) and `individual` modes. */
class DecisionMakerAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val modelOption =
            when (case.modelId) {
                "embeddinggemma2_text_270m" -> DecisionModelOption.EMBEDDING_GEMMA
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        val modelFile = DecisionMakerHelper.MODEL_FILES[modelOption.id]
        listOfNotNull(
                File("/data/local/tmp/decision_models", modelFile),
                sample.activity.getExternalFilesDir(null)?.let { File(it, modelFile) },
            )
            .firstOrNull { it.canRead() && it.length() > 0L }
            ?.let {
                throw RunnerError(
                    "The sample loads $it instead of its bundled asset $modelFile. Remove that file from the device."
                )
            }
        sample.usesModelAsset(modelFile)

        val evaluateMode = case.options.string("evaluate")
        if (evaluateMode != "schema" && evaluateMode != "individual") {
            throw RunnerError("Unsupported evaluate mode: $evaluateMode")
        }
        val delegate = DecisionDelegateOption.valueOf(case.delegate)

        val viewModel = sample.viewModel(DecisionMakerViewModel::class.java)
        var settings =
            sample.await(viewModel.uiState, "initial model load", LOAD_TIMEOUT_MS) { !it.isModelLoading }
        if (settings.modelOption != modelOption) {
            sample.onMain { viewModel.selectModel(modelOption) }
            settings =
                sample.await(viewModel.uiState, "loading model ${modelOption.displayName}", LOAD_TIMEOUT_MS) {
                    !it.isModelLoading
                }
        }
        if (settings.delegateOption != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
            settings =
                sample.await(viewModel.uiState, "loading DecisionMaker on ${case.delegate}", LOAD_TIMEOUT_MS) {
                    !it.isModelLoading
                }
        }
        settings.errorMessage?.let { throw SampleError(it) }
        requireApplied("model", modelOption, settings.modelOption)
        requireApplied("delegate", delegate, settings.delegateOption)

        val questionsJson = case.json.getJSONObject("questions")
        val urgentJson = questionsJson.getJSONObject("urgent")
        val condition = urgentJson.getString("condition")
        val threshold = urgentJson.optDouble("threshold", 0.5).toFloat()

        val deptJson = questionsJson.getJSONObject("department")
        val criteriaJson = deptJson.getJSONObject("criteria")
        val criteria = linkedMapOf<String, String>()
        val keysIter = criteriaJson.keys()
        while (keysIter.hasNext()) {
            val k = keysIter.next()
            criteria[k] = criteriaJson.getString(k)
        }
        val deptInstructions = deptJson.optString("instructions", "")

        val severityJson = questionsJson.getJSONObject("severity")
        val rubricJson = severityJson.getJSONArray("rubric")
        val rubric = List(rubricJson.length()) { idx -> rubricJson.getString(idx) }
        val severityInstructions = severityJson.optString("instructions", "")

        sample.onMain {
            viewModel.configureBooleanDraft(condition = condition, threshold = threshold)
            viewModel.configureChoiceDraft(criteria = criteria, instructions = deptInstructions)
            viewModel.configureScoreDraft(rubric = rubric, instructions = severityInstructions)
        }

        return case.steps.map { step ->
            val text = step.string("text")
            if (text != text.trim()) {
                throw RunnerError("The sample trims input text, which would alter \"$text\".")
            }

            if (evaluateMode == "schema") {
                sample.onMain {
                    viewModel.selectQuestionType(QuestionType.TRIAGE)
                    viewModel.updateInputText(text)
                    requireApplied("questionType", QuestionType.TRIAGE, viewModel.uiState.value.questionType)
                    if (!viewModel.uiState.value.canEvaluate) {
                        throw RunnerError("The sample is not ready to triage ${step.key}: ${viewModel.uiState.value}")
                    }
                    viewModel.evaluate()
                }
                val done =
                    sample.await(viewModel.uiState, "triage schema evaluation of ${step.key}") {
                        !it.isEvaluating && (it.outcome != null || it.errorMessage != null)
                    }
                done.errorMessage?.let { throw SampleError(it) }
                val triage =
                    done.outcome?.triageResult
                        ?: throw SampleError("Triage evaluation returned no structured triage result.")
                Run(step.key, toDecisionObservation(triage.urgent, triage.department, triage.severity))
            } else {
                val urgent = evaluateSingle(sample, viewModel, QuestionType.BOOLEAN, text, step.key) {
                    it.booleanResult ?: throw SampleError("Boolean evaluation returned no booleanResult.")
                }
                val department = evaluateSingle(sample, viewModel, QuestionType.CHOICE, text, step.key) {
                    it.choiceResult ?: throw SampleError("Choice evaluation returned no choiceResult.")
                }
                val severity = evaluateSingle(sample, viewModel, QuestionType.SCORE, text, step.key) {
                    it.scoreResult ?: throw SampleError("Score evaluation returned no scoreResult.")
                }
                Run(step.key, toDecisionObservation(urgent, department, severity))
            }
        }
    }

    private fun <T> evaluateSingle(
        sample: SampleSession,
        viewModel: DecisionMakerViewModel,
        type: QuestionType,
        text: String,
        stepKey: String,
        extract: (com.google.mediapipe.examples.decisionmaker.DecisionOutcomeUi) -> T,
    ): T {
        sample.onMain {
            viewModel.selectQuestionType(type)
            viewModel.updateInputText(text)
            requireApplied("questionType", type, viewModel.uiState.value.questionType)
            if (!viewModel.uiState.value.canEvaluate) {
                throw RunnerError("The sample is not ready to evaluate ${type.name} for $stepKey")
            }
            viewModel.evaluate()
        }
        val done =
            sample.await(viewModel.uiState, "${type.name} evaluation of $stepKey") {
                !it.isEvaluating && (it.outcome != null || it.errorMessage != null)
            }
        done.errorMessage?.let { throw SampleError(it) }
        val outcome = done.outcome ?: throw SampleError("${type.name} evaluation returned no outcome.")
        return extract(outcome)
    }

    private fun toDecisionObservation(
        urgent: BooleanOutcomeData,
        department: ChoiceOutcomeData,
        severity: ScoreOutcomeData,
    ): JSONObject {
        val deptProbsJson = JSONObject()
        department.probabilities.forEach { (k, v) -> deptProbsJson.put(k, v) }
        return json(
            "urgent" to
                json(
                    "value" to urgent.value,
                    "probabilityTrue" to urgent.probabilityTrue,
                ),
            "department" to
                json(
                    "selected" to department.selected,
                    "probabilities" to deptProbsJson,
                ),
            "severity" to
                json(
                    "expectedScore" to severity.expectedScore,
                    "probabilities" to severity.probabilities,
                ),
        )
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 240_000L
    }
}
