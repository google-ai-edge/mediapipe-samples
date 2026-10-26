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

import Foundation
import SwiftUI

#if canImport(MediaPipeTasksAudioTTS)
  import MediaPipeTasksAudioTTS
#elseif canImport(MediaPipeTTS)
  import MediaPipeTTS
#else
  import third_party_mediapipe_tasks_ios_audio_text_to_speech_TextToSpeech
#endif

public struct PresetPhrase: Identifiable, Hashable, Sendable {
  public let id: String
  public let title: String
  public let text: String

  public init(title: String, text: String) {
    self.id = title
    self.title = title
    self.text = text
  }
}

@MainActor
public final class TTSViewModel: ObservableObject {
  @Published public var textToSynthesize: String = "Hello, welcome to MediaPipe TTS."
  @Published public var selectedVoice: String = "af_heart"
  @Published public var availableVoices: [VoiceInfo] = []
  @Published public var statusMessage: String = "Initializing..."
  @Published public var isSynthesizing: Bool = false
  @Published public var isPlaying: Bool = false
  @Published public var selectedLanguage: String = "en-us" {
    didSet {
      if !filteredVoices.contains(where: { $0.voiceId == selectedVoice }) {
        selectedVoice = filteredVoices.first?.voiceId ?? "af_heart"
      }
    }
  }
  @Published public var availableLanguages: [String] = []

  private var tts: TextToSpeech?
  private let player = TTSAudioPlayer(enableSpeakerOutput: true)
  private var synthesisTask: Task<Void, Never>?

  public let presetPhrases: [PresetPhrase] = [
    PresetPhrase(
      title: "Welcome",
      text: "Hello, welcome to MediaPipe TTS."
    ),
    PresetPhrase(
      title: "Demo",
      text: "This is a demonstration of real-time speech synthesis."
    ),
    PresetPhrase(
      title: "Pangram",
      text: "The quick brown fox jumps over the lazy dog."
    ),
    PresetPhrase(
      title: "Neural Voice",
      text: "The Kokoro model provides high-quality neural voice."
    ),
    PresetPhrase(
      title: "Bilingual",
      text: "Bilingual voice switching is supported with Kokoro voices."
    ),
  ]

  public init() {
    setUpTTS()
  }

  public var filteredVoices: [VoiceInfo] {
    availableVoices.filter { $0.locale == selectedLanguage }
  }

  private func setUpTTS() {
    do {
      let modelPath = getModelPath()
      guard FileManager.default.fileExists(atPath: modelPath) else {
        statusMessage = "Model not found at: \(modelPath)"
        return
      }

      #if targetEnvironment(simulator)
        let defaultDelegate: TTSHardwareDelegate = .cpu
      #else
        let defaultDelegate: TTSHardwareDelegate = .gpu
      #endif

      let options = TextToSpeechOptions(
        baseOptions: TTSBaseOptions(
          modelAssetPath: modelPath,
          delegate: defaultDelegate
        ),
        defaultVoiceId: "af_heart",
        speakingRate: 1.0
      )

      tts = try createTextToSpeech(options: options)
      availableVoices = try tts?.listVoices() ?? []
      availableLanguages = Array(Set(availableVoices.map { $0.locale })).sorted()

      print("Available Voices: \(availableVoices.map { "\($0.voiceId): \($0.locale)" })")
      print("Available Languages: \(availableLanguages)")

      if !availableLanguages.contains(selectedLanguage) {
        selectedLanguage = availableLanguages.first ?? "en-us"
      }

      // Select first available voice for the default language if current selection is not available
      if !filteredVoices.contains(where: { $0.voiceId == selectedVoice }) {
        selectedVoice = filteredVoices.first?.voiceId ?? "af_heart"
      }

      let modelFilename = (modelPath as NSString).lastPathComponent
      statusMessage = "Kokoro (\(modelFilename)). Voices: \(availableVoices.count)"
    } catch {
      statusMessage = "Init error: \(error.localizedDescription)"
    }
  }

  private func getModelPath() -> String {
    if let bundlePath = Bundle.main.path(forResource: "kokoro_int8_gpu_fp16", ofType: "litertlm")
      ?? Bundle.main.path(forResource: "kokoro_int8_gpu", ofType: "litertlm")
      ?? Bundle.main.path(forResource: "kokoro_acoustic_int8", ofType: "litertlm")
      ?? Bundle.main.path(forResource: "kokoro", ofType: "litertlm") {
      return bundlePath
    }
    #if os(iOS)
      let documentsURL = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
      let fullPathGpu = documentsURL.appendingPathComponent("kokoro_int8_gpu.litertlm").path
      if FileManager.default.fileExists(atPath: fullPathGpu) {
        return fullPathGpu
      }
      let fullPath = documentsURL.appendingPathComponent("kokoro_acoustic_int8.litertlm").path
      if FileManager.default.fileExists(atPath: fullPath) {
        return fullPath
      }
      return documentsURL.appendingPathComponent("kokoro.litertlm").path
    #else
      let modelsDir = FileManager.default.homeDirectoryForCurrentUser.appendingPathComponent(
        "models")
      let pathGpu = modelsDir.appendingPathComponent("kokoro_int8_gpu.litertlm").path
      if FileManager.default.fileExists(atPath: pathGpu) {
        return pathGpu
      }
      let path1 = modelsDir.appendingPathComponent("kokoro_acoustic_int8.litertlm").path
      let path2 = modelsDir.appendingPathComponent("kokoro.litertlm").path
      if FileManager.default.fileExists(atPath: path1) {
        return path1
      }
      return path2
    #endif
  }

  public func synthesize() {
    guard let tts = tts else {
      statusMessage = "TTS not initialized"
      return
    }

    guard !textToSynthesize.isEmpty else {
      statusMessage = "Please enter text"
      return
    }

    synthesisTask?.cancel()
    isSynthesizing = true
    statusMessage = "Synthesizing..."

    player.stop()

    var task: Task<Void, Never>?
    task = Task {
      do {
        let result = try await tts.synthesizeAsync(
          textToSynthesize,
          options: SynthesisOptions(voiceId: selectedVoice)
        )
        guard !Task.isCancelled && self.synthesisTask == task else { return }
        var hasNaN = false
        var minVal: Float = .infinity
        var maxVal: Float = -.infinity
        for s in result.samples {
          if s.isNaN || s.isInfinite { hasNaN = true }
          else {
            if s < minVal { minVal = s }
            if s > maxVal { maxVal = s }
          }
        }
        print("Synthesized: \(result.samples.count) samples at \(result.sampleRateHz) Hz. min=\(minVal), max=\(maxVal), hasNaN=\(hasNaN)")
        player.enqueue(result) {
          Task { @MainActor in
            guard self.isPlaying else { return }
            self.isPlaying = false
            self.statusMessage = String(
              format: "Ready (%d samples, [%.2f, %.2f], NaN=%@)",
              result.samples.count,
              minVal.isInfinite ? 0 : minVal,
              maxVal.isInfinite ? 0 : maxVal,
              hasNaN ? "YES" : "NO"
            )
          }
        }
        isPlaying = true
        statusMessage = "Playing (\(result.samples.count) samples)..."
      } catch is CancellationError {
        if self.synthesisTask == task {
          statusMessage = "Synthesis cancelled"
        }
      } catch {
        if self.synthesisTask == task {
          statusMessage = "Synthesis error: \(error.localizedDescription)"
        }
      }
      if self.synthesisTask == task {
        isSynthesizing = false
      }
    }
    synthesisTask = task
  }

  public func synthesizeStreaming() {
    guard let tts = tts else {
      statusMessage = "TTS not initialized"
      return
    }

    guard !textToSynthesize.isEmpty else {
      statusMessage = "Please enter text"
      return
    }

    synthesisTask?.cancel()
    isSynthesizing = true
    statusMessage = "Streaming..."

    player.stop()

    let prompt = textToSynthesize
    let voice = selectedVoice
    var task: Task<Void, Never>?
    task = Task {
      let startTime = Date()
      var ttfaMs: Double = -1
      var chunks = 0
      do {
        for try await chunk in tts.synthesizeStream(
          prompt,
          options: SynthesisOptions(voiceId: voice)
        ) {
          try Task.checkCancellation()
          guard self.synthesisTask == task else { return }
          if ttfaMs < 0 {
            ttfaMs = Date().timeIntervalSince(startTime) * 1000.0
          }
          chunks += 1
          let isFinalChunk = chunk.isFinal
          player.enqueue(chunk) {
            if isFinalChunk {
              Task { @MainActor in
                guard self.isPlaying else { return }
                self.isPlaying = false
                self.statusMessage = "Ready"
              }
            }
          }
          isPlaying = true
          statusMessage = String(
            format: "Streaming chunk #%d (TTFA %.0f ms)...",
            chunks,
            ttfaMs
          )
        }
        guard !Task.isCancelled && self.synthesisTask == task else { return }
        statusMessage = String(
          format: "Streamed %d chunks (TTFA %.0f ms). Playing...",
          chunks,
          max(0, ttfaMs)
        )
      } catch is CancellationError {
        if self.synthesisTask == task {
          statusMessage = "Synthesis cancelled"
        }
      } catch {
        if self.synthesisTask == task {
          statusMessage = "Synthesis error: \(error.localizedDescription)"
        }
      }
      if self.synthesisTask == task {
        isSynthesizing = false
      }
    }
    synthesisTask = task
  }

  public func cancel() {
    synthesisTask?.cancel()
    synthesisTask = nil
    if isSynthesizing {
      isSynthesizing = false
      statusMessage = "Synthesis cancelled"
    }
    if isPlaying {
      player.stop()
      isPlaying = false
      statusMessage = "Playback stopped"
    }
  }
}
