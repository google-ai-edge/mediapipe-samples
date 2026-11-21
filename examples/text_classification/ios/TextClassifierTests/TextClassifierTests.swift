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
@testable import TextClassifier

final class TextClassifierTests: XCTestCase {
  func testMobileBertSpecSentencesAndGoldenBaseline() async throws {
    let helper = TextClassifierHelper()
    let loadMs = try await helper.load(model: .mobileBert, delegate: .cpu)
    XCTAssertGreaterThanOrEqual(loadMs, 0)

    let specCases: [(text: String, expectedRaw: String, expectedDisplay: String, minScore: Float)] =
      [
        ("I'm looking forward to what will come next.", "positive", "Positive", 0.7),
        (
          "This MediaPipe release validation test is working wonderfully!",
          "positive",
          "Positive",
          0.7
        ),
        (
          "This is a terrible, broken and disappointing release. I hate it.",
          "negative",
          "Negative",
          0.7
        ),
      ]

    for specCase in specCases {
      let output = try await helper.classify(text: specCase.text)
      XCTAssertEqual(output.categories.count, 2)
      let top = try XCTUnwrap(output.topCategory)
      XCTAssertEqual(top.rawLabel, specCase.expectedRaw)
      XCTAssertEqual(top.displayLabel, specCase.expectedDisplay)
      XCTAssertGreaterThanOrEqual(
        top.score,
        specCase.minScore,
        "Expected score >= \(specCase.minScore) for '\(specCase.text)', got \(top.score)"
      )

      let sum = output.categories.reduce(0.0) { $0 + Double($1.score) }
      XCTAssertEqual(sum, 1.0, accuracy: 1e-3)
    }

    // Golden regression check from original MediaPipe sample test.
    let bleak = try await helper.classify(text: "unflinchingly bleak and desperate")
    XCTAssertEqual(bleak.categories.count, 2)
    let bleakTop = try XCTUnwrap(bleak.topCategory)
    XCTAssertEqual(bleakTop.rawLabel, "negative")
    XCTAssertEqual(bleakTop.displayLabel, "Negative")
    XCTAssertEqual(bleakTop.score, 0.956, accuracy: 0.02)
  }

  func testAverageWordSpecSentencesAndLabelNormalization() async throws {
    let helper = TextClassifierHelper()
    let loadMs = try await helper.load(model: .wordVec, delegate: .cpu)
    XCTAssertGreaterThanOrEqual(loadMs, 0)

    let specCases: [(text: String, expectedRaw: String, expectedDisplay: String, minScore: Float)] =
      [
        (
          "This film is a wonderful, uplifting masterpiece with great acting!",
          "1",
          "Positive",
          0.6
        ),
        (
          "This MediaPipe release validation test is working wonderfully!",
          "1",
          "Positive",
          0.6
        ),
        (
          "This is a terrible, broken and disappointing release. I hate it.",
          "0",
          "Negative",
          0.6
        ),
      ]

    for specCase in specCases {
      let output = try await helper.classify(text: specCase.text)
      XCTAssertEqual(output.categories.count, 2)
      let top = try XCTUnwrap(output.topCategory)
      XCTAssertEqual(top.rawLabel, specCase.expectedRaw)
      XCTAssertEqual(top.displayLabel, specCase.expectedDisplay)
      XCTAssertGreaterThanOrEqual(
        top.score,
        specCase.minScore,
        "Expected WordVec score >= \(specCase.minScore) for '\(specCase.text)', got \(top.score)"
      )

      let sum = output.categories.reduce(0.0) { $0 + Double($1.score) }
      XCTAssertEqual(sum, 1.0, accuracy: 1e-3)
    }
  }

  @MainActor
  func testEveryUserSelectableOptionCombinationSucceedsWithoutError() async throws {
    let viewModel = TextClassifierViewModel(autoLoad: false)
    for model in TextClassifierModelOption.allCases {
      let loaded = await viewModel.configure(model: model, delegate: .cpu)
      XCTAssertTrue(
        loaded,
        "Failed to load \(model.rawValue): \(viewModel.errorMessage ?? "")"
      )
      XCTAssertTrue(viewModel.isReady)
      XCTAssertNil(viewModel.errorMessage)

      for preset in TextClassificationPreset.defaults {
        viewModel.applyPreset(preset)
        let result = await viewModel.classify()
        let unwrapped = try XCTUnwrap(
          result,
          "Classify failed for \(model.rawValue) preset=\(preset.id): \(viewModel.errorMessage ?? "")"
        )
        XCTAssertEqual(unwrapped.categories.count, 2)
        XCTAssertNil(viewModel.errorMessage)
        let top = try XCTUnwrap(unwrapped.topCategory)
        XCTAssertTrue(["Positive", "Negative"].contains(top.displayLabel))
        XCTAssertGreaterThanOrEqual(top.score, 0.5)
        XCTAssertLessThanOrEqual(top.score, 1.0001)
      }
    }
  }

  @MainActor
  func testViewModelPresetsStateInvalidationAndValidation() async throws {
    let viewModel = TextClassifierViewModel(
      model: .mobileBert,
      delegate: .cpu,
      autoLoad: false
    )

    let loaded = await viewModel.loadClassifier()
    XCTAssertTrue(loaded, "Expected MobileBERT to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertEqual(viewModel.headerSubtitle, "MobileBERT · CPU")
    XCTAssertEqual(viewModel.activePresetID, "mediapipe")

    let positivePreset = try XCTUnwrap(
      TextClassificationPreset.defaults.first { $0.id == "positive" }
    )
    viewModel.applyPreset(positivePreset)
    XCTAssertEqual(viewModel.activePresetID, "positive")

    let rawPosOutput = await viewModel.classify()
    let posOutput = try XCTUnwrap(rawPosOutput)
    XCTAssertEqual(posOutput.topCategory?.displayLabel, "Positive")
    XCTAssertEqual(posOutput.topCategory?.polarity, .positive)
    XCTAssertGreaterThan(posOutput.topCategory?.score ?? 0, 0.9)
    XCTAssertEqual(viewModel.result, posOutput)

    // Editing input invalidates stale classification result.
    viewModel.inputText = "   "
    XCTAssertNil(viewModel.result)
    XCTAssertNil(viewModel.activePresetID)
    XCTAssertFalse(viewModel.canClassify)

    let negativePreset = try XCTUnwrap(
      TextClassificationPreset.defaults.first { $0.id == "negative" }
    )
    viewModel.applyPreset(negativePreset)
    XCTAssertTrue(viewModel.canClassify)
    let rawNegOutput = await viewModel.classify()
    let negOutput = try XCTUnwrap(rawNegOutput)
    XCTAssertEqual(negOutput.topCategory?.displayLabel, "Negative")
    XCTAssertEqual(negOutput.topCategory?.polarity, .negative)
    XCTAssertGreaterThan(negOutput.topCategory?.score ?? 0, 0.9)

    // Switching models updates subtitle and clears previous result.
    let switched = await viewModel.configure(model: .wordVec, delegate: .cpu)
    XCTAssertTrue(switched)
    XCTAssertNil(viewModel.result)
    XCTAssertEqual(viewModel.headerSubtitle, "WordVec · CPU")
  }

  @MainActor
  func testGpuDelegateDoesNotSilentlyFallBackOnUnsupportedTextModels() async throws {
    for model in TextClassifierModelOption.allCases {
      let viewModel = TextClassifierViewModel(
        model: model,
        delegate: .gpu,
        autoLoad: false
      )
      let loaded = await viewModel.loadClassifier()
      XCTAssertFalse(
        loaded,
        "Expected \(model.rawValue) to reject Metal GPU delegate on iOS without silent CPU fallback"
      )
      XCTAssertTrue(viewModel.isGpuError, "Expected GPU error flag for \(model.rawValue)")
      XCTAssertNotNil(viewModel.errorMessage)
      XCTAssertFalse(viewModel.isReady)

      // Explicitly switching back to CPU recovers cleanly.
      let recovered = await viewModel.configure(model: model, delegate: .cpu)
      XCTAssertTrue(recovered, "Expected CPU recovery to succeed for \(model.rawValue)")
      XCTAssertTrue(viewModel.isReady)
      XCTAssertFalse(viewModel.isGpuError)
      XCTAssertNil(viewModel.errorMessage)
      let rawOutput = await viewModel.classify()
      let output = try XCTUnwrap(rawOutput)
      XCTAssertEqual(output.categories.count, 2)
    }
  }
}
