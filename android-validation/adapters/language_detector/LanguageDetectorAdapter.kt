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

import com.google.mediapipe.examples.languagedetector.DetectorDelegate
import com.google.mediapipe.examples.languagedetector.LanguageDetectorHelper
import com.google.mediapipe.examples.languagedetector.LanguageDetectorViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the language detection sample: selects the delegate, types each sentence, and taps Detect. */
class LanguageDetectorAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "language_detector") throw RunnerError("The sample has no model ${case.modelId}.")
        sample.usesModelAsset(LanguageDetectorHelper.MODEL_DETECTOR)
        val delegate = DetectorDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(LanguageDetectorViewModel::class.java)
        var ready = sample.await(viewModel.uiState, "the model to load") { !it.isBusy }
        if (ready.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
            ready = sample.await(viewModel.uiState, "the delegate to load") { !it.isBusy }
        }
        ready.errorMessage?.let { throw SampleError(it) }
        if (!ready.isReady) throw RunnerError("The sample finished loading without becoming ready: $ready")
        requireApplied("delegate", delegate, ready.delegate)

        return case.steps.map { step ->
            val text = step.string("text")
            val started =
                sample.onMain {
                    viewModel.updateInputText(text)
                    viewModel.detect()
                    viewModel.uiState.value.isDetecting
                }
            // detect() returns without a word while the button of the sample is disabled.
            if (!started) throw RunnerError("The sample did not start to detect the language of ${step.key}.")
            val state =
                sample.await(viewModel.uiState, "the languages of ${step.key}") {
                    !it.isDetecting && (it.result != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            requireApplied("input text", text, state.inputText)
            // The language code as MediaPipe returned it; the sample shows the name of the language.
            Run(
                step.key,
                json("categories" to state.result!!.predictions.map { json("label" to it.languageCode, "score" to it.probability) }),
            )
        }
    }
}
