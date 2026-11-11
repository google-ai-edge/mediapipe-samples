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
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizer
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizer.TextSummarizerOptions
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizerStreamingResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Thin wrapper managing the [TextSummarizer] lifecycle and unary/streaming summarization. */
class TextSummarizerHelper(
    private val context: Context,
    var mode: TextSummarizerOptions.Mode = TextSummarizerOptions.Mode.KEYPOINTS,
    var maxNumTokens: Int = DEFAULT_MAX_NUM_TOKENS,
    initOnCreate: Boolean = true,
) {

    @Volatile private var summarizer: TextSummarizer? = null

    val isReady: Boolean
        get() = summarizer != null

    init {
        if (initOnCreate) {
            initSummarizer()
        }
    }

    fun initSummarizer(): Boolean {
        clearSummarizer()
        return try {
            val modelFile = ensureModelFileInCache()
            val optionsBuilder =
                TextSummarizerOptions.builder().setMode(mode).setMaxNumTokens(maxNumTokens)

            summarizer =
                try {
                    ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use {
                        pfd ->
                        optionsBuilder.setModelAssetFileDescriptor(pfd)
                        TextSummarizer.createFromOptions(context, optionsBuilder.build())
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Falling back to model file path for TextSummarizer", e)
                    TextSummarizer.createFromOptions(
                        context,
                        TextSummarizerOptions.builder()
                            .setMode(mode)
                            .setMaxNumTokens(maxNumTokens)
                            .setModelPath(modelFile.absolutePath)
                            .build(),
                    )
                }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextSummarizer", e)
            summarizer = null
            false
        }
    }

    fun clearSummarizer() {
        runCatching { summarizer?.close() }
        summarizer = null
    }

    fun summarize(text: String): ResultBundle {
        val engine = summarizer ?: throw IllegalStateException("TextSummarizer is not initialized.")
        val startMs = SystemClock.uptimeMillis()
        val summary = engine.summarize(text).getSummary().orEmpty().trim()
        val elapsedMs = SystemClock.uptimeMillis() - startMs
        return ResultBundle(summary = summary, inferenceTimeMs = elapsedMs)
    }

    fun summarizeStreaming(
        text: String,
        onChunk: (String) -> Unit,
        onComplete: (ResultBundle) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val engine =
            summarizer
                ?: run {
                    onError(IllegalStateException("TextSummarizer is not initialized."))
                    return
                }
        val startMs = SystemClock.uptimeMillis()
        val accumulated = StringBuilder()

        try {
            engine.summarizeStreaming(
                text,
                object : TextSummarizer.SummarizationResultCallback {
                    override fun onNext(result: TextSummarizerStreamingResult) {
                        val chunk = result.getChunk().orEmpty()
                        if (chunk.isNotEmpty()) {
                            accumulated.append(chunk)
                            onChunk(chunk)
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        onError(throwable)
                    }

                    override fun onDone() {
                        val elapsedMs = SystemClock.uptimeMillis() - startMs
                        onComplete(
                            ResultBundle(
                                summary = accumulated.toString().trim(),
                                inferenceTimeMs = elapsedMs,
                            )
                        )
                    }
                },
            )
        } catch (t: Throwable) {
            onError(t)
        }
    }

    private fun ensureModelFileInCache(): File {
        val targetFile = File(context.cacheDir, MODEL_ASSET)
        val appUpdatedAt =
            runCatching {
                    context.packageManager
                        .getPackageInfo(context.packageName, 0)
                        .lastUpdateTime
                }
                .getOrDefault(0L)
        if (
            targetFile.exists() &&
                targetFile.length() > 0L &&
                targetFile.lastModified() >= appUpdatedAt
        ) {
            return targetFile
        }
        val tmpFile = File(context.cacheDir, "$MODEL_ASSET.tmp")
        context.assets.open(MODEL_ASSET).use { input ->
            FileOutputStream(tmpFile).use { output -> input.copyTo(output) }
        }
        if (!tmpFile.renameTo(targetFile)) {
            tmpFile.delete()
            throw IOException("Failed to move staged model file into cache.")
        }
        return targetFile
    }

    data class ResultBundle(
        val summary: String,
        val inferenceTimeMs: Long,
    )

    companion object {
        private const val TAG = "TextSummarizerHelper"
        const val MODEL_ASSET = "summarization_quant_200m_2modes.litertlm"
        const val DEFAULT_MAX_NUM_TOKENS = 2048
    }
}
