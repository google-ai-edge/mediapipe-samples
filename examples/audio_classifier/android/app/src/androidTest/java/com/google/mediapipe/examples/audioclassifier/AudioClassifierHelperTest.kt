/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.audioclassifier

import android.content.Context
import android.media.AudioFormat
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.tasks.components.containers.AudioData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Before
import java.io.DataInputStream

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class AudioClassifierHelperTest {
    companion object {
        private const val AUDIO_CAT_SOUND = "cat_sound.wav"
        private const val AUDIO_DURATION = 6000L // milliseconds
        private val expectedResult =
            setOf("Cat", "Meow", "Domestic animals, pets", "Speech")
    }

    private lateinit var audioClassifierHelper: AudioClassifierHelper
    private lateinit var context: Context

    @Before
    fun setup() {
        audioClassifierHelper =
            AudioClassifierHelper(ApplicationProvider.getApplicationContext())
        context = InstrumentationRegistry.getInstrumentation().context
    }

    @Test
    fun classificationResultShouldNotBeChange() {
        val audioData = getAudioDataFromAsset()

        assertNotNull(audioData)

        val resultBundle = audioClassifierHelper.classifyAudio(audioData!!)

        assertNotNull(resultBundle)
        val actualResult =
            resultBundle!!.results.first().classificationResults()
                .map {
                    it.classifications().first().categories()
                        .mapNotNull { it.categoryName() }
                }.flatten().toSet()

        assertEquals(expectedResult, actualResult)
    }

    private fun getAudioDataFromAsset(): AudioData? {

        val inputStream = context.assets.open(AUDIO_CAT_SOUND)
        val dataInputStream = DataInputStream(inputStream)
        val targetArray = ByteArray(dataInputStream.available())
        dataInputStream.read(targetArray)
        val audioFloatArrayData = targetArray.toShortArray()

        // calculate the sample rate
        val expectedSampleRate =
            audioFloatArrayData.size / (AUDIO_DURATION / 1000F / AudioClassifierHelper.EXPECTED_INPUT_LENGTH)

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
