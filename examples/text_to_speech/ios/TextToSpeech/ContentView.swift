// Copyright 2026 The MediaPipe Authors.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

import SwiftUI

#if canImport(MediaPipeTasksAudio)
  import MediaPipeTasksAudio
#elseif canImport(MediaPipeTasksAudioTTS)
  import MediaPipeTasksAudioTTS
#elseif canImport(MediaPipeTTS)
  import MediaPipeTTS
#else
  import third_party_mediapipe_tasks_ios_audio_text_to_speech_TextToSpeech
#endif

#if canImport(UIKit)
  import UIKit
  extension Color {
    fileprivate static let appBackground = Color(uiColor: .systemBackground)
    fileprivate static let appSecondaryBackground = Color(uiColor: .secondarySystemBackground)
    fileprivate static let appSeparator = Color(uiColor: .separator)
  }
#elseif canImport(AppKit)
  import AppKit
  extension Color {
    fileprivate static let appBackground = Color(nsColor: .windowBackgroundColor)
    fileprivate static let appSecondaryBackground = Color(nsColor: .controlBackgroundColor)
    fileprivate static let appSeparator = Color(nsColor: .separatorColor)
  }
#endif

public struct ContentView: View {
  @StateObject private var viewModel = TTSViewModel()

  public init() {}

  public var body: some View {
    VStack(spacing: 20) {
      // Header
      VStack(spacing: 6) {
        Text("MediaPipe TTS Demo")
          .font(.title2.bold())
        Text("On-Device Speech Synthesis")
          .font(.subheadline)
          .foregroundColor(.secondary)
      }
      .padding(.top, 10)

      // Status Bar
      HStack {
        Text(viewModel.statusMessage)
          .font(.caption.monospaced())
          .foregroundColor(.primary)
        Spacer()
        if viewModel.isSynthesizing {
          ProgressView()
            .scaleEffect(0.8)
        }
      }
      .padding(.horizontal, 14)
      .padding(.vertical, 8)
      .background(Color.appSecondaryBackground)
      .cornerRadius(8)
      .padding(.horizontal)

      // Settings: Language & Voice
      HStack(spacing: 15) {
        VStack(alignment: .leading, spacing: 4) {
          Text("Language")
            .font(.caption)
            .foregroundColor(.secondary)
          Picker("Language", selection: $viewModel.selectedLanguage) {
            ForEach(viewModel.availableLanguages, id: \.self) { lang in
              Text(lang).tag(lang)
            }
          }
          .pickerStyle(.menu)
        }

        VStack(alignment: .leading, spacing: 4) {
          Text("Voice")
            .font(.caption)
            .foregroundColor(.secondary)
          Picker("Voice", selection: $viewModel.selectedVoice) {
            if viewModel.filteredVoices.isEmpty {
              Text("No voices").tag("")
            } else {
              ForEach(viewModel.filteredVoices, id: \.voiceId) { voice in
                Text(voice.voiceId).tag(voice.voiceId)
              }
            }
          }
          .pickerStyle(.menu)
          .disabled(viewModel.filteredVoices.isEmpty)
        }
        Spacer()
      }
      .padding(.horizontal)

      // Preset Phrases
      VStack(alignment: .leading, spacing: 8) {
        HStack {
          Text("Preset Phrases")
            .font(.headline)
          Spacer()
          Menu {
            ForEach(viewModel.presetPhrases) { preset in
              Button(preset.text) {
                viewModel.textToSynthesize = preset.text
              }
            }
          } label: {
            HStack(spacing: 3) {
              Text("All")
              Image(systemName: "chevron.down")
                .font(.caption2)
            }
            .font(.caption.bold())
            .foregroundColor(.blue)
          }
        }
        ScrollView(.horizontal, showsIndicators: false) {
          HStack(spacing: 8) {
            ForEach(viewModel.presetPhrases) { preset in
              let isSelected = viewModel.textToSynthesize == preset.text
              Button(action: {
                viewModel.textToSynthesize = preset.text
              }) {
                Text(preset.title)
                  .font(.subheadline.weight(isSelected ? .semibold : .regular))
                  .padding(.horizontal, 14)
                  .padding(.vertical, 7)
                  .background(isSelected ? Color.blue : Color.blue.opacity(0.12))
                  .foregroundColor(isSelected ? .white : .blue)
                  .clipShape(Capsule())
              }
              .buttonStyle(.plain)
            }
          }
        }
      }
      .padding(.horizontal)

      // Manual Input
      VStack(alignment: .leading, spacing: 8) {
        Text("Text to Synthesize")
          .font(.headline)
        TextEditor(text: $viewModel.textToSynthesize)
          .padding(4)
          .background(Color.appBackground)
          .cornerRadius(8)
          .overlay(
            RoundedRectangle(cornerRadius: 8)
              .stroke(Color.appSeparator, lineWidth: 1)
          )
      }
      .padding(.horizontal)
      .frame(maxHeight: .infinity)

      // Action Buttons
      let isActive = viewModel.isSynthesizing || viewModel.isPlaying
      HStack(spacing: 12) {
        Button(action: {
          if isActive {
            viewModel.cancel()
          } else {
            viewModel.synthesize()
          }
        }) {
          HStack {
            Spacer()
            if viewModel.isSynthesizing {
              ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                .padding(.trailing, 8)
            }
            Text(viewModel.isSynthesizing ? "Cancel" : (viewModel.isPlaying ? "Stop" : "Speak"))
              .font(.headline)
            Spacer()
          }
          .foregroundColor(.white)
          .padding(.vertical, 14)
          .background(
            viewModel.textToSynthesize.isEmpty && !isActive
              ? Color.gray : (isActive ? Color.red : Color.blue)
          )
          .cornerRadius(10)
          .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
        }
        .buttonStyle(.plain)
        .disabled(viewModel.textToSynthesize.isEmpty && !isActive)

        if !isActive {
          Button(action: {
            viewModel.synthesizeStreaming()
          }) {
            HStack {
              Spacer()
              Text("Stream")
                .font(.headline)
              Spacer()
            }
            .foregroundColor(.white)
            .padding(.vertical, 14)
            .background(viewModel.textToSynthesize.isEmpty ? Color.gray : Color.teal)
            .cornerRadius(10)
            .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
          }
          .buttonStyle(.plain)
          .disabled(viewModel.textToSynthesize.isEmpty)
        }
      }
      .padding(.horizontal)
      .padding(.bottom, 20)
    }
    .background(Color.appBackground)
  }
}
