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

/// Curated sentence pair preset for one-tap interactive exploration.
public struct TextPairPreset: Identifiable, Equatable, Sendable {
  public let id: String
  public let label: String
  public let firstText: String
  public let secondText: String
  public let documentTitle: String
  public let preferredContext: TextEmbedderContextMode

  public init(
    id: String,
    label: String,
    firstText: String,
    secondText: String,
    documentTitle: String = "",
    preferredContext: TextEmbedderContextMode = .similarity
  ) {
    self.id = id
    self.label = label
    self.firstText = firstText
    self.secondText = secondText
    self.documentTitle = documentTitle
    self.preferredContext = preferredContext
  }

  public static let defaults: [TextPairPreset] = [
    TextPairPreset(
      id: "mediapipe",
      label: "MediaPipe",
      firstText:
        "MediaPipe offers cross-platform, customizable ML solutions for live and streaming media.",
      secondText:
        "I use MediaPipe to create amazing things with text, audio, and visual content."
    ),
    TextPairPreset(
      id: "similar",
      label: "Similar",
      firstText: "The golden retriever ran across the sunny sandy beach toward the ocean.",
      secondText: "A happy dog sprinted along the ocean shoreline on the warm sand."
    ),
    TextPairPreset(
      id: "retrieval",
      label: "Query → Doc",
      firstText: "How does on-device ML keep user data private?",
      secondText:
        "On-device machine learning runs inference directly on the user's phone without sending personal text to remote servers.",
      documentTitle: "On-Device Privacy Architecture",
      preferredContext: .retrieval
    ),
    TextPairPreset(
      id: "unrelated",
      label: "Unrelated",
      firstText: "The golden retriever ran across the sunny sandy beach toward the ocean.",
      secondText:
        "The federal reserve adjusted benchmark interest rates following the quarterly inflation report."
    ),
    TextPairPreset(
      id: "identical",
      label: "Identical",
      firstText: "On-device machine learning keeps user data private and responsive.",
      secondText: "On-device machine learning keeps user data private and responsive."
    ),
  ]
}

/// Observable MainActor ViewModel driving the MediaPipe Text Embedder sample.
@Observable
@MainActor
public final class TextEmbedderViewModel {
  public var firstText: String {
    didSet {
      if oldValue != firstText {
        result = nil
      }
    }
  }

  public var secondText: String {
    didSet {
      if oldValue != secondText {
        result = nil
      }
    }
  }

  public var documentTitle: String {
    didSet {
      if oldValue != documentTitle {
        result = nil
      }
    }
  }

  public private(set) var selectedModel: TextEmbedderModelOption
  public private(set) var selectedDelegate: TextEmbedderDelegateOption
  public private(set) var l2Normalize: Bool
  public private(set) var quantize: Bool
  public private(set) var contextMode: TextEmbedderContextMode

  public private(set) var isModelLoading: Bool = false
  public private(set) var isComparing: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var modelLoadTimeMs: Double?
  public private(set) var result: TextEmbedderComparisonResult?
  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  private let helper: TextEmbedderHelper
  private let autoCompareOnInitialLoad: Bool
  private var hasCompletedInitialComparison: Bool = false
  private var activeTask: Task<Void, Never>?
  private var generation: UInt64 = 0

  public var isBusy: Bool {
    isModelLoading || isComparing
  }

  public var canCompare: Bool {
    !isBusy
      && isReady
      && !firstText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
      && !secondText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
  }

  public var activePresetID: String? {
    TextPairPreset.defaults.first {
      $0.firstText == firstText && $0.secondText == secondText
    }?.id
  }

  public var headerSubtitle: String {
    "\(selectedModel.displayName) · \(selectedModel.dimensions)d · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading \(selectedModel.displayName)…")
    }
    if isComparing {
      return .loading("Computing embeddings…")
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
    model: TextEmbedderModelOption = .embeddingGemma,
    delegate: TextEmbedderDelegateOption = .cpu,
    l2Normalize: Bool = false,
    quantize: Bool = false,
    contextMode: TextEmbedderContextMode = .similarity,
    locator: SampleModelLocator = SampleModelLocator(),
    autoLoad: Bool = true,
    autoCompareOnInitialLoad: Bool = false
  ) {
    let initialPreset = TextPairPreset.defaults[0]
    self.firstText = initialPreset.firstText
    self.secondText = initialPreset.secondText
    self.documentTitle = initialPreset.documentTitle
    self.selectedModel = model
    self.selectedDelegate = delegate
    self.l2Normalize = l2Normalize
    self.quantize = quantize
    self.contextMode = contextMode
    self.helper = TextEmbedderHelper(locator: locator)
    self.autoCompareOnInitialLoad = autoCompareOnInitialLoad

    if autoLoad {
      scheduleReload()
    }
  }

  // MARK: - Synchronous UI Actions

  public func applyPreset(_ preset: TextPairPreset) {
    firstText = preset.firstText
    secondText = preset.secondText
    documentTitle = preset.documentTitle
    if selectedModel.supportsFormatContext {
      contextMode = preset.preferredContext
    }
    result = nil
    errorMessage = nil
  }

  public func selectContextMode(_ mode: TextEmbedderContextMode) {
    guard contextMode != mode else { return }
    contextMode = mode
    result = nil
  }

  public func swapTexts() {
    let previousFirst = firstText
    firstText = secondText
    secondText = previousFirst
    result = nil
  }

  public func selectModel(_ model: TextEmbedderModelOption) {
    guard selectedModel != model else { return }
    selectedModel = model
    result = nil
    scheduleReload()
  }

  public func selectDelegate(_ delegate: TextEmbedderDelegateOption) {
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

  public func setL2Normalize(_ enabled: Bool) {
    guard l2Normalize != enabled else { return }
    l2Normalize = enabled
    result = nil
    scheduleReload()
  }

  public func setQuantize(_ enabled: Bool) {
    guard quantize != enabled else { return }
    quantize = enabled
    result = nil
    scheduleReload()
  }

  public func retryInitialization() {
    scheduleReload()
  }

  public func triggerCompare() {
    guard canCompare else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      await self?.compare()
    }
  }

  // MARK: - Awaitable Operations (for Tests & Validation Hosts)

  /// Loads or reloads the embedder with the current configuration and awaits completion.
  @discardableResult
  public func loadEmbedder() async -> Bool {
    generation &+= 1
    let currentGen = generation
    let model = selectedModel
    let delegate = selectedDelegate
    let normalize = l2Normalize
    let quant = quantize

    isModelLoading = true
    isComparing = false
    isReady = false
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(
        model: model,
        delegate: delegate,
        l2Normalize: normalize,
        quantize: quant
      )
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      isModelLoading = false
      isReady = true
      return true
    } catch let error as TextEmbedderError {
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

  /// Updates configuration fields in a single batch and reloads the embedder once.
  @discardableResult
  public func configure(
    model: TextEmbedderModelOption? = nil,
    delegate: TextEmbedderDelegateOption? = nil,
    l2Normalize: Bool? = nil,
    quantize: Bool? = nil,
    contextMode: TextEmbedderContextMode? = nil
  ) async -> Bool {
    activeTask?.cancel()
    if let model { selectedModel = model }
    if let delegate { selectedDelegate = delegate }
    if let l2Normalize { self.l2Normalize = l2Normalize }
    if let quantize { self.quantize = quantize }
    if let contextMode { self.contextMode = contextMode }
    result = nil
    return await loadEmbedder()
  }

  /// Runs similarity comparison on the current `firstText` and `secondText` and awaits completion.
  @discardableResult
  public func compare() async -> TextEmbedderComparisonResult? {
    guard canCompare else { return nil }
    generation &+= 1
    let currentGen = generation
    let textA = firstText
    let textB = secondText
    let activeContext: TextEmbedderContextMode =
      selectedModel.supportsFormatContext ? contextMode : .none
    let title = documentTitle

    isComparing = true
    errorMessage = nil
    isGpuError = false

    do {
      let comparison = try await helper.compare(
        firstText: textA,
        secondText: textB,
        contextMode: activeContext,
        documentTitle: title
      )
      guard generation == currentGen else { return nil }
      isComparing = false
      result = comparison
      return comparison
    } catch let error as TextEmbedderError {
      guard generation == currentGen else { return nil }
      isComparing = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isComparing = false
      errorMessage = error.localizedDescription
      isGpuError = false
      return nil
    }
  }

  private func scheduleReload() {
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let loaded = await self.loadEmbedder()
      if loaded && self.autoCompareOnInitialLoad && !self.hasCompletedInitialComparison {
        self.hasCompletedInitialComparison = true
        _ = await self.compare()
      }
    }
  }
}
