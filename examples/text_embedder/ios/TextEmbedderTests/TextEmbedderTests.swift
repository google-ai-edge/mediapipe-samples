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

import MediaPipeSampleTesting
import MediaPipeSampleUI
import XCTest
@testable import TextEmbedder

final class TextEmbedderTests: XCTestCase {
  private static let identicalA = "I love watching sunny sunsets at the beach."
  private static let relatedB = "Warm ocean evenings are wonderful."
  private static let unrelatedB = "The quarterly tax filing deadline is next Tuesday."

  func testUniversalSentenceEncoderSemanticOrderingAndNorm() async throws {
    let helper = TextEmbedderHelper()
    _ = try await helper.load(
      model: .universalSentenceEncoder,
      delegate: .cpu,
      l2Normalize: true,
      quantize: false
    )

    let identical = try await helper.compare(
      firstText: Self.identicalA,
      secondText: Self.identicalA
    )
    let related = try await helper.compare(
      firstText: Self.identicalA,
      secondText: Self.relatedB
    )
    let unrelated = try await helper.compare(
      firstText: Self.identicalA,
      secondText: Self.unrelatedB
    )

    XCTAssertEqual(identical.dimensions, 100)
    XCTAssertFalse(identical.isQuantized)
    XCTAssertEqual(identical.firstNorm, 1.0, accuracy: 1e-3)
    XCTAssertGreaterThanOrEqual(identical.similarity, 0.99)
    XCTAssertGreaterThan(
      related.similarity - unrelated.similarity,
      0.15,
      "Expected related (\(related.similarity)) to beat unrelated (\(unrelated.similarity)) by > 0.15"
    )
  }

  func testMobileBertEmbedderBaselineAndQuantization() async throws {
    let helper = TextEmbedderHelper()
    _ = try await helper.load(
      model: .mobileBert,
      delegate: .cpu,
      l2Normalize: true,
      quantize: false
    )

    let pair = try await helper.compare(
      firstText: "I'm feeling so good",
      secondText: "I'm okay I guess"
    )
    let same = try await helper.compare(
      firstText: "I'm feeling so good",
      secondText: "I'm feeling so good"
    )

    XCTAssertEqual(pair.dimensions, 512)
    XCTAssertEqual(pair.similarity, 0.9191, accuracy: 0.03)
    XCTAssertGreaterThanOrEqual(same.similarity, 0.99)

    _ = try await helper.load(
      model: .mobileBert,
      delegate: .cpu,
      l2Normalize: true,
      quantize: true
    )
    let quantized = try await helper.embed(text: "I'm feeling so good")
    XCTAssertEqual(quantized.dimensions, 512)
    XCTAssertNotNil(quantized.quantizedEmbedding)
    XCTAssertNil(quantized.floatEmbedding)
  }

  func testEmbeddingGemmaSemanticOrderingAndContextModes() async throws {
    let helper = TextEmbedderHelper()
    _ = try await helper.load(
      model: .embeddingGemma,
      delegate: .cpu,
      l2Normalize: true,
      quantize: false
    )

    let identical = try await helper.compare(
      firstText: Self.identicalA,
      secondText: Self.identicalA,
      contextMode: .none
    )
    let related = try await helper.compare(
      firstText: Self.identicalA,
      secondText: Self.relatedB,
      contextMode: .none
    )
    let unrelated = try await helper.compare(
      firstText: Self.identicalA,
      secondText: Self.unrelatedB,
      contextMode: .none
    )

    XCTAssertEqual(identical.dimensions, 768)
    XCTAssertGreaterThanOrEqual(identical.similarity, 0.99)
    XCTAssertGreaterThan(
      related.similarity - unrelated.similarity,
      0.15,
      "Expected related (\(related.similarity)) to beat unrelated (\(unrelated.similarity)) by > 0.15"
    )

    // Asymmetric retrieval with document title formatting.
    let retrievalPreset = try XCTUnwrap(
      TextPairPreset.defaults.first { $0.id == "retrieval" }
    )
    let retrieval = try await helper.compare(
      firstText: retrievalPreset.firstText,
      secondText: retrievalPreset.secondText,
      contextMode: .retrieval,
      documentTitle: retrievalPreset.documentTitle
    )
    XCTAssertGreaterThan(retrieval.similarity, 0.5)
  }

  @MainActor
  func testViewModelDrivesPresetsOptionsAndComparison() async throws {
    let viewModel = TextEmbedderViewModel(
      model: .universalSentenceEncoder,
      delegate: .cpu,
      l2Normalize: true,
      quantize: false,
      autoLoad: false
    )

    let loaded = await viewModel.loadEmbedder()
    XCTAssertTrue(loaded, "Expected Universal Sentence Encoder to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertNil(viewModel.errorMessage)

    let identicalPreset = try XCTUnwrap(TextPairPreset.defaults.first { $0.id == "identical" })
    viewModel.applyPreset(identicalPreset)
    XCTAssertEqual(viewModel.activePresetID, "identical")

    let firstResult = await viewModel.compare()
    let unwrapped = try XCTUnwrap(firstResult)
    XCTAssertGreaterThanOrEqual(unwrapped.similarity, 0.99)
    XCTAssertEqual(unwrapped.dimensions, 100)
    XCTAssertEqual(viewModel.result, unwrapped)

    // Swapping clears stale result cleanly.
    viewModel.firstText = "Alpha"
    viewModel.secondText = "Beta"
    viewModel.swapTexts()
    XCTAssertEqual(viewModel.firstText, "Beta")
    XCTAssertEqual(viewModel.secondText, "Alpha")
    XCTAssertNil(viewModel.result)

    // Verify EmbeddingMetrics matches MediaPipe cosine similarity.
    let helper = TextEmbedderHelper()
    _ = try await helper.load(
      model: .universalSentenceEncoder,
      delegate: .cpu,
      l2Normalize: false,
      quantize: false
    )
    let rawA = try await helper.embed(text: Self.identicalA)
    let rawB = try await helper.embed(text: Self.relatedB)
    let embA = try XCTUnwrap(rawA.floatEmbedding)
    let embB = try XCTUnwrap(rawB.floatEmbedding)
    let expected = try EmbeddingMetrics.cosineSimilarity(embA, embB)
    let actual = try await helper.compare(firstText: Self.identicalA, secondText: Self.relatedB)
    XCTAssertEqual(actual.similarity, expected, accuracy: 1e-4)
  }

  @MainActor
  func testGpuDelegateDoesNotSilentlyFallBackOnUnsupportedTextModel() async {
    let viewModel = TextEmbedderViewModel(
      model: .universalSentenceEncoder,
      delegate: .gpu,
      autoLoad: false
    )
    let loaded = await viewModel.loadEmbedder()
    // On iOS/macOS GPU delegate, string/int token inputs are unsupported; if it fails,
    // verify the error is surfaced explicitly rather than silently falling back to CPU.
    if !loaded {
      XCTAssertTrue(viewModel.isGpuError)
      XCTAssertNotNil(viewModel.errorMessage)
      XCTAssertFalse(viewModel.isReady)
    }
  }
}
