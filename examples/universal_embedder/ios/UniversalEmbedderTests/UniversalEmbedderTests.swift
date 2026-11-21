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
import UIKit
import XCTest
@testable import UniversalEmbedder

final class UniversalEmbedderTests: XCTestCase {
  private static func bundledURL(for photo: BundledSamplePhoto) throws -> URL {
    try XCTUnwrap(
      photo.url(in: .main),
      "Expected bundled sample image \(photo.resourceName).\(photo.fileExtension) in main bundle"
    )
  }

  func testTextToTextSemanticOrderingAndUnitNorm() async throws {
    let helper = UniversalEmbedderHelper()
    let loadMs = try await helper.load(
      model: .embeddingGemma2,
      delegate: .cpu,
      l2Normalize: true
    )
    XCTAssertGreaterThan(loadMs, 0)

    let identical = try await helper.compare(
      .text("a photo of a cat"),
      .text("a photo of a cat")
    )
    let related = try await helper.compare(
      .text("a photo of a cat"),
      .text("an adorable fluffy kitten")
    )
    let unrelated = try await helper.compare(
      .text("a photo of a cat"),
      .text("the quarterly tax filing deadline")
    )

    XCTAssertEqual(identical.dimensions, 768)
    XCTAssertEqual(identical.firstModality, .text)
    XCTAssertEqual(identical.secondModality, .text)
    XCTAssertEqual(identical.firstNorm, 1.0, accuracy: 1e-3)
    XCTAssertEqual(identical.secondNorm, 1.0, accuracy: 1e-3)
    XCTAssertGreaterThanOrEqual(identical.similarity, 0.99)
    XCTAssertLessThanOrEqual(identical.similarity, 1.0001)
    XCTAssertGreaterThan(
      related.similarity - unrelated.similarity,
      0.15,
      "Expected related (\(related.similarity)) to beat unrelated (\(unrelated.similarity)) by > 0.15"
    )

    await helper.close()
  }

  func testCrossModalTextImageAndImageToImageRanking() async throws {
    let helper = UniversalEmbedderHelper()
    _ = try await helper.load(
      model: .embeddingGemma2,
      delegate: .cpu,
      l2Normalize: true
    )

    let catURL = try Self.bundledURL(for: .cuteCat)
    let coffeeURL = try Self.bundledURL(for: .coffeeMug)
    let beachURL = try Self.bundledURL(for: .sunnyBeach)
    let mountainURL = try Self.bundledURL(for: .snowyMountain)

    // Cross-modal Text <-> Image: matching captions beat mismatched captions by >= 0.05.
    let catCaptionCatPhoto = try await helper.compare(
      .text("a photo of a cute fluffy cat"),
      .imageURL(catURL, label: "Cute Cat")
    )
    let catCaptionCoffeePhoto = try await helper.compare(
      .text("a photo of a cute fluffy cat"),
      .imageURL(coffeeURL, label: "Coffee Mug")
    )
    let coffeeCaptionCoffeePhoto = try await helper.compare(
      .text("a warm ceramic coffee mug"),
      .imageURL(coffeeURL, label: "Coffee Mug")
    )
    let coffeeCaptionCatPhoto = try await helper.compare(
      .text("a warm ceramic coffee mug"),
      .imageURL(catURL, label: "Cute Cat")
    )

    XCTAssertEqual(catCaptionCatPhoto.dimensions, 768)
    XCTAssertEqual(catCaptionCatPhoto.modalitiesLabel, "Text ↔ Img")
    XCTAssertEqual(catCaptionCatPhoto.firstNorm, 1.0, accuracy: 1e-3)
    XCTAssertEqual(catCaptionCatPhoto.secondNorm, 1.0, accuracy: 1e-3)
    XCTAssertGreaterThan(
      catCaptionCatPhoto.similarity - catCaptionCoffeePhoto.similarity,
      0.05,
      "Expected cat match (\(catCaptionCatPhoto.similarity)) to beat mismatch (\(catCaptionCoffeePhoto.similarity)) by >= 0.05"
    )
    XCTAssertGreaterThan(
      coffeeCaptionCoffeePhoto.similarity - coffeeCaptionCatPhoto.similarity,
      0.05,
      "Expected coffee match (\(coffeeCaptionCoffeePhoto.similarity)) to beat mismatch (\(coffeeCaptionCatPhoto.similarity)) by >= 0.05"
    )

    let beachCaptionBeachPhoto = try await helper.compare(
      .imageURL(beachURL, label: "Sunny Beach"),
      .text("warm sand on a sunny ocean beach")
    )
    let beachCaptionMountainPhoto = try await helper.compare(
      .imageURL(mountainURL, label: "Snowy Mountain"),
      .text("warm sand on a sunny ocean beach")
    )
    XCTAssertGreaterThan(
      beachCaptionBeachPhoto.similarity - beachCaptionMountainPhoto.similarity,
      0.05,
      "Expected beach match (\(beachCaptionBeachPhoto.similarity)) to beat mountain mismatch (\(beachCaptionMountainPhoto.similarity)) by >= 0.05"
    )

    // Image <-> Image: identical photo ~1.0, beats different photo by >= 0.10.
    let samePhoto = try await helper.compare(
      .imageURL(beachURL, label: "Sunny Beach"),
      .imageURL(beachURL, label: "Sunny Beach")
    )
    let differentPhotos = try await helper.compare(
      .imageURL(beachURL, label: "Sunny Beach"),
      .imageURL(mountainURL, label: "Snowy Mountain")
    )
    XCTAssertEqual(samePhoto.modalitiesLabel, "Img ↔ Img")
    XCTAssertGreaterThanOrEqual(samePhoto.similarity, 0.99)
    XCTAssertLessThanOrEqual(samePhoto.similarity, 1.0001)
    XCTAssertGreaterThan(
      samePhoto.similarity - differentPhotos.similarity,
      0.10,
      "Expected identical photo (\(samePhoto.similarity)) to beat different (\(differentPhotos.similarity)) by >= 0.10"
    )

    // Verify UIImage -> MPImage path works as well.
    let catUIImage = try XCTUnwrap(UIImage(contentsOfFile: catURL.path))
    let uiImageEmbedding = try await helper.embed(image: catUIImage)
    XCTAssertEqual(uiImageEmbedding.dimensions, 768)
    XCTAssertTrue(EmbeddingMetrics.allFinite(uiImageEmbedding.floatEmbedding))
    XCTAssertEqual(uiImageEmbedding.l2Norm, 1.0, accuracy: 1e-3)

    await helper.close()
  }

  func testL2NormalizeTrueAndFalseAndEmbeddingMetricsAgreement() async throws {
    let helper = UniversalEmbedderHelper()
    let catURL = try Self.bundledURL(for: .cuteCat)

    // l2Normalize = true -> unit length.
    _ = try await helper.load(model: .embeddingGemma2, delegate: .cpu, l2Normalize: true)
    let normText = try await helper.embed(text: "a cute fluffy cat")
    let normImage = try await helper.embed(imageURL: catURL)
    XCTAssertEqual(normText.dimensions, 768)
    XCTAssertEqual(normImage.dimensions, 768)
    XCTAssertEqual(normText.l2Norm, 1.0, accuracy: 1e-3)
    XCTAssertEqual(normImage.l2Norm, 1.0, accuracy: 1e-3)
    XCTAssertTrue(EmbeddingMetrics.allFinite(normText.floatEmbedding))
    XCTAssertTrue(EmbeddingMetrics.allFinite(normImage.floatEmbedding))

    let expectedSim = try EmbeddingMetrics.cosineSimilarity(
      normText.floatEmbedding,
      normImage.floatEmbedding
    )
    let actualSim = try await helper.compare(
      .text("a cute fluffy cat"),
      .imageURL(catURL, label: "Cute Cat")
    )
    XCTAssertEqual(actualSim.similarity, expectedSim, accuracy: 1e-4)

    // l2Normalize = false -> finite embeddings and consistent cosine similarity.
    _ = try await helper.load(model: .embeddingGemma2, delegate: .cpu, l2Normalize: false)
    let rawText = try await helper.embed(text: "a cute fluffy cat")
    let rawImage = try await helper.embed(imageURL: catURL)
    XCTAssertEqual(rawText.dimensions, 768)
    XCTAssertEqual(rawImage.dimensions, 768)
    XCTAssertTrue(EmbeddingMetrics.allFinite(rawText.floatEmbedding))
    XCTAssertTrue(EmbeddingMetrics.allFinite(rawImage.floatEmbedding))
    XCTAssertGreaterThan(rawText.l2Norm, 0.0)
    XCTAssertGreaterThan(rawImage.l2Norm, 0.0)

    let rawExpectedSim = try EmbeddingMetrics.cosineSimilarity(
      rawText.floatEmbedding,
      rawImage.floatEmbedding
    )
    let rawActualSim = try await helper.compare(
      .text("a cute fluffy cat"),
      .imageURL(catURL, label: "Cute Cat")
    )
    XCTAssertEqual(rawActualSim.similarity, rawExpectedSim, accuracy: 1e-4)
    XCTAssertEqual(rawActualSim.similarity, actualSim.similarity, accuracy: 1e-3)

    await helper.close()
  }

  @MainActor
  func testEveryUserSelectableOptionCombinationSucceedsWithoutError() async throws {
    let viewModel = UniversalEmbedderViewModel(autoLoad: false)
    for delegate in UniversalEmbedderDelegateOption.allCases {
      for l2Normalize in [false, true] {
        let loaded = await viewModel.configure(delegate: delegate, l2Normalize: l2Normalize)
        XCTAssertTrue(
          loaded,
          "Failed to load delegate=\(delegate.rawValue) l2Normalize=\(l2Normalize): \(viewModel.errorMessage ?? "")"
        )
        XCTAssertTrue(viewModel.isReady)
        XCTAssertNil(viewModel.errorMessage)

        for preset in MultimodalPairPreset.defaults {
          viewModel.applyPreset(preset)
          XCTAssertEqual(viewModel.activePresetID, preset.id)
          let result = await viewModel.compare()
          let unwrapped = try XCTUnwrap(
            result,
            "Compare failed for delegate=\(delegate.rawValue) preset=\(preset.id) l2Normalize=\(l2Normalize): \(viewModel.errorMessage ?? "")"
          )
          XCTAssertEqual(unwrapped.dimensions, 768)
          XCTAssertTrue(unwrapped.similarity.isFinite)
          XCTAssertGreaterThanOrEqual(unwrapped.similarity, -1.0001)
          XCTAssertLessThanOrEqual(unwrapped.similarity, 1.0001)
          XCTAssertNil(viewModel.errorMessage)
        }
      }
    }
  }

  @MainActor
  func testViewModelStateTransitionsSwapAndCustomPhotoNormalization() async throws {
    let viewModel = UniversalEmbedderViewModel(autoLoad: false)
    XCTAssertFalse(viewModel.isReady)
    XCTAssertFalse(viewModel.canCompare)

    // Verify all 10 bundled photos exist and produce thumbnails.
    XCTAssertEqual(BundledSamplePhoto.all.count, 10)
    for photo in BundledSamplePhoto.all {
      XCTAssertNotNil(photo.url(in: .main), "Missing bundled image \(photo.id)")
      XCTAssertNotNil(viewModel.thumbnail(for: photo), "Failed to decode thumbnail for \(photo.id)")
    }

    let loaded = await viewModel.loadEmbedder()
    XCTAssertTrue(loaded, "Expected model to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertTrue(viewModel.canCompare)

    let initialResult = await viewModel.compare()
    let unwrappedInitial = try XCTUnwrap(initialResult)
    XCTAssertEqual(viewModel.result, unwrappedInitial)
    XCTAssertEqual(unwrappedInitial.modalitiesLabel, "Text ↔ Img")

    // Swapping slots reverses modalities and clears stale result.
    let beforeA = viewModel.slotA
    let beforeB = viewModel.slotB
    viewModel.swapInputs()
    XCTAssertEqual(viewModel.slotA, beforeB)
    XCTAssertEqual(viewModel.slotB, beforeA)
    XCTAssertNil(viewModel.result)

    // Custom photo ingestion via normalized JPEG bytes.
    let bananaURL = try Self.bundledURL(for: .yellowBanana)
    let rawBananaBytes = try Data(contentsOf: bananaURL)
    XCTAssertTrue(viewModel.setSlotACustomImageData(rawBananaBytes, label: "My Banana"))
    XCTAssertTrue(viewModel.slotA.isUsingCustomImage)
    XCTAssertEqual(viewModel.slotA.activeImageLabel, "My Banana")
    XCTAssertNotNil(viewModel.displayImage(for: viewModel.slotA))

    viewModel.setSlotB(
      EmbedderSlotState(
        modality: .text,
        text: "a yellow piece of fruit",
        samplePhoto: .yellowBanana
      )
    )
    let customResult = await viewModel.compare()
    let unwrappedCustom = try XCTUnwrap(customResult)
    XCTAssertEqual(unwrappedCustom.modalitiesLabel, "Img ↔ Text")
    XCTAssertGreaterThan(unwrappedCustom.similarity, 0.4)

    // Selecting a bundled sample clears custom image.
    viewModel.selectSlotASamplePhoto(.redApple)
    XCTAssertFalse(viewModel.slotA.isUsingCustomImage)
    XCTAssertEqual(viewModel.slotA.samplePhoto, .redApple)
    XCTAssertNil(viewModel.result)
  }

  @MainActor
  func testGpuDelegateCrossModalRankingAndCacheReload() async throws {
    let viewModel = UniversalEmbedderViewModel(delegate: .gpu, autoLoad: false)
    let loaded = await viewModel.loadEmbedder()
    XCTAssertTrue(loaded, "Expected GPU delegate to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertFalse(viewModel.isGpuError)
    XCTAssertNil(viewModel.errorMessage)

    let catURL = try Self.bundledURL(for: .cuteCat)
    let coffeeURL = try Self.bundledURL(for: .coffeeMug)

    let rawMatch = await viewModel.compare(
      .text("a cute fluffy cat"),
      .imageURL(catURL, label: "Cute Cat")
    )
    let catMatch = try XCTUnwrap(rawMatch)
    let rawMismatch = await viewModel.compare(
      .text("a cute fluffy cat"),
      .imageURL(coffeeURL, label: "Coffee Mug")
    )
    let catMismatch = try XCTUnwrap(rawMismatch)
    XCTAssertEqual(catMatch.dimensions, 768)
    XCTAssertEqual(catMatch.firstNorm, 1.0, accuracy: 1e-3)
    XCTAssertEqual(catMatch.secondNorm, 1.0, accuracy: 1e-3)
    XCTAssertGreaterThan(
      catMatch.similarity - catMismatch.similarity,
      0.05,
      "Expected GPU cat match (\(catMatch.similarity)) to beat mismatch (\(catMismatch.similarity)) by >= 0.05"
    )
  }
}
