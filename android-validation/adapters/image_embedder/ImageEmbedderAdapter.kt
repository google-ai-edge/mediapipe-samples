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

import android.graphics.Bitmap
import com.google.mediapipe.examples.imageembedder.EmbedderDelegate
import com.google.mediapipe.examples.imageembedder.ImageEmbedderHelper
import com.google.mediapipe.examples.imageembedder.ImageEmbedderModel
import com.google.mediapipe.examples.imageembedder.ImageEmbedderUiState
import com.google.mediapipe.examples.imageembedder.ImageEmbedderViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.objects
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the image embedding sample: picks a photo or video for each of its two slots and taps Compare. */
class ImageEmbedderAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val (model, asset) =
            when (case.modelId) {
                "mobilenet_v3_small" -> ImageEmbedderModel.SMALL to ImageEmbedderHelper.MODEL_SMALL_PATH
                "mobilenet_v3_large" -> ImageEmbedderModel.LARGE to ImageEmbedderHelper.MODEL_LARGE_PATH
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(asset)
        val runningMode = case.options.string("runningMode")
        val l2Normalize = case.options.boolean("l2Normalize")
        val quantize = case.options.boolean("quantize")
        val delegate = EmbedderDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(ImageEmbedderViewModel::class.java)
        // The sample loads its default model when it starts, and loads again for each setting that
        // changes. Each load is awaited, so that only one is ever under way.
        var settings = awaitModel(sample, viewModel)
        if (settings.model != model) {
            sample.onMain { viewModel.selectModel(model) }
            settings = awaitModel(sample, viewModel)
        }
        if (settings.l2Normalize != l2Normalize) {
            sample.onMain { viewModel.setL2Normalize(l2Normalize) }
            settings = awaitModel(sample, viewModel)
        }
        if (settings.quantize != quantize) {
            sample.onMain { viewModel.setQuantize(quantize) }
            settings = awaitModel(sample, viewModel)
        }
        if (settings.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
            settings = awaitModel(sample, viewModel)
        }
        requireApplied("model", model, settings.model)
        requireApplied("l2Normalize", l2Normalize, settings.l2Normalize)
        requireApplied("quantize", quantize, settings.quantize)
        requireApplied("delegate", delegate, settings.delegate)

        return case.steps.map { step ->
            val pair = step.json.getJSONArray("pair").objects()
            pick(sample, viewModel, 1, pair[0].getString("image"), runningMode) { it.firstBitmap }
            pick(sample, viewModel, 2, pair[1].getString("image"), runningMode) { it.secondBitmap }
            val pickedState = viewModel.uiState.value
            requireApplied("runningMode (slot 1 video)", runningMode == "VIDEO", pickedState.firstVideoUri != null)
            requireApplied("runningMode (slot 2 video)", runningMode == "VIDEO", pickedState.secondVideoUri != null)

            // compareImages() keeps the result it shows until the new one is there (unless picking cleared it).
            var previous: Any? = null
            val started =
                sample.onMain {
                    previous = viewModel.uiState.value.result
                    viewModel.compareImages()
                    viewModel.uiState.value.isComparing
                }
            if (!started) throw RunnerError("The sample did not start to compare the inputs of ${step.key}.")
            val state =
                sample.await(viewModel.uiState, "the similarity of ${step.key}") {
                    !it.isComparing && ((it.result != null && it.result !== previous) || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            Run(step.key, json("similarity" to state.result!!.similarity))
        }
    }

    private fun awaitModel(sample: SampleSession, viewModel: ImageEmbedderViewModel): ImageEmbedderUiState {
        val state =
            sample.await(viewModel.uiState, "the model to load") {
                !it.isLoadingModel && (it.isModelReady || it.errorMessage != null)
            }
        state.errorMessage?.let { throw SampleError(it) }
        return state
    }

    /** Picks the photo or video [asset] for [slot], and waits until the sample shows it there. */
    private fun pick(
        sample: SampleSession,
        viewModel: ImageEmbedderViewModel,
        slot: Int,
        asset: String,
        runningMode: String,
        shown: (ImageEmbedderUiState) -> Bitmap?,
    ) {
        val uri =
            when (runningMode) {
                "IMAGE" -> sample.inputUri(asset)
                "VIDEO" -> sample.inputVideoUri(asset)
                else -> throw RunnerError("Unsupported runningMode $runningMode.")
            }
        val before = sample.onMain { shown(viewModel.uiState.value).also { viewModel.setPickedImage(slot, uri) } }
        val state =
            sample.await(viewModel.uiState, "slot $slot ($asset) to load") {
                shown(it) !== before || it.errorMessage != null
            }
        state.errorMessage?.let { throw SampleError(it) }
    }
}
