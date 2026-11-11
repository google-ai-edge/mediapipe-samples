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

package com.google.mediapipe.examples.universalembedder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.EmbeddingMetrics
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UniversalEmbedderHelperTest {

    companion object {
        private const val TAG = "UniversalEmbedderHelperTest"
    }

    private fun loadAssetBitmap(context: Context, assetPath: String): Bitmap {
        val decoded = context.assets.open(assetPath).use { BitmapFactory.decodeStream(it) }
        assertNotNull("Missing asset $assetPath", decoded)
        return if (decoded.config == Bitmap.Config.ARGB_8888) decoded
        else decoded.copy(Bitmap.Config.ARGB_8888, false)
    }

    @Test
    fun multimodalEmbeddings_crossModalAndSameModalRanking() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val initialDelegate = UniversalEmbedderHelper.defaultDelegate()
        val helper =
            UniversalEmbedderHelper(
                context = context,
                currentDelegate = initialDelegate,
            )
        var usedDelegate =
            if (initialDelegate == UniversalEmbedderHelper.DELEGATE_CPU) "CPU" else "GPU"
        val recorder = LatencyRecorder("universal_embedder")
        recorder.putMetadata("model", UniversalEmbedderHelper.MODEL_FILE_NAME)

        val initialized =
            recorder.measure("model_load_ms") {
                if (helper.setupUniversalEmbedder()) {
                    true
                } else if (initialDelegate != UniversalEmbedderHelper.DELEGATE_CPU) {
                    usedDelegate = "CPU"
                    helper.currentDelegate = UniversalEmbedderHelper.DELEGATE_CPU
                    helper.setupUniversalEmbedder()
                } else {
                    false
                }
            }
        recorder.putMetadata("delegate", usedDelegate)
        try {
            assertTrue("UniversalEmbedder failed to initialize", initialized && helper.isReady)

            val catBmp = loadAssetBitmap(context, "images/cute_cat.jpg")
            val beachBmp = loadAssetBitmap(context, "images/sunny_beach.jpg")
            val mountainBmp = loadAssetBitmap(context, "images/snowy_mountain.jpg")

            val catTextMatch =
                recorder.measure("compare_cross_modal_ms") {
                    helper.compare(
                        UniversalEmbedderHelper.Input.Image(catBmp),
                        UniversalEmbedderHelper.Input.Text("a cute fluffy cat"),
                    )!!
                }
            val catTextMismatch =
                helper.compare(
                    UniversalEmbedderHelper.Input.Image(catBmp),
                    UniversalEmbedderHelper.Input.Text(
                        "central bank interest rate policy decision"
                    ),
                )!!
            Log.i(
                TAG,
                "Cross-modal catMatch=${catTextMatch.similarity} catMismatch=${catTextMismatch.similarity} dims=${catTextMatch.dimensions}",
            )
            assertTrue("Expected positive embedding dims", catTextMatch.dimensions > 0)
            assertTrue(
                "Expected matching caption (${catTextMatch.similarity}) > mismatch (${catTextMismatch.similarity}) + 0.15",
                catTextMatch.similarity > catTextMismatch.similarity + 0.15,
            )

            val textSimilar =
                recorder.measure("compare_text_ms") {
                    helper.compare(
                        UniversalEmbedderHelper.Input.Text(
                            "A golden retriever ran across the sunny sandy beach."
                        ),
                        UniversalEmbedderHelper.Input.Text(
                            "A happy dog sprinted along the ocean shoreline on warm sand."
                        ),
                    )!!
                }
            val textUnrelated =
                helper.compare(
                    UniversalEmbedderHelper.Input.Text(
                        "A golden retriever ran across the sunny sandy beach."
                    ),
                    UniversalEmbedderHelper.Input.Text(
                        "Quarterly semiconductor manufacturing yield report."
                    ),
                )!!
            assertTrue(
                "Expected textSimilar (${textSimilar.similarity}) > textUnrelated (${textUnrelated.similarity}) + 0.20",
                textSimilar.similarity > textUnrelated.similarity + 0.20,
            )

            val sameImage =
                helper.compare(
                    UniversalEmbedderHelper.Input.Image(beachBmp),
                    UniversalEmbedderHelper.Input.Image(beachBmp),
                )!!
            val distinctImage =
                helper.compare(
                    UniversalEmbedderHelper.Input.Image(beachBmp),
                    UniversalEmbedderHelper.Input.Image(mountainBmp),
                )!!
            assertEquals(1.0, sameImage.similarity, 1e-4)
            assertTrue(
                "Expected same image (${sameImage.similarity}) > beach vs mountain (${distinctImage.similarity}) + 0.15",
                sameImage.similarity > distinctImage.similarity + 0.15,
            )

            val emb = helper.embedText("MediaPipe universal embedder")!!
            assertTrue(EmbeddingMetrics.l2Norm(emb.floatEmbedding()) > 0.0)

            repeat(5) {
                recorder.measure("compare_steady_ms") {
                    helper.compare(
                        UniversalEmbedderHelper.Input.Image(beachBmp),
                        UniversalEmbedderHelper.Input.Text("warm sand beside ocean waves"),
                    )
                }
            }
            recorder.flush(context)
        } finally {
            helper.clearUniversalEmbedder()
        }
    }
}
