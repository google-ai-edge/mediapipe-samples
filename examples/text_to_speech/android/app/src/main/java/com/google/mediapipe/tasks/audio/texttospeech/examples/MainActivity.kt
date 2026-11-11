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

@file:Suppress("PackageName")

package com.google.mediapipe.tasks.audio.texttospeech.examples

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.google.mediapipe.examples.common.ui.MediaPipeTheme

/**
 * Interactive on-device demo for the MediaPipe-TTS Kotlin SDK running Kokoro-82M in-process.
 *
 * Preserves scripted intent extras (`text`, `voice`, `num_threads`, `target_bucket`, `play_audio`,
 * `stream`, `autorun`) and the `MEDIAPIPE_TTS_DEMO_RESULT` logcat contract used by
 * `scripts/run_android_demo.sh`.
 */
class MainActivity : ComponentActivity() {
  private val viewModel: TextToSpeechViewModel by viewModels()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    viewModel.handleLaunchIntent(intent, initialLaunch = true)
    setContent {
      MediaPipeTheme {
        val state by viewModel.uiState.collectAsState()
        TextToSpeechScreen(
          state = state,
          onSelectPreset = viewModel::selectPreset,
          onTextChange = viewModel::updateText,
          onSelectLanguage = viewModel::selectLanguage,
          onSelectVoice = viewModel::selectVoice,
          onSpeak = viewModel::speakUnary,
          onStream = viewModel::speakStreaming,
          onReplay = viewModel::replayLastAudio,
          onStop = viewModel::stop,
          onSelectDelegateMode = viewModel::setDelegateMode,
          onSelectSpeakingRate = viewModel::setSpeakingRate,
          onFallbackToCpu = viewModel::fallbackToCpu,
        )
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    viewModel.handleLaunchIntent(intent, initialLaunch = false)
  }

  companion object {
    const val TAG = "MediaPipeTtsDemo"
    const val RESULT_MARKER = "MEDIAPIPE_TTS_DEMO_RESULT"

    const val EXTRA_TEXT = "text"
    const val EXTRA_VOICE = "voice"
    const val EXTRA_NUM_THREADS = "num_threads"
    const val EXTRA_TARGET_BUCKET = "target_bucket"
    const val EXTRA_PLAY_AUDIO = "play_audio"
    const val EXTRA_STREAM = "stream"
    const val EXTRA_AUTORUN = "autorun"

    const val MODEL_FILE_NAME = TextToSpeechHelper.LEGACY_MODEL_FILE_NAME
    const val OUTPUT_WAV_NAME = TextToSpeechHelper.OUTPUT_WAV_NAME
    const val DEFAULT_VOICE = TextToSpeechHelper.DEFAULT_VOICE
  }
}
