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
import os
import XCTest
@testable import TextProofreader

final class TextProofreaderTests: XCTestCase {
  func testSpecSentencesAndCorrectionReconstruction() async throws {
    let helper = TextProofreaderHelper()
    let loadMs = try await helper.load(
      model: .gemma200M,
      delegate: .cpu,
      maxTokens: TextProofreaderMaxTokensOption.balanced.tokenCount
    )
    XCTAssertGreaterThan(loadMs, 0)

    // Spec step 1: single spelling typo with exact expected output.
    let step1Input = "I am a studnt."
    let step1 = try await helper.proofread(text: step1Input)
    XCTAssertEqual(step1.proofreadText.trimmingCharacters(in: .whitespacesAndNewlines), "I am a student.")
    XCTAssertGreaterThan(step1.editCount, 0)
    XCTAssertTrue(step1.hasEdits)
    XCTAssertFalse(step1.wasStreamed)
    XCTAssertFalse(step1.corrections.isEmpty)
    XCTAssertEqual(step1.reconstructedOriginalText, step1Input)
    XCTAssertEqual(step1.reconstructedProofreadText, step1.proofreadText)

    // Spec step 2: multiple grammar, punctuation, and capitalization fixes.
    let step2Input = "i hope you doing . theyre a great addition on any families."
    let step2 = try await helper.proofread(text: step2Input)
    let trimmedStep2 = step2.proofreadText.trimmingCharacters(in: .whitespacesAndNewlines)
    XCTAssertTrue(
      trimmedStep2.hasPrefix("I hope you"),
      "Expected output to start with 'I hope you', got: '\(trimmedStep2)'"
    )
    XCTAssertTrue(
      trimmedStep2.contains("They're") || trimmedStep2.contains("They are"),
      "Expected output to contain \"They're\" or \"They are\", got: '\(trimmedStep2)'"
    )
    XCTAssertGreaterThan(step2.editCount, 0)
    XCTAssertEqual(step2.reconstructedOriginalText, step2Input)
    XCTAssertEqual(step2.reconstructedProofreadText, step2.proofreadText)

    await helper.close()
  }

  func testCleanSentenceRemainsUnchanged() async throws {
    let helper = TextProofreaderHelper()
    _ = try await helper.load(
      model: .gemma200M,
      delegate: .cpu,
      maxTokens: TextProofreaderMaxTokensOption.balanced.tokenCount
    )

    let cleanPreset = try XCTUnwrap(ProofreaderPreset.defaults.first { $0.id == "clean" })
    let result = try await helper.proofread(text: cleanPreset.text)
    XCTAssertEqual(
      result.proofreadText.trimmingCharacters(in: .whitespacesAndNewlines),
      cleanPreset.text
    )
    XCTAssertEqual(result.editCount, 0)
    XCTAssertFalse(result.hasEdits)

    await helper.close()
  }

  func testStreamingProofreadingDeliversChunksAndCorrections() async throws {
    let helper = TextProofreaderHelper()
    _ = try await helper.load(
      model: .gemma200M,
      delegate: .cpu,
      maxTokens: TextProofreaderMaxTokensOption.balanced.tokenCount
    )

    let input = "Please send me the report by tommorow morning so we can reviewed it together."
    let collectedChunks = OSAllocatedUnfairLock(initialState: [String]())

    let result = try await helper.proofreadStreaming(text: input) { update in
      collectedChunks.withLock { $0.append(update.chunk) }
    }

    let chunks = collectedChunks.withLock { $0 }
    XCTAssertFalse(chunks.isEmpty, "Expected at least one streamed text chunk")
    XCTAssertEqual(chunks.joined(), result.proofreadText)
    XCTAssertTrue(result.wasStreamed)
    XCTAssertFalse(
      result.proofreadText.localizedCaseInsensitiveContains("tommorow"),
      "Expected typo 'tommorow' to be corrected in '\(result.proofreadText)'"
    )
    XCTAssertTrue(
      result.proofreadText.localizedCaseInsensitiveContains("tomorrow"),
      "Expected 'tomorrow' in '\(result.proofreadText)'"
    )
    XCTAssertFalse(result.corrections.isEmpty, "Expected streaming final result to include corrections")
    XCTAssertGreaterThan(result.editCount, 0)
    XCTAssertEqual(result.reconstructedOriginalText, input)
    XCTAssertEqual(result.reconstructedProofreadText, result.proofreadText)

    await helper.close()
  }

  @MainActor
  func testViewModelStateTransitionsPresetsAndApplyFix() async throws {
    let viewModel = TextProofreaderViewModel(
      maxTokensOption: .balanced,
      isStreamingEnabled: false,
      autoLoad: false
    )

    XCTAssertFalse(viewModel.isReady)
    XCTAssertFalse(viewModel.canProofread)

    let loaded = await viewModel.loadProofreader()
    XCTAssertTrue(loaded, "Expected proofreader to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertNil(viewModel.errorMessage)
    XCTAssertNotNil(viewModel.modelLoadTimeMs)

    let tensePreset = try XCTUnwrap(ProofreaderPreset.defaults.first { $0.id == "tense" })
    viewModel.applyPreset(tensePreset)
    XCTAssertEqual(viewModel.activePresetID, "tense")
    XCTAssertTrue(viewModel.canProofread)

    let rawOutput = await viewModel.proofread()
    let output = try XCTUnwrap(rawOutput)
    XCTAssertEqual(viewModel.result, output)
    XCTAssertFalse(output.proofreadText.localizedCaseInsensitiveContains("evry"))
    XCTAssertTrue(output.proofreadText.localizedCaseInsensitiveContains("every day"))
    XCTAssertGreaterThan(output.editCount, 0)

    // Display mode switching preserves result.
    viewModel.displayMode = .corrected
    XCTAssertEqual(viewModel.displayMode, .corrected)
    XCTAssertEqual(viewModel.result, output)
    viewModel.displayMode = .diff
    XCTAssertEqual(viewModel.displayMode, .diff)

    // Apply Fix copies corrected output into inputText and clears stale result.
    let expectedCorrected = output.proofreadText.trimmingCharacters(in: .whitespacesAndNewlines)
    viewModel.applyCorrectedToInput()
    XCTAssertEqual(viewModel.inputText, expectedCorrected)
    XCTAssertNil(viewModel.result)
    XCTAssertTrue(viewModel.streamingText.isEmpty)

    // Whitespace-only input disables proofreading cleanly.
    viewModel.inputText = "   \n  "
    XCTAssertFalse(viewModel.canProofread)
    let nilOutput = await viewModel.proofread()
    XCTAssertNil(nilOutput)
  }

  @MainActor
  func testEveryUserSelectableOptionCombinationSucceedsWithoutError() async throws {
    let viewModel = TextProofreaderViewModel(autoLoad: false)
    let testInput = "I am a studnt."

    for maxTokens in TextProofreaderMaxTokensOption.allCases {
      let loaded = await viewModel.configure(
        model: .gemma200M,
        delegate: .cpu,
        maxTokensOption: maxTokens
      )
      XCTAssertTrue(
        loaded,
        "Failed to load with maxTokens=\(maxTokens.tokenCount): \(viewModel.errorMessage ?? "")"
      )
      XCTAssertTrue(viewModel.isReady)
      XCTAssertNil(viewModel.errorMessage)

      for streaming in [false, true] {
        viewModel.setStreamingEnabled(streaming)
        viewModel.inputText = testInput
        let rawOutput = await viewModel.proofread()
        let output = try XCTUnwrap(
          rawOutput,
          "Proofread failed for maxTokens=\(maxTokens.tokenCount) streaming=\(streaming): \(viewModel.errorMessage ?? "")"
        )
        XCTAssertEqual(
          output.proofreadText.trimmingCharacters(in: .whitespacesAndNewlines),
          "I am a student.",
          "Unexpected output for maxTokens=\(maxTokens.tokenCount) streaming=\(streaming)"
        )
        XCTAssertEqual(output.wasStreamed, streaming)
        XCTAssertGreaterThan(output.editCount, 0)
        XCTAssertNil(viewModel.errorMessage)
      }
    }
  }

  func testAllPresetsProduceExpectedCorrections() async throws {
    let helper = TextProofreaderHelper()
    _ = try await helper.load(
      model: .gemma200M,
      delegate: .cpu,
      maxTokens: TextProofreaderMaxTokensOption.balanced.tokenCount
    )

    for preset in ProofreaderPreset.defaults {
      let result = try await helper.proofread(text: preset.text)
      XCTAssertEqual(result.reconstructedOriginalText, preset.text, "Preset '\(preset.id)' reconstruction mismatch")
      XCTAssertEqual(result.reconstructedProofreadText, result.proofreadText, "Preset '\(preset.id)' output reconstruction mismatch")
      if preset.id == "clean" {
        XCTAssertEqual(
          result.proofreadText.trimmingCharacters(in: .whitespacesAndNewlines),
          preset.text
        )
        XCTAssertEqual(result.editCount, 0)
        XCTAssertFalse(result.hasEdits)
      } else {
        XCTAssertGreaterThan(result.editCount, 0, "Expected edits for preset '\(preset.id)'")
        XCTAssertTrue(result.hasEdits, "Expected hasEdits for preset '\(preset.id)'")
      }
    }

    await helper.close()
  }

  @MainActor
  func testGpuDelegateIsRejectedExplicitlyWithoutSilentFallback() async {
    let viewModel = TextProofreaderViewModel(
      delegate: .gpu,
      autoLoad: false
    )
    let loaded = await viewModel.loadProofreader()
    XCTAssertFalse(loaded, "TextProofreader on iOS only supports CPU delegate and should reject GPU")
    XCTAssertTrue(viewModel.isGpuError)
    XCTAssertNotNil(viewModel.errorMessage)
    XCTAssertFalse(viewModel.isReady)
  }
}
