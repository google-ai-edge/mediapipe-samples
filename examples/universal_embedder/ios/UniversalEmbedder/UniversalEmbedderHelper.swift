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
@preconcurrency import MediaPipeTasksRetrieval
import UIKit

/// Supported multimodal MediaPipe embedding models bundled with this sample.
public enum UniversalEmbedderModelOption: String, CaseIterable, Identifiable, Sendable {
  case embeddingGemma2 = "embeddinggemma-2-text-vision-440m"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .embeddingGemma2: return "Gemma 2 440M"
    }
  }

  public var fullName: String {
    switch self {
    case .embeddingGemma2: return "EmbeddingGemma 2 (440M)"
    }
  }

  public var dimensions: Int {
    switch self {
    case .embeddingGemma2: return 768
    }
  }

  public var summary: String {
    switch self {
    case .embeddingGemma2:
      return "768d shared text & vision embedding space running locally via LiteRT-LM."
    }
  }

  public var resourceName: String {
    switch self {
    case .embeddingGemma2: return "embeddinggemma-2-text-vision-440m"
    }
  }

  public var fileExtension: String {
    switch self {
    case .embeddingGemma2: return "litertlm"
    }
  }

  public var assetFilename: String {
    "\(resourceName).\(fileExtension)"
  }
}

/// Hardware execution delegate supported by `UniversalEmbedder`.
public enum UniversalEmbedderDelegateOption: String, CaseIterable, Identifiable, Sendable {
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

/// Input modality supported in each comparison slot.
public enum UniversalEmbedderModality: String, CaseIterable, Identifiable, Equatable, Sendable {
  case text
  case image

  public var id: String { rawValue }

  public var label: String {
    switch self {
    case .text: return "Text"
    case .image: return "Image"
    }
  }

  public var shortLabel: String {
    switch self {
    case .text: return "Text"
    case .image: return "Img"
    }
  }

  public var systemImage: String {
    switch self {
    case .text: return "text.alignleft"
    case .image: return "photo"
    }
  }
}

/// A single multimodal input passed to ``UniversalEmbedderHelper``.
public enum UniversalEmbedderInput: Equatable, Sendable {
  case text(String)
  case imageData(Data, label: String = "Image")
  case imageURL(URL, label: String? = nil)

  public var modality: UniversalEmbedderModality {
    switch self {
    case .text: return .text
    case .imageData, .imageURL: return .image
    }
  }

  public var displayLabel: String {
    switch self {
    case .text: return "Text"
    case .imageData(_, let label): return label
    case .imageURL(let url, let label): return label ?? url.deletingPathExtension().lastPathComponent
    }
  }
}

/// Output of embedding a single text or image input.
public struct UniversalEmbedderSingleResult: Equatable, Sendable {
  public let floatEmbedding: [Float]
  public let dimensions: Int
  public let l2Norm: Double
  public let modality: UniversalEmbedderModality
  public let inferenceTimeMs: Double
}

/// Output of comparing two multimodal inputs via cosine similarity.
public struct UniversalEmbedderComparisonResult: Equatable, Sendable {
  public let similarity: Double
  public let dimensions: Int
  public let firstModality: UniversalEmbedderModality
  public let secondModality: UniversalEmbedderModality
  public let firstNorm: Double
  public let secondNorm: Double
  public let firstInferenceTimeMs: Double
  public let secondInferenceTimeMs: Double
  public let totalInferenceTimeMs: Double

  public var modalitiesLabel: String {
    "\(firstModality.shortLabel) ↔ \(secondModality.shortLabel)"
  }
}

/// Errors surfaced by ``UniversalEmbedderHelper``.
public enum UniversalEmbedderError: LocalizedError, Equatable, Sendable {
  case gpuUnsupported(String)
  case initializationFailed(String)
  case inferenceFailed(String)
  case invalidImage(String)
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
    case .invalidImage(let message):
      return message
    case .notInitialized:
      return "Universal Embedder is not initialized."
    }
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksRetrieval.UniversalEmbedder`.
///
/// Runs model loading and multimodal embedding extraction off the main actor, keeps LiteRT-LM
/// weight/compiled caches in a writable Caches subdirectory, and surfaces explicit delegate
/// errors without silently falling back from GPU to CPU.
public actor UniversalEmbedderHelper {
  private let locator: SampleModelLocator
  private let cacheDirectoryURL: URL
  private var embedder: UniversalEmbedder?

  public init(
    locator: SampleModelLocator = SampleModelLocator(),
    cacheDirectoryURL: URL? = nil
  ) {
    self.locator = locator
    if let cacheDirectoryURL {
      self.cacheDirectoryURL = cacheDirectoryURL
    } else {
      let cachesRoot =
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
        ?? FileManager.default.temporaryDirectory
      self.cacheDirectoryURL = cachesRoot.appendingPathComponent(
        "MediaPipeUniversalEmbedderCache",
        isDirectory: true
      )
    }
  }

  /// Loads or reloads `UniversalEmbedder` with the requested model, delegate, and L2 normalization.
  ///
  /// Returns the elapsed model load duration in milliseconds.
  public func load(
    model: UniversalEmbedderModelOption = .embeddingGemma2,
    delegate: UniversalEmbedderDelegateOption = .cpu,
    l2Normalize: Bool = true
  ) throws -> Double {
    embedder = nil

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: model.resourceName,
        fileExtension: model.fileExtension
      )
    } catch {
      throw UniversalEmbedderError.initializationFailed(error.localizedDescription)
    }

    do {
      try FileManager.default.createDirectory(
        at: cacheDirectoryURL,
        withIntermediateDirectories: true
      )
    } catch {
      throw UniversalEmbedderError.initializationFailed(
        "Unable to create cache directory: \(error.localizedDescription)"
      )
    }

    let options = UniversalEmbedderOptions()
    options.baseOptions.modelAssetPath = modelURL.path
    options.baseOptions.delegate = delegate.mediaPipeDelegate
    options.textDelegate = delegate.mediaPipeDelegate
    options.visionDelegate = delegate.mediaPipeDelegate
    options.l2Normalize = l2Normalize
    options.cacheDirectory = cacheDirectoryURL.path

    let start = ContinuousClock.now
    do {
      embedder = try UniversalEmbedder(options: options)
    } catch {
      if delegate == .gpu {
        throw UniversalEmbedderError.gpuUnsupported(error.localizedDescription)
      }
      throw UniversalEmbedderError.initializationFailed(error.localizedDescription)
    }
    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases the underlying MediaPipe task instance.
  public func close() {
    embedder = nil
  }

  /// Computes a single embedding vector for `text`.
  public func embed(text: String) throws -> UniversalEmbedderSingleResult {
    try embed(.text(text))
  }

  /// Computes a single embedding vector for encoded JPEG/PNG image bytes.
  public func embed(imageData: Data) throws -> UniversalEmbedderSingleResult {
    try embed(.imageData(imageData))
  }

  /// Computes a single embedding vector for an image file at `imageURL`.
  public func embed(imageURL: URL) throws -> UniversalEmbedderSingleResult {
    try embed(.imageURL(imageURL))
  }

  /// Computes a single embedding vector for a `UIImage` via `MPImage`.
  public func embed(image: UIImage) throws -> UniversalEmbedderSingleResult {
    guard let embedder else { throw UniversalEmbedderError.notInitialized }
    let start = ContinuousClock.now
    let embedding: Embedding
    do {
      let mpImage = try MPImage(uiImage: image)
      let result = try embedder.embed(image: mpImage)
      guard let first = result.embeddings.first else {
        throw UniversalEmbedderError.inferenceFailed("Model returned no embeddings.")
      }
      embedding = first
    } catch let error as UniversalEmbedderError {
      throw error
    } catch {
      throw UniversalEmbedderError.inferenceFailed(error.localizedDescription)
    }
    let elapsedMs = Self.elapsedMilliseconds(since: start)
    let stats = Self.extractFloatValues(from: embedding)
    return UniversalEmbedderSingleResult(
      floatEmbedding: stats.floats,
      dimensions: stats.dimensions,
      l2Norm: stats.norm,
      modality: .image,
      inferenceTimeMs: elapsedMs
    )
  }

  /// Computes a single embedding vector for a text or image input.
  public func embed(_ input: UniversalEmbedderInput) throws -> UniversalEmbedderSingleResult {
    guard let embedder else { throw UniversalEmbedderError.notInitialized }
    let (embedding, elapsedMs) = try runEmbed(embedder: embedder, input: input)
    let stats = Self.extractFloatValues(from: embedding)
    return UniversalEmbedderSingleResult(
      floatEmbedding: stats.floats,
      dimensions: stats.dimensions,
      l2Norm: stats.norm,
      modality: input.modality,
      inferenceTimeMs: elapsedMs
    )
  }

  /// Computes embeddings for two multimodal inputs and returns their MediaPipe cosine similarity.
  public func compare(
    _ first: UniversalEmbedderInput,
    _ second: UniversalEmbedderInput
  ) throws -> UniversalEmbedderComparisonResult {
    guard let embedder else { throw UniversalEmbedderError.notInitialized }

    let totalStart = ContinuousClock.now
    let (firstEmbedding, firstMs) = try runEmbed(embedder: embedder, input: first)
    let (secondEmbedding, secondMs) = try runEmbed(embedder: embedder, input: second)

    let similarity: Double
    do {
      similarity = try UniversalEmbedder.cosineSimilarity(
        embedding1: firstEmbedding,
        embedding2: secondEmbedding
      ).doubleValue
    } catch {
      throw UniversalEmbedderError.inferenceFailed(error.localizedDescription)
    }

    let totalMs = Self.elapsedMilliseconds(since: totalStart)
    let firstStats = Self.extractFloatValues(from: firstEmbedding)
    let secondStats = Self.extractFloatValues(from: secondEmbedding)

    return UniversalEmbedderComparisonResult(
      similarity: similarity,
      dimensions: firstStats.dimensions,
      firstModality: first.modality,
      secondModality: second.modality,
      firstNorm: firstStats.norm,
      secondNorm: secondStats.norm,
      firstInferenceTimeMs: firstMs,
      secondInferenceTimeMs: secondMs,
      totalInferenceTimeMs: totalMs
    )
  }

  private func runEmbed(
    embedder: UniversalEmbedder,
    input: UniversalEmbedderInput
  ) throws -> (Embedding, Double) {
    let start = ContinuousClock.now
    let result: EmbeddingResult
    do {
      switch input {
      case .text(let text):
        result = try embedder.embed(text: text)
      case .imageData(let data, _):
        guard !data.isEmpty else {
          throw UniversalEmbedderError.invalidImage("Image data is empty.")
        }
        result = try embedder.embed(imageBytes: data)
      case .imageURL(let url, _):
        let data: Data
        do {
          data = try Data(contentsOf: url)
        } catch {
          throw UniversalEmbedderError.invalidImage(
            "Unable to read image at \(url.lastPathComponent): \(error.localizedDescription)"
          )
        }
        guard !data.isEmpty else {
          throw UniversalEmbedderError.invalidImage("Image file is empty.")
        }
        result = try embedder.embed(imageBytes: data)
      }
    } catch let error as UniversalEmbedderError {
      throw error
    } catch {
      throw UniversalEmbedderError.inferenceFailed(error.localizedDescription)
    }

    guard let embedding = result.embeddings.first else {
      throw UniversalEmbedderError.inferenceFailed("Model returned no embeddings.")
    }
    return (embedding, Self.elapsedMilliseconds(since: start))
  }

  private static func extractFloatValues(
    from embedding: Embedding
  ) -> (floats: [Float], dimensions: Int, norm: Double) {
    guard let floatNumbers = embedding.floatEmbedding, !floatNumbers.isEmpty else {
      return ([], 0, 0.0)
    }
    let floats = floatNumbers.map(\.floatValue)
    let sumSq = floats.reduce(0.0) { acc, value in
      let d = Double(value)
      return acc + d * d
    }
    return (floats, floats.count, sqrt(sumSq))
  }

  private static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}
