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
import os
@testable import TextSummarizer

final class TextSummarizerTests: XCTestCase {
  /// Passage from `.github/scripts/release_validate/spec/text/text_summarizer.json`.
  private static let brusselsSpecPassage =
    "This station is in the centre of the European quarter of Brussels, being adjacent to the Berlaymont building (headquarters of the European Commission), the Justus Lipsius building (Council of the European Union) and numerous other EU offices. The station is named after the area around the Schuman roundabout, which was named after Robert Schuman. The station sits on the Rue de la Loi/Wetstraat, a major city thoroughfare, and is close to Parc du Cinquantenaire/Jubelpark."

  func testKeyPointsModeMatchesSpecAssertions() async throws {
    let helper = TextSummarizerHelper()
    let loadMs = try await helper.load(
      model: .gemma200M,
      mode: .keyPoints,
      maxTokens: .extended,
      delegate: .cpu
    )
    XCTAssertGreaterThan(loadMs, 0)

    let output = try await helper.summarize(text: Self.brusselsSpecPassage)
    await helper.close()

    XCTAssertEqual(output.mode, .keyPoints)
    XCTAssertFalse(output.streamed)
    XCTAssertFalse(output.summary.isEmpty)
    XCTAssertGreaterThan(output.inferenceTimeMs, 0)
    XCTAssertLessThanOrEqual(
      output.characterLengthRatio,
      1.0,
      "Expected Key Points summary (\(output.summaryCharacterCount) chars) to be shorter than input (\(output.sourceCharacterCount) chars): \(output.summary)"
    )
    XCTAssertGreaterThanOrEqual(
      output.bulletPoints.count,
      2,
      "Expected at least 2 bullet points in Key Points mode, got: \(output.summary)"
    )
    assertMentionsSpecTerms(output.summary)
  }

  func testTldrModeMatchesSpecAssertions() async throws {
    let helper = TextSummarizerHelper()
    _ = try await helper.load(
      model: .gemma200M,
      mode: .tldr,
      maxTokens: .extended,
      delegate: .cpu
    )

    let output = try await helper.summarize(text: Self.brusselsSpecPassage)
    await helper.close()

    XCTAssertEqual(output.mode, .tldr)
    XCTAssertFalse(output.streamed)
    XCTAssertFalse(output.summary.isEmpty)
    XCTAssertLessThanOrEqual(
      output.characterLengthRatio,
      0.5,
      "Expected TL;DR summary (\(output.summaryCharacterCount) chars) <= 50% of input (\(output.sourceCharacterCount) chars): \(output.summary)"
    )
    XCTAssertTrue(
      output.bulletPoints.isEmpty,
      "Expected TL;DR summary to be prose rather than bullets, got: \(output.summary)"
    )
    assertMentionsSpecTerms(output.summary)
  }

  func testStreamingSummarizationEmitsIncrementalChunks() async throws {
    let helper = TextSummarizerHelper()
    _ = try await helper.load(
      model: .gemma200M,
      mode: .tldr,
      maxTokens: .extended,
      delegate: .cpu
    )

    let receivedChunks = OSAllocatedUnfairLock(initialState: [String]())
    let output = try await helper.summarizeStreaming(text: Self.brusselsSpecPassage) { chunk in
      receivedChunks.withLock { $0.append(chunk) }
    }
    await helper.close()

    let chunks = receivedChunks.withLock { $0 }
    let joined = chunks.joined().trimmingCharacters(in: .whitespacesAndNewlines)

    XCTAssertTrue(output.streamed)
    XCTAssertGreaterThanOrEqual(chunks.count, 1)
    XCTAssertEqual(output.chunkCount, chunks.count)
    XCTAssertEqual(output.summary, joined)
    XCTAssertFalse(output.summary.isEmpty)
    assertMentionsSpecTerms(output.summary)
  }

  @MainActor
  func testViewModelStateTransitionsPresetsAndSummarization() async throws {
    let viewModel = TextSummarizerViewModel(
      model: .gemma200M,
      mode: .keyPoints,
      maxTokens: .extended,
      delegate: .cpu,
      streamingEnabled: false,
      autoLoad: false
    )

    XCTAssertFalse(viewModel.isReady)
    XCTAssertFalse(viewModel.canSummarize)
    XCTAssertEqual(viewModel.activePresetID, "brussels")

    let loaded = await viewModel.loadSummarizer()
    XCTAssertTrue(loaded, "Expected Gemma 200M to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertTrue(viewModel.canSummarize)
    XCTAssertNil(viewModel.errorMessage)
    XCTAssertNotNil(viewModel.modelLoadTimeMs)

    let weatherPreset = try XCTUnwrap(SummarizerPreset.defaults.first { $0.id == "weather" })
    viewModel.applyPreset(weatherPreset)
    XCTAssertEqual(viewModel.activePresetID, "weather")
    XCTAssertNil(viewModel.result)

    let brusselsPreset = try XCTUnwrap(SummarizerPreset.defaults.first { $0.id == "brussels" })
    viewModel.applyPreset(brusselsPreset)
    XCTAssertEqual(viewModel.activePresetID, "brussels")
    XCTAssertNil(viewModel.result)

    let rawOutput = await viewModel.summarize()
    let output = try XCTUnwrap(rawOutput)
    XCTAssertEqual(viewModel.result, output)
    XCTAssertEqual(output.mode, .keyPoints)
    XCTAssertGreaterThanOrEqual(output.bulletPoints.count, 2)
    XCTAssertEqual(viewModel.headerSubtitle, "Gemma 200M · Key Points · CPU")

    // Editing input clears stale result.
    viewModel.inputText = "   "
    XCTAssertNil(viewModel.result)
    XCTAssertFalse(viewModel.canSummarize)
    XCTAssertNil(viewModel.activePresetID)

    // Toggling streaming flag updates state cleanly without requiring a model reload.
    viewModel.setStreamingEnabled(true)
    XCTAssertTrue(viewModel.streamingEnabled)
  }

  @MainActor
  func testEveryUserSelectableSettingsCombinationSucceedsWithoutError() async throws {
    let viewModel = TextSummarizerViewModel(autoLoad: false)
    let passage = SummarizerPreset.defaults[0].text

    for mode in TextSummarizerModeOption.allCases {
      for maxTokens in TextSummarizerMaxTokensOption.allCases {
        let loaded = await viewModel.configure(
          model: .gemma200M,
          mode: mode,
          maxTokens: maxTokens,
          delegate: .cpu,
          streamingEnabled: false
        )
        XCTAssertTrue(
          loaded,
          "Failed to load mode=\(mode.rawValue) maxTokens=\(maxTokens.rawValue): \(viewModel.errorMessage ?? "")"
        )
        XCTAssertNil(viewModel.errorMessage)

        for streaming in [false, true] {
          viewModel.setStreamingEnabled(streaming)
          viewModel.inputText = passage
          let rawResult = await viewModel.summarize()
          let output = try XCTUnwrap(
            rawResult,
            "Summarize failed for mode=\(mode.rawValue) maxTokens=\(maxTokens.rawValue) streaming=\(streaming): \(viewModel.errorMessage ?? "")"
          )
          XCTAssertEqual(output.mode, mode)
          XCTAssertEqual(output.streamed, streaming)
          XCTAssertFalse(output.summary.isEmpty)
          XCTAssertGreaterThan(output.inferenceTimeMs, 0)
          XCTAssertNil(viewModel.errorMessage)
        }
      }
    }
  }

  @MainActor
  func testGpuDelegateDoesNotSilentlyFallBack() async {
    let viewModel = TextSummarizerViewModel(
      model: .gemma200M,
      mode: .keyPoints,
      maxTokens: .extended,
      delegate: .gpu,
      autoLoad: false
    )
    let loaded = await viewModel.loadSummarizer()
    XCTAssertFalse(loaded, "Expected GPU delegate to be rejected for CPU-only summarization model")
    XCTAssertTrue(viewModel.isGpuError)
    XCTAssertNotNil(viewModel.errorMessage)
    XCTAssertFalse(viewModel.isReady)
  }

  func testAllPresetsSummarizeCleanlyInBothModes() async throws {
    let helper = TextSummarizerHelper()
    for mode in TextSummarizerModeOption.allCases {
      _ = try await helper.load(
        model: .gemma200M,
        mode: mode,
        maxTokens: .extended,
        delegate: .cpu
      )
      for preset in SummarizerPreset.defaults {
        let output = try await helper.summarize(text: preset.text)
        XCTAssertFalse(output.summary.isEmpty)
        if mode == .keyPoints {
          XCTAssertGreaterThanOrEqual(output.bulletPoints.count, 2)
        } else {
          XCTAssertLessThan(output.summaryWordCount, output.sourceWordCount)
        }
      }
    }
    await helper.close()
  }

  func testOutputBulletParsingAndWordReductionHelpers() {
    let sample = """
      * MediaPipe runs models on-device for low latency.
      * Private user data stays offline.
      - Quantized bundles support mobile hardware.
      """
    let bullets = TextSummarizerOutput.parseBulletPoints(from: sample)
    XCTAssertEqual(bullets.count, 3)
    XCTAssertEqual(bullets[0], "MediaPipe runs models on-device for low latency.")
    XCTAssertEqual(bullets[2], "Quantized bundles support mobile hardware.")
    XCTAssertEqual(TextSummarizerOutput.wordCount(of: "* One two\n- Three four"), 4)

    let output = TextSummarizerOutput(
      summary: "Short summary here.",
      mode: .tldr,
      sourceCharacterCount: 100,
      sourceWordCount: 20,
      summaryCharacterCount: 19,
      summaryWordCount: 3,
      inferenceTimeMs: 420.0,
      streamed: false,
      chunkCount: 1
    )
    XCTAssertEqual(output.wordReductionPercent, 85)
    XCTAssertEqual(output.characterLengthRatio, 0.19, accuracy: 1e-6)
  }

  private func assertMentionsSpecTerms(_ summary: String) {
    let lowered = summary.lowercased()
    XCTAssertTrue(
      lowered.contains("brussels"),
      "Expected summary to mention 'Brussels', got: \(summary)"
    )
    let mentionsAny = ["schuman", "european", "eu"].contains { lowered.contains($0) }
    XCTAssertTrue(
      mentionsAny,
      "Expected summary to mention one of Schuman/European/EU, got: \(summary)"
    )
  }
}
