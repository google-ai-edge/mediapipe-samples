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
import ImageIO
import MediaPipeSampleUI
import Observation
import UIKit

/// A bundled sample photo located under `images/*.jpg` inside the app bundle.
public struct BundledSamplePhoto: Identifiable, Equatable, Hashable, Sendable {
  public let id: String
  public let label: String
  public let resourceName: String
  public let fileExtension: String
  public let subdirectory: String

  public init(
    id: String,
    label: String,
    resourceName: String? = nil,
    fileExtension: String = "jpg",
    subdirectory: String = "images"
  ) {
    self.id = id
    self.label = label
    self.resourceName = resourceName ?? id
    self.fileExtension = fileExtension
    self.subdirectory = subdirectory
  }

  public func url(in bundle: Bundle = .main) -> URL? {
    if let nested = bundle.url(
      forResource: resourceName,
      withExtension: fileExtension,
      subdirectory: subdirectory
    ) {
      return nested
    }
    return bundle.url(forResource: resourceName, withExtension: fileExtension)
  }

  public static let cuteCat = BundledSamplePhoto(id: "cute_cat", label: "Cute Cat")
  public static let yellowBanana = BundledSamplePhoto(id: "yellow_banana", label: "Yellow Banana")
  public static let sunnyBeach = BundledSamplePhoto(id: "sunny_beach", label: "Sunny Beach")
  public static let snowyMountain = BundledSamplePhoto(id: "snowy_mountain", label: "Snowy Mountain")
  public static let coffeeMug = BundledSamplePhoto(id: "coffee_mug", label: "Coffee Mug")
  public static let fastCar = BundledSamplePhoto(id: "fast_car", label: "Fast Car")
  public static let redApple = BundledSamplePhoto(id: "red_apple", label: "Red Apple")
  public static let openBook = BundledSamplePhoto(id: "open_book", label: "Open Book")
  public static let greenTree = BundledSamplePhoto(id: "green_tree", label: "Green Tree")
  public static let blueSky = BundledSamplePhoto(id: "blue_sky", label: "Blue Sky")

  public static let all: [BundledSamplePhoto] = [
    cuteCat,
    yellowBanana,
    sunnyBeach,
    snowyMountain,
    coffeeMug,
    fastCar,
    redApple,
    openBook,
    greenTree,
    blueSky,
  ]
}

/// Mutable state for one of the two multimodal comparison slots.
public struct EmbedderSlotState: Equatable, Sendable {
  public var modality: UniversalEmbedderModality
  public var text: String
  public var samplePhoto: BundledSamplePhoto
  public var customImageData: Data?
  public var customImageLabel: String?

  public init(
    modality: UniversalEmbedderModality,
    text: String,
    samplePhoto: BundledSamplePhoto,
    customImageData: Data? = nil,
    customImageLabel: String? = nil
  ) {
    self.modality = modality
    self.text = text
    self.samplePhoto = samplePhoto
    self.customImageData = customImageData
    self.customImageLabel = customImageLabel
  }

  public var isUsingCustomImage: Bool {
    customImageData != nil
  }

  public var activeImageLabel: String {
    customImageLabel ?? samplePhoto.label
  }

  public func resolvedInput(in bundle: Bundle = .main) -> UniversalEmbedderInput? {
    switch modality {
    case .text:
      let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
      return trimmed.isEmpty ? nil : .text(trimmed)
    case .image:
      if let customImageData, !customImageData.isEmpty {
        return .imageData(customImageData, label: activeImageLabel)
      }
      guard let url = samplePhoto.url(in: bundle) else { return nil }
      return .imageURL(url, label: samplePhoto.label)
    }
  }
}

/// Curated multimodal input pair preset for one-tap interactive exploration.
public struct MultimodalPairPreset: Identifiable, Equatable, Sendable {
  public let id: String
  public let label: String
  public let firstSlot: EmbedderSlotState
  public let secondSlot: EmbedderSlotState

  public init(
    id: String,
    label: String,
    firstSlot: EmbedderSlotState,
    secondSlot: EmbedderSlotState
  ) {
    self.id = id
    self.label = label
    self.firstSlot = firstSlot
    self.secondSlot = secondSlot
  }

  public static let defaults: [MultimodalPairPreset] = [
    MultimodalPairPreset(
      id: "text_image",
      label: "Text ↔ Image",
      firstSlot: EmbedderSlotState(
        modality: .text,
        text: "a cute fluffy cat",
        samplePhoto: .cuteCat
      ),
      secondSlot: EmbedderSlotState(
        modality: .image,
        text: "A fluffy tabby kitten sitting upright.",
        samplePhoto: .cuteCat
      )
    ),
    MultimodalPairPreset(
      id: "beach_text",
      label: "Beach ↔ Text",
      firstSlot: EmbedderSlotState(
        modality: .image,
        text: "A sunny sandy ocean beach.",
        samplePhoto: .sunnyBeach
      ),
      secondSlot: EmbedderSlotState(
        modality: .text,
        text: "warm sand on a sunny ocean beach",
        samplePhoto: .snowyMountain
      )
    ),
    MultimodalPairPreset(
      id: "text_text",
      label: "Text ↔ Text",
      firstSlot: EmbedderSlotState(
        modality: .text,
        text: "A golden retriever ran across the sunny sandy beach.",
        samplePhoto: .sunnyBeach
      ),
      secondSlot: EmbedderSlotState(
        modality: .text,
        text: "A happy dog sprinted along the ocean shoreline on warm sand.",
        samplePhoto: .cuteCat
      )
    ),
    MultimodalPairPreset(
      id: "image_image",
      label: "Image ↔ Image",
      firstSlot: EmbedderSlotState(
        modality: .image,
        text: "Sunny ocean beach",
        samplePhoto: .sunnyBeach
      ),
      secondSlot: EmbedderSlotState(
        modality: .image,
        text: "Snowy mountain peak",
        samplePhoto: .snowyMountain
      )
    ),
    MultimodalPairPreset(
      id: "unrelated",
      label: "Unrelated",
      firstSlot: EmbedderSlotState(
        modality: .text,
        text: "the quarterly tax filing deadline",
        samplePhoto: .coffeeMug
      ),
      secondSlot: EmbedderSlotState(
        modality: .image,
        text: "A fluffy tabby kitten sitting upright.",
        samplePhoto: .cuteCat
      )
    ),
  ]
}

/// Observable MainActor ViewModel driving the MediaPipe Universal Embedder sample.
@Observable
@MainActor
public final class UniversalEmbedderViewModel {
  public var slotA: EmbedderSlotState {
    didSet {
      if oldValue != slotA {
        result = nil
      }
    }
  }

  public var slotB: EmbedderSlotState {
    didSet {
      if oldValue != slotB {
        result = nil
      }
    }
  }

  public private(set) var selectedModel: UniversalEmbedderModelOption
  public private(set) var selectedDelegate: UniversalEmbedderDelegateOption
  public private(set) var l2Normalize: Bool

  public private(set) var isModelLoading: Bool = false
  public private(set) var isComparing: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var modelLoadTimeMs: Double?
  public private(set) var result: UniversalEmbedderComparisonResult?
  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  private let helper: UniversalEmbedderHelper
  private let resourceBundle: Bundle
  private let autoCompareOnInitialLoad: Bool
  private var hasCompletedInitialComparison: Bool = false
  private var activeTask: Task<Void, Never>?
  private var generation: UInt64 = 0
  private var thumbnailCache: [String: UIImage] = [:]

  public var isBusy: Bool {
    isModelLoading || isComparing
  }

  public var canCompare: Bool {
    !isBusy
      && isReady
      && slotA.resolvedInput(in: resourceBundle) != nil
      && slotB.resolvedInput(in: resourceBundle) != nil
  }

  public var activePresetID: String? {
    MultimodalPairPreset.defaults.first { preset in
      Self.slot(slotA, matches: preset.firstSlot) && Self.slot(slotB, matches: preset.secondSlot)
    }?.id
  }

  public var headerSubtitle: String {
    let dims = result?.dimensions ?? selectedModel.dimensions
    return "\(selectedModel.displayName) · \(dims)d · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading \(selectedModel.displayName)…")
    }
    if isComparing {
      return .loading("Embedding multimodal inputs…")
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
    model: UniversalEmbedderModelOption = .embeddingGemma2,
    delegate: UniversalEmbedderDelegateOption = .cpu,
    l2Normalize: Bool = true,
    locator: SampleModelLocator = SampleModelLocator(),
    resourceBundle: Bundle = .main,
    autoLoad: Bool = true,
    autoCompareOnInitialLoad: Bool = false
  ) {
    let initialPreset = MultimodalPairPreset.defaults[0]
    self.slotA = initialPreset.firstSlot
    self.slotB = initialPreset.secondSlot
    self.selectedModel = model
    self.selectedDelegate = delegate
    self.l2Normalize = l2Normalize
    self.helper = UniversalEmbedderHelper(locator: locator)
    self.resourceBundle = resourceBundle
    self.autoCompareOnInitialLoad = autoCompareOnInitialLoad

    if autoLoad {
      scheduleReload()
    }
  }

  // MARK: - Synchronous UI Actions

  public func applyPreset(_ preset: MultimodalPairPreset) {
    slotA = preset.firstSlot
    slotB = preset.secondSlot
    result = nil
    errorMessage = nil
  }

  public func setSlotA(_ slot: EmbedderSlotState) {
    slotA = slot
  }

  public func setSlotB(_ slot: EmbedderSlotState) {
    slotB = slot
  }

  public func setSlotAModality(_ modality: UniversalEmbedderModality) {
    guard slotA.modality != modality else { return }
    slotA.modality = modality
  }

  public func setSlotBModality(_ modality: UniversalEmbedderModality) {
    guard slotB.modality != modality else { return }
    slotB.modality = modality
  }

  public func selectSlotASamplePhoto(_ photo: BundledSamplePhoto) {
    slotA.modality = .image
    slotA.samplePhoto = photo
    slotA.customImageData = nil
    slotA.customImageLabel = nil
  }

  public func selectSlotBSamplePhoto(_ photo: BundledSamplePhoto) {
    slotB.modality = .image
    slotB.samplePhoto = photo
    slotB.customImageData = nil
    slotB.customImageLabel = nil
  }

  @discardableResult
  public func setSlotACustomImageData(_ rawData: Data, label: String = "Photo Library") -> Bool {
    guard let normalized = Self.normalizeImageData(rawData) else {
      errorMessage = "Could not decode the selected photo."
      return false
    }
    slotA.modality = .image
    slotA.customImageData = normalized
    slotA.customImageLabel = label
    errorMessage = nil
    return true
  }

  @discardableResult
  public func setSlotBCustomImageData(_ rawData: Data, label: String = "Photo Library") -> Bool {
    guard let normalized = Self.normalizeImageData(rawData) else {
      errorMessage = "Could not decode the selected photo."
      return false
    }
    slotB.modality = .image
    slotB.customImageData = normalized
    slotB.customImageLabel = label
    errorMessage = nil
    return true
  }

  public func swapInputs() {
    let previousA = slotA
    slotA = slotB
    slotB = previousA
    result = nil
  }

  public func selectDelegate(_ delegate: UniversalEmbedderDelegateOption) {
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

  // MARK: - Image Thumbnails

  public func thumbnail(for photo: BundledSamplePhoto) -> UIImage? {
    if let cached = thumbnailCache[photo.id] {
      return cached
    }
    guard let url = photo.url(in: resourceBundle) else { return nil }
    let image = Self.downsampledImage(at: url, maxPixelSize: 240) ?? UIImage(contentsOfFile: url.path)
    if let image {
      thumbnailCache[photo.id] = image
    }
    return image
  }

  public func displayImage(for slot: EmbedderSlotState) -> UIImage? {
    if let customImageData = slot.customImageData {
      return UIImage(data: customImageData)
    }
    return thumbnail(for: slot.samplePhoto)
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

    isModelLoading = true
    isComparing = false
    isReady = false
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(
        model: model,
        delegate: delegate,
        l2Normalize: normalize
      )
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      isModelLoading = false
      isReady = true
      return true
    } catch let error as UniversalEmbedderError {
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
    delegate: UniversalEmbedderDelegateOption? = nil,
    l2Normalize: Bool? = nil
  ) async -> Bool {
    activeTask?.cancel()
    if let delegate { selectedDelegate = delegate }
    if let l2Normalize { self.l2Normalize = l2Normalize }
    result = nil
    return await loadEmbedder()
  }

  /// Runs similarity comparison on the current `slotA` and `slotB` and awaits completion.
  @discardableResult
  public func compare() async -> UniversalEmbedderComparisonResult? {
    guard canCompare,
      let firstInput = slotA.resolvedInput(in: resourceBundle),
      let secondInput = slotB.resolvedInput(in: resourceBundle)
    else {
      return nil
    }
    return await compare(firstInput, secondInput)
  }

  /// Runs similarity comparison on explicit multimodal inputs and awaits completion.
  @discardableResult
  public func compare(
    _ firstInput: UniversalEmbedderInput,
    _ secondInput: UniversalEmbedderInput
  ) async -> UniversalEmbedderComparisonResult? {
    guard !isBusy, isReady else { return nil }
    generation &+= 1
    let currentGen = generation

    isComparing = true
    errorMessage = nil
    isGpuError = false

    do {
      let comparison = try await helper.compare(firstInput, secondInput)
      guard generation == currentGen else { return nil }
      isComparing = false
      result = comparison
      return comparison
    } catch let error as UniversalEmbedderError {
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

  // MARK: - Helpers

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

  private static func slot(_ current: EmbedderSlotState, matches preset: EmbedderSlotState) -> Bool {
    guard current.modality == preset.modality else { return false }
    switch current.modality {
    case .text:
      return current.text == preset.text
    case .image:
      return !current.isUsingCustomImage && current.samplePhoto == preset.samplePhoto
    }
  }

  private static func downsampledImage(at url: URL, maxPixelSize: CGFloat) -> UIImage? {
    guard let source = CGImageSourceCreateWithURL(url as CFURL, nil) else { return nil }
    let options: [CFString: Any] = [
      kCGImageSourceCreateThumbnailFromImageAlways: true,
      kCGImageSourceShouldCacheImmediately: true,
      kCGImageSourceCreateThumbnailWithTransform: true,
      kCGImageSourceThumbnailMaxPixelSize: maxPixelSize,
    ]
    guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary)
    else {
      return nil
    }
    return UIImage(cgImage: cgImage)
  }

  /// Normalizes picked photo data (including HEIC or rotated camera photos) to an up-oriented JPEG
  /// bounded at `maxDimension` pixels so LiteRT-LM's image decoder can ingest it reliably and fast.
  public static func normalizeImageData(_ rawData: Data, maxDimension: CGFloat = 1024) -> Data? {
    guard let image = UIImage(data: rawData) else { return nil }
    let originalSize = image.size
    guard originalSize.width > 0, originalSize.height > 0 else { return nil }

    let longestSide = max(originalSize.width, originalSize.height)
    let scaleRatio = longestSide > maxDimension ? (maxDimension / longestSide) : 1.0
    let targetSize = CGSize(
      width: max(1, (originalSize.width * scaleRatio).rounded()),
      height: max(1, (originalSize.height * scaleRatio).rounded())
    )

    let format = UIGraphicsImageRendererFormat()
    format.scale = 1.0
    format.opaque = true
    let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
    let rendered = renderer.image { _ in
      image.draw(in: CGRect(origin: .zero, size: targetSize))
    }
    return rendered.jpegData(compressionQuality: 0.9)
  }
}
