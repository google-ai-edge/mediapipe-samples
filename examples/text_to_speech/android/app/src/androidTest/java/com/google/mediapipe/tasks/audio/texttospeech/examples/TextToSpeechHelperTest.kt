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

package com.google.mediapipe.tasks.audio.texttospeech.examples

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextToSpeechHelperTest {

  companion object {
    private const val TAG = "TextToSpeechHelperTest"
    private const val EXPECTED_SAMPLE_RATE_HZ = 24_000
  }

  private fun rmsAmplitude(samples: FloatArray): Double {
    if (samples.isEmpty()) return 0.0
    var sumSq = 0.0
    for (s in samples) {
      assertTrue("Audio sample must be finite, found $s", s.isFinite())
      sumSq += s.toDouble() * s.toDouble()
    }
    return sqrt(sumSq / samples.size.toDouble())
  }

  @Test
  fun kokoro_unaryAndStreamingSynthesis_produceValid24kHzSpeechAndWav() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val recorder = LatencyRecorder("text_to_speech")
    val modelFile = TextToSpeechHelper.findModelFile(context)
    assertNotNull("Kokoro model file not found in assets or storage", modelFile)
    recorder.putMetadata("model", modelFile!!.name)

    val loaded =
      recorder.measure("model_load_ms") {
        TextToSpeechHelper.createAndWarmUp(
          context = context,
          modelFile = modelFile,
          delegateMode = TtsDelegateMode.AUTO,
          runWarmup = true,
        )
      }
    recorder.putMetadata("delegate", loaded.usedDelegate.name)

    try {
      assertTrue(
        "Expected bundled Kokoro voices including af_heart",
        loaded.voices.any { it.voiceId == TextToSpeechHelper.DEFAULT_VOICE },
      )

      val unaryResult =
        recorder.measure("unary_synth_ms") {
          loaded.engine.synthesizeText(
            "Hello from MediaPipe Text to Speech running locally on Android.",
            TextToSpeechHelper.DEFAULT_VOICE,
          )
        }
      assertEquals(EXPECTED_SAMPLE_RATE_HZ, unaryResult.sampleRateHz)
      assertTrue("Expected > 1s of unary speech, got ${unaryResult.durationSeconds}s", unaryResult.durationSeconds > 1.0f)
      val unaryRms = rmsAmplitude(unaryResult.samples)
      Log.i(TAG, "Unary synthesis: samples=${unaryResult.samples.size} duration=${unaryResult.durationSeconds}s rms=$unaryRms")
      assertTrue("Expected non-silent waveform (rms > 0.01), got $unaryRms", unaryRms > 0.01)

      val player = TtsAudioPlayer()
      var chunkCount = 0
      recorder.measure("stream_synth_ms") {
        runBlocking {
          loaded.engine
            .streamText(
              "First sentence cleanly synthesized. Second sentence arrives as the next audio chunk!",
              TextToSpeechHelper.DEFAULT_VOICE,
            )
            .collect { chunk ->
              assertEquals(EXPECTED_SAMPLE_RATE_HZ, chunk.sampleRateHz)
              if (chunk.samples.isNotEmpty()) {
                chunkCount += 1
                player.enqueue(chunk)
              }
            }
        }
      }
      assertTrue("Expected at least 1 non-empty streamed audio chunk", chunkCount >= 1)
      assertTrue("Expected > 24000 streamed samples", player.totalSamples > EXPECTED_SAMPLE_RATE_HZ)

      val outWav = File(context.cacheDir, "test_tts_output.wav")
      outWav.delete()
      player.writeWavFile(outWav)
      assertTrue("WAV export file should exist", outWav.isFile)
      assertEquals(44L + player.totalSamples * 2L, outWav.length())
      val header = outWav.inputStream().use { it.readNBytes(4) }.decodeToString()
      assertEquals("RIFF", header)
    } finally {
      loaded.engine.close()
      recorder.flush(context)
    }
  }
}
