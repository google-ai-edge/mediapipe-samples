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

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoded mono PCM WAV audio buffer with its sample rate. */
data class DecodedWavAudio(val samples: FloatArray, val sampleRateHz: Int) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DecodedWavAudio) return false
    return sampleRateHz == other.sampleRateHz && samples.contentEquals(other.samples)
  }

  override fun hashCode(): Int {
    var result = samples.contentHashCode()
    result = 31 * result + sampleRateHz
    return result
  }
}

/** Parses standard RIFF/WAVE 16-bit PCM or 32-bit IEEE float audio streams into normalized floats. */
object WavAudioLoader {
  private const val PCM_FORMAT_INT = 1
  private const val PCM_FORMAT_IEEE_FLOAT = 3

  fun decode(inputStream: InputStream): DecodedWavAudio {
    val bytes = inputStream.use { it.readBytes() }
    require(bytes.size >= 44) { "WAV stream too short (${bytes.size} bytes)." }

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    val riff = ByteArray(4).also { buf.get(it) }.decodeToString()
    buf.int // chunk size
    val wave = ByteArray(4).also { buf.get(it) }.decodeToString()
    require(riff == "RIFF" && wave == "WAVE") { "Invalid WAV header: $riff/$wave" }

    var audioFormat = 0
    var numChannels = 1
    var sampleRateHz = 16_000
    var bitsPerSample = 16
    var dataOffset = -1
    var dataSize = 0

    while (buf.remaining() >= 8) {
      val chunkId = ByteArray(4).also { buf.get(it) }.decodeToString()
      val chunkSize = buf.int
      require(chunkSize >= 0 && chunkSize <= buf.remaining()) {
        "Invalid WAV chunk '$chunkId' size: $chunkSize"
      }
      val chunkStart = buf.position()
      when (chunkId) {
        "fmt " -> {
          require(chunkSize >= 16) { "WAV fmt chunk too small: $chunkSize" }
          audioFormat = buf.short.toInt() and 0xFFFF
          numChannels = (buf.short.toInt() and 0xFFFF).coerceAtLeast(1)
          sampleRateHz = buf.int
          buf.int // byte rate
          buf.short // block align
          bitsPerSample = buf.short.toInt() and 0xFFFF
        }
        "data" -> {
          dataOffset = chunkStart
          dataSize = chunkSize
          break
        }
      }
      buf.position(chunkStart + chunkSize + (chunkSize and 1))
    }

    require(dataOffset >= 0) { "WAV stream missing 'data' chunk." }
    require(
      (audioFormat == PCM_FORMAT_INT && bitsPerSample == 16) ||
        (audioFormat == PCM_FORMAT_IEEE_FLOAT && bitsPerSample == 32)
    ) {
      "Unsupported WAV format=$audioFormat bitsPerSample=$bitsPerSample"
    }

    val bytesPerSample = bitsPerSample / 8
    val totalFrames = dataSize / (bytesPerSample * numChannels)
    val samples = FloatArray(totalFrames)
    buf.position(dataOffset)

    for (frame in 0 until totalFrames) {
      var channelSum = 0.0f
      for (ch in 0 until numChannels) {
        val sampleValue =
          if (audioFormat == PCM_FORMAT_IEEE_FLOAT) {
            buf.float
          } else {
            buf.short.toFloat() / 32768.0f
          }
        channelSum += sampleValue
      }
      samples[frame] = (channelSum / numChannels).coerceIn(-1.0f, 1.0f)
    }

    return DecodedWavAudio(samples = samples, sampleRateHz = sampleRateHz)
  }
}
