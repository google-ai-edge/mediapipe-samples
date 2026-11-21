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
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber
import com.google.mediapipe.tasks.audio.texttospeech.examples.TextToSpeechHelper
import com.google.mediapipe.tasks.audio.texttospeech.examples.TextToSpeechViewModel
import com.google.mediapipe.tasks.audio.texttospeech.examples.TtsDelegateMode
import com.google.mediapipe.tasks.audio.texttospeech.examples.TtsModelOption
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File

/** Drives the text-to-speech sample across its four bundled models in unary and streaming modes. */
class TextToSpeechAdapter : TaskAdapter {
    override fun run(case: ValidationCase, sample: SampleSession): List<Run> {
        val modelOption =
            when (case.modelId) {
                "kokoro_82m_cpu" -> TtsModelOption.KOKORO_CPU
                "kokoro_82m_gpu" -> TtsModelOption.KOKORO_GPU
                "pocket_tts_en" -> TtsModelOption.POCKET_TTS
                "phonon_31m_en" -> TtsModelOption.PHONON
                else -> throw RunnerError("The sample has no model ${case.modelId}.")
            }
        if (modelOption == TtsModelOption.KOKORO_CPU) {
            listOfNotNull(sample.activity.getExternalFilesDir(null), File("/data/local/tmp"))
                .flatMap { dir -> TextToSpeechHelper.CANDIDATE_MODEL_NAMES.map { File(dir, it) } }
                .firstOrNull { it.isFile && it.length() > 0L }
                ?.let {
                    throw RunnerError(
                        "The sample loads the sideloaded model $it instead of its bundled asset ${modelOption.assetName}."
                    )
                }
        }
        sample.usesModelAsset(modelOption.assetName)
        val stream = case.options.boolean("stream")
        val delegateMode =
            when (case.delegate) {
                "CPU" -> TtsDelegateMode.CPU
                "GPU" -> TtsDelegateMode.GPU
                else -> throw RunnerError("The sample has no delegate ${case.delegate}.")
            }

        val viewModel = sample.viewModel(TextToSpeechViewModel::class.java)
        var loaded =
            sample.await(viewModel.uiState, "the initial TTS model to finish loading", LOAD_TIMEOUT_MS) {
                !it.isLoadingEngine && (it.isEngineReady || it.errorMessage != null)
            }
        if (loaded.selectedModel != modelOption || loaded.delegateMode != delegateMode) {
            sample.beforeDelegateSwitch()
            sample.onMain {
                viewModel.selectModel(modelOption)
                viewModel.setDelegateMode(delegateMode)
            }
            loaded =
                sample.await(
                    viewModel.uiState,
                    "${modelOption.assetName} to load on ${case.delegate}",
                    LOAD_TIMEOUT_MS,
                ) {
                    !it.isLoadingEngine && (it.isEngineReady || it.errorMessage != null)
                }
        }
        loaded.errorMessage?.let { throw SampleError(it) }
        requireApplied("model", modelOption, loaded.selectedModel)
        requireApplied("delegate", delegateMode, loaded.delegateMode)
        requireApplied("activeDelegate", case.delegate, loaded.activeDelegateLabel)

        val listenWithAsset = case.json.optString("listenWith").takeIf { it.isNotBlank() }
        val transcriberModelFile = listenWithAsset?.let { sample.assetFile(it) }

        return case.steps.map { step ->
            val text = step.string("text")
            if (text != text.trim()) {
                throw RunnerError("The sample trims input text, which would alter \"$text\".")
            }
            sample.onMain {
                viewModel.updateText(text)
                if (stream) viewModel.speakStreaming() else viewModel.speakUnary()
            }
            val state =
                sample.await(viewModel.uiState, "speech synthesis of ${step.key}", SYNTH_TIMEOUT_MS) {
                    !it.isBusy && (it.synthesizedAudio != null || it.errorMessage != null)
                }
            state.errorMessage?.let { throw SampleError(it) }
            val rawSamples =
                state.synthesizedSamples
                    ?: throw RunnerError("The sample finished ${step.key} without synthesized samples.")
            val rate =
                state.sampleRateHz
                    ?: throw RunnerError("The sample finished ${step.key} without a sample rate.")
            val durationSec = if (rate > 0) rawSamples.size.toDouble() / rate.toDouble() else 0.0

            var heard: String? = null
            var heardError: String? = null
            if (transcriberModelFile != null && rate > 0) {
                try {
                    val cleanSamples =
                        FloatArray(rawSamples.size) { idx ->
                            val v = rawSamples[idx]
                            if (v.isFinite()) v else 0f
                        }
                    val options =
                        AudioTranscriber.AudioTranscriberOptions.builder()
                            .setBaseOptions(
                                BaseOptions.builder()
                                    .setModelAssetPath(transcriberModelFile.absolutePath)
                                    .setDelegate(Delegate.CPU)
                                    .build()
                            )
                            .setSampleRateHz(rate)
                            .setModelArchitecture(AudioTranscriber.ModelArchitecture.AUTO)
                            .setCacheDir(sample.activity.cacheDir.absolutePath)
                            .build()
                    AudioTranscriber.createFromOptions(sample.activity, options).use { transcriber ->
                        transcriber.prewarm()
                        val transcriptResult = transcriber.transcribe(cleanSamples, true)
                        heard = transcriptResult.segments().joinToString(" ") { it.transcript() }
                    }
                } catch (error: Throwable) {
                    heardError = "${error.javaClass.simpleName}: ${error.message}"
                }
            }

            Run(
                step.key,
                json(
                    "pieces" to
                        state.pieces.map { piece ->
                            json(
                                "chunkIndex" to piece.chunkIndex,
                                "isFinal" to piece.isFinal,
                                "samples" to piece.sampleCount,
                                "sampleRateHz" to piece.sampleRateHz,
                                "dtype" to "float32",
                                "ndim" to 1,
                            )
                        },
                    "sampleRateHz" to rate,
                    "durationSec" to durationSec,
                    "rms" to (state.rms ?: 0.0),
                    "peak" to (state.peak ?: 0.0),
                    "nonFinite" to state.nonFiniteCount,
                    "wordTimestamps" to
                        state.wordTimestamps.map { stamp ->
                            json(
                                "word" to stamp.word,
                                "startSec" to stamp.startSec.toDouble(),
                                "endSec" to stamp.endSec.toDouble(),
                            )
                        },
                    "heard" to heard,
                    "heardError" to heardError,
                ),
            )
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 300_000L
        const val SYNTH_TIMEOUT_MS = 180_000L
    }
}
