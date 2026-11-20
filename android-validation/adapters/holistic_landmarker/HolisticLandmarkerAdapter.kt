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

import com.google.mediapipe.examples.holisticlandmarker.HolisticLandmarkerHelper
import com.google.mediapipe.examples.holisticlandmarker.HolisticLandmarkerViewModel
import com.google.mediapipe.examples.holisticlandmarker.LandmarkerDelegate
import com.google.mediapipe.examples.holisticlandmarker.LandmarkerTab
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the holistic landmarker sample through its Gallery tab, as a user who picks an image or video. */
class HolisticLandmarkerAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "holistic_landmarker") throw RunnerError("The sample has no model ${case.modelId}.")
        sample.usesModelAsset(HolisticLandmarkerHelper.MODEL_HOLISTIC_PATH)
        val runningMode = case.options.string("runningMode")
        // HolisticLandmarkerHelper.setupHolisticLandmarker() always enables face blendshapes and segmentation masks.
        case.options.fixed("outputFaceBlendshapes", true)
        case.options.fixed("outputSegmentationMask", true)
        val delegate = LandmarkerDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(HolisticLandmarkerViewModel::class.java)
        sample.onMain {
            viewModel.selectTab(LandmarkerTab.GALLERY)
        }
        if (viewModel.uiState.value.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
        }
        val settings = sample.onMain { viewModel.uiState.value }
        requireApplied("tab", LandmarkerTab.GALLERY, settings.selectedTab)
        requireApplied("delegate", delegate, settings.delegate)

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
                sample.await(viewModel.uiState, "the holistic landmarks of ${step.key}") {
                    !it.isDetectingGallery && (it.galleryResult != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            val frame =
                state.galleryResult
                    ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")

            // The sample holds landmarks in pixels: divide by the size of the image.
            fun group(points: List<Pair<Float, Float>>) =
                if (points.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        json(
                            "landmarks" to
                                points.map { (x, y) ->
                                    listOf(x.toDouble() / frame.imageWidth, y.toDouble() / frame.imageHeight)
                                }
                        )
                    )
                }

            val maskJson =
                frame.segmentationMask?.let { mask ->
                    json(
                        "width" to mask.width,
                        "height" to mask.height,
                        "channels" to mask.channels,
                        "imageWidth" to frame.imageWidth,
                        "imageHeight" to frame.imageHeight,
                        "min" to mask.min,
                        "max" to mask.max,
                        "nonFinite" to mask.nonFinite,
                        "fraction" to mask.fraction,
                        "centroid" to
                            if (mask.centroidX != null && mask.centroidY != null) {
                                json("x" to mask.centroidX, "y" to mask.centroidY)
                            } else {
                                null
                            },
                    )
                }

            Run(
                step.key,
                json(
                    "pose" to group(frame.poseLandmarks),
                    "face" to group(frame.faceLandmarks),
                    "leftHand" to group(frame.leftHandLandmarks),
                    "rightHand" to group(frame.rightHandLandmarks),
                    "blendshapeCount" to frame.faceBlendshapeCount,
                    "mask" to maskJson,
                ),
            )
        }
    }
}
