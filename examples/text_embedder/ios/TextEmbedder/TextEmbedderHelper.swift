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

/// Supported MediaPipe text embedding models bundled with this sample.
public enum TextEmbedderModelOption: String, CaseIterable, Identifiable, Sendable {
  case embeddingGemma = "embedding_gemma"
  case mobileBert = "bert_embedder"
  case universalSentenceEncoder = "universal_sentence_encoder"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .embeddingGemma: return "Gemma 300M"
    case .mobileBert: return "MobileBERT"
    case .universalSentenceEncoder: return "Universal"
    }
  }

  public var fullName: String {
    switch self {
    case .embeddingGemma: return "EmbeddingGemma 300M"
    case .mobileBert: return "MobileBERT Embedder"
    case .universalSentenceEncoder: return "Universal Sentence Encoder"
    }
  }

  public var dimensions: Int {
    switch self {
    case .embeddingGemma: return 768
    case .mobileBert: return 512
    case .universalSentenceEncoder: return 100
    }
  }

  public var summary: String {
    switch self {
    case .embeddingGemma:
      return "768d contextual embeddings with task-type prompt formatting."
    case .mobileBert:
      return "512d transformer embeddings for full sentences."
    case .universalSentenceEncoder:
      return "Fast 100d sentence embeddings with minimal footprint."
    }
  }

  public var resourceName: String {
    switch self {
    case .embeddingGemma: return "embedding_gemma"
    case .mobileBert: return "bert_embedder"
    case .universalSentenceEncoder: return "universal_sentence_encoder"
    }
  }

  public var fileExtension: String {
    switch self {
    case .embeddingGemma: return "task"
    case .mobileBert, .universalSentenceEncoder: return "tflite"
    }
  }

  public var assetFilename: String {
    "\(resourceName).\(fileExtension)"
  }

  public var supportsFormatContext: Bool {
    self == .embeddingGemma
  }
}

/// Hardware execution delegate selectable in Settings.
public enum TextEmbedderDelegateOption: String, CaseIterable, Identifiable, Sendable {
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

/// Task-specific prompt formatting modes supported by EmbeddingGemma 300M.
public enum TextEmbedderContextMode: String, CaseIterable, Identifiable, Sendable {
  case similarity
  case retrieval
  case questionAnswering
  case clustering
  case none

  public var id: String { rawValue }

  public var label: String {
    switch self {
    case .similarity: return "Similarity"
    case .retrieval: return "Query → Doc"
    case .questionAnswering: return "Q & A"
    case .clustering: return "Clustering"
    case .none: return "Plain text"
    }
  }

  public var summary: String {
    switch self {
    case .similarity:
      return "Symmetric semantic similarity for comparing two sentences."
    case .retrieval:
      return "Asymmetric retrieval: Text A as query, Text B as titled document."
    case .questionAnswering:
      return "Asymmetric Q&A: question query matched against answer passage."
    case .clustering:
      return "Clustering prompt wrapper for grouping related passages."
    case .none:
      return "Raw text without a TextFormatContext prompt wrapper."
    }
  }

  public var usesDocumentTitle: Bool {
    self == .retrieval || self == .questionAnswering
  }

  func makeFirstContext() -> TextFormatContext? {
    switch self {
    case .similarity:
      return makeContext(type: .semanticSimilarity, role: .query, title: nil)
    case .retrieval:
      return makeContext(type: .retrievalQuery, role: .query, title: nil)
    case .questionAnswering:
      return makeContext(type: .questionAnswering, role: .query, title: nil)
    case .clustering:
      return makeContext(type: .clustering, role: .query, title: nil)
    case .none:
      return nil
    }
  }

  func makeSecondContext(documentTitle: String) -> TextFormatContext? {
    let trimmedTitle = documentTitle.trimmingCharacters(in: .whitespacesAndNewlines)
    let title = usesDocumentTitle && !trimmedTitle.isEmpty ? trimmedTitle : nil
    switch self {
    case .similarity:
      return makeContext(type: .semanticSimilarity, role: .query, title: nil)
    case .retrieval:
      return makeContext(type: .retrievalDocument, role: .document, title: title)
    case .questionAnswering:
      return makeContext(type: .questionAnswering, role: .document, title: title)
    case .clustering:
      return makeContext(type: .clustering, role: .query, title: nil)
    case .none:
      return nil
    }
  }

  private func makeContext(
    type: MPPEmbeddingType,
    role: MPPTextRole,
    title: String?
  ) -> TextFormatContext {
    let context = TextFormatContext()
    context.embeddingType = type
    context.textRole = role
    if let title {
      context.title = title
    }
    return context
  }
}

/// Output of embedding a single piece of text.
public struct TextEmbedderSingleResult: Equatable, Sendable {
  public let floatEmbedding: [Float]?
  public let quantizedEmbedding: [UInt8]?
  public let dimensions: Int
  public let l2Norm: Double
  public let inferenceTimeMs: Double
}

/// Output of comparing two pieces of text via cosine similarity.
public struct TextEmbedderComparisonResult: Equatable, Sendable {
  public let similarity: Double
  public let dimensions: Int
  public let firstNorm: Double
  public let secondNorm: Double
  public let isQuantized: Bool
  public let inferenceTimeMs: Double
}

/// Errors surfaced by ``TextEmbedderHelper``.
public enum TextEmbedderError: LocalizedError, Equatable, Sendable {
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
      return "Text Embedder is not initialized."
    }
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksText.TextEmbedder`.
///
/// Runs model loading and embedding inference off the main actor and surfaces explicit delegate
/// errors without silently falling back from GPU to CPU.
public actor TextEmbedderHelper {
  private let locator: SampleModelLocator
  private var embedder: TextEmbedder?

  public init(locator: SampleModelLocator = SampleModelLocator()) {
    self.locator = locator
  }

  /// Loads or reloads `TextEmbedder` with the requested model, delegate, and post-processing flags.
  ///
  /// Returns the elapsed model load duration in milliseconds.
  public func load(
    model: TextEmbedderModelOption,
    delegate: TextEmbedderDelegateOption,
    l2Normalize: Bool,
    quantize: Bool
  ) throws -> Double {
    embedder = nil

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: model.resourceName,
        fileExtension: model.fileExtension
      )
    } catch {
      throw TextEmbedderError.initializationFailed(error.localizedDescription)
    }

    let options = TextEmbedderOptions()
    options.baseOptions.modelAssetPath = modelURL.path
    options.baseOptions.delegate = delegate.mediaPipeDelegate
    options.l2Normalize = l2Normalize
    options.quantize = quantize

    let start = ContinuousClock.now
    do {
      embedder = try TextEmbedder(options: options)
    } catch {
      if delegate == .gpu {
        throw TextEmbedderError.gpuUnsupported(error.localizedDescription)
      }
      throw TextEmbedderError.initializationFailed(error.localizedDescription)
    }
    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases the underlying MediaPipe task instance.
  public func close() {
    embedder = nil
  }

  /// Computes a single embedding vector for `text`.
  public func embed(
    text: String,
    contextMode: TextEmbedderContextMode = .none,
    documentTitle: String = "",
    asSecondText: Bool = false
  ) throws -> TextEmbedderSingleResult {
    guard let embedder else { throw TextEmbedderError.notInitialized }
    let formatContext =
      asSecondText
      ? contextMode.makeSecondContext(documentTitle: documentTitle)
      : contextMode.makeFirstContext()

    let start = ContinuousClock.now
    let embedding: Embedding
    do {
      let result = try runEmbed(embedder: embedder, text: text, formatContext: formatContext)
      guard let first = result.embeddingResult.embeddings.first else {
        throw TextEmbedderError.inferenceFailed("Model returned no embeddings.")
      }
      embedding = first
    } catch let error as TextEmbedderError {
      throw error
    } catch {
      throw TextEmbedderError.inferenceFailed(error.localizedDescription)
    }

    let elapsedMs = Self.elapsedMilliseconds(since: start)
    let extracted = Self.extractValues(from: embedding)
    return TextEmbedderSingleResult(
      floatEmbedding: extracted.floats,
      quantizedEmbedding: extracted.quantized,
      dimensions: extracted.dimensions,
      l2Norm: extracted.norm,
      inferenceTimeMs: elapsedMs
    )
  }

  /// Computes embeddings for two texts and returns their MediaPipe cosine similarity.
  public func compare(
    firstText: String,
    secondText: String,
    contextMode: TextEmbedderContextMode = .none,
    documentTitle: String = ""
  ) throws -> TextEmbedderComparisonResult {
    guard let embedder else { throw TextEmbedderError.notInitialized }

    let firstContext = contextMode.makeFirstContext()
    let secondContext = contextMode.makeSecondContext(documentTitle: documentTitle)

    let start = ContinuousClock.now
    let firstEmbedding: Embedding
    let secondEmbedding: Embedding
    let similarity: Double

    do {
      let firstResult = try runEmbed(
        embedder: embedder,
        text: firstText,
        formatContext: firstContext
      )
      let secondResult = try runEmbed(
        embedder: embedder,
        text: secondText,
        formatContext: secondContext
      )
      guard
        let first = firstResult.embeddingResult.embeddings.first,
        let second = secondResult.embeddingResult.embeddings.first
      else {
        throw TextEmbedderError.inferenceFailed("Model returned no embeddings.")
      }
      firstEmbedding = first
      secondEmbedding = second
      similarity = try TextEmbedder.cosineSimilarity(
        embedding1: firstEmbedding,
        embedding2: secondEmbedding
      ).doubleValue
    } catch let error as TextEmbedderError {
      throw error
    } catch {
      throw TextEmbedderError.inferenceFailed(error.localizedDescription)
    }

    let elapsedMs = Self.elapsedMilliseconds(since: start)
    let firstStats = Self.extractValues(from: firstEmbedding)
    let secondStats = Self.extractValues(from: secondEmbedding)

    return TextEmbedderComparisonResult(
      similarity: similarity,
      dimensions: firstStats.dimensions,
      firstNorm: firstStats.norm,
      secondNorm: secondStats.norm,
      isQuantized: firstStats.quantized != nil,
      inferenceTimeMs: elapsedMs
    )
  }

  private func runEmbed(
    embedder: TextEmbedder,
    text: String,
    formatContext: TextFormatContext?
  ) throws -> TextEmbedderResult {
    if let formatContext {
      return try embedder.embed(text: text, textFormatContext: formatContext)
    }
    return try embedder.embed(text: text)
  }

  private static func extractValues(
    from embedding: Embedding
  ) -> (floats: [Float]?, quantized: [UInt8]?, dimensions: Int, norm: Double) {
    if let floatNumbers = embedding.floatEmbedding, !floatNumbers.isEmpty {
      let floats = floatNumbers.map(\.floatValue)
      let sumSq = floats.reduce(0.0) { acc, value in
        let d = Double(value)
        return acc + d * d
      }
      return (floats, nil, floats.count, sqrt(sumSq))
    }
    if let quantizedNumbers = embedding.quantizedEmbedding, !quantizedNumbers.isEmpty {
      let bytes = quantizedNumbers.map(\.uint8Value)
      let sumSq = bytes.reduce(0.0) { acc, value in
        let signed = Double(Int8(bitPattern: value))
        return acc + signed * signed
      }
      return (nil, bytes, bytes.count, sqrt(sumSq))
    }
    return (nil, nil, 0, 0.0)
  }

  private static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}
