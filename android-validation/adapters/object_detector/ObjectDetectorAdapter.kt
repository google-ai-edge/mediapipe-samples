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

import com.google.mediapipe.examples.objectdetection.DetectorDelegate
import com.google.mediapipe.examples.objectdetection.DetectorTab
import com.google.mediapipe.examples.objectdetection.ObjectDetectorHelper
import com.google.mediapipe.examples.objectdetection.ObjectDetectorViewModel
import com.google.mediapipe.examples.objectdetection.ObjectModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the object detection sample through its Gallery tab, as a user who picks a photo or video. */
class ObjectDetectorAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val (model, asset) =
            when (case.modelId) {
                "efficientdet_lite0_int8" ->
                    ObjectModel.LITE0_INT8 to ObjectDetectorHelper.MODEL_EFFICIENTDETV0_INT8_NAME
                "efficientdet_lite0_float32" ->
                    ObjectModel.LITE0 to ObjectDetectorHelper.MODEL_EFFICIENTDETV0_NAME
                "efficientdet_lite2_float32" ->
                    ObjectModel.LITE2 to ObjectDetectorHelper.MODEL_EFFICIENTDETV2_NAME
                "ssd_mobilenet_v2_float32" ->
                    ObjectModel.SSD_MOBILENET_V2 to ObjectDetectorHelper.MODEL_SSD_MOBILENETV2_FP32_NAME
                "ssd_mobilenet_v2_float16" ->
                    ObjectModel.SSD_MOBILENET_V2_FP16 to ObjectDetectorHelper.MODEL_SSD_MOBILENETV2_FP16_NAME
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(asset)
        val runningMode = case.options.string("runningMode")
        val threshold = case.options.float("scoreThreshold")
        val delegate = DetectorDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(ObjectDetectorViewModel::class.java)
        sample.onMain {
            viewModel.selectTab(DetectorTab.GALLERY)
            viewModel.selectModel(model)
            viewModel.updateThreshold(threshold)
        }
        if (viewModel.uiState.value.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
        }
        val settings = viewModel.uiState.value
        requireApplied("tab", DetectorTab.GALLERY, settings.selectedTab)
        requireApplied("model", model, settings.model)
        requireApplied("delegate", delegate, settings.delegate)
        requireApplied("scoreThreshold", threshold, settings.threshold)

        return case.steps.map { step ->
            val image = step.string("image")
            val uri =
                when (runningMode) {
                    "IMAGE" -> sample.inputUri(image)
                    "VIDEO" -> sample.inputVideoUri(image)
                    else -> throw RunnerError("Unsupported runningMode $runningMode.")
                }
            sample.onMain { viewModel.detectGalleryUri(uri) }
            val state =
                sample.await(viewModel.uiState, "the detections of ${step.key}") {
                    !it.isDetectingGallery && (it.galleryResult != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            val frame =
                state.galleryResult
                    ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            Run(
                step.key,
                json(
                    "imageWidth" to frame.imageWidth,
                    "imageHeight" to frame.imageHeight,
                    "detections" to
                        frame.detections.map {
                            json(
                                "label" to it.label,
                                "score" to it.score,
                                "box" to
                                    json(
                                        "x" to it.left,
                                        "y" to it.top,
                                        "width" to it.right - it.left,
                                        "height" to it.bottom - it.top,
                                    ),
                            )
                        },
                ),
            )
        }
    }
}
