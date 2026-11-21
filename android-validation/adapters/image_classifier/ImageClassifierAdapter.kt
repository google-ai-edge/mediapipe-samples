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

import com.google.mediapipe.examples.imageclassification.ClassifierDelegate
import com.google.mediapipe.examples.imageclassification.ClassifierModel
import com.google.mediapipe.examples.imageclassification.ClassifierTab
import com.google.mediapipe.examples.imageclassification.ImageClassifierHelper
import com.google.mediapipe.examples.imageclassification.ImageClassifierViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the image classification sample through its Gallery tab, as a user who picks a photo or video. */
class ImageClassifierAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val (model, asset) =
            when (case.modelId) {
                "efficientnet_lite0" -> ClassifierModel.LITE0 to ImageClassifierHelper.MODEL_EFFICIENTNETV0_FILE
                "efficientnet_lite2" -> ClassifierModel.LITE2 to ImageClassifierHelper.MODEL_EFFICIENTNETV2_FILE
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(asset)
        val runningMode = case.options.string("runningMode")
        val maxResults = case.options.int("maxResults")
        val threshold =
            if (case.options.has("scoreThreshold")) case.options.float("scoreThreshold") else 0f
        val delegate = ClassifierDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(ImageClassifierViewModel::class.java)
        sample.onMain {
            viewModel.selectTab(ClassifierTab.GALLERY)
            viewModel.selectModel(model)
            viewModel.selectMaxResults(maxResults)
            viewModel.updateThreshold(threshold)
        }
        if (viewModel.uiState.value.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
        }
        val settings = viewModel.uiState.value
        requireApplied("tab", ClassifierTab.GALLERY, settings.selectedTab)
        requireApplied("model", model, settings.model)
        requireApplied("delegate", delegate, settings.delegate)
        requireApplied("maxResults", maxResults, settings.maxResults)
        requireApplied("scoreThreshold", threshold, settings.threshold)

        return case.steps.map { step ->
            val image = step.string("image")
            val uri =
                when (runningMode) {
                    "IMAGE" -> sample.inputUri(image)
                    "VIDEO" -> sample.inputVideoUri(image)
                    else -> throw RunnerError("Unsupported runningMode $runningMode.")
                }
            sample.onMain { viewModel.classifyGalleryUri(uri) }
            val state =
                sample.await(viewModel.uiState, "the classification of ${step.key}") {
                    !it.isClassifyingGallery && (it.galleryResult != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            val frame =
                state.galleryResult
                    ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            Run(
                step.key,
                json("categories" to frame.categories.map { json("label" to it.label, "score" to it.score) }),
            )
        }
    }
}
