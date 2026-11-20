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

@file:Suppress("KotlinArray")

package com.google.mediapipe.tasks.audio.texttospeech.examples

import android.app.Application
import android.content.Intent
import android.media.AudioTrack
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.VoiceInfo
import com.google.mediapipe.tasks.audio.texttospeech.WordTimestamp
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking

/** Metadata for one synthesized audio piece (single unary utterance or streamed clause chunk). */
data class SynthesizedPieceInfo(
  val chunkIndex: Int,
  val isFinal: Boolean,
  val sampleCount: Int,
  val sampleRateHz: Int,
)

/** Wrapper keeping [TtsUiState.toString] compact while retaining synthesized float32 PCM samples. */
class SynthesizedAudioHolder(val samples: FloatArray) {
  override fun toString(): String = "SynthesizedAudioHolder(samples=${samples.size})"
}

/** Immutable Compose UI state for [TextToSpeechViewModel]. */
data class TtsUiState(
  val isEngineReady: Boolean = false,
  val isLoadingEngine: Boolean = false,
  val isBusy: Boolean = false,
  val isStreaming: Boolean = false,
  val selectedModel: TtsModelOption = TtsModelOption.KOKORO_CPU,
  val delegateMode: TtsDelegateMode = TtsDelegateMode.AUTO,
  val activeDelegateLabel: String = "Auto",
  val speakingRate: Float = 1.0f,
  val selectedPresetIndex: Int = 0,
  val textInput: String = TextToSpeechHelper.PRESETS[0].text,
  val availableLanguages: List<Pair<String, String>> = emptyList(),
  val selectedLanguageTag: String = "en-US",
  val voicesForLanguage: List<VoiceInfo> = emptyList(),
  val selectedVoiceId: String = TextToSpeechHelper.DEFAULT_VOICE,
  val statusBadge: String = "Loading…",
  val durationSeconds: Float? = null,
  val latencyMs: Long? = null,
  val rtf: Double? = null,
  val ttfaMs: Long? = null,
  val chunkCount: Int? = null,
  val pieces: List<SynthesizedPieceInfo> = emptyList(),
  val sampleRateHz: Int? = null,
  val rms: Double? = null,
  val peak: Double? = null,
  val nonFiniteCount: Int = 0,
  val wordTimestamps: List<WordTimestamp> = emptyList(),
  val synthesizedAudio: SynthesizedAudioHolder? = null,
  val hasReplayableAudio: Boolean = false,
  val lastWavPath: String? = null,
  val errorMessage: String? = null,
  val logLines: List<String> = emptyList(),
) {
  val synthesizedSamples: FloatArray?
    get() = synthesizedAudio?.samples
}

/**
 * ViewModel managing Kokoro-82M [TextToSpeech] initialization, unary & streaming synthesis,
 * speaker playback, WAV file export, and scripted automation intents.
 */
class TextToSpeechViewModel(application: Application) : AndroidViewModel(application) {
  private val app = application
  private val worker: ExecutorService = Executors.newSingleThreadExecutor { r ->
    Thread(r, "mediapipe-tts-demo")
  }
  private val busy = AtomicBoolean(false)
  private val loadGeneration = AtomicInteger(0)

  @Volatile private var stopRequested = false
  @Volatile private var activeTrack: AudioTrack? = null
  @Volatile private var tts: TextToSpeech? = null

  private var allVoices: List<VoiceInfo> = emptyList()
  private var pendingVoiceId: String? = null
  @Volatile private var explicitVoiceId: String? = null
  private var lastAudioSamples: FloatArray? = null
  private var lastSampleRateHz: Int = 24_000

  // Runtime intent extras
  private var numThreads = 4
  private var targetBucket = TextToSpeechHelper.DEFAULT_TARGET_BUCKET
  private var playAudio = true
  private var streamMode = false
  private var pendingAutorun = false

  private val _uiState = MutableStateFlow(TtsUiState())
  val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

  init {
    loadEngine()
  }

  fun handleLaunchIntent(intent: Intent?, initialLaunch: Boolean) {
    if (intent == null) return
    numThreads = intent.getIntExtra(MainActivity.EXTRA_NUM_THREADS, numThreads)
    val newBucket = intent.getIntExtra(MainActivity.EXTRA_TARGET_BUCKET, targetBucket)
    val bucketChanged = newBucket != targetBucket
    targetBucket = newBucket
    playAudio = intent.getBooleanExtra(MainActivity.EXTRA_PLAY_AUDIO, playAudio)
    streamMode = intent.getBooleanExtra(MainActivity.EXTRA_STREAM, streamMode)

    intent.getStringExtra(MainActivity.EXTRA_TEXT)?.let { newText ->
      _uiState.update { it.copy(textInput = newText) }
    }
    intent.getStringExtra(MainActivity.EXTRA_VOICE)?.let { voiceId ->
      pendingVoiceId = voiceId
      explicitVoiceId = voiceId
      if (!initialLaunch && allVoices.isNotEmpty()) {
        selectVoice(voiceId)
      }
    }

    val shouldRun =
      intent.getBooleanExtra(
        MainActivity.EXTRA_AUTORUN,
        intent.hasExtra(MainActivity.EXTRA_TEXT) ||
          intent.hasExtra(MainActivity.EXTRA_VOICE) ||
          intent.hasExtra(MainActivity.EXTRA_STREAM),
      )

    if (initialLaunch && bucketChanged && tts != null) {
      pendingAutorun = shouldRun
      loadEngine()
      return
    }

    if (tts != null && shouldRun) {
      synthesize(play = playAudio, streaming = streamMode)
    } else if (initialLaunch) {
      pendingAutorun = shouldRun
    }
  }

  fun updateText(newText: String) {
    _uiState.update { it.copy(textInput = newText, errorMessage = null) }
  }

  fun selectPreset(index: Int) {
    val preset = TextToSpeechHelper.PRESETS.getOrNull(index) ?: return
    _uiState.update { it.copy(selectedPresetIndex = index, textInput = preset.text, errorMessage = null) }
    if (allVoices.isNotEmpty()) {
      selectLanguage(preset.languageTag)
    }
  }

  fun selectLanguage(tag: String) {
    val matching = allVoices.filter { it.locale == tag }
    val wanted = pendingVoiceId ?: TextToSpeechHelper.DEFAULT_VOICE
    val chosenVoice =
      matching.firstOrNull { it.voiceId == wanted }?.voiceId
        ?: matching.firstOrNull { it.voiceId == _uiState.value.selectedVoiceId }?.voiceId
        ?: matching.firstOrNull()?.voiceId
        ?: wanted
    pendingVoiceId = null
    _uiState.update {
      it.copy(
        selectedLanguageTag = tag,
        voicesForLanguage = matching,
        selectedVoiceId = chosenVoice,
      )
    }
  }

  fun selectVoice(voiceId: String) {
    val voice = allVoices.firstOrNull { it.voiceId == voiceId }
    if (voice == null) {
      if (allVoices.isEmpty()) {
        pendingVoiceId = voiceId
        explicitVoiceId = voiceId
      } else {
        appendLog("Requested voice '$voiceId' not found; keeping current selection.")
      }
      return
    }
    explicitVoiceId = voiceId
    pendingVoiceId = voiceId
    selectLanguage(voice.locale)
  }

  fun selectModel(model: TtsModelOption) {
    if (_uiState.value.isBusy) return
    if (
      model == _uiState.value.selectedModel &&
        _uiState.value.isEngineReady &&
        !_uiState.value.isLoadingEngine
    ) {
      return
    }
    explicitVoiceId = null
    pendingVoiceId = null
    _uiState.update {
      it.copy(
        selectedModel = model,
        isEngineReady = false,
        statusBadge = "Loading…",
        errorMessage = null,
        pieces = emptyList(),
        sampleRateHz = null,
        rms = null,
        peak = null,
        nonFiniteCount = 0,
        wordTimestamps = emptyList(),
        synthesizedAudio = null,
      )
    }
    loadEngine()
  }

  fun setDelegateMode(mode: TtsDelegateMode) {
    if (_uiState.value.isBusy) return
    if (
      mode == _uiState.value.delegateMode &&
        _uiState.value.isEngineReady &&
        !_uiState.value.isLoadingEngine
    ) {
      return
    }
    _uiState.update {
      it.copy(
        delegateMode = mode,
        isEngineReady = false,
        statusBadge = "Loading…",
        errorMessage = null,
        pieces = emptyList(),
        sampleRateHz = null,
        rms = null,
        peak = null,
        nonFiniteCount = 0,
        wordTimestamps = emptyList(),
        synthesizedAudio = null,
      )
    }
    loadEngine()
  }

  fun setSpeakingRate(rate: Float) {
    val clamped = rate.coerceIn(0.75f, 1.4f)
    if (_uiState.value.isBusy || clamped == _uiState.value.speakingRate) return
    _uiState.update {
      it.copy(speakingRate = clamped, isEngineReady = false, statusBadge = "Loading…", errorMessage = null)
    }
    loadEngine()
  }

  fun fallbackToCpu() {
    setDelegateMode(TtsDelegateMode.CPU)
  }

  fun speakUnary() = synthesize(play = true, streaming = false)

  fun speakStreaming() = synthesize(play = true, streaming = true)

  fun replayLastAudio() {
    val samples = lastAudioSamples ?: return
    val rate = lastSampleRateHz
    if (!busy.compareAndSet(false, true)) return

    stopRequested = false
    _uiState.update { it.copy(isBusy = true, isStreaming = false, statusBadge = "Playing…", errorMessage = null) }
    worker.execute {
      try {
        TextToSpeechHelper.playBlocking(
          samples = samples,
          sampleRateHz = rate,
          onTrackCreated = { activeTrack = it },
          isStopped = { stopRequested },
        )
        _uiState.update { it.copy(statusBadge = if (stopRequested) "Stopped" else "Done") }
      } finally {
        busy.set(false)
        _uiState.update { it.copy(isBusy = false) }
      }
    }
  }

  fun stop() {
    stopRequested = true
    tts?.cancel()
    activeTrack?.let { track ->
      try {
        track.pause()
        track.flush()
      } catch (_: IllegalStateException) {}
    }
    _uiState.update { it.copy(statusBadge = "Stopping…") }
  }

  fun loadEngine() {
    val generation = loadGeneration.incrementAndGet()
    _uiState.update {
      it.copy(
        isEngineReady = false,
        isLoadingEngine = true,
        statusBadge = "Loading…",
        errorMessage = null,
      )
    }
    appendLog(
      "Device : ${Build.MANUFACTURER} ${Build.MODEL}, " +
        "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    )
    worker.execute {
      if (generation != loadGeneration.get()) return@execute
      val selectedModel = _uiState.value.selectedModel
      val delegateMode = _uiState.value.delegateMode
      val speakingRate = _uiState.value.speakingRate
      try {
        tts?.close()
        tts = null

        val modelFile = TextToSpeechHelper.resolveModelFile(app, selectedModel)
        check(modelFile != null && modelFile.isFile && modelFile.length() > 0) {
          "TTS model not found (${selectedModel.assetName})."
        }

        _uiState.update { it.copy(statusBadge = "Warming…") }
        val loaded =
          TextToSpeechHelper.createAndWarmUp(
            context = app,
            modelFile = modelFile,
            delegateMode = delegateMode,
            speakingRate = speakingRate,
            targetBucket = targetBucket,
            defaultVoiceId = null,
          )
        if (generation != loadGeneration.get()) {
          loaded.engine.close()
          return@execute
        }
        tts = loaded.engine
        allVoices = loaded.voices.sortedBy { it.voiceId }

        val languageTags =
          allVoices
            .map { it.locale }
            .distinct()
            .sortedWith(compareBy({ it != "en-US" }, { TextToSpeechHelper.languageDisplayName(it) }))
        val languageOptions =
          if (languageTags.isNotEmpty()) {
            languageTags.map { it to TextToSpeechHelper.languageDisplayName(it) }
          } else {
            listOf("en-US" to TextToSpeechHelper.languageDisplayName("en-US"))
          }

        val delegateLabel = if (loaded.usedDelegate == Delegate.GPU) "GPU" else "CPU"
        appendLog(
          "Engine created in ${loaded.loadMs} ms (${selectedModel.assetName}, $delegateLabel, " +
            "threads=$numThreads, bucket=$targetBucket, warmup=${loaded.warmupMs} ms); " +
            "${allVoices.size} voices"
        )

        _uiState.update {
          it.copy(
            isEngineReady = true,
            isLoadingEngine = false,
            activeDelegateLabel = delegateLabel,
            availableLanguages = languageOptions,
            voicesForLanguage = emptyList(),
            selectedVoiceId =
              if (allVoices.isEmpty()) "default" else TextToSpeechHelper.DEFAULT_VOICE,
            statusBadge = "Ready",
            errorMessage = null,
          )
        }

        val wanted = pendingVoiceId
        if (allVoices.isNotEmpty()) {
          if (wanted != null && allVoices.any { it.voiceId == wanted }) {
            selectVoice(wanted)
          } else {
            val preferredTag =
              TextToSpeechHelper.PRESETS.getOrNull(_uiState.value.selectedPresetIndex)
                ?.languageTag
                ?.takeIf { tag -> languageTags.contains(tag) }
                ?: languageTags.first()
            selectLanguage(preferredTag)
          }
        }

        if (pendingAutorun) {
          pendingAutorun = false
          synthesize(play = playAudio, streaming = streamMode)
        }
      } catch (t: Throwable) {
        if (generation == loadGeneration.get()) {
          reportError("Failed to load TTS engine", t)
        }
      } finally {
        if (generation == loadGeneration.get()) {
          _uiState.update { it.copy(isLoadingEngine = false) }
        }
      }
    }
  }

  private fun synthesize(play: Boolean, streaming: Boolean) {
    val engine = tts
    if (engine == null) {
      reportError("Engine is not ready yet", null)
      return
    }
    val text = _uiState.value.textInput.trim()
    if (text.isEmpty()) {
      reportError("Please enter some text to speak", null)
      return
    }
    val effectiveVoiceId: String? =
      explicitVoiceId?.takeIf { id -> allVoices.any { it.voiceId == id } }
        ?: TextToSpeechHelper.DEFAULT_VOICE.takeIf { id -> allVoices.any { it.voiceId == id } }
    val voiceLabel = effectiveVoiceId ?: _uiState.value.selectedVoiceId.ifBlank { "default" }
    val localeLabel =
      allVoices.firstOrNull { it.voiceId == effectiveVoiceId }?.locale
        ?: _uiState.value.selectedLanguageTag
    if (!busy.compareAndSet(false, true)) {
      _uiState.update { it.copy(statusBadge = "Busy") }
      return
    }

    stopRequested = false
    _uiState.update {
      it.copy(
        isBusy = true,
        isStreaming = streaming,
        statusBadge = if (streaming) "Streaming…" else "Synthesizing",
        pieces = emptyList(),
        sampleRateHz = null,
        rms = null,
        peak = null,
        nonFiniteCount = 0,
        wordTimestamps = emptyList(),
        synthesizedAudio = null,
        errorMessage = null,
      )
    }

    appendLog("---")
    appendLog("Text   : \"$text\"")
    appendLog("Voice  : $voiceLabel ($localeLabel) [mode=${if (streaming) "stream" else "unary"}]")

    worker.execute {
      try {
        val synthStart = SystemClock.elapsedRealtime()
        if (streaming) {
          val player = TtsAudioPlayer()
          var ttfaMs = -1L
          var playbackWaitMs = 0L
          var chunks = 0
          var sampleRateHz = 24_000
          var streamTrack: AudioTrack? = null
          val bufferedChunks = mutableListOf<FloatArray>()
          val pieceList = mutableListOf<SynthesizedPieceInfo>()
          val streamWordTimestamps = mutableListOf<WordTimestamp>()
          val synthEnd: Long
          try {
            runBlocking {
              engine.streamText(text, effectiveVoiceId).collect { chunk ->
                if (stopRequested) throw UserCancelledException()
                val now = SystemClock.elapsedRealtime()
                val elapsed = max(0L, now - synthStart - playbackWaitMs)
                if (ttfaMs < 0) ttfaMs = elapsed
                chunks += 1
                if (chunk.sampleRateHz > 0) {
                  sampleRateHz = chunk.sampleRateHz
                }
                pieceList.add(
                  SynthesizedPieceInfo(
                    chunkIndex = chunk.chunkIndex,
                    isFinal = chunk.isFinal,
                    sampleCount = chunk.samples.size,
                    sampleRateHz = chunk.sampleRateHz,
                  )
                )
                if (pieceList.size == 1) {
                  streamWordTimestamps.addAll(chunk.wordTimestamps)
                }
                player.enqueue(chunk)
                if (chunk.samples.isNotEmpty()) {
                  bufferedChunks.add(chunk.samples)
                }
                appendLog(
                  "Chunk #$chunks: +${chunk.samples.size} samples @ ${chunk.sampleRateHz} Hz (t=$elapsed ms)"
                )
                if (play && !stopRequested && chunk.samples.isNotEmpty()) {
                  val writeStart = SystemClock.elapsedRealtime()
                  val track =
                    streamTrack
                      ?: TextToSpeechHelper.createStreamingTrack(chunk.sampleRateHz)?.also {
                        streamTrack = it
                        activeTrack = it
                        it.play()
                      }
                  if (track != null) {
                    track.write(chunk.samples, 0, chunk.samples.size, AudioTrack.WRITE_BLOCKING)
                  }
                  playbackWaitMs += SystemClock.elapsedRealtime() - writeStart
                }
              }
            }
            synthEnd = SystemClock.elapsedRealtime()
            streamTrack?.let { track ->
              if (play && !stopRequested && player.totalSamples > 0) {
                val drainDeadline = SystemClock.elapsedRealtime() + 750L
                while (
                  !stopRequested &&
                    SystemClock.elapsedRealtime() < drainDeadline &&
                    track.playbackHeadPosition < player.totalSamples
                ) {
                  SystemClock.sleep(25)
                }
              }
            }
          } finally {
            activeTrack = null
            streamTrack?.let { track ->
              try {
                track.stop()
              } catch (_: IllegalStateException) {}
              track.release()
            }
          }
          val synthMs = max(0L, synthEnd - synthStart - playbackWaitMs)
          if (stopRequested) throw UserCancelledException()
          val totalSamples = player.totalSamples
          check(totalSamples > 0) { "The engine returned no audio samples." }

          val combined = FloatArray(totalSamples)
          var cursor = 0
          bufferedChunks.forEach { part ->
            part.copyInto(combined, destinationOffset = cursor)
            cursor += part.size
          }
          lastAudioSamples = combined
          lastSampleRateHz = sampleRateHz
          val stats = computeSignalStats(combined)

          val durationSec = totalSamples.toFloat() / sampleRateHz.toFloat()
          val audioMs = (durationSec * 1000f).toLong()
          val rtf = if (audioMs > 0) synthMs.toDouble() / audioMs.toDouble() else Double.NaN
          val wavFile = File(app.getExternalFilesDir(null) ?: app.filesDir, TextToSpeechHelper.OUTPUT_WAV_NAME)
          player.writeWavFile(wavFile)

          val playState = if (!play) "skipped" else if (stopRequested) "stopped" else "ok"
          appendLog(
            "${MainActivity.RESULT_MARKER} status=OK mode=stream ttfa_ms=$ttfaMs chunks=$chunks " +
              "synth_ms=$synthMs audio_ms=$audioMs rtf=${"%.4f".format(Locale.US, rtf)} " +
              "samples=$totalSamples sample_rate_hz=$sampleRateHz voice=$voiceLabel " +
              "locale=$localeLabel play=$playState wav=${wavFile.absolutePath}"
          )
          _uiState.update {
            it.copy(
              statusBadge = "Done",
              durationSeconds = durationSec,
              latencyMs = synthMs,
              rtf = rtf,
              ttfaMs = ttfaMs,
              chunkCount = chunks,
              pieces = pieceList.toList(),
              sampleRateHz = sampleRateHz,
              rms = stats.rms,
              peak = stats.peak,
              nonFiniteCount = stats.nonFiniteCount,
              wordTimestamps = streamWordTimestamps.toList(),
              synthesizedAudio = SynthesizedAudioHolder(combined),
              hasReplayableAudio = true,
              lastWavPath = wavFile.name,
            )
          }
        } else {
          val audio = engine.synthesizeText(text, effectiveVoiceId)
          val synthMs = (SystemClock.elapsedRealtime() - synthStart).coerceAtLeast(1L)
          if (stopRequested) throw UserCancelledException()
          check(audio.samples.isNotEmpty()) { "The engine returned no audio samples." }

          lastAudioSamples = audio.samples
          lastSampleRateHz = audio.sampleRateHz
          val stats = computeSignalStats(audio.samples)
          val unaryPieces =
            listOf(
              SynthesizedPieceInfo(
                chunkIndex = 0,
                isFinal = true,
                sampleCount = audio.samples.size,
                sampleRateHz = audio.sampleRateHz,
              )
            )

          val audioMs = (audio.durationSeconds * 1000f).toLong()
          val rtf = if (audioMs > 0) synthMs.toDouble() / audioMs.toDouble() else Double.NaN
          val wavFile = File(app.getExternalFilesDir(null) ?: app.filesDir, TextToSpeechHelper.OUTPUT_WAV_NAME)
          TtsAudioPlayer().apply { enqueue(audio) }.writeWavFile(wavFile)

          _uiState.update {
            it.copy(
              statusBadge = if (play) "Playing…" else "Done",
              durationSeconds = audio.durationSeconds,
              latencyMs = synthMs,
              rtf = rtf,
              ttfaMs = null,
              chunkCount = null,
              pieces = unaryPieces,
              sampleRateHz = audio.sampleRateHz,
              rms = stats.rms,
              peak = stats.peak,
              nonFiniteCount = stats.nonFiniteCount,
              wordTimestamps = audio.wordTimestamps,
              synthesizedAudio = SynthesizedAudioHolder(audio.samples),
              hasReplayableAudio = true,
              lastWavPath = wavFile.name,
            )
          }

          val playState =
            if (!play) {
              "skipped"
            } else {
              val played =
                TextToSpeechHelper.playBlocking(
                  samples = audio.samples,
                  sampleRateHz = audio.sampleRateHz,
                  onTrackCreated = { activeTrack = it },
                  isStopped = { stopRequested },
                )
              when {
                stopRequested -> "stopped"
                !played -> "skipped"
                else -> "ok"
              }
            }

          appendLog(
            "${MainActivity.RESULT_MARKER} status=OK synth_ms=$synthMs audio_ms=$audioMs " +
              "rtf=${"%.4f".format(Locale.US, rtf)} samples=${audio.samples.size} " +
              "sample_rate_hz=${audio.sampleRateHz} voice=$voiceLabel " +
              "locale=$localeLabel play=$playState wav=${wavFile.absolutePath}"
          )
          _uiState.update {
            it.copy(statusBadge = if (stopRequested) "Stopped" else "Done")
          }
        }
      } catch (_: UserCancelledException) {
        appendLog("Cancelled.")
        _uiState.update { it.copy(statusBadge = "Stopped") }
      } catch (_: CancellationException) {
        appendLog("Cancelled.")
        _uiState.update { it.copy(statusBadge = "Stopped") }
      } catch (t: Throwable) {
        reportError("Synthesis failed", t)
      } finally {
        busy.set(false)
        _uiState.update { it.copy(isBusy = false, isStreaming = false) }
      }
    }
  }

  private data class SignalStats(val rms: Double, val peak: Double, val nonFiniteCount: Int)

  private fun computeSignalStats(samples: FloatArray): SignalStats {
    if (samples.isEmpty()) return SignalStats(rms = 0.0, peak = 0.0, nonFiniteCount = 0)
    var sumSq = 0.0
    var peak = 0.0
    var nonFinite = 0
    for (sample in samples) {
      if (sample.isFinite()) {
        val value = sample.toDouble()
        sumSq += value * value
        val absValue = abs(value)
        if (absValue > peak) peak = absValue
      } else {
        nonFinite += 1
      }
    }
    return SignalStats(
      rms = sqrt(sumSq / samples.size.toDouble()),
      peak = peak,
      nonFiniteCount = nonFinite,
    )
  }

  private fun appendLog(line: String) {
    Log.i(MainActivity.TAG, line)
    _uiState.update { current ->
      val updated = (current.logLines + line).takeLast(40)
      current.copy(logLines = updated)
    }
  }

  private fun reportError(prefix: String, t: Throwable?) {
    val detail = t?.let { ": ${it.message ?: it.javaClass.simpleName}" } ?: ""
    val message = prefix + detail
    if (t != null) Log.e(MainActivity.TAG, message, t)
    appendLog("${MainActivity.RESULT_MARKER} status=ERROR message=$message")
    _uiState.update {
      it.copy(statusBadge = "Error", errorMessage = message)
    }
  }

  override fun onCleared() {
    stopRequested = true
    tts?.cancel()
    worker.execute {
      tts?.close()
      tts = null
    }
    worker.shutdown()
    super.onCleared()
  }

  private class UserCancelledException : RuntimeException("Synthesis cancelled.")
}
