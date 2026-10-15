// Copyright 2026 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

@file:Suppress("PackageName", "KotlinArray")

package com.google.mediapipe.tasks.audio.texttospeech.examples

import com.google.mediapipe.tasks.audio.texttospeech.AudioChunk
import com.google.mediapipe.tasks.audio.texttospeech.AudioResult
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

/**
 * Sample-layer PCM audio playback & WAV export utility.
 *
 * The core [com.google.mediapipe.tasks.audio.texttospeech.TextToSpeech] SDK is strictly headless
 * ([AudioResult] float32 PCM out) so it can run in unit tests, background services, and custom
 * Acoustic Echo Cancellation (AEC) pipelines. On Android devices, pass an [AudioTrackSink] backed
 * by `android.media.AudioTrack.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)` to play
 * synthesized audio through the hardware speaker and flush immediately on [stop].
 */
class TtsAudioPlayer(private val audioTrackSink: AudioTrackSink? = null) {

  /** Pluggable sink for Android `AudioTrack` or custom audio output pipelines. */
  interface AudioTrackSink {
    fun onWritePcm(samples: FloatArray, sampleRateHz: Int)

    fun onStopAndFlush()
  }

  private val lock = Any()
  private val pcmBytes = ByteArrayOutputStream()
  private var sampleCount: Int = 0
  private var sampleRateHz: Int = 24000
  private var chunksEnqueued: Int = 0

  /** Enqueues a synthesized [AudioResult] for playback and WAV recording. */
  fun enqueue(result: AudioResult) {
    synchronized(lock) {
      if (result.sampleRateHz > 0) {
        sampleRateHz = result.sampleRateHz
      }
      for (sample in result.samples) {
        val clamped = max(-1.0f, min(1.0f, sample))
        val pcm16 = (clamped * 32767.0f).toInt()
        pcmBytes.write(pcm16 and 0xFF)
        pcmBytes.write((pcm16 ushr 8) and 0xFF)
      }
      sampleCount += result.samples.size
      chunksEnqueued += 1
    }
    if (result.samples.isNotEmpty()) {
      audioTrackSink?.onWritePcm(result.samples, result.sampleRateHz)
    }
  }

  /** Enqueues a streamed [AudioChunk] for playback and WAV recording. */
  fun enqueue(chunk: AudioChunk) {
    enqueue(AudioResult(samples = chunk.samples, sampleRateHz = chunk.sampleRateHz))
  }

  /** Immediately halts playback and flushes buffered audio (invoked upon user barge-in). */
  fun stop() {
    audioTrackSink?.onStopAndFlush()
    synchronized(lock) {
      pcmBytes.reset()
      sampleCount = 0
      chunksEnqueued = 0
    }
  }

  /** Total mono PCM samples currently buffered. */
  val totalSamples: Int
    get() = synchronized(lock) { sampleCount }

  /** Number of audio results enqueued since creation or last [stop]. */
  val chunkCount: Int
    get() = synchronized(lock) { chunksEnqueued }

  /** Writes the accumulated mono PCM samples to a 16-bit little-endian RIFF `.wav` file. */
  fun writeWavFile(outputFile: File) {
    val pcmData: ByteArray
    val rate: Int
    synchronized(lock) {
      pcmData = pcmBytes.toByteArray()
      rate = if (sampleRateHz > 0) sampleRateHz else 24000
    }

    val bytesPerSample = 2
    val numChannels = 1
    val dataSize = pcmData.size
    val riffSize = 36 + dataSize
    val byteRate = rate * numChannels * bytesPerSample
    val blockAlign = (numChannels * bytesPerSample).toShort()
    val bitsPerSample: Short = 16

    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray(Charsets.US_ASCII))
    header.putInt(riffSize)
    header.put("WAVE".toByteArray(Charsets.US_ASCII))
    header.put("fmt ".toByteArray(Charsets.US_ASCII))
    header.putInt(16) // PCM fmt chunk size
    header.putShort(1) // PCM format = 1
    header.putShort(numChannels.toShort())
    header.putInt(rate)
    header.putInt(byteRate)
    header.putShort(blockAlign)
    header.putShort(bitsPerSample)
    header.put("data".toByteArray(Charsets.US_ASCII))
    header.putInt(dataSize)

    outputFile.parentFile?.mkdirs()
    FileOutputStream(outputFile).use { fos ->
      fos.write(header.array())
      fos.write(pcmData)
    }
  }
}
