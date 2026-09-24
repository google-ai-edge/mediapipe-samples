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
import MediaPipeTasksRetrieval

/// Owns the embedding engine and the vector store backing the search index.
///
/// The `UniversalEmbedder` wraps a ~470 MB LiteRT model, so it is created exactly once and kept
/// alive for the lifetime of the helper. Reopening the store only swaps the cheap
/// `SemanticRetriever` / `SqliteVectorStore` pair on top of that same embedder — tearing the engine
/// down and standing a second one up would either fail to mmap the model file or leave callers
/// holding a released engine.
///
/// This class is not thread safe: callers must serialize access (see the serial queue in
/// `ViewController`).
final class SemanticRetrieverHelper {

  /// Name of the EmbeddingGemma V2 model bundled with the app.
  static let modelName = "embeddinggemma-2-text-vision-440m"
  static let modelExtension = "litertlm"

  private static let databaseName = "semantic_db.sqlite"

  private var universalEmbedder: UniversalEmbedder?
  private var semanticRetriever: SemanticRetriever?
  private var currentlyOnGPU = false

  /// True once a vector store has been opened and the helper can accept work.
  var isReady: Bool { semanticRetriever != nil }

  /// Absolute path of the SQLite file the vector store writes to.
  let databasePath: String = {
    let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    return documents.appendingPathComponent(SemanticRetrieverHelper.databaseName).path
  }()

  /// Loads the embedding model. Safe to call repeatedly; the engine is only built the first time,
  /// or rebuilt when the requested accelerator changed.
  func prepareEmbedder(useGPU: Bool) throws {
    if universalEmbedder != nil && currentlyOnGPU == useGPU { return }

    // The accelerator is baked into the engine, so changing it means rebuilding it. Drop the
    // retriever first: it holds on to the provider we are about to release.
    semanticRetriever = nil
    universalEmbedder = nil

    guard
      let modelPath = Bundle.main.path(
        forResource: Self.modelName, ofType: Self.modelExtension)
    else {
      throw HelperError.modelNotFound
    }

    let options = UniversalEmbedderOptions()
    options.baseOptions.modelAssetPath = modelPath
    // Delegates are per-modality: the text tower encodes queries, the vision tower encodes images.
    // Both are pushed onto the same accelerator here.
    let delegate: Delegate = useGPU ? .GPU : .CPU
    options.textDelegate = delegate
    options.visionDelegate = delegate
    options.cacheDirectory =
      FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0].path
    // Embeddings are unit length, so a dot product is the cosine similarity.
    options.l2Normalize = true

    let startedAt = CACurrentMediaTime()
    universalEmbedder = try UniversalEmbedder(options: options)
    currentlyOnGPU = useGPU
    print(
      String(
        format: "Embedding engine ready on %@ in %.0fms", useGPU ? "GPU" : "CPU",
        (CACurrentMediaTime() - startedAt) * 1000))
  }

  /// Closes the previous store (if any) and opens a SQLite backed one, reusing the already loaded
  /// embedding engine.
  func openStore() throws {
    guard let embedder = universalEmbedder else {
      throw HelperError.embedderNotPrepared
    }

    // Releasing the retriever also releases the vector store it was built with, and leaves the
    // embedder untouched — which is exactly what we want here.
    semanticRetriever = nil

    let vectorStore = SqliteVectorStore(
      databasePath: databasePath,
      embeddingDimension: MPPSemanticRetrieverDefaultEmbeddingDimension)
    let components = try SemanticRetrieverComponents(
      vectorStore: vectorStore, chunker: nil, providers: [embedder])

    semanticRetriever = try SemanticRetriever(components: components)
    print("Opened SQLite vector store at \(databasePath).")
  }

  /// Empties the open store. Used to reset state on launch, which is safer than deleting the
  /// database file out from under a live store.
  func clear() throws {
    try semanticRetriever?.deleteAllRecords()
  }

  func embedImage(id: String, path: String) throws {
    guard let semanticRetriever else { throw HelperError.storeNotOpen }
    try semanticRetriever.insertImage(withId: id, filePath: path)
  }

  func searchImages(query: String, limit: Int = 5) throws -> [RetrievalResult] {
    guard let semanticRetriever else { throw HelperError.storeNotOpen }
    return try semanticRetriever.retrieve(withText: query, topK: limit)
  }

  func close() {
    semanticRetriever = nil
    universalEmbedder = nil
  }

  enum HelperError: LocalizedError {
    case modelNotFound
    case embedderNotPrepared
    case storeNotOpen

    var errorDescription: String? {
      switch self {
      case .modelNotFound:
        return
          "\(SemanticRetrieverHelper.modelName).\(SemanticRetrieverHelper.modelExtension) is "
          + "missing from the app bundle."
      case .embedderNotPrepared:
        return "prepareEmbedder(useGPU:) must be called before openStore()."
      case .storeNotOpen:
        return "No vector store is open."
      }
    }
  }
}
