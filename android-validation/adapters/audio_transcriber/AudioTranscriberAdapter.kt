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

package com.google.mediapipe.examples.validation.adapters

import com.google.mediapipe.examples.validation.runner.Run
import com.google.mediapipe.examples.validation.runner.RunnerError
import com.google.mediapipe.examples.validation.runner.SampleError
import com.google.mediapipe.examples.validation.runner.SampleSession
import com.google.mediapipe.examples.validation.runner.TaskAdapter
import com.google.mediapipe.examples.validation.runner.ValidationCase
import com.google.mediapipe.examples.validation.runner.json
import com.google.mediapipe.examples.validation.runner.requireApplied
import com.google.mediapipe.tasks.examples.audiotranscriber.AudioTranscriberHelper
import com.google.mediapipe.tasks.examples.audiotranscriber.AudioTranscriberViewModel
import com.google.mediapipe.tasks.examples.audiotranscriber.ClipTranscriptionMode
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** Drives the audio transcription sample: selects streaming or full-clip mode and feeds the WAV URI. */
class AudioTranscriberAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        if (case.modelId != "parakeet_tdt_v3") throw RunnerError("The sample has no model ${case.modelId}.")
        val model = AudioTranscriberHelper.DEFAULT_MODEL_FILE_NAME
        sample.usesModelAsset(model)
        val stream = case.options.boolean("stream")
        val clipMode = if (stream) ClipTranscriptionMode.STREAMING else ClipTranscriptionMode.FULL_CLIP
        val delegate =
            when (case.delegate) {
                "CPU" -> AudioTranscriberHelper.DELEGATE_CPU
                "GPU" -> AudioTranscriberHelper.DELEGATE_GPU
                else -> throw RunnerError("The sample has no delegate ${case.delegate}.")
            }
        val context = sample.activity
        val sideloaded =
            listOfNotNull(File("/data/local/tmp/audiotranscriber"), context.getExternalFilesDir(null))
                .flatMap { it.listFiles().orEmpty().asList() }
                .filter { it.isFile && it.extension.equals(AudioTranscriberHelper.MODEL_EXTENSION, ignoreCase = true) }
        if (sideloaded.isNotEmpty()) {
            throw RunnerError("The sample loads the sideloaded model ${sideloaded.first()} instead of the one it bundles.")
        }

        val viewModel = sample.viewModel(AudioTranscriberViewModel::class.java)
        var loaded =
            sample.await(viewModel.uiState, "the model to load", LOAD_TIMEOUT_MS) {
                it.isModelReady || it.errorMessage != null
            }
        if (loaded.currentDelegate != delegate) {
            sample.beforeDelegateSwitch()
            sample.onMain { viewModel.setDelegate(delegate) }
            loaded =
                sample.await(viewModel.uiState, "the model to load on the ${case.delegate}", LOAD_TIMEOUT_MS) {
                    it.isModelReady || it.errorMessage != null
                }
        }
        val staged = File(context.cacheDir, model)
        if (staged.isFile && sha256(staged.inputStream()) != sha256(context.assets.open(model))) {
            throw RunnerError("The sample loads $staged, which is not the model it bundles. Clear the cache of the app.")
        }
        loaded.errorMessage?.let { throw SampleError(it) }
        val settings =
            sample.onMain {
                viewModel.setClipTranscriptionMode(clipMode)
                viewModel.uiState.value
            }
        requireApplied("delegate", delegate, settings.currentDelegate)
        requireApplied("model", model, settings.activeModelName)
        requireApplied("stream", clipMode, settings.clipTranscriptionMode)

        return case.steps.map { step ->
            val audio = step.string("audio")
            val uri = sample.inputUri(audio)
            sample.onMain { viewModel.transcribeAudioUri(uri) }
            val state =
                sample.await(viewModel.uiState, "the transcript of ${step.key}") {
                    !it.isTranscribingSample &&
                        (it.statusMessage == AudioTranscriberViewModel.STATUS_SAMPLE_COMPLETE || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            val clipMs =
                state.lastClipDurationMs
                    ?: throw RunnerError("The sample finished ${step.key} without recording clipMs.")
            Run(
                step.key,
                json(
                    "clipMs" to clipMs,
                    "segments" to
                        state.finalizedSegments.map {
                            json(
                                "transcript" to it.transcript(),
                                "isFinal" to it.isFinal,
                                "fromMs" to it.fromTimestampMs(),
                                "toMs" to it.toTimestampMs(),
                            )
                        },
                ),
            )
        }
    }

    private fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        input.use {
            while (true) {
                val count = it.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 240_000L
    }
}
