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
@testable import AudioClassifier

final class AudioClassifierTests: XCTestCase {
  func testBundledSpeechAudioClipsMatchesReleaseSpec() async throws {
    let helper = AudioClassifierHelper()
    let loadMs = try await helper.load(
      model: .yamnet,
      delegate: .cpu,
      runningMode: .audioClips,
      maxResults: 4,
      scoreThreshold: 0.0
    )
    XCTAssertGreaterThanOrEqual(loadMs, 0.0)

    let (clip, _) = try await helper.loadBundledSpeechClip()
    XCTAssertEqual(clip.sampleRate, 16_000.0, accuracy: 1.0)
    XCTAssertGreaterThan(clip.durationMs, 4_000)

    let result = try await helper.classifyClip(clip)
    XCTAssertEqual(result.runningMode, .audioClips)
    XCTAssertEqual(result.windows.count, 5, "Expected 5 YAMNet windows for 4.27 s speech clip")

    let expectedTimestamps = [0, 975, 1950, 2925, 3900]
    let actualTimestamps = result.windows.map(\.timestampMs)
    XCTAssertEqual(actualTimestamps, expectedTimestamps)

    for window in result.windows.prefix(4) {
      let top = try XCTUnwrap(
        window.topCategory,
        "Expected top category for window at \(window.timestampMs) ms"
      )
      XCTAssertEqual(
        top.label,
        "Speech",
        "Expected Speech for window at \(window.timestampMs) ms, got \(top.label) (\(top.score))"
      )
      XCTAssertGreaterThanOrEqual(
        top.score,
        0.5,
        "Expected Speech score >= 0.5 for window at \(window.timestampMs) ms"
      )
    }

    for window in result.windows {
      XCTAssertGreaterThanOrEqual(window.categories.count, 1)
      XCTAssertLessThanOrEqual(window.categories.count, 4)
    }
  }

  func testBundledSpeechAudioStreamMatchesReleaseSpec() async throws {
    let helper = AudioClassifierHelper()
    _ = try await helper.load(
      model: .yamnet,
      delegate: .cpu,
      runningMode: .audioStream,
      maxResults: 4,
      scoreThreshold: 0.0
    )

    let (clip, _) = try await helper.loadBundledSpeechClip()
    let result = try await helper.classifyClip(clip)

    XCTAssertEqual(result.runningMode, .audioStream)
    XCTAssertEqual(
      result.windows.count,
      5,
      "Expected 5 windows (4 full + 1 flushed partial) in AUDIO_STREAM mode; got \(result.windows.count) with timestamps \(result.windows.map(\.timestampMs))"
    )

    let fullWindows = Array(result.windows.prefix(4))
    XCTAssertEqual(fullWindows.count, 4)
    XCTAssertEqual(fullWindows.map(\.timestampMs), [0, 975, 1950, 2925])

    for window in fullWindows {
      let top = try XCTUnwrap(
        window.topCategory,
        "Expected top category for stream window at \(window.timestampMs) ms"
      )
      XCTAssertEqual(
        top.label,
        "Speech",
        "Expected Speech for stream window at \(window.timestampMs) ms, got \(top.label) (\(top.score))"
      )
      XCTAssertGreaterThanOrEqual(top.score, 0.5)
    }

    for (idx, window) in result.windows.enumerated() {
      XCTAssertGreaterThanOrEqual(window.categories.count, 1)
      XCTAssertLessThanOrEqual(window.categories.count, 4)
      XCTAssertEqual(
        window.displayTimestampSeconds,
        Double(idx) * 0.975,
        accuracy: 0.02
      )
    }
  }

  func testSyntheticToneAndWavFileDecodeRoundTrip() async throws {
    let tone = AudioClassifierHelper.generateSineTone(
      frequencyHz: 440.0,
      durationSeconds: 2.0,
      sampleRate: 16_000.0
    )
    XCTAssertEqual(tone.samples.count, 32_000)
    XCTAssertEqual(tone.durationMs, 2_000)

    let bars = tone.waveformBars(count: 32)
    XCTAssertEqual(bars.count, 32)
    XCTAssertTrue(bars.allSatisfy { $0 >= 0.08 && $0 <= 1.0 })

    let tempURL = FileManager.default.temporaryDirectory
      .appendingPathComponent("audio_classifier_test_\(UUID().uuidString).wav")
    defer { try? FileManager.default.removeItem(at: tempURL) }
    try tone.makeWavData().write(to: tempURL)

    let decoded = try AudioClassifierHelper.decodeAudioFile(at: tempURL)
    XCTAssertEqual(decoded.sampleRate, 16_000.0, accuracy: 1.0)
    XCTAssertEqual(decoded.samples.count, 32_000)

    let helper = AudioClassifierHelper()
    _ = try await helper.load(
      model: .yamnet,
      delegate: .cpu,
      runningMode: .audioClips,
      maxResults: 4,
      scoreThreshold: 0.0
    )
    let result = try await helper.classifyAudioFile(at: tempURL)
    XCTAssertGreaterThanOrEqual(result.windows.count, 2)
    XCTAssertFalse(result.windows[0].categories.isEmpty)
  }

  @MainActor
  func testViewModelDrivesModesWindowsAndStateTransitions() async throws {
    let viewModel = AudioClassifierViewModel(
      model: .yamnet,
      delegate: .cpu,
      clipRunningMode: .audioClips,
      maxResults: 4,
      scoreThreshold: 0.0,
      autoLoad: false
    )

    let loaded = await viewModel.loadClassifier()
    XCTAssertTrue(loaded, "Expected YAMNet to load: \(viewModel.errorMessage ?? "")")
    XCTAssertTrue(viewModel.isReady)
    XCTAssertNil(viewModel.errorMessage)

    let rawClipResult = await viewModel.classifyCurrentClip()
    let clipResult = try XCTUnwrap(rawClipResult)
    XCTAssertEqual(clipResult.windows.count, 5)
    XCTAssertEqual(viewModel.activeClipWindow?.topCategory?.label, "Speech")
    XCTAssertFalse(viewModel.waveformBars.isEmpty)
    XCTAssertTrue(viewModel.hasResult)

    // Scrubber selects individual windows cleanly.
    viewModel.selectWindow(2)
    XCTAssertEqual(viewModel.activeWindowIndex, 2)
    XCTAssertEqual(viewModel.activeClipWindow?.timestampMs, 1950)
    XCTAssertEqual(viewModel.activeClipWindow?.topCategory?.label, "Speech")

    // Reconfigure into AUDIO_STREAM mode and classify again.
    let streamLoaded = await viewModel.configure(clipRunningMode: .audioStream, maxResults: 3)
    XCTAssertTrue(streamLoaded, "Expected AUDIO_STREAM reload: \(viewModel.errorMessage ?? "")")
    let rawStreamResult = await viewModel.classifyCurrentClip()
    let streamResult = try XCTUnwrap(rawStreamResult)
    XCTAssertEqual(streamResult.runningMode, .audioStream)
    XCTAssertEqual(streamResult.windows.count, 5)
    XCTAssertEqual(streamResult.windows[0].topCategory?.label, "Speech")
    XCTAssertLessThanOrEqual(streamResult.windows[0].categories.count, 3)

    // Tab switching preserves clip state cleanly.
    viewModel.selectTab(.microphone)
    XCTAssertEqual(viewModel.selectedTab, .microphone)
    XCTAssertFalse(viewModel.hasResult)
    viewModel.selectTab(.clip)
    XCTAssertEqual(viewModel.selectedTab, .clip)
    XCTAssertTrue(viewModel.hasResult)

    // Live overlap options produce valid intervals.
    for option in AudioOverlapOption.allCases {
      XCTAssertGreaterThanOrEqual(option.intervalSeconds, 0.1)
      XCTAssertFalse(option.label.isEmpty)
    }
  }

  @MainActor
  func testEveryUserSelectableOptionCombinationSucceedsWithoutError() async throws {
    let viewModel = AudioClassifierViewModel(autoLoad: false)
    let thresholds: [Float] = [0.0, 0.1, 0.3, 0.5, 0.9]

    for runningMode in AudioClassifierRunningModeOption.allCases {
      for maxResults in 1...5 {
        for threshold in thresholds {
          let loaded = await viewModel.configure(
            model: .yamnet,
            delegate: .cpu,
            clipRunningMode: runningMode,
            maxResults: maxResults,
            scoreThreshold: threshold
          )
          XCTAssertTrue(
            loaded,
            "Failed to load mode=\(runningMode.rawValue) max=\(maxResults) threshold=\(threshold): \(viewModel.errorMessage ?? "")"
          )

          let rawResult = await viewModel.classifyCurrentClip()
          let result = try XCTUnwrap(
            rawResult,
            "Classification failed for mode=\(runningMode.rawValue) max=\(maxResults) threshold=\(threshold): \(viewModel.errorMessage ?? "")"
          )
          XCTAssertNil(viewModel.errorMessage)
          XCTAssertEqual(result.runningMode, runningMode)
          XCTAssertEqual(result.windows.count, 5)

          for window in result.windows {
            XCTAssertLessThanOrEqual(window.categories.count, maxResults)
            for category in window.categories {
              XCTAssertTrue(category.score.isFinite)
              XCTAssertGreaterThanOrEqual(
                category.score + 1e-5,
                threshold,
                "Category \(category.label) scored \(category.score) below threshold \(threshold)"
              )
            }
          }
        }
      }
    }
  }

  @MainActor
  func testGpuDelegateBehaviorDoesNotSilentlyFallBack() async {
    let viewModel = AudioClassifierViewModel(
      model: .yamnet,
      delegate: .gpu,
      clipRunningMode: .audioClips,
      maxResults: 4,
      scoreThreshold: 0.0,
      autoLoad: false
    )
    let loaded = await viewModel.loadClassifier()
    if !loaded {
      print("YAMNet GPU load rejected explicitly: \(viewModel.errorMessage ?? "unknown")")
      XCTAssertTrue(viewModel.isGpuError)
      XCTAssertNotNil(viewModel.errorMessage)
      XCTAssertFalse(viewModel.isReady)
      return
    }

    let result = await viewModel.classifyCurrentClip()
    if result == nil {
      print("YAMNet GPU inference rejected explicitly: \(viewModel.errorMessage ?? "unknown")")
      XCTAssertTrue(viewModel.isGpuError)
      XCTAssertNotNil(viewModel.errorMessage)
    } else {
      print(
        "YAMNet GPU succeeded on simulator with top label: \(result?.windows.first?.topCategory?.label ?? "none") (\(result?.windows.first?.topCategory?.score ?? 0))"
      )
    }
  }
}
