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

import com.google.mediapipe.examples.textembedder.EmbedderDelegate
import com.google.mediapipe.examples.textembedder.EmbedderModel
import com.google.mediapipe.examples.textembedder.EmbeddingContextMode
import com.google.mediapipe.examples.textembedder.TextEmbedderHelper
import com.google.mediapipe.examples.textembedder.TextEmbedderViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.objects
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the text embedding sample: configures options, types the two texts of each pair as plain text, and taps Compare. */
class TextEmbedderAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val (model, asset) =
            when (case.modelId) {
                "universal_sentence_encoder" ->
                    EmbedderModel.UNIVERSAL_SENTENCE_ENCODER to TextEmbedderHelper.MODEL_UNIVERSAL_SENTENCE_ENCODER_PATH
                "embedding_gemma" -> EmbedderModel.EMBEDDING_GEMMA to TextEmbedderHelper.MODEL_EMBEDDING_GEMMA_PATH
                "bert_embedder" -> EmbedderModel.MOBILE_BERT to TextEmbedderHelper.MODEL_MOBILE_BERT_PATH
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(asset)
        val delegate = EmbedderDelegate.valueOf(case.delegate)
        val l2Normalize = case.options.boolean("l2Normalize")
        val quantize = case.options.boolean("quantize")

        val viewModel = sample.viewModel(TextEmbedderViewModel::class.java)
        var ready = sample.await(viewModel.uiState, "the default model to load") { !it.isBusy }
        if (ready.model != model) {
            sample.onMain { viewModel.selectModel(model) }
            ready = sample.await(viewModel.uiState, "the model to load") { !it.isBusy }
        }
        if (ready.l2Normalize != l2Normalize) {
            sample.onMain { viewModel.setL2Normalize(l2Normalize) }
            ready = sample.await(viewModel.uiState, "l2Normalize to apply") { !it.isBusy }
        }
        if (ready.quantize != quantize) {
            sample.onMain { viewModel.setQuantize(quantize) }
            ready = sample.await(viewModel.uiState, "quantize to apply") { !it.isBusy }
        }
        if (model.supportsFormatContext && ready.contextMode != EmbeddingContextMode.NONE) {
            sample.onMain { viewModel.selectContextMode(EmbeddingContextMode.NONE) }
            ready = sample.onMain { viewModel.uiState.value }
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
        requireApplied("l2Normalize", l2Normalize, ready.l2Normalize)
        requireApplied("quantize", quantize, ready.quantize)
        if (model.supportsFormatContext) requireApplied("context mode", EmbeddingContextMode.NONE, ready.contextMode)

        return case.steps.map { step ->
            val (first, second) = step.json.getJSONArray("pair").objects().map { it.getString("text") }
            val started =
                sample.onMain {
                    viewModel.updateFirstText(first)
                    viewModel.updateSecondText(second)
                    viewModel.compare()
                    viewModel.uiState.value.isComparing
                }
            // compare() returns without a word while the button of the sample is disabled.
            if (!started) throw RunnerError("The sample did not start to compare the texts of ${step.key}.")
            val state =
                sample.await(viewModel.uiState, "the similarity of ${step.key}") {
                    !it.isComparing && (it.result != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            requireApplied("first text", first, state.firstText)
            requireApplied("second text", second, state.secondText)
            Run(step.key, json("similarity" to state.result!!.similarity))
        }
    }
}
