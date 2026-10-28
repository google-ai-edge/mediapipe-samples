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

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private object AudioSignalConstants {
  const val RMS_GAIN_MULTIPLIER = 5.0f
  const val MAX_NORMALIZED_RMS_LEVEL = 1.0f
}

/**
 * Computes the normalized root-mean-square (RMS) audio level in `[0.0f, 1.0f]` for the UI level
 * meter.
 */
internal fun FloatArray.calculateRmsLevel(): Float {
  if (isEmpty()) return 0.0f
  var sumOfSquares = 0.0f
  for (sample in this) {
    sumOfSquares += sample * sample
  }
  val rawRms = sqrt(sumOfSquares / size.toFloat())
  return min(
    AudioSignalConstants.MAX_NORMALIZED_RMS_LEVEL,
    rawRms * AudioSignalConstants.RMS_GAIN_MULTIPLIER,
  )
}

/** Cohesive chunk of mono 16 kHz float32 PCM audio samples and its normalized RMS level. */
@Suppress("KotlinArray") // AudioTranscriber.transcribe() takes PCM samples as a FloatArray.
data class AudioChunk(val samples: FloatArray, val rmsLevel: Float = samples.calculateRmsLevel()) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AudioChunk) return false
    return samples.contentEquals(other.samples) && rmsLevel.toBits() == other.rmsLevel.toBits()
  }

  override fun hashCode(): Int {
    var result = samples.contentHashCode()
    result = 31 * result + rmsLevel.hashCode()
    return result
  }
}

/** Abstraction for starting and stopping real-time microphone audio capture. */
interface AudioCapturing {
  /** Starts streaming live microphone audio chunks to [onChunkReceived]. */
  fun startCapture(onChunkReceived: (AudioChunk) -> Unit)

  /** Stops active microphone audio capture and releases underlying hardware resources. */
  fun stopCapture()
}

/** Real-time microphone audio capture engine backed by Android's [AudioRecord] API. */
class AudioCaptureEngine(
  private val sampleRateHz: Int = DEFAULT_SAMPLE_RATE_HZ,
  ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AudioCapturing {

  private val scope = CoroutineScope(ioDispatcher)
  private var captureJob: Job? = null

  @Volatile private var isCapturing = false

  @SuppressLint("MissingPermission")
  override fun startCapture(onChunkReceived: (AudioChunk) -> Unit) {
    if (isCapturing) return

    val channelConfig = AudioFormat.CHANNEL_IN_MONO
    val audioFormat = AudioFormat.ENCODING_PCM_FLOAT
    val minBufferSize = AudioRecord.getMinBufferSize(sampleRateHz, channelConfig, audioFormat)
    if (minBufferSize == AudioRecord.ERROR || minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
      throw IllegalStateException(
        "AudioRecord.getMinBufferSize failed at $sampleRateHz Hz (error code: $minBufferSize)."
      )
    }
    val readChunkSamples = sampleRateHz / CHUNKS_PER_SECOND
    val readChunkBytes = readChunkSamples * Float.SIZE_BYTES
    val bufferSizeInBytes =
      max(minBufferSize * BUFFER_SIZE_MULTIPLIER, readChunkBytes * BUFFER_SIZE_MULTIPLIER)

    val record =
      AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRateHz,
        channelConfig,
        audioFormat,
        bufferSizeInBytes,
      )

    if (record.state != AudioRecord.STATE_INITIALIZED) {
      record.release()
      throw IllegalStateException("Failed to initialize AudioRecord at $sampleRateHz Hz.")
    }

    record.startRecording()
    if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
      record.release()
      throw IllegalStateException("AudioRecord failed to enter recording state.")
    }

    isCapturing = true
    val readBuffer = FloatArray(readChunkSamples)

    captureJob = scope.launch {
      try {
        while (isActive && isCapturing) {
          val readCount = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
          when {
            readCount > 0 -> {
              onChunkReceived(AudioChunk(samples = readBuffer.copyOf(readCount)))
            }
            readCount == 0 -> yield()
            readCount < 0 -> {
              Log.w(TAG, "AudioRecord.read returned error code: $readCount")
              break
            }
          }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Unexpected error while reading microphone audio", e)
      } finally {
        if (captureJob === coroutineContext[Job]) {
          isCapturing = false
        }
        try {
          if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            record.stop()
          }
        } catch (e: IllegalStateException) {
          Log.w(TAG, "Failed to stop AudioRecord cleanly", e)
        }
        record.release()
      }
    }
  }

  override fun stopCapture() {
    isCapturing = false
    captureJob?.cancel()
    captureJob = null
  }

  companion object {
    const val DEFAULT_SAMPLE_RATE_HZ = 16_000
    private const val TAG = "AudioCaptureEngine"
    private const val CHUNKS_PER_SECOND = 10
    private const val BUFFER_SIZE_MULTIPLIER = 2
  }
}
