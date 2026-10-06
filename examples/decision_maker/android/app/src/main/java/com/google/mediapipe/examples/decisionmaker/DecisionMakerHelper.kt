/*
 * Copyright 2026 The MediaPipe Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.decisionmaker

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.decision.BooleanQuestion
import com.google.mediapipe.tasks.decision.BooleanResult
import com.google.mediapipe.tasks.decision.ChoiceQuestion
import com.google.mediapipe.tasks.decision.ChoiceResult
import com.google.mediapipe.tasks.decision.DecisionMaker
import com.google.mediapipe.tasks.decision.DecisionMakerDelegate
import com.google.mediapipe.tasks.decision.DecisionMakerOptions
import com.google.mediapipe.tasks.decision.ScoreQuestion
import com.google.mediapipe.tasks.decision.ScoreResult
import java.io.File

/**
 * Wraps [DecisionMaker] setup (model + delegate) and the three question types:
 * Boolean, Choice and Score. All methods are blocking; call them off the main thread.
 */
class DecisionMakerHelper(
    private val context: Context,
    var currentDelegate: Int = DELEGATE_GPU,
    var currentModel: Int = MODEL_EMBEDDING_GEMMA,
) {
    private var decisionMaker: DecisionMaker? = null

    /** Creates the [DecisionMaker]. Throws if the model is missing or the delegate fails. */
    fun setupDecisionMaker() {
        clearDecisionMaker()
        val options = DecisionMakerOptions.builder()
            .setModelPath(resolveModelPath(MODEL_FILES[currentModel]))
            .setMaxNumTokens(MAX_NUM_TOKENS)
            .setDelegate(
                if (currentDelegate == DELEGATE_GPU) DecisionMakerDelegate.GPU
                else DecisionMakerDelegate.CPU
            )
            .build()
        decisionMaker = DecisionMaker.createFromOptions(context, options)
    }

    fun clearDecisionMaker() {
        decisionMaker?.close()
        decisionMaker = null
    }

    fun evaluateBoolean(
        text: String,
        condition: String,
        threshold: Float,
    ): ResultBundle<BooleanResult> =
        timed { it.evaluate(text, BooleanQuestion(condition = condition, threshold = threshold)) }

    fun evaluateChoice(
        text: String,
        options: Map<String, String>,
        instructions: String
    ): ResultBundle<ChoiceResult> = timed {
        it.evaluate(text, ChoiceQuestion.withDescriptions(options, instructions = instructions))
    }

    fun evaluateScore(
        text: String,
        rubric: List<String>,
        instructions: String
    ): ResultBundle<ScoreResult> = timed {
        it.evaluate(text, ScoreQuestion(rubric = rubric, instructions = instructions))
    }

    private fun <T> timed(block: (DecisionMaker) -> T): ResultBundle<T> {
        val dm = decisionMaker ?: throw IllegalStateException("DecisionMaker is not initialized.")
        val start = SystemClock.uptimeMillis()
        val result = block(dm)
        return ResultBundle(result, SystemClock.uptimeMillis() - start)
    }

    /**
     * Returns a readable path for [fileName]: a copy pushed to the device (see README), or the
     * APK asset downloaded by download_models.gradle (copied to internal storage, and copied
     * again after each app update so a newer bundled model is never shadowed by an old copy).
     */
    private fun resolveModelPath(fileName: String): String {
        val pushed = listOfNotNull(
            File("/data/local/tmp/decision_models", fileName),
            context.getExternalFilesDir(null)?.let { File(it, fileName) },
        ).firstOrNull { it.canRead() && it.length() > 0L }
        if (pushed != null) return pushed.absolutePath

        if (context.assets.list("")?.contains(fileName) != true) {
            throw ModelNotFoundException("$fileName not found. See the README to download the model.")
        }
        val copy = File(context.filesDir, fileName)
        val appUpdatedAt = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        if (!copy.exists() || copy.lastModified() < appUpdatedAt) {
            Log.i(TAG, "Copying $fileName from assets to ${copy.absolutePath}")
            val tmp = File(context.filesDir, "$fileName.tmp")
            context.assets.open(fileName).use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            }
            if (!tmp.renameTo(copy)) throw IllegalStateException("Could not copy $fileName.")
        }
        return copy.absolutePath
    }

    /** The model file is neither pushed to the device nor bundled in the APK. */
    class ModelNotFoundException(message: String) : IllegalStateException(message)

    data class ResultBundle<T>(val result: T, val inferenceTime: Long)

    companion object {
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1

        const val MODEL_EMBEDDING_GEMMA = 0
        const val MODEL_LAYA = 1
        const val MODEL_GLINER = 2

        /** Model files, indexed by the MODEL_* constants (see download_models.gradle). */
        private val MODEL_FILES = listOf(
            "embeddinggemma-2-text-270m.litertlm",
            "laya_s256.task",
            "gliner_s256.task",
        )

        private const val MAX_NUM_TOKENS = 4096
        private const val TAG = "DecisionMakerHelper"
    }
}
