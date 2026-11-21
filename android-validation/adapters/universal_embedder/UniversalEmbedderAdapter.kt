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

import com.google.mediapipe.examples.universalembedder.EmbedInputMode
import com.google.mediapipe.examples.universalembedder.UniversalDelegateOption
import com.google.mediapipe.examples.universalembedder.UniversalEmbedderHelper
import com.google.mediapipe.examples.universalembedder.UniversalEmbedderViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.objects
import com.google.mediapipe.examples.validation.runner.requireApplied
import java.io.File
import org.json.JSONObject

/** Drives the universal embedder sample: puts a text or a picked photo in each of its two slots and taps Compare. */
class UniversalEmbedderAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "embeddinggemma2_text_vision_440m") throw RunnerError("The sample has no model ${case.modelId}.")
        val modelFile = UniversalEmbedderHelper.MODEL_FILE_NAME
        // The sample loads a copy of the model that was pushed to the device rather than its asset
        // (UniversalEmbedderHelper.resolveModelPath). Only the asset is the model the runner checks.
        listOfNotNull(File("/data/local/tmp/universal_embedder_models", modelFile), sample.activity.getExternalFilesDir(null)?.let { File(it, modelFile) })
            .firstOrNull { it.canRead() && it.length() > 0L }
            ?.let { throw RunnerError("The sample loads $it instead of its asset $modelFile. Remove that file from the device.") }
        sample.usesModelAsset(modelFile)
        // UniversalEmbedderHelper.setupUniversalEmbedder() always sets setL2Normalize(true).
        case.options.fixed("l2Normalize", true)
        val delegate = UniversalDelegateOption.valueOf(case.delegate)

        val viewModel = sample.viewModel(UniversalEmbedderViewModel::class.java)
        fun loaded() = sample.await(viewModel.uiState, "the model to load", LOAD_TIMEOUT_MS) { !it.isModelLoading }
        // The sample starts by loading the model on the GPU, and falls back to the CPU if that fails.
        var settings = loaded()
        if (settings.delegateOption != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
            settings = loaded()
        }
        settings.errorMessage?.let { throw SampleError(it) }
        requireApplied("delegate", delegate, settings.delegateOption)

        return case.steps.map { step ->
            step.json.getJSONArray("pair").objects().forEachIndexed { index, input -> fill(sample, viewModel, index == 0, input) }
            sample.onMain {
                val filled = viewModel.uiState.value
                // Filling a slot clears the result, so the result awaited below is the one of this pair.
                if (!filled.canCompare || filled.result != null) throw RunnerError("The sample is not ready to compare ${step.key}: $filled")
                viewModel.compare()
            }
            val state =
                sample.await(viewModel.uiState, "the similarity of ${step.key}") {
                    !it.isComparing && (it.result != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            Run(step.key, json("similarity" to state.result!!.similarity))
        }
    }

    /** Puts [input], `{"text": ...}` or `{"image": ...}`, in the first or the second slot of the sample. */
    private fun fill(sample: SampleSession, viewModel: UniversalEmbedderViewModel, isFirst: Boolean, input: JSONObject) {
        fun slot() = viewModel.uiState.value.let { if (isFirst) it.firstSlot else it.secondSlot }
        if (input.has("text")) {
            val text = input.getString("text")
            // compare() embeds the text of the slot without the white space around it.
            if (text != text.trim()) throw RunnerError("The sample would embed \"${text.trim()}\" for the text \"$text\".")
            sample.onMain {
                viewModel.setSlotMode(isFirst, EmbedInputMode.TEXT)
                viewModel.updateSlotText(isFirst, text)
            }
            requireApplied("the input mode of a slot", EmbedInputMode.TEXT, slot().mode)
            requireApplied("the text of a slot", text, slot().text)
        } else {
            val image = input.getString("image")
            val uri = sample.inputUri(image)
            val before = slot().customBitmap
            sample.onMain { viewModel.setSlotCustomImageUri(isFirst, uri) }
            // The sample ignores a photo it cannot decode: only a new bitmap in the slot shows that it took this one.
            sample.await(viewModel.uiState, "the photo $image to appear in its slot", DECODE_TIMEOUT_MS) {
                slot().customBitmap.let { it != null && it !== before }
            }
            requireApplied("the input mode of a slot", EmbedInputMode.IMAGE, slot().mode)
        }
    }

    private companion object {
        /** The sample copies the model out of the app and compiles it for the GPU, for each case. */
        const val LOAD_TIMEOUT_MS = 300_000L
        const val DECODE_TIMEOUT_MS = 30_000L
    }
}
