/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.audioembedder

import android.content.Context
import android.media.AudioFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.components.containers.AudioData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AudioEmbedderHelperTest {
    companion object {
        private const val SPEECH_WAV_16K_MONO = "speech_16000_hz_mono.wav"
        private const val SPEECH_WAV_48K_MONO = "speech_48000_hz_mono.wav"
        private const val TWO_HEADS_WAV_16K_MONO = "two_heads_16000_hz_mono.wav"
        private const val SPEECH_WAV_DURATION = 4000L // Millisecond
        private const val TWO_HEADS_WAV_DURATION = 1000L // Millisecond
    }

    private lateinit var embedderHelper: AudioEmbedderHelper
    private lateinit var context: Context

    @Before
    fun setup() {
        embedderHelper =
            AudioEmbedderHelper(ApplicationProvider.getApplicationContext())
        context = InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun embeddingResultsWithinAcceptedRangeOne() {
        val audioDataOne = getAudioDataFromAsset(
            SPEECH_WAV_16K_MONO, SPEECH_WAV_DURATION
        )
        val audioDataTwo = getAudioDataFromAsset(
            SPEECH_WAV_48K_MONO, SPEECH_WAV_DURATION
        )

        assertNotNull(audioDataOne)
        assertNotNull(audioDataTwo)

        val bundleResult =
            embedderHelper.compare(audioDataOne!!, audioDataTwo!!)
        assertNotNull(bundleResult)
        assertEquals(0.94, bundleResult!!.similarity, 0.01)
    }

    @Test
    fun embeddingResultsWithinAcceptedRangeTwo() {
        val audioDataOne = getAudioDataFromAsset(
            SPEECH_WAV_16K_MONO, SPEECH_WAV_DURATION
        )
        val audioDataTwo = getAudioDataFromAsset(
            TWO_HEADS_WAV_16K_MONO, TWO_HEADS_WAV_DURATION
        )

        assertNotNull(audioDataOne)
        assertNotNull(audioDataTwo)

        val bundleResult =
            embedderHelper.compare(audioDataOne!!, audioDataTwo!!)
        assertNotNull(bundleResult)
        assertEquals(0.08, bundleResult!!.similarity, 0.01)
    }

    private fun getAudioDataFromAsset(
        audioName: String, audioDuration: Long
    ): AudioData? {

        val inputStream = context.assets.open(audioName)
        val dataInputStream = DataInputStream(inputStream)
        val targetArray = ByteArray(dataInputStream.available())
        dataInputStream.read(targetArray)
        val audioFloatArrayData = targetArray.toShortArray()

        // calculate the sample rate
        val expectedSampleRate =
            audioFloatArrayData.size / (audioDuration / 1000F / AudioEmbedderHelper.YAMNET_EXPECTED_INPUT_LENGTH)

        // create audio data
        val audioData = AudioData.create(
            AudioData.AudioDataFormat.builder().setNumOfChannels(
                AudioFormat.CHANNEL_IN_DEFAULT
            ).setSampleRate(expectedSampleRate).build(),
            audioFloatArrayData.size
        )
        audioData.load(audioFloatArrayData)
        return audioData
    }
}
