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
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.text.textproofreader.TextProofreader
import com.google.mediapipe.tasks.text.textproofreader.TextProofreader.TextProofreaderOptions
import com.google.mediapipe.tasks.text.textproofreader.TextProofreaderResult.Correction
import com.google.mediapipe.tasks.text.textproofreader.TextProofreaderResult.CorrectionType
import com.google.mediapipe.tasks.text.textproofreader.TextProofreaderStreamingResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/** Thin wrapper managing the [TextProofreader] lifecycle and unary/streaming proofreading. */
class TextProofreaderHelper(
    private val context: Context,
    var maxNumTokens: Int = DEFAULT_MAX_NUM_TOKENS,
    initOnCreate: Boolean = true,
) {

    @Volatile private var proofreader: TextProofreader? = null

    val isReady: Boolean
        get() = proofreader != null

    init {
        if (initOnCreate) {
            initProofreader()
        }
    }

    fun initProofreader(): Boolean {
        clearProofreader()
        return try {
            val modelFile = ensureModelFileInCache()
            val optionsBuilder = TextProofreaderOptions.builder().setMaxNumTokens(maxNumTokens)

            proofreader =
                try {
                    ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use {
                        pfd ->
                        optionsBuilder.setModelAssetFileDescriptor(pfd)
                        TextProofreader.createFromOptions(context, optionsBuilder.build())
                    }
                } catch (e: IOException) {
                    Log.w(TAG, "Falling back to model file path for TextProofreader", e)
                    TextProofreader.createFromOptions(
                        context,
                        TextProofreaderOptions.builder()
                            .setMaxNumTokens(maxNumTokens)
                            .setModelPath(modelFile.absolutePath)
                            .build(),
                    )
                }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TextProofreader", e)
            proofreader = null
            false
        }
    }

    fun clearProofreader() {
        runCatching { proofreader?.close() }
        proofreader = null
    }

    fun proofread(text: String): ResultBundle {
        val engine =
            proofreader ?: throw IllegalStateException("TextProofreader is not initialized.")
        val startMs = SystemClock.uptimeMillis()
        val result = engine.proofread(text)
        val elapsedMs = SystemClock.uptimeMillis() - startMs
        val corrections =
            result.getCorrections().orEmpty().map {
                CorrectionUi(
                    text = it.text.orEmpty(),
                    type = it.type ?: CorrectionType.SAME,
                )
            }
        return ResultBundle(
            proofreadText = result.getProofreadText().orEmpty(),
            corrections = corrections,
            inferenceTimeMs = elapsedMs,
        )
    }

    fun proofreadStreaming(
        text: String,
        onChunk: (String) -> Unit,
        onComplete: (ResultBundle) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val engine =
            proofreader
                ?: run {
                    onError(IllegalStateException("TextProofreader is not initialized."))
                    return
                }
        val startMs = SystemClock.uptimeMillis()
        val accumulated = StringBuilder()
        var finalCorrections: List<CorrectionUi> = emptyList()

        try {
            engine.proofreadStreaming(
                text,
                object : TextProofreader.ProofreaderResultCallback {
                    override fun onNext(result: TextProofreaderStreamingResult) {
                        val chunk = result.getChunk().orEmpty()
                        if (chunk.isNotEmpty()) {
                            accumulated.append(chunk)
                            onChunk(chunk)
                        }
                        if (result.isDone()) {
                            finalCorrections =
                                result.getCorrections().orEmpty().map { corr: Correction ->
                                    CorrectionUi(
                                        text = corr.text.orEmpty(),
                                        type = corr.type ?: CorrectionType.SAME,
                                    )
                                }
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        onError(throwable)
                    }

                    override fun onDone() {
                        val elapsedMs = SystemClock.uptimeMillis() - startMs
                        onComplete(
                            ResultBundle(
                                proofreadText = accumulated.toString(),
                                corrections = finalCorrections,
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

    data class CorrectionUi(
        val text: String,
        val type: CorrectionType,
    )

    data class ResultBundle(
        val proofreadText: String,
        val corrections: List<CorrectionUi>,
        val inferenceTimeMs: Long,
    ) {
        val editCount: Int
            get() =
                corrections.count {
                    it.type == CorrectionType.INSERTION || it.type == CorrectionType.DELETION
                }
    }

    companion object {
        private const val TAG = "TextProofreaderHelper"
        const val MODEL_ASSET = "proofread_quant_200m.litertlm"
        const val DEFAULT_MAX_NUM_TOKENS = 2048
    }
}
