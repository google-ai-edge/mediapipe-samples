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
@testable import LanguageDetector

final class LanguageDetectorTests: XCTestCase {
  func testSpecMultilingualDetection() async throws {
    let helper = LanguageDetectorHelper()
    let loadMs = try await helper.load(
      delegate: .cpu,
      maxResults: 5,
      scoreThreshold: 0.001
    )
    XCTAssertGreaterThanOrEqual(loadMs, 0.0)

    // Matches .github/scripts/release_validate/spec/text/language_detector.json:
    // Chinese proverb whose characters overlap with Japanese ("match": "present", minScore 0.3).
    let proverb = try await helper.detect(text: "分久必合合久必分")
    let zhPrediction = proverb.predictions.first { $0.languageCode == "zh" }
    XCTAssertNotNil(zhPrediction, "Expected 'zh' among predictions: \(proverb.predictions)")
    XCTAssertGreaterThanOrEqual(zhPrediction?.probability ?? 0.0, 0.3)

    // Spec top-1 sentences plus Russian coverage.
    let topCases: [(text: String, expectedCode: String, minScore: Double)] = [
      ("To be, or not to be, that is the question", "en", 0.5),
      ("Bonjour, ceci est un test de validation automatique.", "fr", 0.5),
      ("Hola, esta es una prueba de validación automática para la nueva versión.", "es", 0.5),
      ("Hallo, dies ist ein automatischer Validierungstest für die neue Version.", "de", 0.5),
      ("これは新しいリリースの自動検証テストです。", "ja", 0.5),
      ("Привет мир! Машинное обучение на устройстве работает быстро и безопасно.", "ru", 0.5),
    ]

    for testCase in topCases {
      let result = try await helper.detect(text: testCase.text)
      let top = try XCTUnwrap(
        result.topPrediction,
        "Expected prediction for '\(testCase.text)'"
      )
      XCTAssertEqual(
        top.languageCode,
        testCase.expectedCode,
        "Unexpected top language for '\(testCase.text)': \(result.predictions)"
      )
      XCTAssertGreaterThanOrEqual(top.probability, testCase.minScore)
      XCTAssertGreaterThanOrEqual(result.inferenceTimeMs, 0.0)
      XCTAssertFalse(top.displayName.isEmpty)
    }
  }

  func testAllDefaultPresetsDetectExpectedLanguages() async throws {
    let helper = LanguageDetectorHelper()
    _ = try await helper.load(
      delegate: .cpu,
      maxResults: LanguageDetectorMaxResultsOption.top3.count,
      scoreThreshold: LanguageDetectorHelper.defaultScoreThreshold
    )

    for preset in LanguagePreset.defaults {
      let result = try await helper.detect(text: preset.text)
      let top = try XCTUnwrap(
        result.topPrediction,
        "Preset '\(preset.id)' produced no predictions."
      )
      XCTAssertEqual(
        top.languageCode,
        preset.expectedLanguageCode,
        "Preset '\(preset.id)' detected '\(top.languageCode)' instead of '\(preset.expectedLanguageCode)'"
      )
      XCTAssertGreaterThan(top.probability, 0.85)
    }
  }

  func testMaxResultsAndScoreThresholdOptions() async throws {
    let helper = LanguageDetectorHelper()

    // Ambiguous CJK proverb yields multiple candidates (zh & ja).
    for option in LanguageDetectorMaxResultsOption.allCases {
      _ = try await helper.load(
        delegate: .cpu,
        maxResults: option.count,
        scoreThreshold: 0.0
      )
      let result = try await helper.detect(text: "分久必合合久必分")
      XCTAssertGreaterThanOrEqual(result.predictions.count, 1)
      XCTAssertLessThanOrEqual(result.predictions.count, option.count)

      if option == .top1 {
        XCTAssertEqual(result.predictions.count, 1)
      } else {
        XCTAssertGreaterThanOrEqual(result.predictions.count, 2)
      }

      // Verify descending probability ordering.
      for idx in 1..<result.predictions.count {
        XCTAssertGreaterThanOrEqual(
          result.predictions[idx - 1].probability,
          result.predictions[idx].probability
        )
      }
    }

    // Strict score threshold filters out uncertain predictions.
    _ = try await helper.load(delegate: .cpu, maxResults: 5, scoreThreshold: 0.95)
    let filtered = try await helper.detect(text: "分久必合合久必分")
    XCTAssertTrue(
      filtered.predictions.allSatisfy { $0.probability >= 0.95 },
      "Expected all returned predictions to meet scoreThreshold 0.95: \(filtered.predictions)"
    )
  }

  func testResolveLanguageName() {
    let englishLocale = Locale(identifier: "en_US")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "en", locale: englishLocale), "English")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "fr", locale: englishLocale), "French")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "es", locale: englishLocale), "Spanish")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "de", locale: englishLocale), "German")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "zh", locale: englishLocale), "Chinese")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "ja", locale: englishLocale), "Japanese")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "ru", locale: englishLocale), "Russian")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "iw", locale: englishLocale), "Hebrew")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "unknown", locale: englishLocale), "Unknown")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "und", locale: englishLocale), "Unknown")
    XCTAssertEqual(LanguageDetectorHelper.resolveLanguageName(for: "   ", locale: englishLocale), "Unknown")
  }

  @MainActor
  func testViewModelDrivesPresetsOptionsAndState() async throws {
    let viewModel = LanguageDetectorViewModel(
      maxResultsOption: .top3,
      delegate: .cpu,
      autoLoad: false
    )

    XCTAssertEqual(viewModel.maxResultsOption, .top3)
    XCTAssertEqual(viewModel.maxResults, 3)
    XCTAssertEqual(viewModel.headerSubtitle, "Top 3 · CPU")

    let loaded = await viewModel.loadDetector()
    XCTAssertTrue(loaded, "Expected detector to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertNil(viewModel.errorMessage)
    XCTAssertNotNil(viewModel.modelLoadTimeMs)

    let spanishPreset = try XCTUnwrap(LanguagePreset.defaults.first { $0.id == "spanish" })
    viewModel.applyPreset(spanishPreset)
    XCTAssertEqual(viewModel.activePresetID, "spanish")
    XCTAssertNil(viewModel.result)

    let rawSpanishResult = await viewModel.detect()
    let spanishResult = try XCTUnwrap(rawSpanishResult)
    XCTAssertEqual(spanishResult.topPrediction?.languageCode, "es")
    XCTAssertEqual(viewModel.result, spanishResult)

    // Mutating inputText clears stale result cleanly.
    viewModel.inputText = "Hallo Welt, das ist ein Test."
    XCTAssertNil(viewModel.result)
    XCTAssertNil(viewModel.activePresetID)

    let rawGermanResult = await viewModel.detect()
    let germanResult = try XCTUnwrap(rawGermanResult)
    XCTAssertEqual(germanResult.topPrediction?.languageCode, "de")

    // Programmatic configure updates maxResults and scoreThreshold.
    let reconfigured = await viewModel.configure(maxResults: 1, scoreThreshold: 0.01)
    XCTAssertTrue(reconfigured)
    XCTAssertEqual(viewModel.maxResultsOption, .top1)
    XCTAssertEqual(viewModel.maxResults, 1)
    XCTAssertEqual(viewModel.headerSubtitle, "Top 1 · CPU")

    let rawTopOne = await viewModel.detect(text: "こんにちは、今日はとても良い天気ですね。")
    let topOne = try XCTUnwrap(rawTopOne)
    XCTAssertEqual(topOne.predictions.count, 1)
    XCTAssertEqual(topOne.topPrediction?.languageCode, "ja")
  }

  @MainActor
  func testEveryUserSelectableOptionCombinationSucceedsWithoutError() async throws {
    let viewModel = LanguageDetectorViewModel(autoLoad: false)

    for maxOption in LanguageDetectorMaxResultsOption.allCases {
      let loaded = await viewModel.configure(
        delegate: .cpu,
        maxResults: maxOption.count
      )
      XCTAssertTrue(
        loaded,
        "Failed to load maxResults=\(maxOption.count): \(viewModel.errorMessage ?? "")"
      )
      XCTAssertNil(viewModel.errorMessage)

      for preset in LanguagePreset.defaults {
        viewModel.applyPreset(preset)
        let rawResult = await viewModel.detect()
        let result = try XCTUnwrap(
          rawResult,
          "Detect failed for maxResults=\(maxOption.count) preset=\(preset.id): \(viewModel.errorMessage ?? "")"
        )
        XCTAssertGreaterThanOrEqual(result.predictions.count, 1)
        XCTAssertLessThanOrEqual(result.predictions.count, maxOption.count)
        XCTAssertEqual(result.topPrediction?.languageCode, preset.expectedLanguageCode)
        XCTAssertNil(viewModel.errorMessage)
        for item in result.predictions {
          XCTAssertTrue(item.probability.isFinite)
          XCTAssertGreaterThanOrEqual(item.probability, 0.0)
          XCTAssertLessThanOrEqual(item.probability, 1.0001)
        }
      }
    }
  }

  @MainActor
  func testGpuDelegateRejectsWithoutSilentFallback() async {
    let viewModel = LanguageDetectorViewModel(
      maxResultsOption: .top3,
      delegate: .gpu,
      autoLoad: false
    )
    let loaded = await viewModel.loadDetector()
    // MediaPipe LanguageDetector rejects Metal GPU on iOS/macOS because string input tensors
    // are unsupported. Verify the error is surfaced explicitly without silently falling back.
    XCTAssertFalse(loaded, "Expected GPU delegate to be rejected for string-input LanguageDetector.")
    XCTAssertTrue(viewModel.isGpuError)
    XCTAssertNotNil(viewModel.errorMessage)
    XCTAssertFalse(viewModel.isReady)

    // Verify recovery via CPU succeeds cleanly.
    let recovered = await viewModel.configure(delegate: .cpu)
    XCTAssertTrue(recovered, "Expected recovery on CPU delegate to succeed.")
    XCTAssertFalse(viewModel.isGpuError)
    XCTAssertNil(viewModel.errorMessage)
    XCTAssertTrue(viewModel.isReady)
  }
}
