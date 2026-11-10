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

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.Locale
import kotlin.math.ceil

/**
 * Records model load and inference timings during instrumented tests and exports a JSON summary
 * for report-only performance tracking.
 */
class LatencyRecorder(private val suiteName: String) {

    data class MetricSummary(
        val name: String,
        val count: Int,
        val minMs: Double,
        val medianMs: Double,
        val p90Ms: Double,
        val meanMs: Double,
    )

    private val series = linkedMapOf<String, MutableList<Double>>()
    private val metadata = linkedMapOf<String, String>()

    fun putMetadata(key: String, value: String) {
        metadata[key] = value
    }

    fun record(metricName: String, durationMs: Double) {
        require(durationMs >= 0.0) { "Duration must be non-negative: $durationMs" }
        series.getOrPut(metricName) { mutableListOf() }.add(durationMs)
    }

    inline fun <T> measure(metricName: String, block: () -> T): T {
        val startNs = SystemClock.elapsedRealtimeNanos()
        val result = block()
        val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startNs) / 1_000_000.0
        record(metricName, elapsedMs)
        return result
    }

    fun summarize(): List<MetricSummary> =
        series.map { (name, samples) -> summarizeSeries(name, samples) }

    fun toJsonString(): String {
        val summaries = summarize()
        val sb = StringBuilder()
        sb.append('{')
        sb.append("\"suite\":\"").append(escapeJson(suiteName)).append("\",")
        val manufacturer = Build.MANUFACTURER ?: "unknown"
        val model = Build.MODEL ?: "unknown"
        val abi = Build.SUPPORTED_ABIS?.firstOrNull().orEmpty()
        sb.append("\"device\":\"").append(escapeJson("$manufacturer $model")).append("\",")
        sb.append("\"sdkInt\":").append(Build.VERSION.SDK_INT).append(',')
        sb.append("\"abi\":\"").append(escapeJson(abi)).append("\",")
        if (metadata.isNotEmpty()) {
            sb.append("\"metadata\":{")
            metadata.entries.forEachIndexed { idx, (k, v) ->
                if (idx > 0) sb.append(',')
                sb.append('"').append(escapeJson(k)).append("\":\"").append(escapeJson(v)).append('"')
            }
            sb.append("},")
        }
        sb.append("\"metrics\":{")
        summaries.forEachIndexed { idx, s ->
            if (idx > 0) sb.append(',')
            sb.append('"').append(escapeJson(s.name)).append("\":{")
            sb.append("\"count\":").append(s.count).append(',')
            sb.append(String.format(Locale.US, "\"minMs\":%.3f,", s.minMs))
            sb.append(String.format(Locale.US, "\"medianMs\":%.3f,", s.medianMs))
            sb.append(String.format(Locale.US, "\"p90Ms\":%.3f,", s.p90Ms))
            sb.append(String.format(Locale.US, "\"meanMs\":%.3f", s.meanMs))
            sb.append('}')
        }
        sb.append("}}")
        return sb.toString()
    }

    /** Logs the JSON summary and writes it under `latency_reports/` in app storage. */
    fun flush(context: Context): File {
        val json = toJsonString()
        Log.i(TAG, "LATENCY_REPORT $suiteName: $json")
        val dir =
            File(context.getExternalFilesDir(null) ?: context.filesDir, "latency_reports").apply {
                mkdirs()
            }
        val outFile = File(dir, "$suiteName.json")
        outFile.writeText(json)
        return outFile
    }

    companion object {
        private const val TAG = "LatencyRecorder"

        internal fun summarizeSeries(name: String, rawSamples: List<Double>): MetricSummary {
            require(rawSamples.isNotEmpty()) { "Metric '$name' has no samples." }
            val sorted = rawSamples.sorted()
            val n = sorted.size
            val median =
                if (n % 2 == 1) {
                    sorted[n / 2]
                } else {
                    (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
                }
            val p90Index = (ceil(0.90 * n).toInt() - 1).coerceIn(0, n - 1)
            return MetricSummary(
                name = name,
                count = n,
                minMs = sorted.first(),
                medianMs = median,
                p90Ms = sorted[p90Index],
                meanMs = sorted.sum() / n,
            )
        }

        private fun escapeJson(value: String): String =
            buildString(value.length) {
                for (ch in value) {
                    when (ch) {
                        '\\' -> append("\\\\")
                        '"' -> append("\\\"")
                        '\n' -> append("\\n")
                        '\r' -> append("\\r")
                        '\t' -> append("\\t")
                        else -> append(ch)
                    }
                }
            }
    }
}
