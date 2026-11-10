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

package com.google.mediapipe.examples.common.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddingMetricsTest {

    @Test
    fun cosineSimilarity_identicalAndOrthogonal() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(2f, 4f, 6f)
        val ortho = floatArrayOf(-2f, 1f, 0f)

        assertEquals(1.0, EmbeddingMetrics.cosineSimilarity(a, b), 1e-6)
        assertEquals(0.0, EmbeddingMetrics.cosineSimilarity(a, ortho), 1e-6)
    }

    @Test
    fun latencyRecorder_summarizesPercentilesAndJson() {
        val recorder = LatencyRecorder("unit_suite")
        listOf(10.0, 20.0, 30.0, 40.0, 100.0).forEach { recorder.record("inference_ms", it) }
        val summary = recorder.summarize().single()

        assertEquals(5, summary.count)
        assertEquals(10.0, summary.minMs, 1e-6)
        assertEquals(30.0, summary.medianMs, 1e-6)
        assertEquals(100.0, summary.p90Ms, 1e-6)
        assertEquals(40.0, summary.meanMs, 1e-6)

        val json = recorder.toJsonString()
        assertTrue(json.contains("\"suite\":\"unit_suite\""))
        assertTrue(json.contains("\"medianMs\":30.000"))
    }
}
