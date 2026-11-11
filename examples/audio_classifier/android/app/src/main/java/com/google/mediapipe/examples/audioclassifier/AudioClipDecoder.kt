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

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.google.mediapipe.tasks.components.containers.AudioData
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Decoded mono float32 PCM clip ready for playback or classification. */
data class DecodedAudio(
    val samples: FloatArray,
    val sampleRateHz: Int,
) {
    val durationMs: Long
        get() =
            if (sampleRateHz <= 0) 0L else ((samples.size.toLong() * 1000L) / sampleRateHz)

    fun toAudioData(): AudioData {
        val format =
            AudioData.AudioDataFormat.builder()
                .setNumOfChannels(1)
                .setSampleRate(sampleRateHz.toFloat())
                .build()
        return AudioData.create(format, samples.size).apply { load(samples) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedAudio) return false
        return sampleRateHz == other.sampleRateHz && samples.contentEquals(other.samples)
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRateHz
        return result
    }
}

/** Decodes WAV and compressed Android audio files into mono float32 PCM buffers. */
object AudioClipDecoder {

    private const val CODEC_TIMEOUT_US = 10_000L

    fun decodeAssetWav(context: Context, assetName: String): DecodedAudio =
        context.assets.open(assetName).use { decodeWavStream(it) }

    fun decodeWavStream(inputStream: InputStream): DecodedAudio =
        decodeWavBytes(inputStream.readBytes())

    fun decodeWavBytes(bytes: ByteArray): DecodedAudio {
        require(bytes.size >= 44) { "Invalid WAV stream: header too short (${bytes.size} bytes)." }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(readFourCc(bytes, 0) == "RIFF" && readFourCc(bytes, 8) == "WAVE") {
            "Stream is not a valid RIFF/WAVE file."
        }

        var channels = 0
        var sampleRate = 0
        var bitsPerSample = 0
        var audioFormat = 0
        var pcmOffset = -1
        var pcmLength = 0

        var offset = 12
        while (offset + 8 <= bytes.size) {
            val chunkId = readFourCc(bytes, offset)
            val chunkSize = buf.getInt(offset + 4)
            val payloadStart = offset + 8
            if (chunkSize < 0 || payloadStart + chunkSize > bytes.size) break

            when (chunkId) {
                "fmt " -> {
                    require(chunkSize >= 16) { "Invalid WAV fmt chunk size: $chunkSize" }
                    audioFormat = buf.getShort(payloadStart).toInt() and 0xFFFF
                    channels = buf.getShort(payloadStart + 2).toInt() and 0xFFFF
                    sampleRate = buf.getInt(payloadStart + 4)
                    bitsPerSample = buf.getShort(payloadStart + 14).toInt() and 0xFFFF
                }
                "data" -> {
                    pcmOffset = payloadStart
                    pcmLength = chunkSize
                    break
                }
            }
            val padded = if (chunkSize % 2 == 1) chunkSize + 1 else chunkSize
            offset = payloadStart + padded
        }

        require(channels > 0 && sampleRate > 0 && pcmOffset >= 0) {
            "WAV stream missing fmt or data chunk."
        }
        require(audioFormat == 1 || audioFormat == 3) {
            "Unsupported WAV audio format code $audioFormat (expected PCM or IEEE float)."
        }

        val samples =
            when {
                audioFormat == 1 && bitsPerSample == 16 -> {
                    val bytesPerFrame = channels * 2
                    val frameCount = pcmLength / bytesPerFrame
                    FloatArray(frameCount) { frame ->
                        var sum = 0f
                        val base = pcmOffset + frame * bytesPerFrame
                        for (c in 0 until channels) {
                            sum += buf.getShort(base + c * 2).toFloat() / 32768f
                        }
                        (sum / channels).coerceIn(-1f, 1f)
                    }
                }
                audioFormat == 3 && bitsPerSample == 32 -> {
                    val bytesPerFrame = channels * 4
                    val frameCount = pcmLength / bytesPerFrame
                    FloatArray(frameCount) { frame ->
                        var sum = 0f
                        val base = pcmOffset + frame * bytesPerFrame
                        for (c in 0 until channels) {
                            sum += buf.getFloat(base + c * 4)
                        }
                        (sum / channels).coerceIn(-1f, 1f)
                    }
                }
                else ->
                    throw IllegalArgumentException(
                        "Unsupported WAV encoding: format=$audioFormat, bitsPerSample=$bitsPerSample"
                    )
            }

        return DecodedAudio(samples = samples, sampleRateHz = sampleRate)
    }

    fun decodeUri(context: Context, uri: Uri): DecodedAudio {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (
                bytes.size >= 12 &&
                    readFourCc(bytes, 0) == "RIFF" &&
                    readFourCc(bytes, 8) == "WAVE"
            ) {
                return decodeWavBytes(bytes)
            }
        }
        return decodeCompressedUri(context, uri)
    }

    fun generateSineTone(
        freqHz: Float = 440f,
        durationMs: Int = 2_000,
        sampleRateHz: Int = 16_000,
        amplitude: Float = 0.45f,
    ): DecodedAudio {
        val sampleCount = ((durationMs / 1000f) * sampleRateHz).roundToInt().coerceAtLeast(1)
        val angularFreq = 2.0 * PI * freqHz / sampleRateHz
        val samples =
            FloatArray(sampleCount) { i ->
                (amplitude * sin(angularFreq * i)).toFloat()
            }
        return DecodedAudio(samples = samples, sampleRateHz = sampleRateHz)
    }

    private fun decodeCompressedUri(context: Context, uri: Uri): DecodedAudio {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        var selectedTrack = -1
        var trackFormat: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val fmt = extractor.getTrackFormat(i)
            val mime = fmt.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) {
                selectedTrack = i
                trackFormat = fmt
                break
            }
        }
        require(selectedTrack >= 0 && trackFormat != null) {
            "Selected file contains no audio track."
        }

        extractor.selectTrack(selectedTrack)
        val mime = trackFormat.getString(MediaFormat.KEY_MIME)!!
        var sampleRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channelCount = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

        val codec = MediaCodec.createDecoderByType(mime)
        val pcmOut = ByteArrayOutputStream()
        try {
            codec.configure(trackFormat, null, null, 0)
            codec.start()
            val bufferInfo = MediaCodec.BufferInfo()
            var sawInputEos = false
            var sawOutputEos = false

            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inIdx = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(
                                inIdx,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0,
                            )
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)
                when {
                    outIdx >= 0 -> {
                        val outBuf = codec.getOutputBuffer(outIdx)
                        if (outBuf != null && bufferInfo.size > 0) {
                            val chunk = ByteArray(bufferInfo.size)
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            outBuf.get(chunk)
                            pcmOut.write(chunk)
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            sawOutputEos = true
                        }
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFmt = codec.outputFormat
                        if (outFmt.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            sampleRate = outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (outFmt.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                            channelCount = outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        }
                    }
                }
            }
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }

        val rawBytes = pcmOut.toByteArray()
        val shortBuf = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val shorts = ShortArray(shortBuf.remaining()).also { shortBuf.get(it) }
        val safeChannels = channelCount.coerceAtLeast(1)
        val frameCount = shorts.size / safeChannels
        val monoSamples =
            FloatArray(frameCount) { frame ->
                var sum = 0f
                val base = frame * safeChannels
                for (c in 0 until safeChannels) {
                    sum += shorts[base + c].toFloat() / 32768f
                }
                (sum / safeChannels).coerceIn(-1f, 1f)
            }
        return DecodedAudio(samples = monoSamples, sampleRateHz = sampleRate)
    }

    private fun readFourCc(bytes: ByteArray, offset: Int): String =
        String(bytes, offset, 4, Charsets.US_ASCII)
}
