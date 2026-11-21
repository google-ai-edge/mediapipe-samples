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

/// Curated text sample preset for one-tap sentiment exploration.
public struct TextClassificationPreset: Identifiable, Equatable, Sendable {
  public let id: String
  public let label: String
  public let text: String

  public init(id: String, label: String, text: String) {
    self.id = id
    self.label = label
    self.text = text
  }

  public static let defaults: [TextClassificationPreset] = [
    TextClassificationPreset(
      id: "mediapipe",
      label: "MediaPipe",
      text:
        "MediaPipe delivers fast, customizable on-device machine learning solutions that keep user data private and responsive."
    ),
    TextClassificationPreset(
      id: "positive",
      label: "Positive",
      text:
        "I am so thrilled and grateful for this wonderful day, everything came together beautifully!"
    ),
    TextClassificationPreset(
      id: "negative",
      label: "Negative",
      text:
        "Unflinchingly bleak and desperate; a terribly dull, frustrating experience from start to finish."
    ),
    TextClassificationPreset(
      id: "mixed",
      label: "Mixed",
      text:
        "The cinematography is breathtaking, though the pacing drags badly in the middle act."
    ),
  ]
}

/// Observable MainActor ViewModel driving the MediaPipe Text Classifier sample.
@Observable
@MainActor
public final class TextClassifierViewModel {
  public var inputText: String {
    didSet {
      if oldValue != inputText {
        result = nil
      }
    }
  }

  public private(set) var selectedModel: TextClassifierModelOption
  public private(set) var selectedDelegate: TextClassifierDelegateOption

  public private(set) var isModelLoading: Bool = false
  public private(set) var isClassifying: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var modelLoadTimeMs: Double?
  public private(set) var result: TextClassifierOutput?
  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  private let helper: TextClassifierHelper
  private let autoClassifyOnInitialLoad: Bool
  private var hasCompletedInitialClassification: Bool = false
  private var activeTask: Task<Void, Never>?
  private var generation: UInt64 = 0

  public var isBusy: Bool {
    isModelLoading || isClassifying
  }

  public var canClassify: Bool {
    !isBusy
      && isReady
      && !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
  }

  public var activePresetID: String? {
    TextClassificationPreset.defaults.first { $0.text == inputText }?.id
  }

  public var headerSubtitle: String {
    "\(selectedModel.displayName) · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading \(selectedModel.displayName)…")
    }
    if isClassifying {
      return .loading("Classifying text…")
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
    model: TextClassifierModelOption = .mobileBert,
    delegate: TextClassifierDelegateOption = .cpu,
    locator: SampleModelLocator = SampleModelLocator(),
    autoLoad: Bool = true,
    autoClassifyOnInitialLoad: Bool = false
  ) {
    self.inputText = TextClassificationPreset.defaults[0].text
    self.selectedModel = model
    self.selectedDelegate = delegate
    self.helper = TextClassifierHelper(locator: locator)
    self.autoClassifyOnInitialLoad = autoClassifyOnInitialLoad

    if autoLoad {
      scheduleReload()
    }
  }

  // MARK: - Synchronous UI Actions

  public func applyPreset(_ preset: TextClassificationPreset) {
    inputText = preset.text
    result = nil
    errorMessage = nil
  }

  public func selectModel(_ model: TextClassifierModelOption) {
    guard selectedModel != model else { return }
    selectedModel = model
    result = nil
    scheduleReload()
  }

  public func selectDelegate(_ delegate: TextClassifierDelegateOption) {
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

  public func triggerClassify() {
    guard canClassify else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      await self?.classify()
    }
  }

  // MARK: - Awaitable Operations (for Tests & Validation Hosts)

  /// Loads or reloads the classifier with the current configuration and awaits completion.
  @discardableResult
  public func loadClassifier() async -> Bool {
    generation &+= 1
    let currentGen = generation
    let model = selectedModel
    let delegate = selectedDelegate

    isModelLoading = true
    isClassifying = false
    isReady = false
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(model: model, delegate: delegate)
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      isModelLoading = false
      isReady = true
      return true
    } catch let error as TextClassifierError {
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

  /// Updates configuration fields in a single batch and reloads the classifier once.
  @discardableResult
  public func configure(
    model: TextClassifierModelOption? = nil,
    delegate: TextClassifierDelegateOption? = nil
  ) async -> Bool {
    activeTask?.cancel()
    if let model { selectedModel = model }
    if let delegate { selectedDelegate = delegate }
    result = nil
    return await loadClassifier()
  }

  /// Classifies the current `inputText` and awaits completion.
  @discardableResult
  public func classify() async -> TextClassifierOutput? {
    guard canClassify else { return nil }
    generation &+= 1
    let currentGen = generation
    let text = inputText

    isClassifying = true
    errorMessage = nil
    isGpuError = false

    do {
      let output = try await helper.classify(text: text)
      guard generation == currentGen else { return nil }
      isClassifying = false
      result = output
      return output
    } catch let error as TextClassifierError {
      guard generation == currentGen else { return nil }
      isClassifying = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isClassifying = false
      errorMessage = error.localizedDescription
      isGpuError = false
      return nil
    }
  }

  private func scheduleReload() {
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let loaded = await self.loadClassifier()
      if loaded && self.autoClassifyOnInitialLoad && !self.hasCompletedInitialClassification {
        self.hasCompletedInitialClassification = true
        _ = await self.classify()
      }
    }
  }
}
