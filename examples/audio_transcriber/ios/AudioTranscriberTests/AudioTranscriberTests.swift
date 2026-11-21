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

import AudioTranscriber
import MediaPipeSampleTesting
import MediaPipeSampleUI
import XCTest

final class AudioTranscriberTests: XCTestCase {
  private static let referenceTranscript = "Send a text to Mike, I'll be home late tomorrow."
  private static let maxWordErrorRate = 0.2

  // MARK: - Audio Decoding & Metric Helpers

  func testWordErrorRateHelperMatchesSpecCases() {
    XCTAssertEqual(
      Self.wordErrorRate(
        reference: "Send a text to Mike, I'll be home.",
        hypothesis: "send a text to mike i'll be home"
      ),
      0.0,
      accuracy: 1e-6
    )
    XCTAssertEqual(
      Self.wordErrorRate(reference: "a b c d", hypothesis: "a x c"),
      0.5,
      accuracy: 1e-6
    )
    XCTAssertEqual(
      Self.wordErrorRate(reference: "a b", hypothesis: ""),
      1.0,
      accuracy: 1e-6
    )
  }

  func testSampleWavDecodingAndResampling() async throws {
    let helper = AudioTranscriberHelper()
    let sampleURL = try await helper.locateBundledSampleURL()
    let clip = try AudioClipDecoder.decode(url: sampleURL)

    XCTAssertEqual(clip.sampleRateHz, 16_000.0, accuracy: 0.5)
    XCTAssertEqual(clip.sourceSampleRateHz, 16_000.0, accuracy: 0.5)
    XCTAssertEqual(clip.durationSeconds, 4.27, accuracy: 0.15)
    XCTAssertTrue(EmbeddingMetrics.allFinite(clip.samples))
    XCTAssertGreaterThan(AudioClipDecoder.normalizedRmsLevel(for: clip.samples), 0.01)

    // Verify mono resampling from 48 kHz to 16 kHz.
    let tone48k = (0..<48_000).map { i in
      sin(Float(i) * 2.0 * .pi * 440.0 / 48_000.0) * 0.5
    }
    let resampled16k = try AudioClipDecoder.resampleMono(
      tone48k,
      from: 48_000.0,
      to: 16_000.0
    )
    XCTAssertEqual(Double(resampled16k.count), 16_000.0, accuracy: 512.0)
    XCTAssertTrue(EmbeddingMetrics.allFinite(resampled16k))
  }

  // MARK: - Real-Model CPU Transcription (Full Clip & Streaming)

  func testFullClipAndStreamingTranscriptionMatchReferenceWithLowWER() async throws {
    let helper = AudioTranscriberHelper()
    let loadMs = try await helper.load(model: .parakeetTdtV3, delegate: .cpu)
    XCTAssertGreaterThan(loadMs, 0.0)

    let sampleURL = try await helper.locateBundledSampleURL()

    // 1. Full-clip mode (spec mode: "file", stream == false)
    let fileResult = try await helper.transcribe(url: sampleURL, stream: false)
    let fileWer = Self.wordErrorRate(
      reference: Self.referenceTranscript,
      hypothesis: fileResult.transcript
    )
    print(
      "[AudioTranscriberTests] CPU file mode: transcript=\"\(fileResult.transcript)\" "
        + "segments=\(fileResult.segments.count) "
        + "ranges=\(fileResult.segments.map(\.timeRangeLabel)) "
        + "WER=\(String(format: "%.3f", fileWer)) "
        + "latencyMs=\(String(format: "%.1f", fileResult.inferenceTimeMs)) "
        + "RTF=\(String(format: "%.3f", fileResult.realTimeFactor))"
    )
    XCTAssertEqual(fileResult.mode, .fullClip)
    XCTAssertEqual(fileResult.chunkCount, 1)
    XCTAssertFalse(fileResult.transcript.isEmpty)
    XCTAssertEqual(fileResult.audioDurationSeconds, 4.27, accuracy: 0.15)
    XCTAssertGreaterThan(fileResult.inferenceTimeMs, 0.0)
    XCTAssertGreaterThan(fileResult.realTimeFactor, 0.0)
    XCTAssertLessThanOrEqual(
      fileWer,
      Self.maxWordErrorRate,
      "File mode WER \(fileWer) exceeded \(Self.maxWordErrorRate) for transcript '\(fileResult.transcript)'"
    )

    // 2. Streaming mode (spec mode: "stream", stream == true) on the SAME prewarmed helper
    actor UpdateCounter {
      var count = 0
      func increment() { count += 1 }
    }
    let counter = UpdateCounter()
    let streamResult = try await helper.transcribe(url: sampleURL, stream: true) { _ in
      await counter.increment()
    }
    let progressCount = await counter.count
    let streamWer = Self.wordErrorRate(
      reference: Self.referenceTranscript,
      hypothesis: streamResult.transcript
    )
    print(
      "[AudioTranscriberTests] CPU stream mode: transcript=\"\(streamResult.transcript)\" "
        + "WER=\(String(format: "%.3f", streamWer)) "
        + "chunks=\(streamResult.chunkCount) progressUpdates=\(progressCount) "
        + "latencyMs=\(String(format: "%.1f", streamResult.inferenceTimeMs)) "
        + "RTF=\(String(format: "%.3f", streamResult.realTimeFactor))"
    )
    XCTAssertEqual(streamResult.mode, .streaming)
    XCTAssertGreaterThan(streamResult.chunkCount, 1)
    XCTAssertEqual(progressCount, streamResult.chunkCount)
    XCTAssertFalse(streamResult.transcript.isEmpty)
    XCTAssertLessThanOrEqual(
      streamWer,
      Self.maxWordErrorRate,
      "Stream mode WER \(streamWer) exceeded \(Self.maxWordErrorRate) for transcript '\(streamResult.transcript)'"
    )

    await helper.close()
  }

  // MARK: - ViewModel State Transitions & User-Selectable Option Matrix

  @MainActor
  func testViewModelEveryUserSelectableOptionCombinationSucceedsWithoutError() async throws {
    let viewModel = AudioTranscriberViewModel(
      model: .parakeetTdtV3,
      delegate: .cpu,
      clipMode: .fullClip,
      autoLoad: false,
      autoTranscribeSampleOnLaunch: false
    )

    let loaded = await viewModel.loadTranscriber()
    XCTAssertTrue(loaded, "Expected Parakeet TDT to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertNil(viewModel.errorMessage)
    XCTAssertNotNil(viewModel.modelLoadTimeMs)

    let uiDelegates: [AudioTranscriberDelegateOption] =
      AudioTranscriberViewModel.supportsGpuDelegateInUI ? [.cpu, .gpu] : [.cpu]

    for delegate in uiDelegates {
      for clipMode in AudioClipTranscriptionMode.allCases {
        let configured = await viewModel.configure(delegate: delegate, clipMode: clipMode)
        XCTAssertTrue(
          configured,
          "Configure failed for delegate=\(delegate.rawValue) mode=\(clipMode.rawValue): \(viewModel.errorMessage ?? "")"
        )
        XCTAssertEqual(viewModel.selectedDelegate, delegate)
        XCTAssertEqual(viewModel.clipMode, clipMode)

        let result = await viewModel.transcribeBundledSample()
        let unwrapped = try XCTUnwrap(
          result,
          "Transcribe failed for delegate=\(delegate.rawValue) mode=\(clipMode.rawValue): \(viewModel.errorMessage ?? "")"
        )
        let wer = Self.wordErrorRate(
          reference: Self.referenceTranscript,
          hypothesis: unwrapped.transcript
        )
        XCTAssertLessThanOrEqual(
          wer,
          Self.maxWordErrorRate,
          "WER \(wer) exceeded \(Self.maxWordErrorRate) for \(delegate.rawValue)/\(clipMode.rawValue): '\(unwrapped.transcript)'"
        )
        XCTAssertEqual(viewModel.lastResult, unwrapped)
        XCTAssertEqual(viewModel.fullTranscript, unwrapped.transcript)
        XCTAssertTrue(viewModel.hasTranscript)
        XCTAssertNil(viewModel.errorMessage)
        print(
          "[AudioTranscriberTests] Matrix \(delegate.rawValue)/\(clipMode.rawValue): "
            + "transcript=\"\(unwrapped.transcript)\" WER=\(String(format: "%.3f", wer)) "
            + "latencyMs=\(String(format: "%.1f", unwrapped.inferenceTimeMs))"
        )
      }
    }

    // Verify switching back to CPU after GPU works cleanly without crashes.
    let backToCpu = await viewModel.configure(delegate: .cpu, clipMode: .fullClip)
    XCTAssertTrue(backToCpu, "Failed to switch back to CPU: \(viewModel.errorMessage ?? "")")
    let afterSwitchOptional = await viewModel.transcribeBundledSample()
    let afterSwitch = try XCTUnwrap(afterSwitchOptional)
    XCTAssertLessThanOrEqual(
      Self.wordErrorRate(reference: Self.referenceTranscript, hypothesis: afterSwitch.transcript),
      Self.maxWordErrorRate
    )

    // Verify input mode switching and clearing state cleanly.
    viewModel.selectInputMode(.microphone)
    XCTAssertEqual(viewModel.inputMode, .microphone)
    viewModel.selectInputMode(.clip)
    XCTAssertEqual(viewModel.inputMode, .clip)

    await viewModel.clearTranscript()
    XCTAssertFalse(viewModel.hasTranscript)
    XCTAssertTrue(viewModel.finalizedSegments.isEmpty)
    XCTAssertTrue(viewModel.interimTranscript.isEmpty)
    XCTAssertNil(viewModel.lastResult)
    XCTAssertNil(viewModel.errorMessage)
  }

  // MARK: - GPU Delegate Probe & Strict Error Handling

  @MainActor
  func testGpuDelegateBehaviorWithoutSilentFallback() async throws {
    let viewModel = AudioTranscriberViewModel(
      model: .parakeetTdtV3,
      delegate: .gpu,
      clipMode: .fullClip,
      autoLoad: false,
      autoTranscribeSampleOnLaunch: false
    )

    let loaded = await viewModel.loadTranscriber()
    if loaded {
      let result = await viewModel.transcribeBundledSample(stream: false)
      let unwrapped = try XCTUnwrap(
        result,
        "GPU loaded but transcription failed: \(viewModel.errorMessage ?? "")"
      )
      let wer = Self.wordErrorRate(
        reference: Self.referenceTranscript,
        hypothesis: unwrapped.transcript
      )
      print(
        "[AudioTranscriberTests] GPU SUCCEEDED: transcript=\"\(unwrapped.transcript)\" "
          + "WER=\(String(format: "%.3f", wer)) "
          + "latencyMs=\(String(format: "%.1f", unwrapped.inferenceTimeMs))"
      )
      XCTAssertLessThanOrEqual(wer, Self.maxWordErrorRate)
    } else {
      print(
        "[AudioTranscriberTests] GPU UNSUPPORTED (surfaced explicitly): \(viewModel.errorMessage ?? "")"
      )
      XCTAssertTrue(viewModel.isGpuError)
      XCTAssertNotNil(viewModel.errorMessage)
      XCTAssertFalse(viewModel.isReady)
    }
  }

  // MARK: - Word Error Rate (matches .github/scripts/release_validate/verify.py)

  private static func normalizeWords(_ text: String) -> [String] {
    let lower = text.lowercased()
    var cleaned = ""
    cleaned.reserveCapacity(lower.count)
    for scalar in lower.unicodeScalars {
      if CharacterSet.alphanumerics.contains(scalar) || scalar == "'" || scalar == "_" {
        cleaned.unicodeScalars.append(scalar)
      } else {
        cleaned.append(" ")
      }
    }
    return cleaned.split(whereSeparator: \.isWhitespace).map(String.init)
  }

  private static func wordErrorRate(reference: String, hypothesis: String) -> Double {
    let ref = normalizeWords(reference)
    let hyp = normalizeWords(hypothesis)
    guard !ref.isEmpty else { return hyp.isEmpty ? 0.0 : 1.0 }

    var prev = Array(0...hyp.count)
    var curr = Array(repeating: 0, count: hyp.count + 1)

    for (i, refWord) in ref.enumerated() {
      curr[0] = i + 1
      for (j, hypWord) in hyp.enumerated() {
        let cost = refWord == hypWord ? 0 : 1
        curr[j + 1] = min(
          prev[j + 1] + 1,
          curr[j] + 1,
          prev[j] + cost
        )
      }
      swap(&prev, &curr)
    }
    return Double(prev[hyp.count]) / Double(ref.count)
  }
}
