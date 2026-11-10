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

package com.google.mediapipe.examples.imageembedder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.EmbeddingMetrics
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageEmbedderHelperTest {

    companion object {
        private const val TAG = "ImageEmbedderHelperTest"
    }

    @Test
    fun mobileNetV3Small_cpuSimilarityAndLatency_matchesGolden() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pizzaA = loadAppBitmap(context, ImageEmbedderHelper.ASSET_AMERICAN_PIZZA)
        val pizzaB = loadAppBitmap(context, ImageEmbedderHelper.ASSET_ITALY_PIZZA)
        val cup = loadAppBitmap(context, ImageEmbedderHelper.ASSET_CUP)

        val recorder = LatencyRecorder("image_embedder_small_cpu")
        recorder.putMetadata("model", ImageEmbedderHelper.MODEL_SMALL_PATH)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                ImageEmbedderHelper(
                    context = context,
                    currentDelegate = ImageEmbedderHelper.DELEGATE_CPU,
                    currentModel = ImageEmbedderHelper.MODEL_SMALL,
                )
            }
        try {
            assertFalse("MobileNet V3 Small failed to initialize", helper.isClosed)

            val selfSim = helper.embed(pizzaA, pizzaA)
            assertNotNull(selfSim)
            EmbeddingMetrics.assertSimilarityClose(
                expected = 1.0,
                actual = selfSim!!.similarity,
                tolerance = 1e-3,
                label = "small.selfSimilarity",
            )

            val pizzasBundle = recorder.measure("embed_pair_ms") { helper.embed(pizzaA, pizzaB) }!!
            val unrelatedBundle = helper.embed(pizzaA, cup)!!
            Log.i(TAG, "Small CPU: pizzas=${pizzasBundle.similarity}, pizzaVsCup=${unrelatedBundle.similarity}")

            EmbeddingMetrics.assertSimilarityClose(
                expected = 0.40,
                actual = pizzasBundle.similarity,
                tolerance = 0.09,
                label = "small.pizzasSimilarity",
            )
            assertTrue(
                "Expected pizza pair (${pizzasBundle.similarity}) > pizza vs cup (${unrelatedBundle.similarity})",
                pizzasBundle.similarity > unrelatedBundle.similarity,
            )

            repeat(5) {
                recorder.measure("embed_steady_ms") { helper.embed(pizzaA, pizzaB) }
            }
            recorder.flush(context)
        } finally {
            helper.clearImageEmbedder()
            pizzaA.recycle()
            pizzaB.recycle()
            cup.recycle()
        }
    }

    @Test
    fun mobileNetV3Large_cpuSimilarityAndLatency_matchesOrdering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pizzaA = loadAppBitmap(context, ImageEmbedderHelper.ASSET_AMERICAN_PIZZA)
        val pizzaB = loadAppBitmap(context, ImageEmbedderHelper.ASSET_ITALY_PIZZA)
        val cup = loadAppBitmap(context, ImageEmbedderHelper.ASSET_CUP)

        val recorder = LatencyRecorder("image_embedder_large_cpu")
        recorder.putMetadata("model", ImageEmbedderHelper.MODEL_LARGE_PATH)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                ImageEmbedderHelper(
                    context = context,
                    currentDelegate = ImageEmbedderHelper.DELEGATE_CPU,
                    currentModel = ImageEmbedderHelper.MODEL_LARGE,
                )
            }
        try {
            assertFalse("MobileNet V3 Large failed to initialize", helper.isClosed)

            val pizzasBundle = recorder.measure("embed_pair_ms") { helper.embed(pizzaA, pizzaB) }!!
            val unrelatedBundle = helper.embed(pizzaA, cup)!!
            Log.i(TAG, "Large CPU: pizzas=${pizzasBundle.similarity}, pizzaVsCup=${unrelatedBundle.similarity}")

            assertTrue(
                "Expected pizza pair (${pizzasBundle.similarity}) > pizza vs cup (${unrelatedBundle.similarity})",
                pizzasBundle.similarity > unrelatedBundle.similarity,
            )

            repeat(5) {
                recorder.measure("embed_steady_ms") { helper.embed(pizzaA, pizzaB) }
            }
            recorder.flush(context)
        } finally {
            helper.clearImageEmbedder()
            pizzaA.recycle()
            pizzaB.recycle()
            cup.recycle()
        }
    }

    private fun loadAppBitmap(context: Context, assetName: String): Bitmap =
        context.assets.open(assetName).use { stream ->
            requireNotNull(BitmapFactory.decodeStream(stream)) { "Failed to decode $assetName" }
        }
}
