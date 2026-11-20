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

import com.google.mediapipe.examples.textproofreader.TextProofreaderHelper
import com.google.mediapipe.examples.textproofreader.TextProofreaderViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import com.google.mediapipe.tasks.text.textproofreader.TextProofreader

/** Drives the text proofreader sample: types each text and taps Proofread. */
class TextProofreaderAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "gemma_200m") throw RunnerError("The sample has no model ${case.modelId}.")
        sample.usesModelAsset(TextProofreaderHelper.MODEL_ASSET)

        if (case.delegate != "CPU") {
            val noSdkDelegate =
                TextProofreader.TextProofreaderOptions.Builder::class.java.methods.none {
                    it.name == "setBaseOptions" || it.name == "setDelegate"
                }
            if (noSdkDelegate) {
                throw SampleError(
                    "Only CPU delegate is supported. TextProofreaderOptions.Builder has no delegate option in the MediaPipe Android SDK."
                )
            }
            throw RunnerError(
                "TextProofreaderOptions.Builder now exposes a delegate option; wire it in the sample and adapter."
            )
        }

        val viewModel = sample.viewModel(TextProofreaderViewModel::class.java)
        val ready = sample.await(viewModel.uiState, "the model to load", LOAD_TIMEOUT_MS) { !it.isBusy }
        ready.errorMessage?.let { throw SampleError(it) }

        return case.steps.map { step ->
            val text = step.string("text")
            if (text != text.trim()) {
                throw RunnerError("The sample would proofread \"${text.trim()}\" for the text \"$text\".")
            }
            val started =
                sample.onMain {
                    viewModel.updateInputText(text)
                    viewModel.proofread(streaming = false)
                    viewModel.uiState.value.isProofreading
                }
            if (!started) throw RunnerError("The sample did not start to proofread ${step.key}.")
            val state =
                sample.await(viewModel.uiState, "the proofreading of ${step.key}", LOAD_TIMEOUT_MS) {
                    !it.isProofreading && (it.result != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            requireApplied("input text", text, state.inputText)
            val result =
                state.result
                    ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            Run(
                step.key,
                json(
                    "proofreadText" to result.proofreadText,
                    "corrections" to
                        result.corrections.map { correction ->
                            json("type" to correction.type.name, "text" to correction.text)
                        },
                ),
            )
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 240_000L
    }
}
