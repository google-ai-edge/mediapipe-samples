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

import com.google.mediapipe.examples.gesturerecognizer.GestureRecognizerViewModel
import com.google.mediapipe.examples.gesturerecognizer.RecognizerDelegate
import com.google.mediapipe.examples.gesturerecognizer.RecognizerTab
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the gesture recognizer sample through its Gallery tab, as a user who picks one photo after another. */
class GestureRecognizerAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "gesture_recognizer") throw RunnerError("The sample has no model ${case.modelId}.")
        // GestureRecognizerHelper.MP_RECOGNIZER_TASK, which is private.
        sample.usesModelAsset("gesture_recognizer.task")
        val runningMode = case.options.string("runningMode")
        val delegate = RecognizerDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(GestureRecognizerViewModel::class.java)
        val settings =
            sample.onMain {
                viewModel.selectTab(RecognizerTab.GALLERY)
                viewModel.selectDelegate(delegate)
                viewModel.uiState.value
            }
        requireApplied("tab", RecognizerTab.GALLERY, settings.selectedTab)
        requireApplied("delegate", delegate, settings.delegate)

        return case.steps.map { step ->
            val image = step.string("image")
            val uri =
                when (runningMode) {
                    "IMAGE" -> sample.inputUri(image)
                    "VIDEO" -> sample.inputVideoUri(image)
                    else -> throw RunnerError("Unsupported runningMode $runningMode.")
                }
            // Clears the result of the photo before and sets isRecognizingGallery before it returns.
            sample.onMain { viewModel.recognizeGalleryUri(uri) }
            val state = sample.await(viewModel.uiState, "the gesture of ${step.key}") { !it.isRecognizingGallery && (it.galleryResult != null || it.errorMessage != null) }
            state.errorMessage?.let { throw SampleError(it) }
            val frame = state.galleryResult ?: throw RunnerError("The sample finished ${step.key} with neither a result nor an error.")
            // The sample holds one gesture for the photo, the best of all hands. Of one hand, that is its gesture;
            // of several, the sample does not say whose it is.
            val gesture = frame.topGesture?.takeIf { frame.hands.size == 1 }
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
                                "gesture" to gesture?.name,
                                "score" to gesture?.score,
                            )
                        }
                ),
            )
        }
    }
}
