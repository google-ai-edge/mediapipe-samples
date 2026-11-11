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

package com.google.mediapipe.examples.textproofreader

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextProofreaderHelperTest {

    companion object {
        private const val TAG = "TextProofreaderHelperTest"
    }

    @Test
    fun proofread_correctsKnownErrorsAndMeasuresStability() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("text_proofreader")
        recorder.putMetadata("model", TextProofreaderHelper.MODEL_ASSET)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                TextProofreaderHelper(context = context)
            }
        try {
            assertTrue("TextProofreader failed to initialize", helper.isReady)

            val noisyInput =
                "She go to school evry day and always forgeting her homework on desk."
            val steadyOutputs = mutableListOf<String>()
            repeat(5) { idx ->
                val result =
                    recorder.measure(
                        if (idx == 0) "proofread_first_ms" else "proofread_steady_ms"
                    ) {
                        helper.proofread(noisyInput)
                    }
                steadyOutputs.add(result.proofreadText.trim())
                Log.i(
                    TAG,
                    "run[$idx] output='${result.proofreadText}' edits=${result.editCount} ms=${result.inferenceTimeMs}",
                )
            }

            val primary = steadyOutputs.first()
            assertTrue("Proofread output should not be empty", primary.isNotEmpty())
            assertFalse(
                "Expected spelling typo 'evry' to be corrected in '$primary'",
                primary.contains("evry", ignoreCase = true),
            )
            assertTrue(
                "Expected 'every day' in '$primary'",
                primary.contains("every day", ignoreCase = true),
            )
            val uniqueOutputs = steadyOutputs.toSet()
            recorder.putMetadata("five_run_unique_count", uniqueOutputs.size.toString())
            Log.i(TAG, "5-run unique outputs (${uniqueOutputs.size}): $uniqueOutputs")

            val cleanInput =
                "MediaPipe helps developers build fast, on-device AI experiences on Android."
            val cleanResult = helper.proofread(cleanInput)
            Log.i(TAG, "clean output='${cleanResult.proofreadText}' edits=${cleanResult.editCount}")
            assertEquals(
                "Clean input should remain unchanged",
                cleanInput,
                cleanResult.proofreadText.trim(),
            )
            assertEquals("Expected zero edits on clean input", 0, cleanResult.editCount)

            // Verify streaming callback delivers non-empty text and completes cleanly.
            val streamInput =
                "Please send me the report by tommorow morning so we can reviewed it."
            val latch = CountDownLatch(1)
            val streamedResult = AtomicReference<TextProofreaderHelper.ResultBundle?>()
            val streamError = AtomicReference<Throwable?>()
            helper.proofreadStreaming(
                text = streamInput,
                onChunk = {},
                onComplete = { bundle ->
                    streamedResult.set(bundle)
                    latch.countDown()
                },
                onError = { err ->
                    streamError.set(err)
                    latch.countDown()
                },
            )
            assertTrue("Timed out waiting for streaming proofread", latch.await(60, TimeUnit.SECONDS))
            val err = streamError.get()
            if (err != null) {
                throw AssertionError("Streaming proofread failed", err)
            }
            val bundle = streamedResult.get()
            assertNotNull(bundle)
            Log.i(TAG, "streamed='${bundle!!.proofreadText}' edits=${bundle.editCount}")
            assertFalse(
                "Expected typo 'tommorow' to be corrected in '${bundle.proofreadText}'",
                bundle.proofreadText.contains("tommorow", ignoreCase = true),
            )
            assertTrue(
                "Expected 'tomorrow' in '${bundle.proofreadText}'",
                bundle.proofreadText.contains("tomorrow", ignoreCase = true),
            )

            recorder.flush(context)
        } finally {
            helper.clearProofreader()
        }
    }
}
