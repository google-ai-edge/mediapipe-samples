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

import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import com.mediapipe.example.interactivesegmentation.BrushMode
import com.mediapipe.example.interactivesegmentation.DrawnStroke
import com.mediapipe.example.interactivesegmentation.InteractiveSegmentationHelper
import com.mediapipe.example.interactivesegmentation.InteractiveSegmentationViewModel
import com.mediapipe.example.interactivesegmentation.SegmenterDelegate
import org.json.JSONObject

/**
 * Drives the interactive segmentation sample: loads each photo and submits positive point, lasso,
 * and optional negative exclusion strokes through [InteractiveSegmentationViewModel].
 */
class InteractiveSegmenterAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val asset =
            when (case.modelId) {
                "magic_touch" -> InteractiveSegmentationHelper.MP_INTERACTIVE_SEGMENTATION_MODEL
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        sample.usesModelAsset(asset)
        val select = case.options.string("select")
        val delegate = SegmenterDelegate.valueOf(case.delegate)
        val probesSpec = case.json.optJSONObject("probes") ?: JSONObject()

        val viewModel = sample.viewModel(InteractiveSegmentationViewModel::class.java)
        var settings =
            sample.await(viewModel.uiState, "the interactive segmenter to initialize") {
                !it.isInitializing
            }
        if (settings.delegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
            settings =
                sample.await(viewModel.uiState, "the interactive segmenter delegate to switch") {
                    !it.isInitializing
                }
        }
        settings.errorMessage?.let { throw SampleError(it) }
        requireApplied("delegate", delegate, settings.delegate)

        return case.steps.map { step ->
            val uri = sample.inputUri(step.string("image"))
            sample.onMain { viewModel.loadImageUri(uri) }
            val loaded =
                sample.await(viewModel.uiState, "the image of ${step.key} to load") {
                    !it.isSegmenting && (it.inputBitmap != null || it.errorMessage != null)
                }
            loaded.errorMessage?.let { throw SampleError(it) }

            when (select) {
                "point" -> {
                    val point = step.json.getJSONArray("point")
                    val px = point.getDouble(0).toFloat()
                    val py = point.getDouble(1).toFloat()
                    val modeState =
                        sample.onMain {
                            viewModel.selectBrushMode(BrushMode.POSITIVE)
                            viewModel.uiState.value
                        }
                    requireApplied("brushMode", BrushMode.POSITIVE, modeState.brushMode)
                    sample.onMain {
                        viewModel.addStroke(DrawnStroke(BrushMode.POSITIVE, listOf(px to py)))
                    }
                }
                "lasso" -> {
                    val box = step.json.getJSONArray("lasso")
                    val lx0 = box.getDouble(0).toFloat()
                    val ly0 = box.getDouble(1).toFloat()
                    val lx1 = box.getDouble(2).toFloat()
                    val ly1 = box.getDouble(3).toFloat()
                    val lassoPoints =
                        listOf(
                            lx0 to ly0,
                            lx1 to ly0,
                            lx1 to ly1,
                            lx0 to ly1,
                            lx0 to ly0,
                        )
                    val modeState =
                        sample.onMain {
                            viewModel.selectBrushMode(BrushMode.LASSO)
                            viewModel.uiState.value
                        }
                    requireApplied("brushMode", BrushMode.LASSO, modeState.brushMode)
                    sample.onMain {
                        viewModel.addStroke(DrawnStroke(BrushMode.LASSO, lassoPoints))
                    }
                }
                else -> throw RunnerError("Unsupported select mode \"$select\".")
            }

            var state =
                sample.await(viewModel.uiState, "the primary selection of ${step.key}") {
                    !it.isSegmenting && (it.result != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }

            if (step.has("negativeStroke")) {
                val neg = step.json.getJSONArray("negativeStroke")
                val nx0 = neg.getDouble(0).toFloat()
                val ny0 = neg.getDouble(1).toFloat()
                val nx1 = neg.getDouble(2).toFloat()
                val ny1 = neg.getDouble(3).toFloat()
                val negPoints = linePoints(nx0, ny0, nx1, ny1, NEGATIVE_STROKE_POINTS)
                val modeState =
                    sample.onMain {
                        viewModel.selectBrushMode(BrushMode.NEGATIVE)
                        viewModel.uiState.value
                    }
                requireApplied("brushMode", BrushMode.NEGATIVE, modeState.brushMode)
                sample.onMain {
                    viewModel.addStroke(DrawnStroke(BrushMode.NEGATIVE, negPoints))
                }
                state =
                    sample.await(viewModel.uiState, "the negative stroke refinement of ${step.key}") {
                        !it.isSegmenting
                    }
                state.errorMessage?.let { throw SampleError(it) }
            }

            val inputBitmap =
                state.inputBitmap
                    ?: throw RunnerError("The sample lost its input bitmap during ${step.key}.")
            val result =
                state.result
                    ?: throw RunnerError(
                        "The sample finished ${step.key} with neither a mask nor an error."
                    )

            val probesJson = JSONObject()
            for (probeName in probesSpec.keys()) {
                val coords = probesSpec.getJSONArray(probeName)
                val px = coords.getDouble(0)
                val py = coords.getDouble(1)
                probesJson.put(probeName, result.isSelectedAtNormalized(px, py))
            }

            Run(
                step.key,
                json(
                    "imageWidth" to inputBitmap.width,
                    "imageHeight" to inputBitmap.height,
                    "mask" to
                        json(
                            "width" to result.maskWidth,
                            "height" to result.maskHeight,
                            "channels" to result.channels,
                            "min" to result.minConfidence,
                            "max" to result.maxConfidence,
                            "nonFinite" to result.nonFiniteCount,
                        ),
                    "fraction" to result.foregroundFraction,
                    "centroid" to
                        if (result.centroidX != null && result.centroidY != null) {
                            json("x" to result.centroidX, "y" to result.centroidY)
                        } else {
                            null
                        },
                    "bounds" to
                        if (
                            result.boundsMinX != null &&
                                result.boundsMaxX != null &&
                                result.boundsMinY != null &&
                                result.boundsMaxY != null
                        ) {
                            json(
                                "minX" to result.boundsMinX,
                                "maxX" to result.boundsMaxX,
                                "minY" to result.boundsMinY,
                                "maxY" to result.boundsMaxY,
                            )
                        } else {
                            null
                        },
                    "probes" to probesJson,
                ),
            )
        }
    }

    private fun linePoints(
        x0: Float,
        y0: Float,
        x1: Float,
        y1: Float,
        steps: Int,
    ): List<Pair<Float, Float>> =
        List(steps) { idx ->
            val t = idx.toFloat() / (steps - 1).coerceAtLeast(1).toFloat()
            (x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t)
        }

    private companion object {
        const val NEGATIVE_STROKE_POINTS = 25
    }
}
