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
@preconcurrency import MediaPipeTasksText

/// Supported MediaPipe text classification models bundled with this sample.
public enum TextClassifierModelOption: String, CaseIterable, Identifiable, Sendable {
  case mobileBert = "bert_classifier"
  case wordVec = "average_word_classifier"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .mobileBert: return "MobileBERT"
    case .wordVec: return "WordVec"
    }
  }

  public var fullName: String {
    switch self {
    case .mobileBert: return "MobileBERT"
    case .wordVec: return "Average WordVec"
    }
  }

  public var architectureLabel: String {
    switch self {
    case .mobileBert: return "Transformer"
    case .wordVec: return "Bag of Words"
    }
  }

  public var summary: String {
    switch self {
    case .mobileBert:
      return "Contextual transformer classifier for high sentiment accuracy."
    case .wordVec:
      return "Compact bag-of-words classifier with sub-millisecond latency."
    }
  }

  public var resourceName: String {
    rawValue
  }

  public var fileExtension: String {
    "tflite"
  }

  public var assetFilename: String {
    "\(resourceName).\(fileExtension)"
  }
}

/// Hardware execution delegate supported programmatically by MediaPipe `BaseOptions`.
public enum TextClassifierDelegateOption: String, CaseIterable, Identifiable, Sendable {
  case cpu = "CPU"
  case gpu = "GPU"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .cpu: return "CPU"
    case .gpu: return "GPU (Metal)"
    }
  }

  public var shortName: String { rawValue }

  var mediaPipeDelegate: Delegate {
    switch self {
    case .cpu: return .CPU
    case .gpu: return .GPU
    }
  }
}

/// Normalized sentiment polarity derived from a classification category.
public enum SentimentPolarity: String, Equatable, Sendable {
  case positive
  case negative
  case other

  init(displayLabel: String) {
    switch displayLabel.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
    case "positive":
      self = .positive
    case "negative":
      self = .negative
    default:
      self = .other
    }
  }
}

/// A single ranked classification category with both raw model label and normalized UI label.
public struct TextClassifierCategory: Identifiable, Equatable, Sendable {
  public let index: Int
  public let rawLabel: String
  public let displayLabel: String
  public let score: Float

  public init(
    index: Int,
    rawLabel: String,
    displayLabel: String,
    score: Float
  ) {
    self.index = index
    self.rawLabel = rawLabel
    self.displayLabel = displayLabel
    self.score = score
  }

  public var id: String {
    "\(index)_\(rawLabel)"
  }

  public var polarity: SentimentPolarity {
    SentimentPolarity(displayLabel: displayLabel)
  }
}

/// Output of classifying a piece of text.
public struct TextClassifierOutput: Equatable, Sendable {
  public let categories: [TextClassifierCategory]
  public let inferenceTimeMs: Double

  public init(categories: [TextClassifierCategory], inferenceTimeMs: Double) {
    self.categories = categories
    self.inferenceTimeMs = inferenceTimeMs
  }

  public var topCategory: TextClassifierCategory? {
    categories.first
  }
}

/// Errors surfaced by ``TextClassifierHelper``.
public enum TextClassifierError: LocalizedError, Equatable, Sendable {
  case gpuUnsupported(String)
  case initializationFailed(String)
  case inferenceFailed(String)
  case notInitialized

  public var isGpuError: Bool {
    if case .gpuUnsupported = self { return true }
    return false
  }

  public var errorDescription: String? {
    switch self {
    case .gpuUnsupported(let message):
      return message
    case .initializationFailed(let message):
      return message
    case .inferenceFailed(let message):
      return message
    case .notInitialized:
      return "Text Classifier is not initialized."
    }
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksText.TextClassifier`.
///
/// Runs model loading and sentiment inference off the main actor and surfaces explicit delegate
/// errors without silently falling back from GPU to CPU.
public actor TextClassifierHelper {
  private let locator: SampleModelLocator
  private var classifier: TextClassifier?
  private var activeModel: TextClassifierModelOption = .mobileBert
  private var activeDelegate: TextClassifierDelegateOption = .cpu

  public init(locator: SampleModelLocator = SampleModelLocator()) {
    self.locator = locator
  }

  /// Loads or reloads `TextClassifier` with the requested model and delegate.
  ///
  /// Returns the elapsed model load duration in milliseconds.
  public func load(
    model: TextClassifierModelOption,
    delegate: TextClassifierDelegateOption = .cpu
  ) throws -> Double {
    classifier = nil
    activeModel = model
    activeDelegate = delegate

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: model.resourceName,
        fileExtension: model.fileExtension
      )
    } catch {
      throw TextClassifierError.initializationFailed(error.localizedDescription)
    }

    let options = TextClassifierOptions()
    options.baseOptions.modelAssetPath = modelURL.path
    options.baseOptions.delegate = delegate.mediaPipeDelegate

    let start = ContinuousClock.now
    do {
      classifier = try TextClassifier(options: options)
    } catch {
      if delegate == .gpu {
        throw TextClassifierError.gpuUnsupported(error.localizedDescription)
      }
      throw TextClassifierError.initializationFailed(error.localizedDescription)
    }
    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases the underlying MediaPipe task instance.
  public func close() {
    classifier = nil
  }

  /// Classifies `text` and returns categories sorted descending by confidence score.
  public func classify(text: String) throws -> TextClassifierOutput {
    guard let classifier else { throw TextClassifierError.notInitialized }

    let start = ContinuousClock.now
    let result: TextClassifierResult
    do {
      result = try classifier.classify(text: text)
    } catch {
      if activeDelegate == .gpu {
        throw TextClassifierError.gpuUnsupported(error.localizedDescription)
      }
      throw TextClassifierError.inferenceFailed(error.localizedDescription)
    }
    let elapsedMs = Self.elapsedMilliseconds(since: start)

    let rawCategories =
      result.classificationResult.classifications.first?.categories ?? []
    guard !rawCategories.isEmpty else {
      throw TextClassifierError.inferenceFailed("Model returned no classification categories.")
    }

    let model = activeModel
    let categories = rawCategories
      .sorted { $0.score > $1.score }
      .map { category in
        let rawLabel = category.categoryName ?? "\(category.index)"
        let preferredLabel =
          (category.displayName?.isEmpty == false) ? category.displayName! : rawLabel
        return TextClassifierCategory(
          index: category.index,
          rawLabel: rawLabel,
          displayLabel: Self.normalizeLabel(model: model, rawLabel: preferredLabel),
          score: category.score
        )
      }

    return TextClassifierOutput(
      categories: categories,
      inferenceTimeMs: elapsedMs
    )
  }

  /// Normalizes model-specific raw labels (e.g. WordVec `"1"`/`"0"`) into human-readable titles.
  public static func normalizeLabel(
    model: TextClassifierModelOption,
    rawLabel: String
  ) -> String {
    let trimmed = rawLabel.trimmingCharacters(in: .whitespacesAndNewlines)
    let mapped: String
    if model == .wordVec {
      switch trimmed {
      case "1": mapped = "Positive"
      case "0": mapped = "Negative"
      default: mapped = trimmed
      }
    } else {
      mapped = trimmed
    }

    guard let first = mapped.first else { return mapped }
    return first.uppercased() + mapped.dropFirst()
  }

  private static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}
