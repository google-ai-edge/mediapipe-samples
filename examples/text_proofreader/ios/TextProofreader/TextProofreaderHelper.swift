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
import MediaPipeSampleUI
@preconcurrency import MediaPipeTasksText
import os

/// Supported MediaPipe text proofreader models bundled with this sample.
public enum TextProofreaderModelOption: String, CaseIterable, Identifiable, Sendable {
  case gemma200M = "gemma_200m"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .gemma200M: return "Gemma 200M"
    }
  }

  public var fullName: String {
    switch self {
    case .gemma200M: return "Gemma 200M (Int8)"
    }
  }

  public var summary: String {
    switch self {
    case .gemma200M:
      return "On-device 200M LiteRT-LM model trained for grammar, spelling, and punctuation correction."
    }
  }

  public var resourceName: String {
    switch self {
    case .gemma200M: return "proofread_quant_200m"
    }
  }

  public var fileExtension: String {
    switch self {
    case .gemma200M: return "litertlm"
    }
  }

  public var assetFilename: String {
    "\(resourceName).\(fileExtension)"
  }
}

/// Hardware execution delegate exposed programmatically for tests and validation harnesses.
///
/// Text Proofreader runs on CPU on iOS; the UI surfaces CPU as read-only diagnostics.
public enum TextProofreaderDelegateOption: String, CaseIterable, Identifiable, Sendable {
  case cpu = "CPU"
  case gpu = "GPU"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .cpu: return "CPU"
    case .gpu: return "GPU (Metal)"
    }
  }

  public var shortName: String { rawValue }

  var mediaPipeDelegate: Delegate {
    switch self {
    case .cpu: return .CPU
    case .gpu: return .GPU
    }
  }
}

/// Selectable token budget options for the proofreader session.
public enum TextProofreaderMaxTokensOption: Int, CaseIterable, Identifiable, Sendable {
  case compact = 256
  case balanced = 512
  case extended = 1024

  public var id: Int { rawValue }

  public var tokenCount: Int { rawValue }

  public var label: String { "\(rawValue)" }

  public var shortLabel: String { "\(rawValue) tok" }
}

/// Type of diff segment returned by MediaPipe Text Proofreader.
public enum TextProofreaderCorrectionType: String, Equatable, Sendable {
  case same
  case insertion
  case deletion

  init(mediaPipeType: CorrectionType) {
    switch mediaPipeType {
    case .same:
      self = .same
    case .insertion:
      self = .insertion
    case .deletion:
      self = .deletion
    @unknown default:
      self = .same
    }
  }
}

/// A single diff segment transforming the input text into the proofread text.
public struct TextProofreaderCorrection: Equatable, Sendable {
  public let type: TextProofreaderCorrectionType
  public let text: String

  public init(type: TextProofreaderCorrectionType, text: String) {
    self.type = type
    self.text = text
  }
}

/// Incremental update emitted during streaming proofreading.
public struct TextProofreaderStreamUpdate: Equatable, Sendable {
  public let chunk: String
  public let accumulatedText: String
  public let sequence: Int
}

/// Output of a completed text proofreading operation.
public struct TextProofreaderOutput: Equatable, Sendable {
  public let inputText: String
  public let proofreadText: String
  public let corrections: [TextProofreaderCorrection]
  public let inferenceTimeMs: Double
  public let wasStreamed: Bool

  public init(
    inputText: String,
    proofreadText: String,
    corrections: [TextProofreaderCorrection],
    inferenceTimeMs: Double,
    wasStreamed: Bool
  ) {
    self.inputText = inputText
    self.proofreadText = proofreadText
    self.corrections = corrections
    self.inferenceTimeMs = inferenceTimeMs
    self.wasStreamed = wasStreamed
  }

  /// Number of individual insertion and deletion operations in ``corrections``.
  public var editCount: Int {
    corrections.reduce(into: 0) { count, correction in
      if correction.type != .same {
        count += 1
      }
    }
  }

  /// Whether the proofreader made any change to the input text.
  public var hasEdits: Bool {
    editCount > 0 || proofreadText != inputText
  }

  /// Reconstructs the original text by concatenating `.same` and `.deletion` segments.
  public var reconstructedOriginalText: String {
    corrections
      .filter { $0.type == .same || $0.type == .deletion }
      .map(\.text)
      .joined()
  }

  /// Reconstructs the proofread text by concatenating `.same` and `.insertion` segments.
  public var reconstructedProofreadText: String {
    corrections
      .filter { $0.type == .same || $0.type == .insertion }
      .map(\.text)
      .joined()
  }
}

/// Errors surfaced by ``TextProofreaderHelper``.
public enum TextProofreaderError: LocalizedError, Equatable, Sendable {
  case gpuUnsupported(String)
  case initializationFailed(String)
  case inferenceFailed(String)
  case notInitialized

  public var isGpuError: Bool {
    if case .gpuUnsupported = self { return true }
    return false
  }

  public var errorDescription: String? {
    switch self {
    case .gpuUnsupported(let message):
      return message
    case .initializationFailed(let message):
      return message
    case .inferenceFailed(let message):
      return message
    case .notInitialized:
      return "Text Proofreader is not initialized."
    }
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksText.TextProofreader`.
///
/// Runs model loading and unary/streaming proofreading off the main actor and surfaces explicit
/// MediaPipe errors without silently falling back from GPU to CPU.
public actor TextProofreaderHelper {
  private let locator: SampleModelLocator
  private var proofreader: TextProofreader?

  public init(locator: SampleModelLocator = SampleModelLocator()) {
    self.locator = locator
  }

  /// Loads or reloads `TextProofreader` with the requested model, delegate, and token budget.
  ///
  /// Returns the elapsed model load duration in milliseconds.
  public func load(
    model: TextProofreaderModelOption = .gemma200M,
    delegate: TextProofreaderDelegateOption = .cpu,
    maxTokens: Int = TextProofreaderMaxTokensOption.balanced.tokenCount,
    cacheDirectory: String? = nil
  ) throws -> Double {
    if let existing = proofreader {
      try? existing.close()
      proofreader = nil
    }

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: model.resourceName,
        fileExtension: model.fileExtension
      )
    } catch {
      throw TextProofreaderError.initializationFailed(error.localizedDescription)
    }

    let options = TextProofreaderOptions()
    options.baseOptions.modelAssetPath = modelURL.path
    options.baseOptions.delegate = delegate.mediaPipeDelegate
    if maxTokens > 0 {
      options.maxNumTokens = maxTokens
    }
    if let cacheDirectory {
      options.cacheDirectory = cacheDirectory
    } else if let defaultCache = Self.defaultCacheDirectory() {
      options.cacheDirectory = defaultCache
    }

    let start = ContinuousClock.now
    do {
      proofreader = try TextProofreader(options: options)
    } catch {
      if delegate == .gpu {
        throw TextProofreaderError.gpuUnsupported(error.localizedDescription)
      }
      throw TextProofreaderError.initializationFailed(error.localizedDescription)
    }
    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases the underlying MediaPipe task instance.
  public func close() {
    if let existing = proofreader {
      try? existing.close()
      proofreader = nil
    }
  }

  /// Runs unary proofreading on `text` and returns the proofread text and diff corrections.
  public func proofread(text: String) throws -> TextProofreaderOutput {
    guard let proofreader else { throw TextProofreaderError.notInitialized }

    let start = ContinuousClock.now
    let result: TextProofreaderResult
    do {
      result = try proofreader.proofread(text: text)
    } catch {
      throw TextProofreaderError.inferenceFailed(error.localizedDescription)
    }
    let elapsedMs = Self.elapsedMilliseconds(since: start)

    let corrections = result.corrections.map {
      TextProofreaderCorrection(
        type: TextProofreaderCorrectionType(mediaPipeType: $0.type),
        text: $0.text
      )
    }

    return TextProofreaderOutput(
      inputText: text,
      proofreadText: result.proofreadText,
      corrections: corrections,
      inferenceTimeMs: elapsedMs,
      wasStreamed: false
    )
  }

  /// Runs streaming proofreading on `text`, invoking `onUpdate` for each non-empty chunk and
  /// returning the final proofread text and diff corrections once complete.
  public func proofreadStreaming(
    text: String,
    onUpdate: @escaping @Sendable (TextProofreaderStreamUpdate) -> Void
  ) async throws -> TextProofreaderOutput {
    guard let proofreader else { throw TextProofreaderError.notInitialized }

    let start = ContinuousClock.now

    struct StreamState: Sendable {
      var accumulatedText = ""
      var sequence = 0
      var isFinished = false
      var continuation: CheckedContinuation<TextProofreaderOutput, Error>?
    }

    let state = OSAllocatedUnfairLock(initialState: StreamState())

    return try await withCheckedThrowingContinuation { continuation in
      state.withLock { $0.continuation = continuation }

      do {
        try proofreader.proofreadStreaming(text: text) { streamResult, error in
          if let error {
            let pending = state.withLock { s -> CheckedContinuation<TextProofreaderOutput, Error>? in
              guard !s.isFinished else { return nil }
              s.isFinished = true
              let cont = s.continuation
              s.continuation = nil
              return cont
            }
            pending?.resume(
              throwing: TextProofreaderError.inferenceFailed(error.localizedDescription)
            )
            return
          }

          guard let streamResult else {
            let pending = state.withLock { s -> CheckedContinuation<TextProofreaderOutput, Error>? in
              guard !s.isFinished else { return nil }
              s.isFinished = true
              let cont = s.continuation
              s.continuation = nil
              return cont
            }
            pending?.resume(
              throwing: TextProofreaderError.inferenceFailed(
                "Proofreader stream returned an empty response."
              )
            )
            return
          }

          let chunk = streamResult.chunk
          let isDone = streamResult.done
          let rawCorrections = streamResult.corrections

          let transition = state.withLock {
            s -> (
              update: TextProofreaderStreamUpdate?,
              finalText: String?,
              continuation: CheckedContinuation<TextProofreaderOutput, Error>?
            ) in
            guard !s.isFinished else { return (nil, nil, nil) }

            var update: TextProofreaderStreamUpdate?
            if !chunk.isEmpty {
              s.accumulatedText.append(chunk)
              s.sequence += 1
              update = TextProofreaderStreamUpdate(
                chunk: chunk,
                accumulatedText: s.accumulatedText,
                sequence: s.sequence
              )
            }

            if isDone {
              s.isFinished = true
              let finalText = s.accumulatedText
              let cont = s.continuation
              s.continuation = nil
              return (update, finalText, cont)
            }

            return (update, nil, nil)
          }

          if let update = transition.update {
            onUpdate(update)
          }

          if let finalText = transition.finalText, let cont = transition.continuation {
            let elapsedMs = Self.elapsedMilliseconds(since: start)
            let corrections = (rawCorrections ?? []).map {
              TextProofreaderCorrection(
                type: TextProofreaderCorrectionType(mediaPipeType: $0.type),
                text: $0.text
              )
            }
            cont.resume(
              returning: TextProofreaderOutput(
                inputText: text,
                proofreadText: finalText,
                corrections: corrections,
                inferenceTimeMs: elapsedMs,
                wasStreamed: true
              )
            )
          }
        }
      } catch {
        let pending = state.withLock { s -> CheckedContinuation<TextProofreaderOutput, Error>? in
          guard !s.isFinished else { return nil }
          s.isFinished = true
          let cont = s.continuation
          s.continuation = nil
          return cont
        }
        pending?.resume(
          throwing: TextProofreaderError.inferenceFailed(error.localizedDescription)
        )
      }
    }
  }

  private static func defaultCacheDirectory() -> String? {
    let fm = FileManager.default
    guard let cachesURL = fm.urls(for: .cachesDirectory, in: .userDomainMask).first else {
      return nil
    }
    let dirURL = cachesURL.appendingPathComponent("MediaPipeTextProofreaderCache", isDirectory: true)
    try? fm.createDirectory(at: dirURL, withIntermediateDirectories: true)
    return dirURL.path
  }

  private static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}
