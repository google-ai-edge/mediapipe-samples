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

/// Curated multilingual sentence preset for one-tap interactive language detection.
public struct LanguagePreset: Identifiable, Equatable, Sendable {
  public let id: String
  public let label: String
  public let expectedLanguageCode: String
  public let text: String

  public init(id: String, label: String, expectedLanguageCode: String, text: String) {
    self.id = id
    self.label = label
    self.expectedLanguageCode = expectedLanguageCode
    self.text = text
  }

  public static let defaults: [LanguagePreset] = [
    LanguagePreset(
      id: "french",
      label: "Français",
      expectedLanguageCode: "fr",
      text: "Bonjour tout le monde, comment allez-vous aujourd'hui ?"
    ),
    LanguagePreset(
      id: "chinese",
      label: "中文",
      expectedLanguageCode: "zh",
      text: "你好！我爱小猫"
    ),
    LanguagePreset(
      id: "english",
      label: "English",
      expectedLanguageCode: "en",
      text:
        "MediaPipe offers cross-platform, customizable ML solutions for live and streaming media."
    ),
    LanguagePreset(
      id: "spanish",
      label: "Español",
      expectedLanguageCode: "es",
      text: "Hola, me encanta aprender nuevos idiomas con inteligencia artificial."
    ),
    LanguagePreset(
      id: "german",
      label: "Deutsch",
      expectedLanguageCode: "de",
      text:
        "Hallo Welt, maschinelles Lernen auf dem Endgerät ist schnell und datenschutzfreundlich."
    ),
    LanguagePreset(
      id: "japanese",
      label: "日本語",
      expectedLanguageCode: "ja",
      text: "こんにちは、今日はとても良い天気ですね。"
    ),
    LanguagePreset(
      id: "russian",
      label: "Русский",
      expectedLanguageCode: "ru",
      text: "Привет мир! Машинное обучение на устройстве работает быстро и безопасно."
    ),
  ]
}

/// Observable MainActor ViewModel driving the MediaPipe Language Detector sample.
@Observable
@MainActor
public final class LanguageDetectorViewModel {
  public var inputText: String {
    didSet {
      if oldValue != inputText {
        result = nil
      }
    }
  }

  public private(set) var maxResultsOption: LanguageDetectorMaxResultsOption
  public private(set) var maxResults: Int
  public private(set) var scoreThreshold: Float
  public private(set) var selectedDelegate: LanguageDetectorDelegateOption

  public private(set) var isModelLoading: Bool = false
  public private(set) var isDetecting: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var modelLoadTimeMs: Double?
  public private(set) var result: LanguageDetectorDetectionResult?
  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  private let helper: LanguageDetectorHelper
  private let autoDetectOnInitialLoad: Bool
  private var hasCompletedInitialDetection: Bool = false
  private var activeTask: Task<Void, Never>?
  private var generation: UInt64 = 0

  public var isBusy: Bool {
    isModelLoading || isDetecting
  }

  public var canDetect: Bool {
    !isBusy
      && isReady
      && !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
  }

  public var activePresetID: String? {
    LanguagePreset.defaults.first { $0.text == inputText }?.id
  }

  public var headerSubtitle: String {
    let limitLabel = maxResults > 0 ? "Top \(maxResults)" : "All Results"
    return "\(limitLabel) · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading detector…")
    }
    if isDetecting {
      return .loading("Detecting language…")
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
    maxResultsOption: LanguageDetectorMaxResultsOption = .top3,
    scoreThreshold: Float = LanguageDetectorHelper.defaultScoreThreshold,
    delegate: LanguageDetectorDelegateOption = .cpu,
    locator: SampleModelLocator = SampleModelLocator(),
    autoLoad: Bool = true,
    autoDetectOnInitialLoad: Bool = false
  ) {
    self.inputText = LanguagePreset.defaults[0].text
    self.maxResultsOption = maxResultsOption
    self.maxResults = maxResultsOption.count
    self.scoreThreshold = scoreThreshold
    self.selectedDelegate = delegate
    self.helper = LanguageDetectorHelper(locator: locator)
    self.autoDetectOnInitialLoad = autoDetectOnInitialLoad

    if autoLoad {
      scheduleReload()
    }
  }

  // MARK: - Synchronous UI Actions

  public func applyPreset(_ preset: LanguagePreset) {
    inputText = preset.text
    result = nil
    errorMessage = nil
  }

  public func selectMaxResults(_ option: LanguageDetectorMaxResultsOption) {
    guard maxResultsOption != option || maxResults != option.count else { return }
    maxResultsOption = option
    maxResults = option.count
    result = nil
    scheduleReload()
  }

  public func selectDelegate(_ delegate: LanguageDetectorDelegateOption) {
    guard selectedDelegate != delegate else { return }
    selectedDelegate = delegate
    result = nil
    scheduleReload()
  }

  public func switchToCpuDelegate() {
    if selectedDelegate == .cpu {
      scheduleReload()
    } else {
      selectDelegate(.cpu)
    }
  }

  public func retryInitialization() {
    scheduleReload()
  }

  public func triggerDetect() {
    guard canDetect else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      await self?.detect()
    }
  }

  // MARK: - Awaitable Operations (for Tests & Validation Hosts)

  /// Loads or reloads the detector with the current configuration and awaits completion.
  @discardableResult
  public func loadDetector() async -> Bool {
    generation &+= 1
    let currentGen = generation
    let delegate = selectedDelegate
    let limit = maxResults
    let threshold = scoreThreshold

    isModelLoading = true
    isDetecting = false
    isReady = false
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(
        delegate: delegate,
        maxResults: limit,
        scoreThreshold: threshold
      )
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      isModelLoading = false
      isReady = true
      return true
    } catch let error as LanguageDetectorError {
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

  /// Updates configuration fields in a single batch and reloads the detector once.
  @discardableResult
  public func configure(
    delegate: LanguageDetectorDelegateOption? = nil,
    maxResults: Int? = nil,
    scoreThreshold: Float? = nil
  ) async -> Bool {
    activeTask?.cancel()
    if let delegate { selectedDelegate = delegate }
    if let maxResults {
      self.maxResults = maxResults
      if let matchingOption = LanguageDetectorMaxResultsOption(rawValue: maxResults) {
        maxResultsOption = matchingOption
      }
    }
    if let scoreThreshold { self.scoreThreshold = scoreThreshold }
    result = nil
    return await loadDetector()
  }

  /// Runs language detection on the current `inputText` and awaits completion.
  @discardableResult
  public func detect() async -> LanguageDetectorDetectionResult? {
    guard canDetect else { return nil }
    generation &+= 1
    let currentGen = generation
    let text = inputText

    isDetecting = true
    errorMessage = nil
    isGpuError = false

    do {
      let detection = try await helper.detect(text: text)
      guard generation == currentGen else { return nil }
      isDetecting = false
      result = detection
      return detection
    } catch let error as LanguageDetectorError {
      guard generation == currentGen else { return nil }
      isDetecting = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isDetecting = false
      errorMessage = error.localizedDescription
      isGpuError = false
      return nil
    }
  }

  /// Updates `inputText` to `text` and runs language detection immediately.
  @discardableResult
  public func detect(text: String) async -> LanguageDetectorDetectionResult? {
    inputText = text
    return await detect()
  }

  private func scheduleReload() {
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let loaded = await self.loadDetector()
      if loaded && self.autoDetectOnInitialLoad && !self.hasCompletedInitialDetection {
        self.hasCompletedInitialDetection = true
        _ = await self.detect()
      }
    }
  }
}
