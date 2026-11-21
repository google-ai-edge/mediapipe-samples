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

/// Supported MediaPipe text summarization models bundled with this sample.
public enum TextSummarizerModelOption: String, CaseIterable, Identifiable, Sendable {
  case gemma200M = "gemma_200m"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .gemma200M: return "Gemma 200M"
    }
  }

  public var fullName: String {
    switch self {
    case .gemma200M: return "Gemma 200M (2-Mode)"
    }
  }

  public var summary: String {
    switch self {
    case .gemma200M:
      return "Quantized 200M LiteRT-LM model supporting Key Points and TL;DR summaries on-device."
    }
  }

  public var resourceName: String {
    switch self {
    case .gemma200M: return "summarization_quant_200m_2modes"
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

/// Output style for on-device text summarization.
public enum TextSummarizerModeOption: String, CaseIterable, Identifiable, Sendable {
  case keyPoints = "keypoints"
  case tldr = "tldr"

  public var id: String { rawValue }

  public var label: String {
    switch self {
    case .keyPoints: return "Key Points"
    case .tldr: return "TL;DR"
    }
  }

  public var specValue: String {
    switch self {
    case .keyPoints: return "KEYPOINTS"
    case .tldr: return "TLDR"
    }
  }

  public var summary: String {
    switch self {
    case .keyPoints:
      return "Extracts concise bulleted takeaways from the source passage."
    case .tldr:
      return "Distills the source passage into a brief, readable paragraph."
    }
  }

  var mediaPipeMode: TextSummarizerMode {
    switch self {
    case .keyPoints: return .keyPoints
    case .tldr: return .tldr
    }
  }
}

/// Maximum combined input/output token budget for the LiteRT-LM engine.
public enum TextSummarizerMaxTokensOption: Int, CaseIterable, Identifiable, Sendable {
  case compact = 512
  case balanced = 1024
  case extended = 2048

  public var id: Int { rawValue }

  public var label: String {
    "\(rawValue)"
  }

  public var displayLabel: String {
    "\(rawValue) tokens"
  }
}

/// Hardware execution delegate exposed programmatically for validation harnesses.
///
/// The Gemma 200M summarization LiteRT-LM model only supports CPU execution, so the interactive
/// UI keeps accelerator selection read-only (`CPU`).
public enum TextSummarizerDelegateOption: String, CaseIterable, Identifiable, Sendable {
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

/// Structured result of an on-device summarization call.
public struct TextSummarizerOutput: Equatable, Sendable {
  public let summary: String
  public let mode: TextSummarizerModeOption
  public let sourceCharacterCount: Int
  public let sourceWordCount: Int
  public let summaryCharacterCount: Int
  public let summaryWordCount: Int
  public let inferenceTimeMs: Double
  public let streamed: Bool
  public let chunkCount: Int

  public init(
    summary: String,
    mode: TextSummarizerModeOption,
    sourceCharacterCount: Int,
    sourceWordCount: Int,
    summaryCharacterCount: Int,
    summaryWordCount: Int,
    inferenceTimeMs: Double,
    streamed: Bool,
    chunkCount: Int
  ) {
    self.summary = summary
    self.mode = mode
    self.sourceCharacterCount = sourceCharacterCount
    self.sourceWordCount = sourceWordCount
    self.summaryCharacterCount = summaryCharacterCount
    self.summaryWordCount = summaryWordCount
    self.inferenceTimeMs = inferenceTimeMs
    self.streamed = streamed
    self.chunkCount = chunkCount
  }

  /// Parsed bullet items when the summary uses markdown or bullet list syntax.
  public var bulletPoints: [String] {
    Self.parseBulletPoints(from: summary)
  }

  /// Percentage reduction in word count relative to the source text (clamped to 0...99).
  public var wordReductionPercent: Int {
    guard sourceWordCount > 0, summaryWordCount < sourceWordCount else { return 0 }
    let reduction = (1.0 - Double(summaryWordCount) / Double(sourceWordCount)) * 100.0
    return min(max(Int(reduction.rounded()), 1), 99)
  }

  /// Ratio of summary character length to source character length.
  public var characterLengthRatio: Double {
    guard sourceCharacterCount > 0 else { return 1.0 }
    return Double(summaryCharacterCount) / Double(sourceCharacterCount)
  }

  public static func parseBulletPoints(from text: String) -> [String] {
    var items: [String] = []
    for rawLine in text.components(separatedBy: .newlines) {
      let trimmed = rawLine.trimmingCharacters(in: .whitespaces)
      guard !trimmed.isEmpty else { continue }
      if trimmed.hasPrefix("* ") || trimmed.hasPrefix("- ") || trimmed.hasPrefix("• ") {
        let content = trimmed.dropFirst(2).trimmingCharacters(in: .whitespaces)
        if !content.isEmpty {
          items.append(content)
        }
      } else if trimmed.hasPrefix("*") || trimmed.hasPrefix("-") || trimmed.hasPrefix("•") {
        let content = trimmed.dropFirst().trimmingCharacters(in: .whitespaces)
        if !content.isEmpty {
          items.append(content)
        }
      }
    }
    return items
  }

  public static func wordCount(of text: String) -> Int {
    text.split { $0.isWhitespace || $0.isNewline }
      .filter { token in token.contains { $0.isLetter || $0.isNumber } }
      .count
  }
}

/// Errors surfaced by ``TextSummarizerHelper``.
public enum TextSummarizerError: LocalizedError, Equatable, Sendable {
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
      return "Text Summarizer is not initialized."
    }
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksText.TextSummarizer`.
///
/// Runs model loading and summarization off the main actor, supports both unary and streaming
/// generation, and surfaces explicit delegate errors without silently falling back from GPU to CPU.
public actor TextSummarizerHelper {
  private let locator: SampleModelLocator
  private let customCacheDirectory: String?
  private var summarizer: TextSummarizer?
  private var activeMode: TextSummarizerModeOption = .keyPoints

  public init(
    locator: SampleModelLocator = SampleModelLocator(),
    cacheDirectory: String? = nil
  ) {
    self.locator = locator
    self.customCacheDirectory = cacheDirectory
  }

  /// Loads or reloads `TextSummarizer` with the requested model, mode, token budget, and delegate.
  ///
  /// Returns the elapsed model load duration in milliseconds.
  public func load(
    model: TextSummarizerModelOption = .gemma200M,
    mode: TextSummarizerModeOption = .keyPoints,
    maxTokens: TextSummarizerMaxTokensOption = .extended,
    delegate: TextSummarizerDelegateOption = .cpu
  ) throws -> Double {
    close()

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: model.resourceName,
        fileExtension: model.fileExtension
      )
    } catch {
      throw TextSummarizerError.initializationFailed(error.localizedDescription)
    }

    let options = TextSummarizerOptions()
    options.baseOptions.modelAssetPath = modelURL.path
    options.baseOptions.delegate = delegate.mediaPipeDelegate
    options.mode = mode.mediaPipeMode
    options.maxNumTokens = maxTokens.rawValue
    if let cacheDir = resolvedCacheDirectory() {
      options.cacheDirectory = cacheDir
    }

    let start = ContinuousClock.now
    do {
      summarizer = try TextSummarizer(options: options)
      activeMode = mode
    } catch {
      if delegate == .gpu {
        throw TextSummarizerError.gpuUnsupported(error.localizedDescription)
      }
      throw TextSummarizerError.initializationFailed(error.localizedDescription)
    }
    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases the underlying MediaPipe task instance.
  public func close() {
    if let summarizer {
      try? summarizer.close()
    }
    summarizer = nil
  }

  /// Performs synchronous on-device summarization on the actor executor.
  public func summarize(text: String) throws -> TextSummarizerOutput {
    guard let summarizer else { throw TextSummarizerError.notInitialized }
    let trimmedInput = text.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmedInput.isEmpty else {
      throw TextSummarizerError.inferenceFailed("Input text must not be empty.")
    }

    let start = ContinuousClock.now
    let summaryText: String
    do {
      summaryText = try summarizer.summarize(text: trimmedInput)
        .summary
        .trimmingCharacters(in: .whitespacesAndNewlines)
    } catch {
      throw TextSummarizerError.inferenceFailed(error.localizedDescription)
    }

    let elapsedMs = Self.elapsedMilliseconds(since: start)
    return Self.makeOutput(
      summary: summaryText,
      sourceText: trimmedInput,
      mode: activeMode,
      inferenceTimeMs: elapsedMs,
      streamed: false,
      chunkCount: summaryText.isEmpty ? 0 : 1
    )
  }

  /// Performs streaming on-device summarization, delivering incremental chunks via `onChunk`.
  public func summarizeStreaming(
    text: String,
    onChunk: @Sendable @escaping (String) -> Void
  ) async throws -> TextSummarizerOutput {
    guard let activeSummarizer = summarizer else {
      throw TextSummarizerError.notInitialized
    }
    let trimmedInput = text.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !trimmedInput.isEmpty else {
      throw TextSummarizerError.inferenceFailed("Input text must not be empty.")
    }

    let mode = activeMode
    let start = ContinuousClock.now

    let (rawSummary, chunkCount) = try await withCheckedThrowingContinuation {
      (continuation: CheckedContinuation<(String, Int), any Error>) in
      let state = OSAllocatedUnfairLock(
        initialState: StreamState(continuation: continuation)
      )
      do {
        try activeSummarizer.summarizeStreaming(text: trimmedInput) { streamResult, error in
          if let error {
            let pending = state.withLock { s -> CheckedContinuation<(String, Int), any Error>? in
              let c = s.continuation
              s.continuation = nil
              return c
            }
            pending?.resume(
              throwing: TextSummarizerError.inferenceFailed(error.localizedDescription)
            )
            return
          }

          let chunk = streamResult?.chunk ?? ""
          let isDone = streamResult?.done ?? false

          let outcome: (emitChunk: Bool, completion: (CheckedContinuation<(String, Int), any Error>, String, Int)?) =
            state.withLock { s in
              var shouldEmit = false
              if !chunk.isEmpty {
                s.accumulated += chunk
                s.chunkCount += 1
                shouldEmit = true
              }
              var finish: (CheckedContinuation<(String, Int), any Error>, String, Int)?
              if isDone, let cont = s.continuation {
                s.continuation = nil
                finish = (cont, s.accumulated, s.chunkCount)
              }
              return (shouldEmit, finish)
            }

          if outcome.emitChunk {
            onChunk(chunk)
          }
          if let (cont, fullText, count) = outcome.completion {
            cont.resume(returning: (fullText, count))
          }
        }
      } catch {
        let pending = state.withLock { s -> CheckedContinuation<(String, Int), any Error>? in
          let c = s.continuation
          s.continuation = nil
          return c
        }
        pending?.resume(
          throwing: TextSummarizerError.inferenceFailed(error.localizedDescription)
        )
      }
    }

    let elapsedMs = Self.elapsedMilliseconds(since: start)
    let trimmedSummary = rawSummary.trimmingCharacters(in: .whitespacesAndNewlines)
    return Self.makeOutput(
      summary: trimmedSummary,
      sourceText: trimmedInput,
      mode: mode,
      inferenceTimeMs: elapsedMs,
      streamed: true,
      chunkCount: chunkCount
    )
  }

  private struct StreamState {
    var accumulated: String = ""
    var chunkCount: Int = 0
    var continuation: CheckedContinuation<(String, Int), any Error>?
  }

  private func resolvedCacheDirectory() -> String? {
    if let customCacheDirectory {
      return customCacheDirectory
    }
    guard
      let cachesRoot = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
    else {
      return nil
    }
    let dir = cachesRoot.appendingPathComponent("MediaPipeTextSummarizerCache", isDirectory: true)
    try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    return dir.path
  }

  private static func makeOutput(
    summary: String,
    sourceText: String,
    mode: TextSummarizerModeOption,
    inferenceTimeMs: Double,
    streamed: Bool,
    chunkCount: Int
  ) -> TextSummarizerOutput {
    TextSummarizerOutput(
      summary: summary,
      mode: mode,
      sourceCharacterCount: sourceText.count,
      sourceWordCount: TextSummarizerOutput.wordCount(of: sourceText),
      summaryCharacterCount: summary.count,
      summaryWordCount: TextSummarizerOutput.wordCount(of: summary),
      inferenceTimeMs: inferenceTimeMs,
      streamed: streamed,
      chunkCount: chunkCount
    )
  }

  private static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}
