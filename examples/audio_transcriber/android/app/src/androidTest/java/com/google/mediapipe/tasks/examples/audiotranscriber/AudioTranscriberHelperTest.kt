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

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.examples.common.testing.LatencyRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AudioTranscriberHelperTest {

  companion object {
    private const val TAG = "AudioTranscriberHelperTest"
  }

  @Test
  fun parakeetCpu_transcribesSampleSpeechWavAndRecordsLatency() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val recorder = LatencyRecorder("audio_transcriber")
    recorder.putMetadata("model", AudioTranscriberViewModel.DEFAULT_MODEL_FILE_NAME)
    recorder.putMetadata("delegate", "CPU")

    val audio =
      context.assets.open(AudioTranscriberViewModel.SAMPLE_AUDIO_ASSET).use {
        WavAudioLoader.decode(it)
      }
    assertEquals(AudioTranscriberViewModel.SAMPLE_RATE_HZ, audio.sampleRateHz)
    assertTrue("Expected non-empty audio samples", audio.samples.size > AudioTranscriberViewModel.SAMPLE_RATE_HZ)

    val modelFile =
      resolveDefaultModelFile(
        sideloadDirectories = emptyList(),
        cacheDir = context.cacheDir,
        assetOpener = context.assets::open,
      )

    val transcriber =
      recorder.measure("model_load_ms") {
        AudioTranscribing.create(
          context = context,
          modelFile = modelFile,
          delegateIndex = AudioTranscriberViewModel.DELEGATE_CPU,
        ).also { it.prewarm() }
      }

    try {
      val collectedSegments = mutableListOf<String>()
      val chunkSize = AudioTranscriberViewModel.SAMPLE_RATE_HZ / 2

      recorder.measure("transcribe_sample_wav_ms") {
        var offset = 0
        while (offset < audio.samples.size) {
          val end = (offset + chunkSize).coerceAtMost(audio.samples.size)
          val chunk = audio.samples.copyOfRange(offset, end)
          val isLast = end == audio.samples.size
          val partial = transcriber.transcribe(chunk, endOfStream = isLast)
          partial.segments().filter { it.isFinal }.forEach { segment ->
            val text = segment.transcript().trim()
            if (text.isNotEmpty()) collectedSegments.add(text)
          }
          offset = end
        }
        val finalResult = transcriber.finishStream()
        finalResult.segments().forEach { segment ->
          val text = segment.transcript().trim()
          if (text.isNotEmpty()) collectedSegments.add(text)
        }
        val combinedText = finalResult.getText().trim()
        if (collectedSegments.isEmpty() && combinedText.isNotEmpty()) {
          collectedSegments.add(combinedText)
        }
      }

      val fullTranscript = collectedSegments.joinToString(" ").trim()
      Log.i(TAG, "Parakeet CPU sample WAV transcript: \"$fullTranscript\"")
      val lower = fullTranscript.lowercase()
      assertTrue(
        "Expected intelligible English speech containing 'mike' and 'tomorrow', got '$fullTranscript'",
        lower.contains("mike") && lower.contains("tomorrow"),
      )
    } finally {
      transcriber.close()
      recorder.flush(context)
    }
  }
}
