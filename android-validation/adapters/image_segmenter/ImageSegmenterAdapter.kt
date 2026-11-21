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

import com.google.mediapipe.examples.imagesegmenter.ImageSegmenterHelper
import com.google.mediapipe.examples.imagesegmenter.ImageSegmenterViewModel
import com.google.mediapipe.examples.imagesegmenter.SegmentationModel
import com.google.mediapipe.examples.imagesegmenter.SegmenterDelegate
import com.google.mediapipe.examples.imagesegmenter.SegmenterTab
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied

/** Drives the image segmentation sample through its Gallery tab across image, confidence, and video modes. */
class ImageSegmenterAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val (model, asset) =
            when (case.modelId) {
                "deeplab_v3" ->
                    SegmentationModel.DEEPLABV3 to ImageSegmenterHelper.MODEL_DEEPLABV3_PATH
                "hair_segmenter" ->
                    SegmentationModel.HAIR_SEGMENTER to
                        ImageSegmenterHelper.MODEL_HAIR_SEGMENTER_PATH
                "selfie_segmenter" ->
                    SegmentationModel.SELFIE_SEGMENTER to
                        ImageSegmenterHelper.MODEL_SELFIE_SEGMENTER_PATH
                "selfie_segmenter_landscape" ->
                    SegmentationModel.SELFIE_LANDSCAPE to
                        ImageSegmenterHelper.MODEL_SELFIE_LANDSCAPE_PATH
                "selfie_multiclass" ->
                    SegmentationModel.SELFIE_MULTICLASS to
                        ImageSegmenterHelper.MODEL_SELFIE_MULTICLASS_PATH
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(asset)
        val runningMode = case.options.string("runningMode")
        val confidenceCategory =
            if (case.options.has("confidenceCategory")) {
                case.options.string("confidenceCategory")
            } else {
                null
            }
        val delegate = SegmenterDelegate.valueOf(case.delegate)

        val viewModel = sample.viewModel(ImageSegmenterViewModel::class.java)
        val settings =
            sample.onMain {
                viewModel.selectTab(SegmenterTab.GALLERY)
                viewModel.selectModel(model)
                if (viewModel.uiState.value.delegate != delegate) {
                    sample.beforeDelegateSwitch()
                    viewModel.selectDelegate(delegate)
                }
                viewModel.selectConfidenceCategory(confidenceCategory)
                viewModel.uiState.value
            }
        requireApplied("tab", SegmenterTab.GALLERY, settings.selectedTab)
        requireApplied("model", model, settings.model)
        requireApplied("delegate", delegate, settings.delegate)
        requireApplied("confidenceCategory", confidenceCategory, settings.confidenceCategory)

        return case.steps.map { step ->
            val image = step.string("image")
            val uri =
                when (runningMode) {
                    "IMAGE" -> sample.inputUri(image)
                    "VIDEO" -> sample.inputVideoUri(image)
                    else -> throw RunnerError("Unsupported runningMode $runningMode.")
                }
            sample.onMain { viewModel.segmentGalleryUri(uri) }
            val state =
                sample.await(viewModel.uiState, "the segmentation of ${step.key}") {
                    !it.isSegmentingGallery && (it.galleryResult != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            val frame =
                state.galleryResult
                    ?: throw RunnerError(
                        "The sample finished ${step.key} with neither a result nor an error."
                    )

            Run(
                step.key,
                json(
                    "imageWidth" to frame.inputWidth,
                    "imageHeight" to frame.inputHeight,
                    "maskWidth" to frame.maskWidth,
                    "maskHeight" to frame.maskHeight,
                    "labels" to frame.labels,
                    "classes" to
                        frame.classSegments.map { seg ->
                            json(
                                "index" to seg.index,
                                "label" to seg.label,
                                "fraction" to seg.fraction,
                                "centroid" to json("x" to seg.centroidX, "y" to seg.centroidY),
                            )
                        },
                    "confidence" to
                        frame.confidenceMask?.let { conf ->
                            json(
                                "category" to conf.category,
                                "index" to conf.index,
                                "count" to conf.count,
                                "width" to conf.width,
                                "height" to conf.height,
                                "channels" to conf.channels,
                                "min" to conf.minConfidence,
                                "max" to conf.maxConfidence,
                                "nonFinite" to conf.nonFiniteCount,
                                "fraction" to conf.foregroundFraction,
                                "centroid" to
                                    if (conf.centroidX != null && conf.centroidY != null) {
                                        json("x" to conf.centroidX, "y" to conf.centroidY)
                                    } else {
                                        null
                                    },
                            )
                        },
                ),
            )
        }
    }
}
