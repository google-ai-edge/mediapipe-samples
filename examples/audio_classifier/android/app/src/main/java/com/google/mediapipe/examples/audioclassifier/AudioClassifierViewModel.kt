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

package com.google.mediapipe.examples.audioclassifier

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.audio.core.RunningMode
import java.util.concurrent.Executors
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ClassifierInputMode {
    CLIP,
    MICROPHONE,
}

sealed interface SampleClipSource {
    val id: String
    val label: String

    data object BundledSpeech : SampleClipSource {
        override val id: String = "speech"
        override val label: String = "Speech clip (16 kHz)"
    }

    data object SyntheticTone : SampleClipSource {
        override val id: String = "tone"
        override val label: String = "440 Hz tone"
    }

    data class CustomUri(val uri: Uri, val displayName: String) : SampleClipSource {
        override val id: String = "custom"
        override val label: String = displayName
    }
}

enum class OverlapOption(val index: Int, val label: String) {
    ZERO(0, "0%"),
    TWENTY_FIVE(1, "25%"),
    FIFTY(2, "50%"),
    SEVENTY_FIVE(3, "75%"),
}

data class AudioCategoryUi(
    val label: String,
    val score: Float,
)

data class AudioWindowUi(
    val windowIndex: Int,
    val startMs: Long,
    val categories: List<AudioCategoryUi>,
)

data class AudioClassifierUiState(
    val inputMode: ClassifierInputMode = ClassifierInputMode.CLIP,
    val selectedSource: SampleClipSource = SampleClipSource.BundledSpeech,
    val maxResults: Int = AudioClassifierHelper.DEFAULT_NUM_OF_RESULTS,
    val threshold: Float = AudioClassifierHelper.DISPLAY_THRESHOLD,
    val overlapOption: OverlapOption = OverlapOption.FIFTY,
    val isBusy: Boolean = false,
    val isListening: Boolean = false,
    val isPlaying: Boolean = false,
    val hasMicPermission: Boolean = false,
    val windows: List<AudioWindowUi> = emptyList(),
    val activeWindowIndex: Int = 0,
    val liveCategories: List<AudioCategoryUi> = emptyList(),
    val hasClipResult: Boolean = false,
    val hasLiveResult: Boolean = false,
    val inferenceTimeMs: Long? = null,
    val sampleRateHz: Int? = null,
    val durationMs: Long? = null,
    val errorMessage: String? = null,
) {
    val displayedCategories: List<AudioCategoryUi>
        get() =
            when (inputMode) {
                ClassifierInputMode.CLIP ->
                    windows.getOrNull(activeWindowIndex)?.categories.orEmpty()
                ClassifierInputMode.MICROPHONE -> liveCategories
            }

    val hasResult: Boolean
        get() =
            when (inputMode) {
                ClassifierInputMode.CLIP -> hasClipResult
                ClassifierInputMode.MICROPHONE -> hasLiveResult
            }
}

class AudioClassifierViewModel(application: Application) :
    AndroidViewModel(application), AudioClassifierHelper.ClassifierListener {

    private val _uiState = MutableStateFlow(AudioClassifierUiState())
    val uiState: StateFlow<AudioClassifierUiState> = _uiState.asStateFlow()

    private val classifierExecutor = Executors.newSingleThreadExecutor()
    private val classifierDispatcher = classifierExecutor.asCoroutineDispatcher()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var helper: AudioClassifierHelper? = null
    private var playbackJob: Job? = null
    private var audioTrack: AudioTrack? = null

    fun selectInputMode(mode: ClassifierInputMode) {
        if (_uiState.value.inputMode == mode) return
        stopPlaybackAndMic()
        _uiState.update {
            it.copy(
                inputMode = mode,
                errorMessage = null,
            )
        }
    }

    fun selectClipSource(source: SampleClipSource) {
        stopPlaybackOnly()
        _uiState.update {
            it.copy(
                selectedSource = source,
                windows = emptyList(),
                activeWindowIndex = 0,
                hasClipResult = false,
                inferenceTimeMs = null,
                sampleRateHz = null,
                durationMs = null,
                errorMessage = null,
            )
        }
    }

    fun selectCustomAudioUri(uri: Uri, displayName: String) {
        selectClipSource(SampleClipSource.CustomUri(uri, displayName))
        classifySelectedClip(playAudio = true)
    }

    fun updateHasMicPermission(granted: Boolean) {
        _uiState.update { it.copy(hasMicPermission = granted) }
    }

    fun updateMaxResults(maxResults: Int) {
        val clamped = maxResults.coerceIn(1, 5)
        if (_uiState.value.maxResults == clamped) return
        val wasListening = _uiState.value.isListening
        stopPlaybackAndMic()
        _uiState.update {
            it.copy(
                maxResults = clamped,
                windows = emptyList(),
                hasClipResult = false,
                liveCategories = emptyList(),
                hasLiveResult = false,
                inferenceTimeMs = null,
            )
        }
        if (wasListening && _uiState.value.inputMode == ClassifierInputMode.MICROPHONE) {
            startMicrophone()
        }
    }

    fun updateThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.1f, 0.8f)
        if (_uiState.value.threshold == clamped) return
        val wasListening = _uiState.value.isListening
        stopPlaybackAndMic()
        _uiState.update {
            it.copy(
                threshold = clamped,
                windows = emptyList(),
                hasClipResult = false,
                liveCategories = emptyList(),
                hasLiveResult = false,
                inferenceTimeMs = null,
            )
        }
        if (wasListening && _uiState.value.inputMode == ClassifierInputMode.MICROPHONE) {
            startMicrophone()
        }
    }

    fun updateOverlap(option: OverlapOption) {
        if (_uiState.value.overlapOption == option) return
        val wasListening = _uiState.value.isListening
        stopPlaybackAndMic()
        _uiState.update {
            it.copy(
                overlapOption = option,
                liveCategories = emptyList(),
                hasLiveResult = false,
            )
        }
        if (wasListening && _uiState.value.inputMode == ClassifierInputMode.MICROPHONE) {
            startMicrophone()
        }
    }

    fun selectWindow(index: Int) {
        val count = _uiState.value.windows.size
        if (index in 0 until count) {
            _uiState.update { it.copy(activeWindowIndex = index) }
        }
    }

    fun classifySelectedClip(playAudio: Boolean = false) {
        if (_uiState.value.isBusy) return
        stopPlaybackOnly()
        val snapshot = _uiState.value
        _uiState.update {
            it.copy(
                isBusy = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val result =
                    withContext(classifierDispatcher) {
                        val decoded =
                            when (val source = snapshot.selectedSource) {
                                SampleClipSource.BundledSpeech ->
                                    AudioClipDecoder.decodeAssetWav(
                                        context,
                                        AudioClassifierHelper.SAMPLE_SPEECH_ASSET,
                                    )
                                SampleClipSource.SyntheticTone ->
                                    AudioClipDecoder.generateSineTone(freqHz = 440f, durationMs = 2_000)
                                is SampleClipSource.CustomUri ->
                                    AudioClipDecoder.decodeUri(context, source.uri)
                            }

                        helper?.stopAudioClassification()
                        val localHelper =
                            AudioClassifierHelper(
                                context = context,
                                classificationThreshold = snapshot.threshold,
                                overlap = snapshot.overlapOption.index,
                                numOfResults = snapshot.maxResults,
                                runningMode = RunningMode.AUDIO_CLIPS,
                                listener = this@AudioClassifierViewModel,
                            )
                        helper = localHelper
                        val bundle =
                            localHelper.classifyAudio(decoded.toAudioData())
                                ?: throw IllegalStateException("Audio classification failed.")
                        localHelper.stopAudioClassification()

                        val windowStepMs =
                            (AudioClassifierHelper.EXPECTED_INPUT_LENGTH * 1000f).toLong()
                        val windowResults =
                            bundle.results
                                .firstOrNull()
                                ?.classificationResults()
                                ?.mapIndexed { idx, cr ->
                                    val categories =
                                        cr.classifications()
                                            .firstOrNull()
                                            ?.categories()
                                            .orEmpty()
                                            .sortedByDescending { it.score() }
                                            .take(snapshot.maxResults)
                                            .map { cat ->
                                                AudioCategoryUi(
                                                    label =
                                                        cat.displayName()
                                                            ?.takeIf { it.isNotBlank() }
                                                            ?: cat.categoryName().orEmpty(),
                                                    score = cat.score(),
                                                )
                                            }
                                    AudioWindowUi(
                                        windowIndex = idx,
                                        startMs = idx * windowStepMs,
                                        categories = categories,
                                    )
                                }
                                .orEmpty()

                        val bestWindowIdx =
                            windowResults
                                .indexOfFirst { it.categories.isNotEmpty() }
                                .coerceAtLeast(0)

                        ClipClassificationOutcome(
                            decoded = decoded,
                            windows = windowResults,
                            initialWindow = bestWindowIdx,
                            inferenceTimeMs = bundle.inferenceTime,
                        )
                    }

                _uiState.update {
                    it.copy(
                        isBusy = false,
                        windows = result.windows,
                        activeWindowIndex = result.initialWindow,
                        hasClipResult = true,
                        inferenceTimeMs = result.inferenceTimeMs,
                        sampleRateHz = result.decoded.sampleRateHz,
                        durationMs = result.decoded.durationMs,
                        errorMessage = null,
                    )
                }

                if (playAudio && result.windows.isNotEmpty()) {
                    startPlayback(result.decoded, result.windows.size)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clip classification error", e)
                _uiState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = e.message ?: "Unable to classify audio clip.",
                    )
                }
            }
        }
    }

    fun startMicrophone() {
        if (!_uiState.value.hasMicPermission || _uiState.value.isListening) return
        stopPlaybackOnly()
        val snapshot = _uiState.value
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }

        viewModelScope.launch {
            val initialized =
                withContext(classifierDispatcher) {
                    helper?.stopAudioClassification()
                    val streamHelper =
                        AudioClassifierHelper(
                            context = getApplication(),
                            classificationThreshold = snapshot.threshold,
                            overlap = snapshot.overlapOption.index,
                            numOfResults = snapshot.maxResults,
                            runningMode = RunningMode.AUDIO_STREAM,
                            listener = this@AudioClassifierViewModel,
                            initOnCreate = false,
                        )
                    helper = streamHelper
                    streamHelper.initClassifier()
                }
            _uiState.update {
                it.copy(
                    isBusy = false,
                    isListening = initialized,
                    sampleRateHz = AudioClassifierHelper.SAMPLING_RATE_IN_HZ,
                )
            }
        }
    }

    fun stopMicrophone() {
        viewModelScope.launch {
            withContext(classifierDispatcher) { helper?.stopAudioClassification() }
            _uiState.update { it.copy(isListening = false) }
        }
    }

    fun stopPlaybackAndMic() {
        stopPlaybackOnly()
        if (_uiState.value.isListening) {
            stopMicrophone()
        }
    }

    fun onPauseLifecycle() {
        stopPlaybackAndMic()
    }

    private fun startPlayback(decoded: DecodedAudio, windowCount: Int) {
        stopPlaybackOnly()
        playbackJob =
            viewModelScope.launch {
                val track =
                    runCatching { createAndPlayAudioTrack(decoded) }.getOrNull()
                audioTrack = track
                _uiState.update { it.copy(isPlaying = track != null || windowCount > 1) }
                try {
                    val stepMs = (AudioClassifierHelper.EXPECTED_INPUT_LENGTH * 1000f).toLong()
                    for (idx in 0 until windowCount) {
                        if (!isActive) break
                        _uiState.update { it.copy(activeWindowIndex = idx) }
                        val remainingMs = (decoded.durationMs - idx * stepMs).coerceAtLeast(300L)
                        delay(min(stepMs, remainingMs))
                    }
                } finally {
                    releaseAudioTrack()
                    _uiState.update { it.copy(isPlaying = false) }
                }
            }
    }

    fun stopPlaybackOnly() {
        playbackJob?.cancel()
        playbackJob = null
        releaseAudioTrack()
        _uiState.update { it.copy(isPlaying = false) }
    }

    private fun createAndPlayAudioTrack(decoded: DecodedAudio): AudioTrack? {
        if (decoded.samples.isEmpty() || decoded.sampleRateHz <= 0) return null
        val bufferBytes = decoded.samples.size * Float.SIZE_BYTES
        val track =
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(decoded.sampleRateHz)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
        track.write(decoded.samples, 0, decoded.samples.size, AudioTrack.WRITE_BLOCKING)
        track.play()
        return track
    }

    private fun releaseAudioTrack() {
        audioTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
            }
            runCatching { track.release() }
        }
        audioTrack = null
    }

    override fun onError(error: String) {
        mainHandler.post {
            _uiState.update {
                it.copy(
                    isBusy = false,
                    isListening = false,
                    errorMessage = error,
                )
            }
        }
    }

    override fun onResult(resultBundle: AudioClassifierHelper.ResultBundle) {
        val categories =
            resultBundle.results
                .firstOrNull()
                ?.classificationResults()
                ?.firstOrNull()
                ?.classifications()
                ?.firstOrNull()
                ?.categories()
                .orEmpty()
                .sortedByDescending { it.score() }
                .take(_uiState.value.maxResults)
                .map { cat ->
                    AudioCategoryUi(
                        label =
                            cat.displayName()?.takeIf { it.isNotBlank() }
                                ?: cat.categoryName().orEmpty(),
                        score = cat.score(),
                    )
                }
        mainHandler.post {
            _uiState.update {
                it.copy(
                    liveCategories = categories,
                    hasLiveResult = true,
                    inferenceTimeMs = resultBundle.inferenceTime,
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopPlaybackOnly()
        classifierExecutor.execute { helper?.stopAudioClassification() }
        classifierExecutor.shutdown()
    }

    private data class ClipClassificationOutcome(
        val decoded: DecodedAudio,
        val windows: List<AudioWindowUi>,
        val initialWindow: Int,
        val inferenceTimeMs: Long,
    )

    companion object {
        private const val TAG = "AudioClassifierVM"
    }
}
