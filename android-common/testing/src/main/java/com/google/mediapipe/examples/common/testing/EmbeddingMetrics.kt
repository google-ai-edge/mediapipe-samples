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

import kotlin.math.abs
import kotlin.math.sqrt
import org.junit.Assert.assertTrue

/** Shared quality metrics and assertions for embedding-based MediaPipe samples. */
object EmbeddingMetrics {

    /** Computes cosine similarity in `[-1.0, 1.0]` between two non-empty vectors of equal length. */
    fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        require(a.isNotEmpty()) { "Embedding vector 'a' must not be empty." }
        require(a.size == b.size) {
            "Embedding lengths must match (${a.size} != ${b.size})."
        }
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            val va = a[i].toDouble()
            val vb = b[i].toDouble()
            dot += va * vb
            normA += va * va
            normB += vb * vb
        }
        val denom = sqrt(normA) * sqrt(normB)
        require(denom > 0.0) { "Embedding vectors must have non-zero norm." }
        return (dot / denom).coerceIn(-1.0, 1.0)
    }

    /** Computes the L2 norm of [vector]. */
    fun l2Norm(vector: FloatArray): Double {
        var sumSq = 0.0
        for (v in vector) {
            sumSq += v.toDouble() * v.toDouble()
        }
        return sqrt(sumSq)
    }

    /** Asserts [actual] is within `[expected - tolerance, expected + tolerance]`. */
    fun assertSimilarityClose(
        expected: Double,
        actual: Double,
        tolerance: Double = 0.02,
        label: String = "cosineSimilarity",
    ) {
        val diff = abs(expected - actual)
        assertTrue(
            "$label expected $expected ± $tolerance, but was $actual (diff=$diff)",
            diff <= tolerance,
        )
    }

    /** Asserts [actual] lies inside `[minInclusive, maxInclusive]`. */
    fun assertInRange(
        actual: Double,
        minInclusive: Double,
        maxInclusive: Double,
        label: String = "value",
    ) {
        assertTrue(
            "$label expected in [$minInclusive, $maxInclusive], but was $actual",
            actual in minInclusive..maxInclusive,
        )
    }
}
