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

import com.google.mediapipe.examples.facelandmarker.FaceLandmarkerHelper
import com.google.mediapipe.examples.facelandmarker.FaceLandmarkerViewModel
import com.google.mediapipe.examples.facelandmarker.LandmarkerDelegate
import com.google.mediapipe.examples.facelandmarker.LandmarkerTab
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import org.json.JSONObject

/** Drives the face landmarker sample through its Gallery tab, as a user who picks an image or video. */
class FaceLandmarkerAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "face_landmarker") throw RunnerError("The sample has no model ${case.modelId}.")
        sample.usesModelAsset(FaceLandmarkerHelper.MP_FACE_LANDMARKER_TASK)
        val runningMode = case.options.string("runningMode")
        val numFaces = case.options.int("numFaces")
        // FaceLandmarkerHelper.setupFaceLandmarker() always enables blendshapes and transformation matrices.
        case.options.fixed("outputFaceBlendshapes", true)
        case.options.fixed("outputFacialTransformationMatrixes", true)
        val delegate = LandmarkerDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(FaceLandmarkerViewModel::class.java)
        sample.onMain {
            viewModel.selectTab(LandmarkerTab.GALLERY)
            viewModel.selectMaxFaces(numFaces)
        }
        if (viewModel.uiState.value.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
        }
        val settings = sample.onMain { viewModel.uiState.value }
        requireApplied("tab", LandmarkerTab.GALLERY, settings.selectedTab)
        requireApplied("delegate", delegate, settings.delegate)
        requireApplied("numFaces", numFaces, settings.maxFaces)

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
                sample.await(viewModel.uiState, "the faces of ${step.key}") {
                    !it.isDetectingGallery && (it.galleryResult != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            val frame =
                state.galleryResult
                    ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            val valuesJson =
                JSONObject().apply {
                    for ((name, score) in frame.blendshapesByFace.firstOrNull().orEmpty()) {
                        put(name, score.toDouble())
                    }
                }
            Run(
                step.key,
                json(
                    "faces" to
                        frame.faces.map { face ->
                            json(
                                // The sample holds the landmarks in pixels: divide by the size of the image.
                                "landmarks" to
                                    face.landmarks.map { (x, y) ->
                                        listOf(x.toDouble() / frame.imageWidth, y.toDouble() / frame.imageHeight)
                                    }
                            )
                        },
                    "blendshapeCounts" to frame.blendshapesByFace.map { it.size },
                    "matrixShapes" to frame.matrixShapes.map { (rows, cols) -> listOf(rows, cols) },
                    "values" to valuesJson,
                ),
            )
        }
    }
}
