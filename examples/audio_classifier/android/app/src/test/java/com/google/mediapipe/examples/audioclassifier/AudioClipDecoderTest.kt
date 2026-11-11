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

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioClipDecoderTest {

    @Test
    fun decodeWavBytes_parsesPcm16MonoHeaderAndSamples() {
        val sampleRate = 16_000
        val pcmSamples = shortArrayOf(0, 16384, -16384, 32767)
        val dataSize = pcmSamples.size * 2
        val wav = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray(Charsets.US_ASCII))
        wav.putInt(36 + dataSize)
        wav.put("WAVE".toByteArray(Charsets.US_ASCII))
        wav.put("fmt ".toByteArray(Charsets.US_ASCII))
        wav.putInt(16)
        wav.putShort(1) // PCM
        wav.putShort(1) // mono
        wav.putInt(sampleRate)
        wav.putInt(sampleRate * 2)
        wav.putShort(2)
        wav.putShort(16)
        wav.put("data".toByteArray(Charsets.US_ASCII))
        wav.putInt(dataSize)
        pcmSamples.forEach { wav.putShort(it) }

        val decoded = AudioClipDecoder.decodeWavBytes(wav.array())
        assertEquals(sampleRate, decoded.sampleRateHz)
        assertEquals(4, decoded.samples.size)
        assertEquals(0f, decoded.samples[0], 1e-4f)
        assertEquals(0.5f, decoded.samples[1], 1e-4f)
        assertEquals(-0.5f, decoded.samples[2], 1e-4f)
        assertTrue(decoded.samples[3] > 0.99f)
    }

    @Test
    fun generateSineTone_producesExpectedLengthAndAmplitude() {
        val tone = AudioClipDecoder.generateSineTone(freqHz = 440f, durationMs = 1000, sampleRateHz = 16_000)
        assertEquals(16_000, tone.sampleRateHz)
        assertEquals(16_000, tone.samples.size)
        assertEquals(1_000L, tone.durationMs)
        assertTrue(tone.samples.max() > 0.4f)
        assertTrue(tone.samples.min() < -0.4f)
    }
}
