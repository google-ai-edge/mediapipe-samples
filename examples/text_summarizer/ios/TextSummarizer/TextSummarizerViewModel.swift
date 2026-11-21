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
import MediaPipeSampleUI
import Observation

/// Curated passage preset for one-tap interactive exploration.
public struct SummarizerPreset: Identifiable, Equatable, Sendable {
  public let id: String
  public let label: String
  public let text: String

  public init(id: String, label: String, text: String) {
    self.id = id
    self.label = label
    self.text = text
  }

  public static let defaults: [SummarizerPreset] = [
    SummarizerPreset(
      id: "brussels",
      label: "EU Quarter",
      text:
        "This station is in the centre of the European quarter of Brussels, being adjacent to the Berlaymont building (headquarters of the European Commission), the Justus Lipsius building (Council of the European Union) and numerous other EU offices. The station is named after the area around the Schuman roundabout, which was named after Robert Schuman. The station sits on the Rue de la Loi/Wetstraat, a major city thoroughfare, and is close to Parc du Cinquantenaire/Jubelpark."
    ),
    SummarizerPreset(
      id: "mediapipe",
      label: "On-device AI",
      text:
        "MediaPipe Tasks provides cross-platform, customizable on-device machine learning solutions for mobile, web, and desktop developers. Instead of sending raw audio, camera frames, or sensitive documents to remote cloud servers, apps can run quantized LiteRT and LiteRT-LM models directly on phone hardware. Running models locally lowers network bandwidth costs, keeps private user data offline, and ensures predictable sub-second latency even in airplane mode or poor connectivity. Developers can swap quantized bundles tailored to CPU, GPU, or NPU accelerators while preserving a single Kotlin or Swift application codebase across devices."
    ),
    SummarizerPreset(
      id: "meeting",
      label: "Project sync",
      text:
        "During Tuesday's mobile engineering review, the team finalized the UI modernization across all fourteen computer vision samples and scheduled regression testing on flagship iOS and Android devices. Priya will update the continuous integration matrix to verify downloaded model SHA-256 digests before simulator tests run, while Marcus will audit dark-theme contrast ratios and keyboard resizing behavior prior to the release branch cut on Friday."
    ),
    SummarizerPreset(
      id: "weather",
      label: "Solar storm",
      text:
        "A severe geomagnetic storm reached Earth yesterday afternoon after two coronal mass ejections merged in interplanetary space. Forecasters at the Space Weather Prediction Center reported vivid auroras visible much farther south than usual across North America and Europe. Grid operators temporarily switched high-latitude transformers into protective monitoring modes, while aviation routers adjusted polar flight communications as a standard precaution."
    ),
  ]
}

/// Observable MainActor ViewModel driving the MediaPipe Text Summarizer sample.
@Observable
@MainActor
public final class TextSummarizerViewModel {
  public var inputText: String {
    didSet {
      if oldValue != inputText {
        streamingText = ""
        result = nil
      }
    }
  }

  public private(set) var selectedModel: TextSummarizerModelOption
  public private(set) var selectedMode: TextSummarizerModeOption
  public private(set) var maxTokens: TextSummarizerMaxTokensOption
  public private(set) var selectedDelegate: TextSummarizerDelegateOption
  public private(set) var streamingEnabled: Bool

  public private(set) var isModelLoading: Bool = false
  public private(set) var isSummarizing: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var modelLoadTimeMs: Double?
  public private(set) var streamingText: String = ""
  public private(set) var result: TextSummarizerOutput?
  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  private let helper: TextSummarizerHelper
  private let autoRunOnInitialLoad: Bool
  private var hasCompletedInitialRun: Bool = false
  private var activeTask: Task<Void, Never>?
  private var generation: UInt64 = 0

  public var isBusy: Bool {
    isModelLoading || isSummarizing
  }

  public var canSummarize: Bool {
    !isBusy
      && isReady
      && !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
  }

  public var activePresetID: String? {
    SummarizerPreset.defaults.first { $0.text == inputText }?.id
  }

  public var headerSubtitle: String {
    "\(selectedModel.displayName) · \(selectedMode.label) · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading \(selectedModel.displayName)…")
    }
    if isSummarizing {
      return .loading(streamingEnabled ? "Streaming summary…" : "Summarizing…")
    }
    if errorMessage != nil && !isReady {
      return .failed("Model unavailable")
    }
    if isReady {
      return .ready("Ready · On-Device")
    }
    return .loading("Initializing…")
  }

  public init(
    model: TextSummarizerModelOption = .gemma200M,
    mode: TextSummarizerModeOption = .keyPoints,
    maxTokens: TextSummarizerMaxTokensOption = .extended,
    delegate: TextSummarizerDelegateOption = .cpu,
    streamingEnabled: Bool = true,
    locator: SampleModelLocator = SampleModelLocator(),
    autoLoad: Bool = true,
    autoRunOnInitialLoad: Bool = false
  ) {
    self.inputText = SummarizerPreset.defaults[0].text
    self.selectedModel = model
    self.selectedMode = mode
    self.maxTokens = maxTokens
    self.selectedDelegate = delegate
    self.streamingEnabled = streamingEnabled
    self.helper = TextSummarizerHelper(locator: locator)
    self.autoRunOnInitialLoad = autoRunOnInitialLoad

    if autoLoad {
      scheduleReload()
    }
  }

  // MARK: - Synchronous UI Actions

  public func applyPreset(_ preset: SummarizerPreset) {
    inputText = preset.text
    streamingText = ""
    result = nil
    errorMessage = nil
  }

  public func selectMode(_ mode: TextSummarizerModeOption) {
    guard selectedMode != mode, !isBusy else { return }
    selectedMode = mode
    streamingText = ""
    result = nil
    scheduleReload()
  }

  public func selectMaxTokens(_ option: TextSummarizerMaxTokensOption) {
    guard maxTokens != option, !isBusy else { return }
    maxTokens = option
    streamingText = ""
    result = nil
    scheduleReload()
  }

  public func setStreamingEnabled(_ enabled: Bool) {
    streamingEnabled = enabled
  }

  public func switchToCpuDelegate() {
    if selectedDelegate == .cpu {
      scheduleReload()
    } else {
      selectedDelegate = .cpu
      streamingText = ""
      result = nil
      scheduleReload()
    }
  }

  public func retryInitialization() {
    guard !isBusy else { return }
    scheduleReload()
  }

  public func triggerSummarize() {
    guard canSummarize else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      await self?.summarize()
    }
  }

  // MARK: - Awaitable Operations (for Tests & Validation Hosts)

  /// Loads or reloads the summarizer with the current configuration and awaits completion.
  @discardableResult
  public func loadSummarizer() async -> Bool {
    generation &+= 1
    let currentGen = generation
    let model = selectedModel
    let mode = selectedMode
    let tokens = maxTokens
    let delegate = selectedDelegate

    isModelLoading = true
    isSummarizing = false
    isReady = false
    streamingText = ""
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(
        model: model,
        mode: mode,
        maxTokens: tokens,
        delegate: delegate
      )
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      isModelLoading = false
      isReady = true
      return true
    } catch let error as TextSummarizerError {
      guard generation == currentGen else { return false }
      isModelLoading = false
      isReady = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return false
    } catch {
      guard generation == currentGen else { return false }
      isModelLoading = false
      isReady = false
      errorMessage = error.localizedDescription
      isGpuError = delegate == .gpu
      return false
    }
  }

  /// Updates configuration fields in a single batch and reloads the summarizer once.
  @discardableResult
  public func configure(
    model: TextSummarizerModelOption? = nil,
    mode: TextSummarizerModeOption? = nil,
    maxTokens: TextSummarizerMaxTokensOption? = nil,
    delegate: TextSummarizerDelegateOption? = nil,
    streamingEnabled: Bool? = nil
  ) async -> Bool {
    activeTask?.cancel()
    if let model { selectedModel = model }
    if let mode { selectedMode = mode }
    if let maxTokens { self.maxTokens = maxTokens }
    if let delegate { selectedDelegate = delegate }
    if let streamingEnabled { self.streamingEnabled = streamingEnabled }
    streamingText = ""
    result = nil
    return await loadSummarizer()
  }

  /// Runs summarization on the current `inputText` and awaits completion.
  @discardableResult
  public func summarize(streaming: Bool? = nil) async -> TextSummarizerOutput? {
    guard canSummarize else { return nil }
    generation &+= 1
    let currentGen = generation
    let text = inputText
    let useStreaming = streaming ?? streamingEnabled

    isSummarizing = true
    streamingText = ""
    result = nil
    errorMessage = nil
    isGpuError = false

    do {
      let output: TextSummarizerOutput
      if useStreaming {
        output = try await helper.summarizeStreaming(text: text) { [weak self] chunk in
          Task { @MainActor [weak self] in
            guard let self, self.generation == currentGen else { return }
            self.streamingText += chunk
          }
        }
      } else {
        output = try await helper.summarize(text: text)
      }
      guard generation == currentGen else { return nil }
      isSummarizing = false
      streamingText = ""
      result = output
      return output
    } catch let error as TextSummarizerError {
      guard generation == currentGen else { return nil }
      isSummarizing = false
      streamingText = ""
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isSummarizing = false
      streamingText = ""
      errorMessage = error.localizedDescription
      isGpuError = false
      return nil
    }
  }

  private func scheduleReload() {
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let loaded = await self.loadSummarizer()
      if loaded && self.autoRunOnInitialLoad && !self.hasCompletedInitialRun {
        self.hasCompletedInitialRun = true
        _ = await self.summarize()
      }
    }
  }
}
