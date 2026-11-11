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

package com.google.mediapipe.examples.textsummarizer

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizer.TextSummarizerOptions.Mode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextSummarizerHelperTest {

    companion object {
        private const val TAG = "TextSummarizerHelperTest"
    }

    @Test
    fun summarize_keypointsAndTldrModes_produceConciseRelevantSummaries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("text_summarizer")
        recorder.putMetadata("model", TextSummarizerHelper.MODEL_ASSET)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                TextSummarizerHelper(context = context, mode = Mode.KEYPOINTS)
            }
        try {
            assertTrue("TextSummarizer failed to initialize", helper.isReady)

            val passage = DefaultSummarizerPresets.first().text
            val keypointsOutputs = mutableListOf<String>()
            repeat(5) { idx ->
                val bundle =
                    recorder.measure(
                        if (idx == 0) "summarize_keypoints_first_ms" else "summarize_keypoints_steady_ms"
                    ) {
                        helper.summarize(passage)
                    }
                keypointsOutputs.add(bundle.summary)
                Log.i(TAG, "keypoints[$idx] len=${bundle.summary.length}/${passage.length} summary='${bundle.summary}'")
            }

            val kp = keypointsOutputs.first()
            assertTrue("Keypoints summary should not be empty", kp.isNotBlank())
            val bulletCount = kp.lines().count { it.trimStart().startsWith("*") }
            assertTrue(
                "Expected Keypoints summary to contain bullet points, got '$kp'",
                bulletCount >= 2,
            )
            assertTrue(
                "Expected Keypoints summary to retain domain terms, got '$kp'",
                kp.contains("MediaPipe", ignoreCase = true) ||
                    kp.contains("on-device", ignoreCase = true) ||
                    kp.contains("offline", ignoreCase = true),
            )
            recorder.putMetadata("keypoints_unique_count", keypointsOutputs.toSet().size.toString())

            // Verify TL;DR mode + streaming.
            helper.mode = Mode.TLDR
            assertTrue("Failed to re-init TextSummarizer in TLDR mode", helper.initSummarizer())
            val tldr =
                recorder.measure("summarize_tldr_ms") {
                    helper.summarize(passage)
                }
            Log.i(TAG, "tldr len=${tldr.summary.length}/${passage.length} summary='${tldr.summary}'")
            assertTrue("TL;DR summary should not be empty", tldr.summary.isNotBlank())
            assertTrue(
                "Expected TL;DR summary (${tldr.summary.length}) to be shorter than input (${passage.length})",
                tldr.summary.length < passage.length,
            )

            val latch = CountDownLatch(1)
            val streamedRef = AtomicReference<TextSummarizerHelper.ResultBundle?>()
            val errorRef = AtomicReference<Throwable?>()
            helper.summarizeStreaming(
                text = passage,
                onChunk = {},
                onComplete = { bundle ->
                    streamedRef.set(bundle)
                    latch.countDown()
                },
                onError = { err ->
                    errorRef.set(err)
                    latch.countDown()
                },
            )
            assertTrue("Timed out waiting for streaming summary", latch.await(60, TimeUnit.SECONDS))
            errorRef.get()?.let { throw AssertionError("Streaming summary failed", it) }
            val streamed = streamedRef.get()
            assertNotNull(streamed)
            assertTrue("Streamed summary should not be empty", streamed!!.summary.isNotBlank())

            recorder.flush(context)
        } finally {
            helper.clearSummarizer()
        }
    }
}
