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

import com.google.mediapipe.examples.textclassifier.ClassifierDelegate
import com.google.mediapipe.examples.textclassifier.ClassifierModel
import com.google.mediapipe.examples.textclassifier.TextClassifierViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the text classification sample: selects the model and delegate, types each sentence, and taps Classify. */
class TextClassifierAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val model =
            when (case.modelId) {
                "bert_classifier" -> ClassifierModel.MOBILEBERT
                "average_word_classifier" -> ClassifierModel.WORD_VEC
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(model.modelPath)
        val delegate = ClassifierDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(TextClassifierViewModel::class.java)
        var ready = sample.await(viewModel.uiState, "the default model to load") { !it.isBusy }
        if (ready.model != model) {
            sample.onMain { viewModel.selectModel(model) }
            ready = sample.await(viewModel.uiState, "the model to load") { !it.isBusy }
        }
        if (ready.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
            ready = sample.await(viewModel.uiState, "the delegate to load") { !it.isBusy }
        }
        ready.errorMessage?.let { throw SampleError(it) }
        if (!ready.isReady) throw RunnerError("The sample finished loading without becoming ready: $ready")
        requireApplied("model", model, ready.model)
        requireApplied("delegate", delegate, ready.delegate)

        return case.steps.map { step ->
            val text = step.string("text")
            val started =
                sample.onMain {
                    viewModel.updateInputText(text)
                    viewModel.classify()
                    viewModel.uiState.value.isClassifying
                }
            if (!started) throw RunnerError("The sample did not start to classify ${step.key}.")
            val state =
                sample.await(viewModel.uiState, "the classification of ${step.key}") {
                    !it.isClassifying && (it.result != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            requireApplied("input text", text, state.inputText)
            // The label as MediaPipe returned it; the sample shows a capitalised form of it.
            Run(
                step.key,
                json("categories" to state.result!!.categories.map { json("label" to it.rawLabel, "score" to it.score) }),
            )
        }
    }
}
