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

/// Hardware execution delegate supported programmatically for validation harnesses.
public enum LanguageDetectorDelegateOption: String, CaseIterable, Identifiable, Sendable {
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

/// User-selectable upper bound on the number of ranked language predictions returned.
public enum LanguageDetectorMaxResultsOption: Int, CaseIterable, Identifiable, Sendable {
  case top1 = 1
  case top3 = 3
  case top5 = 5

  public var id: Int { rawValue }

  public var count: Int { rawValue }

  public var label: String {
    "Top \(rawValue)"
  }
}

/// Single predicted language with ISO/BCP-47 language code, localized display name, and score.
public struct LanguageDetectorPredictionItem: Identifiable, Equatable, Sendable {
  public var id: String { languageCode }
  public let languageCode: String
  public let displayName: String
  public let probability: Double

  public init(languageCode: String, displayName: String, probability: Double) {
    self.languageCode = languageCode
    self.displayName = displayName
    self.probability = probability
  }
}

/// Output of running language detection on a piece of text.
public struct LanguageDetectorDetectionResult: Equatable, Sendable {
  public let predictions: [LanguageDetectorPredictionItem]
  public let inferenceTimeMs: Double

  public var topPrediction: LanguageDetectorPredictionItem? {
    predictions.first
  }

  public init(predictions: [LanguageDetectorPredictionItem], inferenceTimeMs: Double) {
    self.predictions = predictions
    self.inferenceTimeMs = inferenceTimeMs
  }
}

/// Errors surfaced by ``LanguageDetectorHelper``.
public enum LanguageDetectorError: LocalizedError, Equatable, Sendable {
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
      return "Language Detector is not initialized."
    }
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksText.LanguageDetector`.
///
/// Runs model loading and inference off the main actor and surfaces explicit delegate errors
/// without silently falling back from GPU to CPU.
public actor LanguageDetectorHelper {
  public static let modelResourceName = "language_detector"
  public static let modelFileExtension = "tflite"
  public static let modelAssetFilename = "language_detector.tflite"
  public static let defaultScoreThreshold: Float = 0.001

  private let locator: SampleModelLocator
  private var detector: LanguageDetector?
  private var activeDelegate: LanguageDetectorDelegateOption = .cpu

  public init(locator: SampleModelLocator = SampleModelLocator()) {
    self.locator = locator
  }

  /// Loads or reloads `LanguageDetector` with the requested delegate, max results, and threshold.
  ///
  /// Returns the elapsed model load duration in milliseconds.
  public func load(
    delegate: LanguageDetectorDelegateOption = .cpu,
    maxResults: Int = LanguageDetectorMaxResultsOption.top3.count,
    scoreThreshold: Float = LanguageDetectorHelper.defaultScoreThreshold
  ) throws -> Double {
    detector = nil
    activeDelegate = delegate

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: Self.modelResourceName,
        fileExtension: Self.modelFileExtension
      )
    } catch {
      throw LanguageDetectorError.initializationFailed(error.localizedDescription)
    }

    let options = LanguageDetectorOptions()
    options.baseOptions.modelAssetPath = modelURL.path
    options.baseOptions.delegate = delegate.mediaPipeDelegate
    options.maxResults = maxResults
    options.scoreThreshold = scoreThreshold

    let start = ContinuousClock.now
    do {
      detector = try LanguageDetector(options: options)
    } catch {
      if delegate == .gpu {
        throw LanguageDetectorError.gpuUnsupported(error.localizedDescription)
      }
      throw LanguageDetectorError.initializationFailed(error.localizedDescription)
    }
    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases the underlying MediaPipe task instance.
  public func close() {
    detector = nil
  }

  /// Predicts candidate languages for `text` sorted by descending probability.
  public func detect(text: String) throws -> LanguageDetectorDetectionResult {
    guard let detector else { throw LanguageDetectorError.notInitialized }

    let start = ContinuousClock.now
    let rawPredictions: [LanguagePrediction]
    do {
      let result = try detector.detect(text: text)
      rawPredictions = result.languages
    } catch let error as LanguageDetectorError {
      throw error
    } catch {
      if activeDelegate == .gpu {
        throw LanguageDetectorError.gpuUnsupported(error.localizedDescription)
      }
      throw LanguageDetectorError.inferenceFailed(error.localizedDescription)
    }

    let elapsedMs = Self.elapsedMilliseconds(since: start)
    let predictions = rawPredictions
      .sorted { $0.probability > $1.probability }
      .map { prediction in
        LanguageDetectorPredictionItem(
          languageCode: prediction.languageCode,
          displayName: Self.resolveLanguageName(for: prediction.languageCode),
          probability: Double(prediction.probability)
        )
      }

    return LanguageDetectorDetectionResult(
      predictions: predictions,
      inferenceTimeMs: elapsedMs
    )
  }

  /// Resolves a localized human-readable language name from a BCP-47 / ISO-639 code.
  public static func resolveLanguageName(
    for rawCode: String,
    locale: Locale = .current
  ) -> String {
    let trimmed = rawCode.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.isEmpty
      || trimmed.caseInsensitiveCompare("unknown") == .orderedSame
      || trimmed.caseInsensitiveCompare("und") == .orderedSame
    {
      return "Unknown"
    }

    let normalized: String
    switch trimmed.lowercased() {
    case "iw": normalized = "he"
    case "ji": normalized = "yi"
    case "in": normalized = "id"
    default: normalized = trimmed
    }

    let localized: String?
    if normalized.contains("-") || normalized.contains("_") {
      localized =
        locale.localizedString(forIdentifier: normalized)
        ?? locale.localizedString(forLanguageCode: normalized)
    } else {
      localized =
        locale.localizedString(forLanguageCode: normalized)
        ?? locale.localizedString(forIdentifier: normalized)
    }

    guard let localized, !localized.isEmpty,
      localized.caseInsensitiveCompare(trimmed) != .orderedSame
    else {
      return trimmed.uppercased()
    }

    guard let first = localized.first else { return localized }
    return String(first).localizedUppercase + localized.dropFirst()
  }

  private static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}
