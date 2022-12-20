/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
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
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifier
import com.google.mediapipe.tasks.audio.audioclassifier.AudioClassifierResult
import com.google.mediapipe.tasks.audio.core.RunningMode
import com.google.mediapipe.tasks.components.containers.AudioData
import com.google.mediapipe.tasks.components.containers.AudioData.AudioDataFormat
import com.google.mediapipe.tasks.components.containers.Classifications
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class AudioClassifierHelper(
    val context: Context,
    var currentModel: Int = YAMNET_MODEL,
    var classificationThreshold: Float = DISPLAY_THRESHOLD,
    var overlap: Int = DEFAULT_OVERLAP,
    var numOfResults: Int = DEFAULT_NUM_OF_RESULTS,
    var currentDelegate: Int = DELEGATE_CPU,
    var runningMode: RunningMode = RunningMode.AUDIO_CLIPS,
    var listener: ClassifierListener? = null,
) {

    private lateinit var recorder: AudioRecord
    private lateinit var executor: ScheduledThreadPoolExecutor
    private var audioClassifier: AudioClassifier? = null
    private val classifyRunnable = Runnable {
        classifyAudio(recorder)
    }

    init {
        initClassifier()
    }

    @SuppressLint("MissingPermission")
    fun initClassifier() {
        // Set general detection options, e.g. number of used threads
        val baseOptionsBuilder = BaseOptions.builder()

        // Use the specified hardware for running the model. Default to CPU.
        // Possible to also use a GPU delegate, but this requires that the classifier be created
        // on the same thread that is using the classifier, which is outside of the scope of this
        // sample's design.
        when (currentDelegate) {
            DELEGATE_CPU -> {
                // Default
                baseOptionsBuilder.setDelegate(Delegate.CPU)
            }
            DELEGATE_GPU -> {
                baseOptionsBuilder.setDelegate(Delegate.GPU)
            }
        }

        when (currentModel) {
            YAMNET_MODEL -> {
                baseOptionsBuilder.setModelAssetPath("yamnet.tflite")
            }
        }
        try {
            // Configures a set of parameters for the classifier and what results will be returned.
            val optionsBuilder =
                AudioClassifier.AudioClassifierOptions.builder()
                    .setScoreThreshold(classificationThreshold)
                    .setMaxResults(numOfResults)
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setRunningMode(runningMode)

            if (runningMode == RunningMode.AUDIO_STREAM) {
                optionsBuilder
                    .setResultListener(this::onResult)
                    .setErrorListener(this::onError)
            }

            val options = optionsBuilder.build()

            // Create the classifier and required supporting objects
            audioClassifier = AudioClassifier.createFromOptions(context, options)
            recorder = AudioRecord(
                MediaRecorder.AudioSource.DEFAULT,
                SAMPLING_RATE_IN_HZ,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                BUFFER_SIZE_IN_BYTES
            )
            startAudioClassification()

        } catch (e: IllegalStateException) {
            listener?.onError(
                "Audio Classifier failed to initialize. See error logs for details"
            )

            Log.e(
                TAG, "MP task failed to load with error: " + e.message
            )
        } catch (e: RuntimeException) {
            listener?.onError(
                "Audio Classifier failed to initialize. See error logs for details"
            )

            Log.e(
                TAG, "MP task failed to load with error: " + e.message
            )
        }
    }

    fun startAudioClassification() {
        if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            return
        }

        recorder.startRecording()
        executor = ScheduledThreadPoolExecutor(1)

        // Each model will expect a specific audio recording length. This formula calculates that
        // length using the input buffer size and tensor format sample rate.
        // For example, YAMNET expects 0.975 second length recordings.
        // This needs to be in milliseconds to avoid the required Long value dropping decimals.
        val lengthInMilliSeconds =
            ((REQUIRE_INPUT_BUFFER_SIZE * 1.0f) / SAMPLING_RATE_IN_HZ) * 1000

        val interval = (lengthInMilliSeconds * (1 - (overlap * 0.25))).toLong()

        executor.scheduleAtFixedRate(
            classifyRunnable,
            0,
            interval,
            TimeUnit.MILLISECONDS
        )
    }

    private fun classifyAudio(audioRecord: AudioRecord) {
        val audioData = AudioData.create(
            AudioDataFormat.builder().setNumOfChannels(
                AudioFormat.CHANNEL_IN_DEFAULT
            ).setSampleRate(SAMPLING_RATE_IN_HZ.toFloat()).build(),
            REQUIRE_INPUT_BUFFER_SIZE
        )
        audioData.load(audioRecord)
        val inferenceTime = SystemClock.uptimeMillis()
        audioClassifier?.classifyAsync(audioData, inferenceTime)
    }

    fun stopAudioClassification() {
        executor.shutdownNow()
        audioClassifier?.close()
        audioClassifier = null
        recorder.stop()
    }

    fun isClose(): Boolean {
        return audioClassifier == null
    }

    private fun onResult(resultListener: AudioClassifierResult) {
        resultListener.classificationResult()?.get()
            ?.let { classificationResult ->
                listener?.onResult(
                    classificationResult.classifications(),
                    classificationResult.timestampMs().orElse(0)
                )
            }
    }

    private fun onError(e: RuntimeException) {
        listener?.onError(e.message.toString())
    }

    companion object {
        private const val TAG = "AudioClassifierHelper"
        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1
        const val DISPLAY_THRESHOLD = 0.3f
        const val DEFAULT_NUM_OF_RESULTS = 2
        const val DEFAULT_OVERLAP = 2
        const val YAMNET_MODEL = 0

        private const val SAMPLING_RATE_IN_HZ = 16000
        private const val CHANNEL_CONFIG: Int = AudioFormat.CHANNEL_IN_FRONT
        private const val AUDIO_FORMAT: Int = AudioFormat.ENCODING_PCM_FLOAT
        private const val BUFFER_SIZE_FACTOR: Int = 2
        private const val REQUIRE_INPUT_BUFFER_SIZE = 15600

        /**
         * Size of the buffer where the audio data is stored by Android
         */
        private const val BUFFER_SIZE_IN_BYTES =
            REQUIRE_INPUT_BUFFER_SIZE * Float
                .SIZE_BYTES * BUFFER_SIZE_FACTOR
    }

    interface ClassifierListener {
        fun onError(error: String)
        fun onResult(results: List<Classifications>, inferenceTime: Long)
    }
}
