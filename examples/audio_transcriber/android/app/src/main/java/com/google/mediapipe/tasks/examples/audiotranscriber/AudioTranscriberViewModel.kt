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
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber.AudioTranscriberOptions
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriberResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
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
  val interimTranscript: String = "",
  val finalizedSegments: List<AudioTranscriberResult.Segment> = emptyList(),
  val audioLevel: Float = 0.0f,
  val statusMessage: String = AudioTranscriberViewModel.STATUS_READY,
  val activeModelName: String = AudioTranscriberViewModel.DEFAULT_MODEL_FILE_NAME,
)

/** Thin seam around MediaPipe's [AudioTranscriber] task API. */
interface AudioTranscribing : AutoCloseable {
  fun prewarm()

  @Suppress("KotlinArray") // AudioTranscriber.transcribe() takes PCM samples as a FloatArray.
  fun transcribe(samples: FloatArray, endOfStream: Boolean = false): AudioTranscriberResult

  fun finishStream(): AudioTranscriberResult

  override fun close()

  companion object {
    /** Creates an [AudioTranscribing] instance backed by MediaPipe's [AudioTranscriber]. */
    fun create(context: Context, modelFile: File): AudioTranscribing {
      // Default to CPU so the sample runs out-of-the-box on both Android emulators and physical
      // devices (the bundled .litertlm archive's TFLite subgraphs support both CPU and GPU).
      val baseOptions =
        BaseOptions.builder()
          .setModelAssetPath(modelFile.absolutePath)
          .setDelegate(Delegate.CPU)
          .build()
      val options =
        AudioTranscriberOptions.builder()
          .setBaseOptions(baseOptions)
          .setSampleRateHz(AudioTranscriberViewModel.SAMPLE_RATE_HZ)
          .setEnableInterimResults(true)
          .setModelArchitecture(AudioTranscriber.ModelArchitecture.AUTO)
          .setCacheDir(context.cacheDir.absolutePath)
          .build()
      val transcriber = AudioTranscriber.createFromOptions(context, options)
      return object : AudioTranscribing {
        override fun prewarm() = transcriber.prewarm()

        override fun transcribe(samples: FloatArray, endOfStream: Boolean) =
          transcriber.transcribe(samples, endOfStream)

        override fun finishStream() = transcriber.finishStream()

        override fun close() = transcriber.close()
      }
    }
  }
}

/**
 * Resolves the default `.litertlm` model file, preferring any sideloaded `.litertlm` file in
 * [sideloadDirectories] before staging the bundled asset [fileName] into [cacheDir].
 */
internal fun resolveDefaultModelFile(
  sideloadDirectories: List<File>,
  cacheDir: File,
  assetOpener: (String) -> InputStream,
  fileName: String = AudioTranscriberViewModel.DEFAULT_MODEL_FILE_NAME,
): File {
  val sideloaded =
    sideloadDirectories
      .asSequence()
      .filter { it.isDirectory }
      .mapNotNull { dir ->
        dir
          .listFiles()
          ?.filter {
            it.isFile &&
              !it.isHidden &&
              it.extension.equals(AudioTranscriberViewModel.MODEL_EXTENSION, ignoreCase = true)
          }
          ?.minByOrNull { it.name }
      }
      .firstOrNull()
  if (sideloaded != null) return sideloaded

  cacheDir.mkdirs()
  val stagedFile = File(cacheDir, fileName)
  if (stagedFile.exists() && stagedFile.length() > 0L) return stagedFile

  val candidatePaths =
    listOf(
      fileName,
      "models/$fileName",
      "third_party/odml/audio/transcription/data/models/$fileName",
    )
  for (path in candidatePaths) {
    try {
      assetOpener(path).use { input ->
        FileOutputStream(stagedFile).use { output -> input.copyTo(output) }
      }
      return stagedFile
    } catch (_: IOException) {
      // Try next candidate path.
    }
  }
  throw FileNotFoundException("Model not found in bundle: $fileName")
}

/**
 * ViewModel coordinating live microphone capture and MediaPipe [AudioTranscriber] streaming speech
 * recognition.
 */
class AudioTranscriberViewModel(
  private val captureEngine: AudioCapturing = AudioCaptureEngine(),
  private val initialModelResolver: suspend () -> File,
  private val transcriberLoader: suspend (File) -> AudioTranscribing,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AudioTranscriberUiState())
  val uiState: StateFlow<AudioTranscriberUiState> = _uiState.asStateFlow()

  private val transcriberMutex = Mutex()
  @Volatile private var transcriber: AudioTranscribing? = null
  @Volatile private var audioChunkChannel: Channel<FloatArray>? = null
  private var isCleared = false

  init {
    viewModelScope.launch {
      val unused =
        loadTranscriber(statusOnSuccess = { STATUS_READY }, modelProvider = initialModelResolver)
    }
  }

  fun toggleRecording() {
    if (_uiState.value.isRecording) stopRecording() else startRecording()
  }

  fun startRecording() {
    if (_uiState.value.isRecording) return
    launchTask("Microphone error") {
      if (
        transcriber == null &&
          !loadTranscriber(statusOnSuccess = { STATUS_READY }, modelProvider = initialModelResolver)
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
      _uiState.update { it.copy(isRecording = true, statusMessage = STATUS_LISTENING) }
      try {
        captureEngine.startCapture { chunk -> onAudioChunkCaptured(chunk) }
      } catch (e: Exception) {
        channel.close()
        audioChunkChannel = null
        _uiState.update { it.copy(isRecording = false, audioLevel = 0.0f) }
        throw e
      }
      launchTask("Transcription error") {
        for (samples in channel) {
          val result =
            withTranscriber {
              if (!_uiState.value.isRecording) return@withTranscriber null
              transcriber?.transcribe(samples, endOfStream = false)
            } ?: continue
          if (!_uiState.value.isRecording) break
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
            current.copy(finalizedSegments = updatedFinalized, interimTranscript = updatedInterim)
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
      val finalResult =
        withTranscriber {
          transcriber?.let { active -> active.finishStream().also { active.prewarm() } }
        } ?: return@launchTask
      _uiState.update { current ->
        current.copy(
          finalizedSegments = current.finalizedSegments + finalResult.segments(),
          interimTranscript = "",
          statusMessage = STATUS_FINALIZED,
        )
      }
    }
  }

  fun clearTranscripts() {
    _uiState.update { it.copy(interimTranscript = "", finalizedSegments = emptyList()) }
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
      val unused = loadTranscriber(statusOnSuccess = { "Loaded ${it.name}" }) { modelFile }
    }
  }

  fun onModelStagingFailed(reason: String?) {
    _uiState.update {
      it.copy(statusMessage = "Initialization error: ${reason ?: "Unknown error"}")
    }
  }

  fun onMicrophonePermissionDenied() {
    stopCaptureSession()
    _uiState.update {
      it.copy(isRecording = false, audioLevel = 0.0f, statusMessage = STATUS_PERMISSION_DENIED)
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
    modelProvider: suspend () -> File,
  ): Boolean =
    try {
      val modelFile =
        withTranscriber {
          if (isCleared) return@withTranscriber null
          runCatching { transcriber?.close() }
          transcriber = null
          val file = modelProvider()
          val instance = transcriberLoader(file)
          instance.prewarm()
          transcriber = instance
          file
        } ?: return false
      _uiState.update {
        it.copy(
          interimTranscript = "",
          finalizedSegments = emptyList(),
          activeModelName = modelFile.name,
          statusMessage = statusOnSuccess(modelFile),
        )
      }
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize AudioTranscriber", e)
      _uiState.update {
        it.copy(statusMessage = "Initialization error: ${e.message ?: "unknown error"}")
      }
      false
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
        _uiState.update { it.copy(statusMessage = "$errorPrefix: ${e.message ?: "unknown error"}") }
      }
    }
  }

  companion object {
    private const val TAG = "AudioTranscriberVM"
    private const val SIDELOAD_TMP_DIR_PATH = "/data/local/tmp/audiotranscriber"
    private const val AUDIO_CHANNEL_CAPACITY = 64
    const val MODEL_EXTENSION = "litertlm"
    const val DEFAULT_MODEL_FILE_NAME = "parakeet_tdt_0.6b_v3_5s_i8_stateful.$MODEL_EXTENSION"
    const val SAMPLE_RATE_HZ = 16_000
    const val STATUS_READY = "Ready"
    const val STATUS_LISTENING = "Listening..."
    const val STATUS_FINALIZED = "Recording finalized"
    const val STATUS_CLEARED = "Cleared"
    const val STATUS_PERMISSION_DENIED = "Microphone permission denied"

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
            transcriberLoader = { file -> AudioTranscribing.create(appContext, file) },
          )
            as T
        }
      }
    }
  }
}
