/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.audioclassifier

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.components.containers.AudioData.AudioDataFormat
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.util.Collections
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt

enum class ClassifierDelegate(val label: String) {
    CPU("CPU"),
    GPU("GPU"),
}

class AudioClassifierHelper(
    val context: Context,
    var classificationThreshold: Float = DISPLAY_THRESHOLD,
    var overlap: Int = DEFAULT_OVERLAP,
    var numOfResults: Int = DEFAULT_NUM_OF_RESULTS,
    var currentDelegate: ClassifierDelegate = ClassifierDelegate.CPU,
    var runningMode: RunningMode = RunningMode.AUDIO_CLIPS,
    var listener: ClassifierListener? = null,
    initOnCreate: Boolean = true,
) {

    private val lock = Any()
    private var recorder: AudioRecord? = null
    private var executor: ScheduledThreadPoolExecutor? = null
    private var audioClassifier: AudioClassifier? = null
    @Volatile private var lastStreamStartMs: Long = 0L
    @Volatile var lastError: String? = null
        private set

    private val classifyRunnable = Runnable {
        val currentRecorder: AudioRecord?
        synchronized(lock) { currentRecorder = recorder }
        if (currentRecorder != null) {
            classifyAudioAsync(currentRecorder)
        }
    }

    init {
        if (initOnCreate) {
            initClassifier()
        }
    }

    @SuppressLint("MissingPermission")
    fun initClassifier(startMicrophone: Boolean = true): Boolean {
        synchronized(lock) {
            stopAudioClassificationLocked()
            lastError = null
            val mpDelegate =
                when (currentDelegate) {
                    ClassifierDelegate.CPU -> Delegate.CPU
                    ClassifierDelegate.GPU -> Delegate.GPU
                }
            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(YAMNET_MODEL)
                    .setDelegate(mpDelegate)
                    .build()

            return try {
                val optionsBuilder =
                    AudioClassifier.AudioClassifierOptions.builder()
                        .setScoreThreshold(classificationThreshold)
                        .setMaxResults(numOfResults)
                        .setBaseOptions(baseOptions)
                        .setRunningMode(runningMode)

                if (runningMode == RunningMode.AUDIO_STREAM) {
                    optionsBuilder
                        .setResultListener(::streamAudioResultListener)
                        .setErrorListener(::streamAudioErrorListener)
                }

                val classifier = AudioClassifier.createFromOptions(context, optionsBuilder.build())
                audioClassifier = classifier

                if (runningMode == RunningMode.AUDIO_STREAM && startMicrophone) {
                    val audioRecord =
                        classifier.createAudioRecord(
                            AudioFormat.CHANNEL_IN_DEFAULT,
                            SAMPLING_RATE_IN_HZ,
                            BUFFER_SIZE_IN_BYTES.toInt(),
                        )
                    recorder = audioRecord
                    startAudioClassificationLocked(audioRecord)
                }
                true
            } catch (e: IllegalStateException) {
                notifyError("Audio Classifier failed to initialize: ${e.message}")
                Log.e(TAG, "MP task failed to load with error: ${e.message}", e)
                false
            } catch (e: RuntimeException) {
                notifyError("Audio Classifier failed to initialize: ${e.message}")
                Log.e(TAG, "MP task failed to load with error: ${e.message}", e)
                false
            }
        }
    }

    private fun startAudioClassificationLocked(audioRecord: AudioRecord) {
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            throw IllegalStateException("AudioRecord failed to initialize.")
        }
        if (audioRecord.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        audioRecord.startRecording()
        val pool = ScheduledThreadPoolExecutor(1)
        executor = pool

        val lengthInMillis =
            ((REQUIRE_INPUT_BUFFER_SIZE * 1.0f) / SAMPLING_RATE_IN_HZ) * 1000f
        val intervalMs = (lengthInMillis * (1f - (overlap * 0.25f))).toLong().coerceAtLeast(100L)

        pool.scheduleAtFixedRate(
            classifyRunnable,
            0L,
            intervalMs,
            TimeUnit.MILLISECONDS,
        )
    }

    private fun classifyAudioAsync(audioRecord: AudioRecord) {
        synchronized(lock) {
            val classifier = audioClassifier ?: return
            val audioData =
                AudioData.create(
                    AudioDataFormat.create(audioRecord.format),
                    SAMPLING_RATE_IN_HZ,
                )
            audioData.load(audioRecord)
            lastStreamStartMs = SystemClock.uptimeMillis()
            classifier.classifyAsync(audioData, lastStreamStartMs)
        }
    }

    fun classifyAudio(audioData: AudioData): ResultBundle? {
        synchronized(lock) {
            val classifier = audioClassifier
            if (classifier == null) {
                if (lastError == null) {
                    notifyError("Audio classifier is not initialized.")
                }
                return null
            }
            val startTime = SystemClock.uptimeMillis()
            return try {
                val result = classifier.classify(audioData)
                val inferenceTime = SystemClock.uptimeMillis() - startTime
                ResultBundle(listOf(result), inferenceTime)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Audio classification failed: ${e.message}", e)
                notifyError("Audio classifier failed to classify: ${e.message}")
                null
            }
        }
    }

    /**
     * Streams mono PCM [samples] at [sampleRateHz] through [RunningMode.AUDIO_STREAM] in 0.975 s
     * windows via [AudioClassifier.classifyAsync], returning every emitted window result in order.
     */
    fun classifyAudioStream(samples: FloatArray, sampleRateHz: Int): ResultBundle? {
        synchronized(lock) {
            stopAudioClassificationLocked()
            lastError = null
            val mpDelegate =
                when (currentDelegate) {
                    ClassifierDelegate.CPU -> Delegate.CPU
                    ClassifierDelegate.GPU -> Delegate.GPU
                }
            val streamedResults = Collections.synchronizedList(mutableListOf<AudioClassifierResult>())
            val streamException = AtomicReference<RuntimeException?>(null)

            val baseOptions =
                BaseOptions.builder()
                    .setModelAssetPath(YAMNET_MODEL)
                    .setDelegate(mpDelegate)
                    .build()

            val startTime = SystemClock.uptimeMillis()
            var streamClassifier: AudioClassifier? = null
            return try {
                val options =
                    AudioClassifier.AudioClassifierOptions.builder()
                        .setScoreThreshold(classificationThreshold)
                        .setMaxResults(numOfResults)
                        .setBaseOptions(baseOptions)
                        .setRunningMode(RunningMode.AUDIO_STREAM)
                        .setResultListener { result -> streamedResults.add(result) }
                        .setErrorListener { error ->
                            Log.e(TAG, "Audio stream error: ${error.message}", error)
                            streamException.compareAndSet(null, error)
                        }
                        .build()

                val classifier =
                    try {
                        AudioClassifier.createFromOptions(context, options)
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "MP task failed to load with error: ${e.message}", e)
                        notifyError("Audio Classifier failed to initialize: ${e.message}")
                        return null
                    }
                streamClassifier = classifier
                val chunkSize = (sampleRateHz * EXPECTED_INPUT_LENGTH).roundToInt().coerceAtLeast(1)
                val format =
                    AudioDataFormat.builder()
                        .setNumOfChannels(1)
                        .setSampleRate(sampleRateHz.toFloat())
                        .build()

                var start = 0
                while (start < samples.size) {
                    streamException.get()?.let { throw it }
                    val end = (start + chunkSize).coerceAtMost(samples.size)
                    val chunk = samples.copyOfRange(start, end)
                    val audioBlock = AudioData.create(format, chunk.size).apply { load(chunk) }
                    val timestampMs = Math.round(start * 1000.0 / sampleRateHz)
                    classifier.classifyAsync(audioBlock, timestampMs)
                    Thread.sleep(20L)
                    start = end
                }
                Thread.sleep(100L)
                classifier.close()
                streamClassifier = null
                streamException.get()?.let { throw it }
                val inferenceTime = SystemClock.uptimeMillis() - startTime
                ResultBundle(streamedResults.toList(), inferenceTime)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "MP task failed in stream mode: ${e.message}", e)
                notifyError("Audio Classifier failed to initialize: ${e.message}")
                null
            } catch (e: RuntimeException) {
                Log.e(TAG, "Audio stream classification failed: ${e.message}", e)
                notifyError("Audio classifier failed to classify: ${e.message}")
                null
            } finally {
                runCatching { streamClassifier?.close() }
            }
        }
    }

    fun stopAudioClassification() {
        synchronized(lock) { stopAudioClassificationLocked() }
    }

    private fun stopAudioClassificationLocked() {
        executor?.shutdownNow()
        executor = null
        recorder?.let { rec ->
            runCatching {
                if (rec.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    rec.stop()
                }
            }
            runCatching { rec.release() }
        }
        recorder = null
        runCatching { audioClassifier?.close() }
        audioClassifier = null
    }

    fun isClosed(): Boolean {
        synchronized(lock) {
            return audioClassifier == null
        }
    }

    private fun streamAudioResultListener(result: AudioClassifierResult) {
        val elapsed = (SystemClock.uptimeMillis() - lastStreamStartMs).coerceAtLeast(0L)
        listener?.onResult(ResultBundle(listOf(result), elapsed))
    }

    private fun streamAudioErrorListener(e: RuntimeException) {
        Log.e(TAG, "Live audio classification failed: ${e.message}", e)
        notifyError(e.message ?: "Live audio classification failed.")
    }

    private fun notifyError(message: String) {
        lastError = message
        listener?.onError(message)
    }

    data class ResultBundle(
        val results: List<AudioClassifierResult>,
        val inferenceTime: Long,
    )

    companion object {
        private const val TAG = "AudioClassifierHelper"
        const val DISPLAY_THRESHOLD = 0.3f
        const val DEFAULT_NUM_OF_RESULTS = 2
        const val DEFAULT_OVERLAP = 2
        const val YAMNET_MODEL = "yamnet.tflite"
        const val SAMPLE_SPEECH_ASSET = "speech_16000_hz_mono.wav"

        const val SAMPLING_RATE_IN_HZ = 16_000
        private const val BUFFER_SIZE_FACTOR = 2
        const val EXPECTED_INPUT_LENGTH = 0.975f
        private const val REQUIRE_INPUT_BUFFER_SIZE =
            SAMPLING_RATE_IN_HZ * EXPECTED_INPUT_LENGTH

        private const val BUFFER_SIZE_IN_BYTES =
            REQUIRE_INPUT_BUFFER_SIZE * Float.SIZE_BYTES * BUFFER_SIZE_FACTOR
    }

    interface ClassifierListener {
        fun onError(error: String)

        fun onResult(resultBundle: ResultBundle)
    }
}
