/*
 * Copyright 2026 The MediaPipe Authors.
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
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioClassifierHelperTest {

    companion object {
        private const val TAG = "AudioClassifierHelperTest"
    }

    @Test
    fun classifySpeechClipAndSineTone_matchesExpectedCategoriesAndOptions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = LatencyRecorder("audio_classifier")
        recorder.putMetadata("model", AudioClassifierHelper.YAMNET_MODEL)
        recorder.putMetadata("delegate", "CPU")

        val helper =
            recorder.measure("model_load_ms") {
                AudioClassifierHelper(
                    context = context,
                    classificationThreshold = AudioClassifierHelper.DISPLAY_THRESHOLD,
                    numOfResults = 3,
                )
            }

        try {
            assertTrue("AudioClassifier failed to initialize", !helper.isClosed())

            val decodedSpeech =
                AudioClipDecoder.decodeAssetWav(context, AudioClassifierHelper.SAMPLE_SPEECH_ASSET)
            assertEquals(AudioClassifierHelper.SAMPLING_RATE_IN_HZ, decodedSpeech.sampleRateHz)
            val speechAudioData = decodedSpeech.toAudioData()

            val speechBundle =
                recorder.measure("classify_speech_clip_ms") {
                    helper.classifyAudio(speechAudioData)
                }
            assertNotNull(speechBundle)
            speechBundle!!

            val classificationWindows = speechBundle.results.first().classificationResults()
            assertTrue("Expected at least 1 audio window", classificationWindows.isNotEmpty())

            val topCategoriesPerWindow =
                classificationWindows.map { window ->
                    window.classifications().first().categories().mapNotNull { cat ->
                        cat.categoryName()?.let { name -> name to cat.score() }
                    }
                }
            Log.i(TAG, "speech windows=${topCategoriesPerWindow.size} details=$topCategoriesPerWindow")

            val allPredictedLabels = topCategoriesPerWindow.flatten().map { it.first }.toSet()
            assertTrue(
                "Expected Speech to be detected in speech_16000_hz_mono.wav, got $allPredictedLabels",
                allPredictedLabels.contains("Speech"),
            )

            // Verify sine tone is also classified coherently (e.g. Sine wave / Musical instrument / Beep).
            val toneAudioData =
                AudioClipDecoder.generateSineTone(freqHz = 440f, durationMs = 2_000).toAudioData()
            val toneBundle =
                recorder.measure("classify_sine_tone_ms") {
                    helper.classifyAudio(toneAudioData)
                }
            assertNotNull(toneBundle)
            val toneLabels =
                toneBundle!!
                    .results
                    .first()
                    .classificationResults()
                    .flatMap { it.classifications().first().categories() }
                    .mapNotNull { it.categoryName() }
                    .toSet()
            Log.i(TAG, "tone labels=$toneLabels")
            assertTrue("Expected non-empty labels for 440 Hz tone", toneLabels.isNotEmpty())

            repeat(5) {
                recorder.measure("classify_steady_ms") {
                    helper.classifyAudio(speechAudioData)
                }
            }
            recorder.flush(context)

            // Verify maxResults = 1 option constrains each window.
            helper.numOfResults = 1
            assertTrue("Failed to re-init AudioClassifier with maxResults=1", helper.initClassifier())
            val limitedBundle = helper.classifyAudio(speechAudioData)!!
            limitedBundle.results.first().classificationResults().forEach { window ->
                val count = window.classifications().first().categories().size
                assertTrue("Expected <= 1 category, got $count", count <= 1)
            }
        } finally {
            helper.stopAudioClassification()
        }
    }
}
