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

import com.google.mediapipe.examples.handlandmarker.HandLandmarkerViewModel
import com.google.mediapipe.examples.handlandmarker.LandmarkerDelegate
import com.google.mediapipe.examples.handlandmarker.LandmarkerTab
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the hand landmarker sample through its Gallery tab, as a user who picks a photo. */
class HandLandmarkerAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "hand_landmarker") throw RunnerError("The sample has no model ${case.modelId}.")
        // HandLandmarkerHelper.MP_HAND_LANDMARKER_TASK, which is private.
        sample.usesModelAsset("hand_landmarker.task")
        val runningMode = case.options.string("runningMode")
        val numHands = case.options.int("numHands")
        val delegate = LandmarkerDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(HandLandmarkerViewModel::class.java)
        val settings =
            sample.onMain {
                viewModel.selectTab(LandmarkerTab.GALLERY)
                viewModel.selectDelegate(delegate)
                viewModel.selectMaxHands(numHands)
                viewModel.uiState.value
            }
        requireApplied("tab", LandmarkerTab.GALLERY, settings.selectedTab)
        requireApplied("delegate", delegate, settings.delegate)
        requireApplied("numHands", numHands, settings.maxHands)

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
            val state = sample.await(viewModel.uiState, "the hands of ${step.key}") { !it.isDetectingGallery && (it.galleryResult != null || it.errorMessage != null) }
            state.errorMessage?.let { throw SampleError(it) }
            val frame = state.galleryResult ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            Run(
                step.key,
                json(
                    "hands" to
                        frame.hands.map { hand ->
                            json(
                                // The sample holds the landmarks in pixels: the fraction MediaPipe returned,
                                // times the size of the image. This divides by that size again.
                                "landmarks" to
                                    hand.landmarks.map { (x, y) ->
                                        listOf(x.toDouble() / frame.imageWidth, y.toDouble() / frame.imageHeight)
                                    },
                                "handedness" to hand.handedness,
                                "handednessScore" to hand.score,
                            )
                        }
                ),
            )
        }
    }
}
