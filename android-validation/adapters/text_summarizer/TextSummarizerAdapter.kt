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

import com.google.mediapipe.examples.textsummarizer.SummaryModeOption
import com.google.mediapipe.examples.textsummarizer.TextSummarizerHelper
import com.google.mediapipe.examples.textsummarizer.TextSummarizerViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizer

/** Drives the text summarizer sample: selects Keypoints or TL;DR mode, types the source text, and taps Summarize. */
class TextSummarizerAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "gemma_200m") throw RunnerError("The sample has no model ${case.modelId}.")
        sample.usesModelAsset(TextSummarizerHelper.MODEL_ASSET)
        val modeOption =
            when (val rawMode = case.options.string("summaryMode")) {
                "KEYPOINTS" -> SummaryModeOption.KEYPOINTS
                "TLDR" -> SummaryModeOption.TLDR
                else -> throw RunnerError("Unsupported summaryMode $rawMode.")
            }

        if (case.delegate != "CPU") {
            val noSdkDelegate =
                TextSummarizer.TextSummarizerOptions.Builder::class.java.methods.none {
                    it.name == "setBaseOptions" || it.name == "setDelegate"
                }
            if (noSdkDelegate) {
                throw SampleError(
                    "Only CPU delegate is supported. TextSummarizerOptions.Builder has no delegate option in the MediaPipe Android SDK."
                )
            }
            throw RunnerError(
                "TextSummarizerOptions.Builder now exposes a delegate option; wire it in the sample and adapter."
            )
        }

        val viewModel = sample.viewModel(TextSummarizerViewModel::class.java)
        var ready = sample.await(viewModel.uiState, "the model to load", LOAD_TIMEOUT_MS) { !it.isBusy }
        if (ready.modeOption != modeOption) {
            sample.onMain { viewModel.selectMode(modeOption) }
            ready = sample.await(viewModel.uiState, "the summary mode to load", LOAD_TIMEOUT_MS) { !it.isBusy }
        }
        ready.errorMessage?.let { throw SampleError(it) }
        requireApplied("summaryMode", modeOption, ready.modeOption)

        return case.steps.map { step ->
            val text = step.string("text")
            if (text != text.trim()) {
                throw RunnerError("The sample would summarize \"${text.trim()}\" for the text \"$text\".")
            }
            val started =
                sample.onMain {
                    viewModel.updateInputText(text)
                    viewModel.summarize(streaming = false)
                    viewModel.uiState.value.isSummarizing
                }
            if (!started) throw RunnerError("The sample did not start to summarize ${step.key}.")
            val state =
                sample.await(viewModel.uiState, "the summary of ${step.key}", LOAD_TIMEOUT_MS) {
                    !it.isSummarizing && (it.result != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            requireApplied("input text", text, state.inputText)
            val result =
                state.result
                    ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            Run(step.key, json("summary" to result.summary))
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 240_000L
    }
}
