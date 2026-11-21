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

import com.google.mediapipe.examples.semanticretriever.RetrieverDelegateOption
import com.google.mediapipe.examples.semanticretriever.SemanticRetrieverHelper
import com.google.mediapipe.examples.semanticretriever.SemanticRetrieverViewModel
import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import java.io.File

/** Drives the semantic retriever sample: clears the index, indexes the spec images and documents, and runs each query. */
class SemanticRetrieverAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "embeddinggemma2_text_vision_440m") {
            throw RunnerError("The sample has no model ${case.modelId}.")
        }
        val modelFile = SemanticRetrieverHelper.MODEL_FILE_NAME
        listOfNotNull(
                File("/data/local/tmp/semantic_retriever_models", modelFile),
                sample.activity.getExternalFilesDir(null)?.let { File(it, modelFile) },
            )
            .firstOrNull { it.canRead() && it.length() > 0L }
            ?.let {
                throw RunnerError(
                    "The sample loads $it instead of its bundled asset $modelFile. Remove that file from the device."
                )
            }
        sample.usesModelAsset(modelFile)

        val limit = case.options.int("limit")
        val minSimilarity = case.options.float("minSimilarity").toDouble()
        val delegate = RetrieverDelegateOption.valueOf(case.delegate)

        val viewModel = sample.viewModel(SemanticRetrieverViewModel::class.java)
        fun waitIdle(what: String) =
            sample.await(viewModel.uiState, what, LOAD_TIMEOUT_MS) { !it.isBusy }

        var settings = waitIdle("initial model and gallery load")
        if (settings.delegateOption != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.selectDelegate(delegate) }
            settings = waitIdle("loading SemanticRetriever on ${case.delegate}")
        }
        settings.errorMessage?.let { throw SampleError(it) }
        requireApplied("delegate", delegate, settings.delegateOption)

        sample.onMain {
            viewModel.updateTopK(limit)
            viewModel.updateMinSimilarity(minSimilarity)
            viewModel.clearIndex()
        }
        val cleared = waitIdle("clearing the index")
        cleared.errorMessage?.let { throw SampleError(it) }
        requireApplied("limit", limit, cleared.topK)
        requireApplied("minSimilarity", minSimilarity, cleared.minSimilarity)
        requireApplied("indexedCount after clearIndex", 0, cleared.indexedCount)

        val indexJson = case.json.getJSONObject("index")
        val imagesArray = indexJson.getJSONArray("images")
        var expectedIndexed = 0
        for (i in 0 until imagesArray.length()) {
            val imageName = imagesArray.getString(i)
            val uri = sample.inputUri(imageName)
            sample.onMain { viewModel.addCustomPhoto(uri) }
            expectedIndexed += 1
            val afterImage = waitIdle("indexing image $imageName")
            afterImage.errorMessage?.let { throw SampleError(it) }
            requireApplied("indexedCount after $imageName", expectedIndexed, afterImage.indexedCount)
        }

        val documentsArray = indexJson.getJSONArray("documents")
        for (i in 0 until documentsArray.length()) {
            val docId = "document ${i + 1}"
            val docText = documentsArray.getString(i)
            sample.onMain { viewModel.addCustomDocument(docId, docText) }
            expectedIndexed += 1
            val afterDoc = waitIdle("indexing $docId")
            afterDoc.errorMessage?.let { throw SampleError(it) }
            requireApplied("indexedCount after $docId", expectedIndexed, afterDoc.indexedCount)
        }

        return case.steps.map { step ->
            val query = step.string("query")
            if (query != query.trim()) {
                throw RunnerError("The sample trims queries, which would alter \"$query\".")
            }
            sample.onMain {
                viewModel.updateQuery(query)
                if (!viewModel.uiState.value.canSearch) {
                    throw RunnerError("The sample is not ready to search ${step.key}: ${viewModel.uiState.value}")
                }
                viewModel.search()
            }
            val searched =
                sample.await(viewModel.uiState, "retrieval results for ${step.key}") {
                    !it.isSearching && (it.lastSearchTimeMs != null || it.errorMessage != null)
                }
            searched.errorMessage?.let { throw SampleError(it) }
            Run(
                step.key,
                json(
                    "results" to
                        searched.matches.map { match ->
                            json(
                                "id" to match.id,
                                "score" to match.score,
                            )
                        }
                ),
            )
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 480_000L
    }
}
