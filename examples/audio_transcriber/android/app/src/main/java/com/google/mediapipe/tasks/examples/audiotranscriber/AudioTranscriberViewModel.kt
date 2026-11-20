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

package com.google.mediapipe.tasks.examples.audiotranscriber

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriberResult
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Duration of the transcribed audio segment in seconds. */
val AudioTranscriberResult.Segment.durationSeconds: Double
  get() = (toTimestampMs() - fromTimestampMs()).coerceAtLeast(0L) / 1_000.0

/** Immutable UI state published by [AudioTranscriberViewModel]. */
data class AudioTranscriberUiState(
  val isRecording: Boolean = false,
  val isTranscribingSample: Boolean = false,
  val isModelReady: Boolean = false,
  val currentDelegate: Int = AudioTranscriberHelper.DELEGATE_CPU,
  val interimTranscript: String = "",
  val finalizedSegments: List<AudioTranscriberResult.Segment> = emptyList(),
  val audioLevel: Float = 0.0f,
  val lastLatencyMs: Long? = null,
  val statusMessage: String = AudioTranscriberViewModel.STATUS_LOADING,
  val errorMessage: String? = null,
  val activeModelName: String = AudioTranscriberHelper.DEFAULT_MODEL_FILE_NAME,
)

/**
 * ViewModel coordinating live microphone capture, sample clip playback/decoding, and MediaPipe
 * [AudioTranscriber] streaming speech recognition.
 */
class AudioTranscriberViewModel(
  private val captureEngine: AudioCapturing = AudioCaptureEngine(),
  private val initialModelResolver: suspend () -> File,
  private val sampleAudioProvider: (suspend () -> DecodedWavAudio)? = null,
  private val transcriberLoader: suspend (File, Int) -> AudioTranscriberHelper,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AudioTranscriberUiState())
  val uiState: StateFlow<AudioTranscriberUiState> = _uiState.asStateFlow()

  private val transcriberMutex = Mutex()
  @Volatile private var transcriber: AudioTranscriberHelper? = null
  @Volatile private var activeModelFile: File? = null
  @Volatile private var audioChunkChannel: Channel<FloatArray>? = null
  private var isCleared = false

  init {
    viewModelScope.launch {
      loadTranscriber(
        statusOnSuccess = { STATUS_READY },
        delegateIndex = _uiState.value.currentDelegate,
        modelProvider = initialModelResolver,
      )
    }
  }

  fun toggleRecording() {
    if (_uiState.value.isRecording) stopRecording() else startRecording()
  }

  fun setDelegate(delegateIndex: Int) {
    if (_uiState.value.isRecording || _uiState.value.isTranscribingSample) return
    if (delegateIndex == _uiState.value.currentDelegate && _uiState.value.isModelReady) return
    _uiState.update {
      it.copy(
        currentDelegate = delegateIndex,
        isModelReady = false,
        statusMessage = STATUS_LOADING,
        errorMessage = null,
      )
    }
    viewModelScope.launch {
      val provider: suspend () -> File = { activeModelFile ?: initialModelResolver() }
      loadTranscriber(
        statusOnSuccess = { STATUS_READY },
        delegateIndex = delegateIndex,
        modelProvider = provider,
      )
    }
  }

  fun fallbackToCpu() {
    setDelegate(AudioTranscriberHelper.DELEGATE_CPU)
  }

  fun transcribeSampleClip() {
    if (_uiState.value.isRecording || _uiState.value.isTranscribingSample) return
    val provider = sampleAudioProvider ?: return

    _uiState.update {
      it.copy(
        isTranscribingSample = true,
        statusMessage = STATUS_TRANSCRIBING_SAMPLE,
        errorMessage = null,
        interimTranscript = "",
        finalizedSegments = emptyList(),
      )
    }

    launchTask("Sample transcription error") {
      try {
        if (
          transcriber == null &&
            !loadTranscriber(
              statusOnSuccess = { STATUS_READY },
              delegateIndex = _uiState.value.currentDelegate,
              modelProvider = { activeModelFile ?: initialModelResolver() },
            )
        ) {
          return@launchTask
        }

        val audio = withContext(ioDispatcher) { provider() }
        val chunkSize = AudioTranscriberHelper.SAMPLE_RATE_HZ / 2 // 500 ms chunks at 16 kHz
        val startMs = SystemClock.uptimeMillis()

        val resultSegments =
          withTranscriber {
            val active = transcriber ?: return@withTranscriber emptyList()
            val accumulated = mutableListOf<AudioTranscriberResult.Segment>()
            var offset = 0
            while (offset < audio.samples.size) {
              val end = (offset + chunkSize).coerceAtMost(audio.samples.size)
              val chunk = audio.samples.copyOfRange(offset, end)
              val isLast = end == audio.samples.size
              val partial = active.transcribe(chunk, endOfStream = isLast)
              val chunkLevel = chunk.calculateRmsLevel()
              _uiState.update { state ->
                val nextFinal = state.finalizedSegments.toMutableList()
                var nextInterim = state.interimTranscript
                for (segment in partial.segments()) {
                  if (segment.isFinal) {
                    nextFinal.add(segment)
                    accumulated.add(segment)
                    nextInterim = ""
                  } else {
                    nextInterim = segment.transcript()
                  }
                }
                state.copy(
                  audioLevel = chunkLevel,
                  finalizedSegments = nextFinal,
                  interimTranscript = nextInterim,
                )
              }
              offset = end
            }
            val finished = active.finishStream()
            active.prewarm()
            accumulated.addAll(finished.segments())
            finished.segments()
          }

        val elapsedMs = (SystemClock.uptimeMillis() - startMs).coerceAtLeast(1L)
        _uiState.update { current ->
          current.copy(
            finalizedSegments = current.finalizedSegments + resultSegments,
            interimTranscript = "",
            audioLevel = 0.0f,
            lastLatencyMs = elapsedMs,
            statusMessage = STATUS_SAMPLE_COMPLETE,
          )
        }
      } finally {
        _uiState.update { it.copy(isTranscribingSample = false, audioLevel = 0.0f) }
      }
    }
  }

  fun startRecording() {
    if (_uiState.value.isRecording || _uiState.value.isTranscribingSample) return
    launchTask("Microphone error") {
      if (
        transcriber == null &&
          !loadTranscriber(
            statusOnSuccess = { STATUS_READY },
            delegateIndex = _uiState.value.currentDelegate,
            modelProvider = { activeModelFile ?: initialModelResolver() },
          )
      ) {
        return@launchTask
      }
      val channel =
        Channel<FloatArray>(
          capacity = AUDIO_CHANNEL_CAPACITY,
          onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
      audioChunkChannel?.close()
      audioChunkChannel = channel
      _uiState.update {
        it.copy(isRecording = true, statusMessage = STATUS_LISTENING, errorMessage = null)
      }
      try {
        captureEngine.startCapture { chunk -> onAudioChunkCaptured(chunk) }
      } catch (e: Exception) {
        channel.close()
        audioChunkChannel = null
        _uiState.update { it.copy(isRecording = false, audioLevel = 0.0f) }
        throw e
      }
      launchTask("Transcription error") {
        val sessionStartMs = SystemClock.uptimeMillis()
        for (samples in channel) {
          val result =
            withTranscriber {
              if (!_uiState.value.isRecording) return@withTranscriber null
              transcriber?.transcribe(samples, endOfStream = false)
            } ?: continue
          if (!_uiState.value.isRecording) break
          val elapsedMs = (SystemClock.uptimeMillis() - sessionStartMs).coerceAtLeast(1L)
          _uiState.update { current ->
            val updatedFinalized = current.finalizedSegments.toMutableList()
            var updatedInterim = current.interimTranscript
            for (segment in result.segments()) {
              if (segment.isFinal) {
                updatedFinalized.add(segment)
                updatedInterim = ""
              } else {
                updatedInterim = segment.transcript()
              }
            }
            current.copy(
              finalizedSegments = updatedFinalized,
              interimTranscript = updatedInterim,
              lastLatencyMs = elapsedMs,
            )
          }
        }
      }
    }
  }

  fun stopRecording() {
    if (!_uiState.value.isRecording) return
    stopCaptureSession()
    _uiState.update { it.copy(isRecording = false, audioLevel = 0.0f) }
    launchTask("Finalization error") {
      val startMs = SystemClock.uptimeMillis()
      val finalResult =
        withTranscriber {
          transcriber?.let { active -> active.finishStream().also { active.prewarm() } }
        } ?: return@launchTask
      val finalizeMs = (SystemClock.uptimeMillis() - startMs).coerceAtLeast(1L)
      _uiState.update { current ->
        current.copy(
          finalizedSegments = current.finalizedSegments + finalResult.segments(),
          interimTranscript = "",
          lastLatencyMs = finalizeMs,
          statusMessage = STATUS_FINALIZED,
        )
      }
    }
  }

  fun clearTranscripts() {
    _uiState.update {
      it.copy(
        interimTranscript = "",
        finalizedSegments = emptyList(),
        lastLatencyMs = null,
        errorMessage = null,
      )
    }
    launchTask("Reset error") {
      withTranscriber {
        transcriber?.let { active ->
          val unused = active.finishStream()
          active.prewarm()
        }
      }
      _uiState.update { it.copy(statusMessage = STATUS_CLEARED) }
    }
  }

  fun loadModel(modelFile: File) {
    if (_uiState.value.isRecording) {
      stopCaptureSession()
      _uiState.update { it.copy(isRecording = false, audioLevel = 0.0f) }
    }
    viewModelScope.launch {
      loadTranscriber(
        statusOnSuccess = { STATUS_READY },
        delegateIndex = _uiState.value.currentDelegate,
        modelProvider = { modelFile },
      )
    }
  }

  fun onModelStagingFailed(reason: String?) {
    val detail = reason ?: "Unknown error"
    _uiState.update {
      it.copy(statusMessage = STATUS_ERROR, errorMessage = "Model staging failed: $detail")
    }
  }

  fun onMicrophonePermissionDenied() {
    stopCaptureSession()
    _uiState.update {
      it.copy(
        isRecording = false,
        audioLevel = 0.0f,
        statusMessage = STATUS_PERMISSION_DENIED,
        errorMessage = STATUS_PERMISSION_DENIED,
      )
    }
  }

  public override fun onCleared() {
    stopCaptureSession()
    CoroutineScope(ioDispatcher).launch {
      transcriberMutex.withLock {
        isCleared = true
        runCatching { transcriber?.close() }
        transcriber = null
      }
    }
  }

  private fun stopCaptureSession() {
    captureEngine.stopCapture()
    audioChunkChannel?.close()
    audioChunkChannel = null
  }

  private fun onAudioChunkCaptured(chunk: AudioChunk) {
    if (!_uiState.value.isRecording) return
    _uiState.update { it.copy(audioLevel = chunk.rmsLevel) }
    val unused = audioChunkChannel?.trySend(chunk.samples)
  }

  private suspend fun loadTranscriber(
    statusOnSuccess: (File) -> String,
    delegateIndex: Int,
    modelProvider: suspend () -> File,
  ): Boolean {
    val startMs = SystemClock.uptimeMillis()
    return try {
      val modelFile =
        withTranscriber {
          if (isCleared) return@withTranscriber null
          runCatching { transcriber?.close() }
          transcriber = null
          val file = modelProvider()
          val instance = transcriberLoader(file, delegateIndex)
          instance.prewarm()
          activeModelFile = file
          transcriber = instance
          file
        } ?: return false
      val loadMs = (SystemClock.uptimeMillis() - startMs).coerceAtLeast(1L)
      _uiState.update {
        it.copy(
          isModelReady = true,
          interimTranscript = "",
          finalizedSegments = emptyList(),
          activeModelName = modelFile.name,
          lastLatencyMs = loadMs,
          statusMessage = statusOnSuccess(modelFile),
          errorMessage = null,
        )
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize AudioTranscriber", e)
      _uiState.update {
        it.copy(
          isModelReady = false,
          statusMessage = STATUS_ERROR,
          errorMessage = e.message ?: "Failed to initialize AudioTranscriber",
        )
      }
      false
    }
  }

  private suspend fun <T> withTranscriber(block: suspend () -> T): T =
    withContext(ioDispatcher) { transcriberMutex.withLock { block() } }

  private fun launchTask(errorPrefix: String, block: suspend () -> Unit) {
    viewModelScope.launch {
      try {
        block()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, errorPrefix, e)
        _uiState.update {
          it.copy(
            statusMessage = STATUS_ERROR,
            errorMessage = "$errorPrefix: ${e.message ?: "unknown error"}",
          )
        }
      }
    }
  }

  companion object {
    private const val TAG = "AudioTranscriberVM"
    private const val SIDELOAD_TMP_DIR_PATH = "/data/local/tmp/audiotranscriber"
    private const val AUDIO_CHANNEL_CAPACITY = 64
    const val SAMPLE_AUDIO_ASSET = "speech_16000_hz_mono.wav"
    const val STATUS_LOADING = "Loading..."
    const val STATUS_READY = "Ready"
    const val STATUS_LISTENING = "Listening..."
    const val STATUS_TRANSCRIBING_SAMPLE = "Transcribing..."
    const val STATUS_SAMPLE_COMPLETE = "Transcribed"
    const val STATUS_FINALIZED = "Finalized"
    const val STATUS_CLEARED = "Cleared"
    const val STATUS_PERMISSION_DENIED = "No mic access"
    const val STATUS_ERROR = "Error"

    fun factory(context: Context): ViewModelProvider.Factory {
      val appContext = context.applicationContext
      return object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
          val sideloadDirs =
            listOfNotNull(File(SIDELOAD_TMP_DIR_PATH), appContext.getExternalFilesDir(null))
          return AudioTranscriberViewModel(
            initialModelResolver = {
              resolveDefaultModelFile(sideloadDirs, appContext.cacheDir, appContext.assets::open)
            },
            sampleAudioProvider = {
              appContext.assets.open(SAMPLE_AUDIO_ASSET).use { WavAudioLoader.decode(it) }
            },
            transcriberLoader = { file, delegate ->
              AudioTranscriberHelper.create(appContext, file, delegate)
            },
          )
            as T
        }
      }
    }
  }
}
