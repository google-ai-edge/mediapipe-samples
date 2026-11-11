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

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.tasks.audio.texttospeech.AudioChunk
import com.google.mediapipe.tasks.audio.texttospeech.AudioResult
import com.google.mediapipe.tasks.audio.texttospeech.SynthesisOptions
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech
import com.google.mediapipe.tasks.audio.texttospeech.TextToSpeechOptions
import com.google.mediapipe.tasks.audio.texttospeech.VoiceInfo
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.flow.Flow

/** Delegate preference for Kokoro TTS initialization. */
enum class TtsDelegateMode(val label: String) {
  AUTO("Auto"),
  CPU("CPU"),
  GPU("GPU"),
}

/** Sample utterance preset displayed as quick chips in the UI. */
data class TtsPreset(val chipLabel: String, val title: String, val languageTag: String, val text: String)

/** Initialization result holding the loaded [TextToSpeech] handle, voices, and resolved delegate. */
data class LoadedTtsEngine(
  val engine: TextToSpeech,
  val voices: List<VoiceInfo>,
  val usedDelegate: Delegate,
  val loadMs: Long,
  val warmupMs: Long,
  val modelFile: File,
)

/**
 * Helper encapsulating model resolution, Kokoro-82M [TextToSpeech] lifecycle, and Android
 * [AudioTrack] speaker playback.
 */
object TextToSpeechHelper {
  private const val TAG = "TextToSpeechHelper"

  const val LEGACY_MODEL_FILE_NAME = "kokoro.litertlm"
  const val BUNDLED_MODEL_FILE_NAME = "kokoro_int8_fp16.litertlm"
  const val OUTPUT_WAV_NAME = "kokoro_android_demo.wav"
  const val DEFAULT_VOICE = "af_heart"
  const val DEFAULT_TARGET_BUCKET = 64

  val CANDIDATE_MODEL_NAMES = listOf(LEGACY_MODEL_FILE_NAME, BUNDLED_MODEL_FILE_NAME)

  val PRESETS =
    listOf(
      TtsPreset(
        chipLabel = "Greeting",
        title = "1 · Greeting",
        languageTag = "en-US",
        text =
          "Hello from Kokoro 82M running in-process on Android via the MediaPipe TTS Kotlin SDK!",
      ),
      TtsPreset(
        chipLabel = "Numbers",
        title = "2 · Numbers & dates",
        languageTag = "en-US",
        text =
          "Your order of 3 items totals \$42.50 and will arrive on Friday, March 14th, " +
            "between 9 and 11 a.m.",
      ),
      TtsPreset(
        chipLabel = "Pangram",
        title = "3 · Pangram",
        languageTag = "en-US",
        text =
          "The quick brown fox jumps over the lazy dog, while the five boxing wizards " +
            "jump quickly.",
      ),
      TtsPreset(
        chipLabel = "Questions",
        title = "4 · Questions & emphasis",
        languageTag = "en-US",
        text =
          "Did you know this voice is generated entirely on your phone? No internet, " +
            "no cloud — just on-device AI!",
      ),
      TtsPreset(
        chipLabel = "Paragraph",
        title = "5 · Longer paragraph",
        languageTag = "en-US",
        text =
          "Text-to-speech converts written words into natural-sounding audio. Kokoro is a " +
            "compact 82-million-parameter model that runs locally through LiteRT-LM, so your " +
            "text never leaves the device and synthesis works even in airplane mode.",
      ),
    )

  /** Locates the Kokoro `.litertlm` model across external storage, `/data/local/tmp`, filesDir, or APK assets. */
  fun findModelFile(context: Context): File? {
    val searchDirs =
      listOfNotNull(
        context.getExternalFilesDir(null),
        File("/data/local/tmp"),
        context.filesDir,
      )
    for (dir in searchDirs) {
      for (name in CANDIDATE_MODEL_NAMES) {
        val candidate = File(dir, name)
        if (candidate.isFile && candidate.length() > 0L) {
          return candidate
        }
      }
    }

    val cacheTargetDir = context.filesDir
    for (assetName in CANDIDATE_MODEL_NAMES) {
      val staged = File(cacheTargetDir, assetName)
      try {
        context.assets.open(assetName).use { input ->
          staged.outputStream().use { output -> input.copyTo(output) }
        }
        if (staged.isFile && staged.length() > 0L) {
          return staged
        }
      } catch (_: Exception) {
        // Try next bundled candidate name.
      }
    }
    return null
  }

  /**
   * Initializes [TextToSpeech], validates available voices, and runs a quick warm-up pass.
   * When [delegateMode] is [TtsDelegateMode.AUTO], attempts GPU first and falls back to CPU if
   * unavailable.
   */
  fun createAndWarmUp(
    context: Context,
    modelFile: File,
    delegateMode: TtsDelegateMode = TtsDelegateMode.AUTO,
    speakingRate: Float = 1.0f,
    targetBucket: Int = DEFAULT_TARGET_BUCKET,
    defaultVoiceId: String = DEFAULT_VOICE,
    runWarmup: Boolean = true,
  ): LoadedTtsEngine {
    val buildOptions: (Delegate) -> TextToSpeechOptions = { delegate ->
      TextToSpeechOptions(
        baseOptions =
          BaseOptions.builder()
            .setModelAssetPath(modelFile.absolutePath)
            .setDelegate(delegate)
            .build(),
        defaultVoiceId = defaultVoiceId,
        speakingRate = speakingRate,
        targetBucket = targetBucket,
        cacheDirectory = context.cacheDir.absolutePath,
      )
    }

    val loadStart = SystemClock.elapsedRealtime()
    val (engine, usedDelegate) =
      when (delegateMode) {
        TtsDelegateMode.CPU -> TextToSpeech.create(buildOptions(Delegate.CPU)) to Delegate.CPU
        TtsDelegateMode.GPU -> TextToSpeech.create(buildOptions(Delegate.GPU)) to Delegate.GPU
        TtsDelegateMode.AUTO ->
          try {
            TextToSpeech.create(buildOptions(Delegate.GPU)) to Delegate.GPU
          } catch (e: Exception) {
            Log.w(TAG, "GPU delegate unavailable ($e); falling back to CPU.")
            TextToSpeech.create(buildOptions(Delegate.CPU)) to Delegate.CPU
          }
      }
    val loadMs = (SystemClock.elapsedRealtime() - loadStart).coerceAtLeast(1L)

    val voices = engine.listVoices()
    check(voices.isNotEmpty()) { "Kokoro model reported no voices." }

    val warmupStart = SystemClock.elapsedRealtime()
    if (runWarmup) {
      val unused = engine.synthesize("Warm up.")
    }
    val warmupMs = if (runWarmup) (SystemClock.elapsedRealtime() - warmupStart).coerceAtLeast(1L) else 0L

    return LoadedTtsEngine(
      engine = engine,
      voices = voices,
      usedDelegate = usedDelegate,
      loadMs = loadMs,
      warmupMs = warmupMs,
      modelFile = modelFile,
    )
  }

  fun languageDisplayName(tag: String): String {
    val name = Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH)
    return if (name.isBlank() || name == tag) tag else "$name [$tag]"
  }

  fun voiceDisplayName(v: VoiceInfo): String {
    val stem =
      v.voiceId.substringAfter('_', v.voiceId).replaceFirstChar { it.titlecase(Locale.ENGLISH) }
    val gender =
      when (v.voiceId.getOrNull(1)) {
        'f' -> "female"
        'm' -> "male"
        else -> null
      }
    return if (gender != null) "$stem ($gender) · ${v.voiceId}" else "$stem · ${v.voiceId}"
  }

  fun createStreamingTrack(sampleRateHz: Int): AudioTrack? {
    val minBufferBytes =
      AudioTrack.getMinBufferSize(
        sampleRateHz,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT,
      )
    if (minBufferBytes <= 0) return null
    val bufferBytes = max(minBufferBytes, sampleRateHz * Float.SIZE_BYTES / 2)
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
            .setSampleRate(sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(bufferBytes)
        .setTransferMode(AudioTrack.MODE_STREAM)
        .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
      track.release()
      return null
    }
    return track
  }

  fun playBlocking(
    samples: FloatArray,
    sampleRateHz: Int,
    onTrackCreated: (AudioTrack?) -> Unit,
    isStopped: () -> Boolean,
  ): Boolean {
    if (samples.isEmpty()) return false
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
            .setSampleRate(sampleRateHz)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        )
        .setBufferSizeInBytes(samples.size * Float.SIZE_BYTES)
        .setTransferMode(AudioTrack.MODE_STATIC)
        .build()
    if (track.state != AudioTrack.STATE_INITIALIZED) {
      Log.w(TAG, "AudioTrack unavailable on this device (state=${track.state}); skipping speaker playback.")
      track.release()
      return false
    }
    onTrackCreated(track)
    try {
      val written = track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
      check(written == samples.size) {
        "AudioTrack.write wrote $written of ${samples.size} samples"
      }
      track.play()
      val deadline = SystemClock.elapsedRealtime() + samples.size * 1000L / sampleRateHz + 100L
      while (!isStopped() && SystemClock.elapsedRealtime() < deadline) {
        SystemClock.sleep(50)
      }
      return !isStopped()
    } finally {
      onTrackCreated(null)
      try {
        track.stop()
      } catch (_: IllegalStateException) {}
      track.release()
    }
  }
}

/** Synthesize helper extensions on [TextToSpeech] preserving clean options defaults. */
fun TextToSpeech.synthesizeText(text: String, voiceId: String): AudioResult =
  synthesize(text, SynthesisOptions(voiceId = voiceId))

fun TextToSpeech.streamText(text: String, voiceId: String): Flow<AudioChunk> =
  synthesizeStream(text, SynthesisOptions(voiceId = voiceId))
