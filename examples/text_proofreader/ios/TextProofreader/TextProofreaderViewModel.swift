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

/// Curated text sample preset for one-tap interactive proofreading.
public struct ProofreaderPreset: Identifiable, Equatable, Sendable {
  public let id: String
  public let label: String
  public let text: String

  public init(id: String, label: String, text: String) {
    self.id = id
    self.label = label
    self.text = text
  }

  public static let defaults: [ProofreaderPreset] = [
    ProofreaderPreset(
      id: "grammar",
      label: "Grammar",
      text: "I seen him yesterday at store, and he dont want no help from nobody."
    ),
    ProofreaderPreset(
      id: "tense",
      label: "Spelling & Tense",
      text: "She go to school evry day and always forgeting her homework on desk."
    ),
    ProofreaderPreset(
      id: "email",
      label: "Meeting Note",
      text: "Please send me the report by tommorow morning so we can reviewed it together."
    ),
    ProofreaderPreset(
      id: "clean",
      label: "Clean Text",
      text: "MediaPipe helps developers build fast, on-device AI experiences for iOS."
    ),
  ]
}

/// Output presentation mode inside the Proofread Result card.
public enum ProofreadDisplayMode: String, CaseIterable, Identifiable, Sendable {
  case diff = "Diff"
  case corrected = "Corrected"

  public var id: String { rawValue }
}

/// Observable MainActor ViewModel driving the MediaPipe Text Proofreader sample.
@Observable
@MainActor
public final class TextProofreaderViewModel {
  public var inputText: String {
    didSet {
      if oldValue != inputText {
        result = nil
        streamingText = ""
        errorMessage = nil
      }
    }
  }

  public private(set) var selectedModel: TextProofreaderModelOption
  public private(set) var selectedDelegate: TextProofreaderDelegateOption
  public private(set) var maxTokensOption: TextProofreaderMaxTokensOption
  public private(set) var isStreamingEnabled: Bool
  public var displayMode: ProofreadDisplayMode

  public private(set) var isModelLoading: Bool = false
  public private(set) var isProofreading: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var modelLoadTimeMs: Double?
  public private(set) var streamingText: String = ""
  public private(set) var result: TextProofreaderOutput?
  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  private let helper: TextProofreaderHelper
  private let autoRunOnInitialLoad: Bool
  private var hasCompletedInitialRun: Bool = false
  private var activeTask: Task<Void, Never>?
  private var generation: UInt64 = 0
  private var lastStreamSequence: Int = 0

  public var isBusy: Bool {
    isModelLoading || isProofreading
  }

  public var canProofread: Bool {
    !isBusy
      && isReady
      && !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
  }

  public var activePresetID: String? {
    ProofreaderPreset.defaults.first { $0.text == inputText }?.id
  }

  public var headerSubtitle: String {
    "\(selectedModel.displayName) · \(maxTokensOption.shortLabel) · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading \(selectedModel.displayName)…")
    }
    if isProofreading {
      return .loading(isStreamingEnabled ? "Streaming proofread…" : "Proofreading…")
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
    model: TextProofreaderModelOption = .gemma200M,
    delegate: TextProofreaderDelegateOption = .cpu,
    maxTokensOption: TextProofreaderMaxTokensOption = .balanced,
    isStreamingEnabled: Bool = true,
    displayMode: ProofreadDisplayMode = .diff,
    locator: SampleModelLocator = SampleModelLocator(),
    autoLoad: Bool = true,
    autoRunOnInitialLoad: Bool = false
  ) {
    self.inputText = ProofreaderPreset.defaults[0].text
    self.selectedModel = model
    self.selectedDelegate = delegate
    self.maxTokensOption = maxTokensOption
    self.isStreamingEnabled = isStreamingEnabled
    self.displayMode = displayMode
    self.helper = TextProofreaderHelper(locator: locator)
    self.autoRunOnInitialLoad = autoRunOnInitialLoad

    if autoLoad {
      scheduleReload()
    }
  }

  // MARK: - Synchronous UI Actions

  public func applyPreset(_ preset: ProofreaderPreset) {
    inputText = preset.text
    result = nil
    streamingText = ""
    errorMessage = nil
  }

  public func applyCorrectedToInput() {
    guard let corrected = result?.proofreadText.trimmingCharacters(in: .whitespacesAndNewlines),
      !corrected.isEmpty
    else {
      return
    }
    inputText = corrected
    result = nil
    streamingText = ""
    errorMessage = nil
  }

  public func selectMaxTokens(_ option: TextProofreaderMaxTokensOption) {
    guard maxTokensOption != option else { return }
    maxTokensOption = option
    result = nil
    streamingText = ""
    scheduleReload()
  }

  public func setStreamingEnabled(_ enabled: Bool) {
    guard isStreamingEnabled != enabled else { return }
    isStreamingEnabled = enabled
  }

  public func switchToCpuDelegate() {
    if selectedDelegate == .cpu {
      scheduleReload()
    } else {
      selectedDelegate = .cpu
      result = nil
      streamingText = ""
      scheduleReload()
    }
  }

  public func retryInitialization() {
    scheduleReload()
  }

  public func triggerProofread() {
    guard canProofread else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      await self?.proofread()
    }
  }

  // MARK: - Awaitable Operations (for Tests & Validation Hosts)

  /// Loads or reloads the proofreader with the current configuration and awaits completion.
  @discardableResult
  public func loadProofreader() async -> Bool {
    generation &+= 1
    let currentGen = generation
    let model = selectedModel
    let delegate = selectedDelegate
    let maxTokens = maxTokensOption.tokenCount

    isModelLoading = true
    isProofreading = false
    isReady = false
    streamingText = ""
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(
        model: model,
        delegate: delegate,
        maxTokens: maxTokens
      )
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      isModelLoading = false
      isReady = true
      return true
    } catch let error as TextProofreaderError {
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

  /// Updates configuration fields in a single batch and reloads the proofreader once.
  @discardableResult
  public func configure(
    model: TextProofreaderModelOption? = nil,
    delegate: TextProofreaderDelegateOption? = nil,
    maxTokensOption: TextProofreaderMaxTokensOption? = nil,
    streamingEnabled: Bool? = nil
  ) async -> Bool {
    activeTask?.cancel()
    if let model { selectedModel = model }
    if let delegate { selectedDelegate = delegate }
    if let maxTokensOption { self.maxTokensOption = maxTokensOption }
    if let streamingEnabled { self.isStreamingEnabled = streamingEnabled }
    result = nil
    streamingText = ""
    return await loadProofreader()
  }

  /// Runs proofreading on `inputText` (unary or streaming) and awaits completion.
  @discardableResult
  public func proofread(streaming: Bool? = nil) async -> TextProofreaderOutput? {
    guard canProofread else { return nil }
    generation &+= 1
    let currentGen = generation
    let text = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
    let useStreaming = streaming ?? isStreamingEnabled

    isProofreading = true
    streamingText = ""
    result = nil
    errorMessage = nil
    isGpuError = false
    lastStreamSequence = 0

    do {
      let output: TextProofreaderOutput
      if useStreaming {
        output = try await helper.proofreadStreaming(text: text) { [weak self] update in
          Task { @MainActor [weak self] in
            guard let self, self.generation == currentGen, update.sequence > self.lastStreamSequence
            else {
              return
            }
            self.lastStreamSequence = update.sequence
            self.streamingText = update.accumulatedText
          }
        }
      } else {
        output = try await helper.proofread(text: text)
      }

      guard generation == currentGen else { return nil }
      isProofreading = false
      streamingText = ""
      result = output
      return output
    } catch let error as TextProofreaderError {
      guard generation == currentGen else { return nil }
      isProofreading = false
      streamingText = ""
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isProofreading = false
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
      let loaded = await self.loadProofreader()
      if loaded && self.autoRunOnInitialLoad && !self.hasCompletedInitialRun {
        self.hasCompletedInitialRun = true
        _ = await self.proofread()
      }
    }
  }
}
