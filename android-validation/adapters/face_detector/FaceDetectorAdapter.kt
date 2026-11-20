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

import com.google.mediapipe.examples.facedetection.DetectorDelegate
import com.google.mediapipe.examples.facedetection.DetectorModel
import com.google.mediapipe.examples.facedetection.DetectorTab
import com.google.mediapipe.examples.facedetection.FaceDetectorHelper
import com.google.mediapipe.examples.facedetection.FaceDetectorViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the face detection sample through its Gallery tab, as a user who picks a photo. */
class FaceDetectorAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val (model, asset) =
            when (case.modelId) {
                "blaze_face_short_range" -> DetectorModel.SHORT_RANGE to FaceDetectorHelper.MODEL_SHORT_RANGE_NAME
                "blaze_face_full_range" -> DetectorModel.FULL_RANGE to FaceDetectorHelper.MODEL_FULL_RANGE_NAME
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(asset)
        val runningMode = case.options.string("runningMode")
        val delegate = DetectorDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(FaceDetectorViewModel::class.java)
        val settings =
            sample.onMain {
                viewModel.selectTab(DetectorTab.GALLERY)
                viewModel.selectModel(model)
                viewModel.selectDelegate(delegate)
                viewModel.uiState.value
            }
        requireApplied("tab", DetectorTab.GALLERY, settings.selectedTab)
        requireApplied("model", model, settings.model)
        requireApplied("delegate", delegate, settings.delegate)

        return case.steps.map { step ->
            val image = step.string("image")
            val uri =
                when (runningMode) {
                    "IMAGE" -> sample.inputUri(image)
                    "VIDEO" -> sample.inputVideoUri(image)
                    else -> throw RunnerError("Unsupported runningMode $runningMode.")
                }
            // Clears the result and sets isDetectingGallery before it returns.
            sample.onMain { viewModel.detectGalleryUri(uri) }
            val state = sample.await(viewModel.uiState, "the faces of ${step.key}") { !it.isDetectingGallery && (it.galleryResult != null || it.errorMessage != null) }
            state.errorMessage?.let { throw SampleError(it) }
            val frame = state.galleryResult ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            Run(
                step.key,
                json(
                    "imageWidth" to frame.imageWidth,
                    "imageHeight" to frame.imageHeight,
                    "detections" to
                        frame.detections.map { face ->
                            json(
                                "score" to face.score,
                                "box" to
                                    json(
                                        "x" to face.left,
                                        "y" to face.top,
                                        "width" to face.right - face.left,
                                        "height" to face.bottom - face.top,
                                    ),
                                // The sample holds the keypoints in pixels: the fraction MediaPipe returned,
                                // times the size of the image. This divides by that size again.
                                "keypoints" to
                                    face.keypoints.map { (x, y) ->
                                        listOf(x.toDouble() / frame.imageWidth, y.toDouble() / frame.imageHeight)
                                    },
                            )
                        },
                ),
            )
        }
    }
}
