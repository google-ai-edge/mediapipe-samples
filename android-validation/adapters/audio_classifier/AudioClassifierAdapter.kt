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

import com.google.mediapipe.examples.audioclassifier.AudioClassifierHelper
import com.google.mediapipe.examples.audioclassifier.AudioClassifierViewModel
import com.google.mediapipe.examples.audioclassifier.ClassifierDelegate
import com.google.mediapipe.examples.audioclassifier.ClassifierInputMode
import com.google.mediapipe.examples.audioclassifier.OverlapOption
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import com.google.mediapipe.tasks.audio.core.RunningMode

/** Drives the audio classification sample through its Clip tab, as a user who picks an audio file. */
class AudioClassifierAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "yamnet") throw RunnerError("The sample has no model ${case.modelId}.")
        sample.usesModelAsset(AudioClassifierHelper.YAMNET_MODEL)
        val runningMode =
            when (val mode = case.options.string("runningMode")) {
                "AUDIO_CLIPS" -> RunningMode.AUDIO_CLIPS
                "AUDIO_STREAM" -> RunningMode.AUDIO_STREAM
                else -> throw RunnerError("Unsupported runningMode $mode.")
            }
        val maxResults = case.options.int("maxResults")
        val delegate = ClassifierDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(AudioClassifierViewModel::class.java)
        if (viewModel.uiState.value.delegate != delegate) {
            sample.beforeDelegateSwitch()
        }
        val settings =
            sample.onMain {
                viewModel.selectInputMode(ClassifierInputMode.CLIP)
                viewModel.selectDelegate(delegate)
                viewModel.selectClipRunningMode(runningMode)
                viewModel.updateMaxResults(maxResults)
                viewModel.updateThreshold(0f)
                viewModel.updateOverlap(OverlapOption.ZERO)
                viewModel.uiState.value
            }
        requireApplied("input mode", ClassifierInputMode.CLIP, settings.inputMode)
        requireApplied("delegate", delegate, settings.delegate)
        requireApplied("runningMode", runningMode, settings.clipRunningMode)
        requireApplied("maxResults", maxResults, settings.maxResults)
        requireApplied("score threshold", 0f, settings.threshold)
        requireApplied("overlap", OverlapOption.ZERO, settings.overlapOption)

        return case.steps.map { step ->
            val audio = step.string("audio")
            val uri = sample.inputUri(audio)
            sample.onMain { viewModel.selectCustomAudioUri(uri, audio) }
            val state =
                sample.await(viewModel.uiState, "the classification of ${step.key}") {
                    !it.isBusy && (it.hasClipResult || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            Run(
                step.key,
                json(
                    "results" to
                        state.windows.map { window ->
                            json(
                                "timestampMs" to window.timestampMs,
                                "categories" to window.categories.map { json("label" to it.label, "score" to it.score) },
                            )
                        }
                ),
            )
        }
    }
}
