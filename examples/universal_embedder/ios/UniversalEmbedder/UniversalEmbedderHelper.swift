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
import QuartzCore
import UIKit
import MediaPipeTasksRetrieval

/// An embedding plus how long it took to produce.
struct EmbeddingRun {
  let embedding: Embedding
  let milliseconds: Double
}

/// Thin wrapper around `UniversalEmbedder`.
///
/// The embedder maps text, images and audio into a single shared vector space, which is what makes
/// cross-modal comparison ("does this sentence describe this picture?") possible.
///
/// Not thread safe: callers must serialize access (see the serial queue in `ViewController`).
final class UniversalEmbedderHelper {

  /// Name of the EmbeddingGemma V2 model bundled with the app.
  static let modelName = "embeddinggemma-2-text-vision-440m"
  static let modelExtension = "litertlm"

  private var embedder: UniversalEmbedder?
  private var currentlyOnGPU = false

  var isReady: Bool { embedder != nil }

  /// Builds the engine, or rebuilds it if the requested accelerator changed.
  func prepare(useGPU: Bool) throws {
    if embedder != nil && currentlyOnGPU == useGPU { return }
    embedder = nil

    guard
      let modelPath = Bundle.main.path(
        forResource: Self.modelName, ofType: Self.modelExtension)
    else {
      throw HelperError.modelNotFound
    }

    let options = UniversalEmbedderOptions()
    options.baseOptions.modelAssetPath = modelPath
    // Delegates are per-modality: the text tower encodes strings, the vision tower images.
    let delegate: Delegate = useGPU ? .GPU : .CPU
    options.textDelegate = delegate
    options.visionDelegate = delegate
    options.cacheDirectory =
      FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].path
    // Embeddings are unit length, so a dot product is the cosine similarity.
    options.l2Normalize = true

    let startedAt = CACurrentMediaTime()
    embedder = try UniversalEmbedder(options: options)
    currentlyOnGPU = useGPU
    print(
      String(
        format: "Embedder ready on %@ in %.0fms", useGPU ? "GPU" : "CPU",
        (CACurrentMediaTime() - startedAt) * 1000))
  }

  func embed(text: String) throws -> EmbeddingRun {
    guard let embedder else { throw HelperError.notPrepared }
    let startedAt = CACurrentMediaTime()
    let result = try embedder.embed(text: text)
    return try run(from: result, startedAt: startedAt)
  }

  func embed(image: UIImage) throws -> EmbeddingRun {
    guard let embedder else { throw HelperError.notPrepared }
    let mpImage = try MPImage(uiImage: image)
    let startedAt = CACurrentMediaTime()
    let result = try embedder.embed(image: mpImage)
    return try run(from: result, startedAt: startedAt)
  }

  /// Cosine similarity, in [-1, 1]. Identical inputs score 1.
  static func similarity(_ first: Embedding, _ second: Embedding) throws -> Double {
    return try UniversalEmbedder.cosineSimilarity(embedding1: first, embedding2: second)
      .doubleValue
  }

  func close() {
    embedder = nil
  }

  private func run(from result: EmbeddingResult, startedAt: CFTimeInterval) throws -> EmbeddingRun {
    guard let embedding = result.embeddings.first else { throw HelperError.emptyResult }
    return EmbeddingRun(
      embedding: embedding, milliseconds: (CACurrentMediaTime() - startedAt) * 1000)
  }

  enum HelperError: LocalizedError {
    case modelNotFound
    case notPrepared
    case emptyResult

    var errorDescription: String? {
      switch self {
      case .modelNotFound:
        return
          "\(UniversalEmbedderHelper.modelName).\(UniversalEmbedderHelper.modelExtension) is "
          + "missing from the app bundle."
      case .notPrepared:
        return "prepare(useGPU:) must be called first."
      case .emptyResult:
        return "The embedder returned no embeddings."
      }
    }
  }
}
