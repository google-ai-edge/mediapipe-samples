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

package com.google.mediapipe.examples.semanticretriever

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SemanticRetrieverHelperTest {

    companion object {
        private const val TAG = "SemanticRetrieverHelperTest"
    }

    private fun setupHelperWithFallback(
        context: Context,
        store: Int,
        sqliteDbName: String = "test_semantic_sqlite.db",
        appSearchDbName: String = "test_semantic_appsearch",
    ): Pair<SemanticRetrieverHelper, String> {
        val initialDelegate = SemanticRetrieverHelper.defaultDelegate()
        val helper =
            SemanticRetrieverHelper(
                context = context,
                currentDelegate = initialDelegate,
                currentStore = store,
                sqliteDbName = sqliteDbName,
                appSearchDbName = appSearchDbName,
            )
        if (initialDelegate == SemanticRetrieverHelper.DELEGATE_CPU) {
            assertTrue(
                "SemanticRetriever failed to initialize on CPU for store=$store",
                helper.setupSemanticRetriever(),
            )
            return helper to "CPU"
        }
        if (helper.setupSemanticRetriever()) {
            return helper to "GPU"
        }
        helper.currentDelegate = SemanticRetrieverHelper.DELEGATE_CPU
        assertTrue(
            "SemanticRetriever failed to initialize on CPU for store=$store",
            helper.setupSemanticRetriever(),
        )
        return helper to "CPU"
    }

    @Test
    fun sqliteStore_indexesSampleGalleryAndRetrievesExpectedMatches() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("semantic_retriever")
        recorder.putMetadata("model", SemanticRetrieverHelper.MODEL_FILE_NAME)
        recorder.putMetadata("store", "SQLite")

        val (helper, usedDelegate) =
            recorder.measure("model_load_ms") {
                setupHelperWithFallback(context, SemanticRetrieverHelper.STORE_SQLITE)
            }
        recorder.putMetadata("delegate", usedDelegate)

        try {
            helper.clearIndex()

            recorder.measure("index_10_images_ms") {
                BundledGalleryItems.forEach { item ->
                    helper.insertImage(
                        id = item.id,
                        imageUri = item.assetUri,
                        metadata = mapOf("label" to item.label, "assetPath" to item.assetPath),
                    )
                }
            }
            assertEquals(BundledGalleryItems.size, helper.getAllRecordIds().size)

            val catSearch =
                recorder.measure("retrieve_cat_ms") {
                    helper.retrieve("a cute fluffy cat", topK = 5)
                }!!
            Log.i(
                TAG,
                "SQLite cat results=${catSearch.results.map { it.id() to it.score() }}",
            )
            assertTrue(
                "Expected cute_cat in top 2 for 'a cute fluffy cat', got ${catSearch.results.take(2).map { it.id() }}",
                catSearch.results.take(2).any { it.id() == "cute_cat" },
            )
            assertTrue(
                "Expected descending similarity scores",
                catSearch.results.zipWithNext().all { (a, b) -> a.score() >= b.score() - 1e-6 },
            )

            val beachSearch = helper.retrieve("warm sandy ocean beach", topK = 5)!!
            Log.i(
                TAG,
                "SQLite beach results=${beachSearch.results.map { it.id() to it.score() }}",
            )
            assertTrue(
                "Expected sunny_beach in top 2 for 'warm sandy ocean beach', got ${beachSearch.results.take(2).map { it.id() }}",
                beachSearch.results.take(2).any { it.id() == "sunny_beach" },
            )

            val mountainSearch = helper.retrieve("snowy mountain peak", topK = 5)!!
            assertTrue(
                "Expected snowy_mountain in top 2, got ${mountainSearch.results.take(2).map { it.id() }}",
                mountainSearch.results.take(2).any { it.id() == "snowy_mountain" },
            )

            repeat(5) {
                recorder.measure("retrieve_steady_ms") {
                    helper.retrieve("warm sandy ocean beach", topK = 5)
                }
            }
            recorder.flush(context)

            helper.clearIndex()
            assertTrue("Expected empty index after clearIndex()", helper.getAllRecordIds().isEmpty())
        } finally {
            helper.clearIndex()
            helper.clearSemanticRetriever()
        }
    }

    @Test
    fun appSearchStore_indexesSubsetAndRetrievesRelevantImage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val (helper, _) = setupHelperWithFallback(context, SemanticRetrieverHelper.STORE_APPSEARCH)
        try {
            helper.clearIndex()
            val subset =
                BundledGalleryItems.filter {
                    it.id in setOf("cute_cat", "sunny_beach", "snowy_mountain", "fast_car")
                }
            subset.forEach { item ->
                helper.insertImage(
                    id = item.id,
                    imageUri = item.assetUri,
                    metadata = mapOf("label" to item.label, "assetPath" to item.assetPath),
                )
            }
            assertEquals(subset.size, helper.getAllRecordIds().size)

            val bundle = helper.retrieve("sleek fast sports car", topK = 3)
            assertNotNull(bundle)
            val results = bundle!!.results
            Log.i(TAG, "AppSearch car results=${results.map { it.id() to it.score() }}")
            assertTrue(
                "Expected fast_car in top 2 for AppSearch retrieval, got ${results.take(2).map { it.id() }}",
                results.take(2).any { it.id() == "fast_car" },
            )
        } finally {
            helper.clearIndex()
            helper.clearSemanticRetriever()
        }
    }
}
