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

@preconcurrency import AVFoundation
import Foundation
import MediaPipeSampleUI
@preconcurrency private import MediaPipeTasksAudio

/// Supported MediaPipe automatic speech recognition models bundled with this sample.
public enum AudioTranscriberModelOption: String, CaseIterable, Identifiable, Sendable {
  case parakeetTdtV3 = "parakeet_tdt_v3"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .parakeetTdtV3: return "Parakeet TDT"
    }
  }

  public var fullName: String {
    switch self {
    case .parakeetTdtV3: return "Parakeet TDT 0.6B v3"
    }
  }

  public var summary: String {
    switch self {
    case .parakeetTdtV3:
      return "Stateful int8 LiteRT-LM automatic speech recognition model (16 kHz mono, 5s window)."
    }
  }

  public var resourceName: String {
    switch self {
    case .parakeetTdtV3: return "parakeet_tdt_0.6b_v3_5s_i8_stateful"
    }
  }

  public var fileExtension: String {
    switch self {
    case .parakeetTdtV3: return "litertlm"
    }
  }

  public var assetFilename: String {
    "\(resourceName).\(fileExtension)"
  }
}

/// Hardware execution delegate supported by `MediaPipeTasksAudio.AudioTranscriber`.
public enum AudioTranscriberDelegateOption: String, CaseIterable, Identifiable, Sendable {
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
}

private extension AudioTranscriberDelegateOption {
  var mediaPipeDelegate: Delegate {
    switch self {
    case .cpu: return .CPU
    case .gpu: return .GPU
    }
  }
}

/// Execution mode for transcribing an audio clip.
public enum AudioClipTranscriptionMode: String, CaseIterable, Identifiable, Sendable {
  case fullClip = "file"
  case streaming = "stream"

  public var id: String { rawValue }

  public var label: String {
    switch self {
    case .fullClip: return "Full Clip"
    case .streaming: return "Streaming"
    }
  }

  public var isStreaming: Bool {
    self == .streaming
  }

  public var summary: String {
    switch self {
    case .fullClip:
      return "Transcribes the entire audio clip in a single inference call."
    case .streaming:
      return "Streams 500 ms chunks sequentially with live interim updates."
    }
  }
}

/// Value-type representation of a single segment emitted by `AudioTranscriber`.
public struct TranscribedSegment: Identifiable, Equatable, Sendable {
  public let id: String
  public let transcript: String
  public let isFinal: Bool
  public let fromTimestampMs: Int64
  public let toTimestampMs: Int64

  public init(
    index: Int,
    transcript: String,
    isFinal: Bool,
    fromTimestampMs: Int64,
    toTimestampMs: Int64
  ) {
    self.id = "\(index)_\(fromTimestampMs)_\(toTimestampMs)_\(isFinal)_\(transcript)"
    self.transcript = transcript
    self.isFinal = isFinal
    self.fromTimestampMs = fromTimestampMs
    self.toTimestampMs = toTimestampMs
  }

  public var durationSeconds: Double {
    Double(max(0, toTimestampMs - fromTimestampMs)) / 1_000.0
  }

  public var timeRangeLabel: String {
    let startSec = Double(max(0, fromTimestampMs)) / 1_000.0
    let endSec = Double(max(fromTimestampMs, toTimestampMs)) / 1_000.0
    if endSec > startSec {
      return String(format: "%.1fs – %.1fs", startSec, endSec)
    }
    return String(format: "%.1fs", durationSeconds)
  }
}

/// Decoded mono 32-bit float PCM audio buffer ready for `AudioTranscriber`.
public struct DecodedAudioClip: Equatable, Sendable {
  public let samples: [Float]
  public let sampleRateHz: Double
  public let sourceSampleRateHz: Double
  public let label: String

  public init(
    samples: [Float],
    sampleRateHz: Double,
    sourceSampleRateHz: Double,
    label: String
  ) {
    self.samples = samples
    self.sampleRateHz = sampleRateHz
    self.sourceSampleRateHz = sourceSampleRateHz
    self.label = label
  }

  public var durationSeconds: Double {
    guard sampleRateHz > 0 else { return 0.0 }
    return Double(samples.count) / sampleRateHz
  }
}

/// Incremental streaming progress update emitted while transcribing audio chunk-by-chunk.
public struct AudioTranscriberStreamUpdate: Equatable, Sendable {
  public let finalizedSegments: [TranscribedSegment]
  public let interimTranscript: String
  public let chunkRmsLevel: Float
  public let processedSeconds: Double
  public let totalSeconds: Double
}

/// Complete output of transcribing an audio clip or recording session.
public struct AudioTranscriptionResult: Equatable, Sendable {
  public let transcript: String
  public let segments: [TranscribedSegment]
  public let audioDurationSeconds: Double
  public let inferenceTimeMs: Double
  public let realTimeFactor: Double
  public let mode: AudioClipTranscriptionMode
  public let chunkCount: Int
}

/// Errors surfaced by ``AudioTranscriberHelper`` and ``AudioClipDecoder``.
public enum AudioTranscriberError: LocalizedError, Equatable, Sendable {
  case gpuUnsupported(String)
  case initializationFailed(String)
  case audioLoadFailed(String)
  case transcriptionFailed(String)
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
    case .audioLoadFailed(let message):
      return message
    case .transcriptionFailed(let message):
      return message
    case .notInitialized:
      return "Audio Transcriber is not initialized."
    }
  }
}

/// Decodes audio files (WAV, M4A, MP3, AIFF, CAF) into 16 kHz mono float32 PCM samples.
public enum AudioClipDecoder {
  public static let targetSampleRateHz: Double = 16_000.0
  private static let rmsGainMultiplier: Float = 5.0
  private static let readChunkFrames: AVAudioFrameCount = 32_768

  /// Decodes any CoreAudio-readable audio file URL to mono float32 PCM at `targetSampleRateHz`.
  public static func decode(
    url: URL,
    targetSampleRateHz: Double = AudioClipDecoder.targetSampleRateHz
  ) throws -> DecodedAudioClip {
    let audioFile: AVAudioFile
    do {
      audioFile = try AVAudioFile(forReading: url)
    } catch {
      throw AudioTranscriberError.audioLoadFailed(
        "Unable to open audio file '\(url.lastPathComponent)': \(error.localizedDescription)"
      )
    }

    let processingFormat = audioFile.processingFormat
    let sourceSampleRate = processingFormat.sampleRate
    let channelCount = Int(processingFormat.channelCount)
    guard sourceSampleRate > 0, channelCount > 0 else {
      throw AudioTranscriberError.audioLoadFailed(
        "Audio file '\(url.lastPathComponent)' has an invalid format."
      )
    }

    let totalFrames = audioFile.length
    guard totalFrames > 0 else {
      throw AudioTranscriberError.audioLoadFailed(
        "Audio file '\(url.lastPathComponent)' contains no audio frames."
      )
    }

    var monoSamples: [Float] = []
    monoSamples.reserveCapacity(Int(totalFrames))

    guard
      let readBuffer = AVAudioPCMBuffer(
        pcmFormat: processingFormat,
        frameCapacity: readChunkFrames
      )
    else {
      throw AudioTranscriberError.audioLoadFailed("Failed to allocate PCM read buffer.")
    }

    while audioFile.framePosition < totalFrames {
      let remaining = AVAudioFrameCount(
        min(Int64(readChunkFrames), totalFrames - audioFile.framePosition)
      )
      guard remaining > 0 else { break }
      readBuffer.frameLength = 0
      do {
        try audioFile.read(into: readBuffer, frameCount: remaining)
      } catch {
        throw AudioTranscriberError.audioLoadFailed(
          "Failed reading PCM frames from '\(url.lastPathComponent)': \(error.localizedDescription)"
        )
      }

      let framesRead = Int(readBuffer.frameLength)
      guard framesRead > 0 else { break }
      guard let floatChannels = readBuffer.floatChannelData else {
        throw AudioTranscriberError.audioLoadFailed("Audio buffer did not expose float PCM data.")
      }

      if channelCount == 1 {
        let ptr = floatChannels[0]
        monoSamples.append(contentsOf: UnsafeBufferPointer(start: ptr, count: framesRead))
      } else {
        let invChannels = 1.0 / Float(channelCount)
        for frame in 0..<framesRead {
          var sum: Float = 0.0
          for ch in 0..<channelCount {
            sum += floatChannels[ch][frame]
          }
          monoSamples.append(min(max(sum * invChannels, -1.0), 1.0))
        }
      }
    }

    let resampled: [Float]
    if abs(sourceSampleRate - targetSampleRateHz) < 0.5 {
      resampled = monoSamples
    } else {
      resampled = try resampleMono(
        monoSamples,
        from: sourceSampleRate,
        to: targetSampleRateHz
      )
    }

    guard !resampled.isEmpty else {
      throw AudioTranscriberError.audioLoadFailed(
        "Audio file '\(url.lastPathComponent)' produced zero PCM samples."
      )
    }

    return DecodedAudioClip(
      samples: resampled,
      sampleRateHz: targetSampleRateHz,
      sourceSampleRateHz: sourceSampleRate,
      label: url.lastPathComponent
    )
  }

  /// Resamples mono float32 PCM samples from `sourceSampleRateHz` to `targetSampleRateHz`.
  public static func resampleMono(
    _ samples: [Float],
    from sourceSampleRateHz: Double,
    to targetSampleRateHz: Double
  ) throws -> [Float] {
    guard !samples.isEmpty else { return [] }
    if abs(sourceSampleRateHz - targetSampleRateHz) < 0.5 {
      return samples
    }
    guard
      let sourceFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32,
        sampleRate: sourceSampleRateHz,
        channels: 1,
        interleaved: false
      ),
      let targetFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32,
        sampleRate: targetSampleRateHz,
        channels: 1,
        interleaved: false
      ),
      let converter = AVAudioConverter(from: sourceFormat, to: targetFormat)
    else {
      throw AudioTranscriberError.audioLoadFailed(
        "Unable to create resampler from \(Int(sourceSampleRateHz)) Hz to \(Int(targetSampleRateHz)) Hz."
      )
    }

    let inputFrames = AVAudioFrameCount(samples.count)
    guard
      let inputBuffer = AVAudioPCMBuffer(pcmFormat: sourceFormat, frameCapacity: inputFrames),
      let inputChannel = inputBuffer.floatChannelData?[0]
    else {
      throw AudioTranscriberError.audioLoadFailed("Unable to allocate source PCM buffer.")
    }
    inputBuffer.frameLength = inputFrames
    samples.withUnsafeBufferPointer { src in
      if let base = src.baseAddress {
        inputChannel.update(from: base, count: samples.count)
      }
    }

    let ratio = targetSampleRateHz / sourceSampleRateHz
    let estimatedOutFrames = max(1, Int(ceil(Double(samples.count) * ratio)) + 512)
    let outputChunkCapacity: AVAudioFrameCount = 32_768
    guard
      let outputBuffer = AVAudioPCMBuffer(
        pcmFormat: targetFormat,
        frameCapacity: outputChunkCapacity
      )
    else {
      throw AudioTranscriberError.audioLoadFailed("Unable to allocate resampled PCM buffer.")
    }

    final class OneShotProvider: @unchecked Sendable {
      let buffer: AVAudioPCMBuffer
      var consumed = false
      init(buffer: AVAudioPCMBuffer) { self.buffer = buffer }
    }

    let provider = OneShotProvider(buffer: inputBuffer)
    var output: [Float] = []
    output.reserveCapacity(estimatedOutFrames)

    while true {
      outputBuffer.frameLength = 0
      var conversionError: NSError?
      let status = converter.convert(to: outputBuffer, error: &conversionError) { _, outStatus in
        if provider.consumed {
          outStatus.pointee = .endOfStream
          return nil
        }
        provider.consumed = true
        outStatus.pointee = .haveData
        return provider.buffer
      }

      if status == .error, let conversionError {
        throw AudioTranscriberError.audioLoadFailed(
          "Audio resampling failed: \(conversionError.localizedDescription)"
        )
      }

      let produced = Int(outputBuffer.frameLength)
      if produced > 0, let outPtr = outputBuffer.floatChannelData?[0] {
        output.append(contentsOf: UnsafeBufferPointer(start: outPtr, count: produced))
      }

      if status == .endOfStream || (status == .inputRanDry && produced == 0) {
        break
      }
    }

    return output
  }

  /// Computes a normalized RMS audio level in `[0.0, 1.0]` for live level metering.
  public static func normalizedRmsLevel(for samples: [Float]) -> Float {
    guard !samples.isEmpty else { return 0.0 }
    var sumOfSquares: Float = 0.0
    for sample in samples {
      sumOfSquares += sample * sample
    }
    let rawRms = sqrt(sumOfSquares / Float(samples.count))
    return min(1.0, rawRms * rmsGainMultiplier)
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksAudio.AudioTranscriber`.
///
/// Runs model loading, prewarming, file decoding, and full-clip / streaming inference off the
/// main actor, surfacing explicit delegate errors without silently falling back from GPU to CPU.
public actor AudioTranscriberHelper {
  public static let targetSampleRateHz: Double = AudioClipDecoder.targetSampleRateHz
  public static let sampleClipResourceName = "speech_16000_hz_mono"
  public static let sampleClipFileExtension = "wav"
  public static let defaultStreamChunkDurationSeconds: Double = 0.5

  private let locator: SampleModelLocator
  private var transcriber: AudioTranscriber?
  private var nextSegmentOrdinal: Int = 0

  public init(locator: SampleModelLocator = SampleModelLocator()) {
    self.locator = locator
  }

  /// Loads and prewarms `AudioTranscriber` with the requested model and delegate.
  ///
  /// Returns the elapsed load + prewarm duration in milliseconds.
  public func load(
    model: AudioTranscriberModelOption = .parakeetTdtV3,
    delegate: AudioTranscriberDelegateOption = .cpu,
    enableInterimResults: Bool = true,
    customModelURL: URL? = nil
  ) throws -> Double {
    closeExistingTranscriber()

    let modelURL: URL
    if let customModelURL {
      modelURL = customModelURL
    } else {
      do {
        modelURL = try locator.locate(
          resourceName: model.resourceName,
          fileExtension: model.fileExtension
        )
      } catch {
        throw AudioTranscriberError.initializationFailed(error.localizedDescription)
      }
    }

    let options = AudioTranscriberOptions()
    options.baseOptions.modelAssetPath = modelURL.path
    options.baseOptions.delegate = delegate.mediaPipeDelegate
    options.sampleRateHz = Self.targetSampleRateHz
    options.enableInterimResults = enableInterimResults
    options.modelArchitecture = .auto
    if let cacheDirectory = FileManager.default.urls(
      for: .cachesDirectory,
      in: .userDomainMask
    ).first {
      options.cacheDirectory = cacheDirectory.path
    }

    let start = ContinuousClock.now
    do {
      let instance = try AudioTranscriber(options: options)
      try instance.prewarm()
      transcriber = instance
      nextSegmentOrdinal = 0
    } catch {
      if delegate == .gpu {
        throw AudioTranscriberError.gpuUnsupported(error.localizedDescription)
      }
      throw AudioTranscriberError.initializationFailed(error.localizedDescription)
    }

    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases native MediaPipe resources held by the active `AudioTranscriber`.
  public func close() {
    closeExistingTranscriber()
  }

  /// Resolves the bundled sample WAV speech clip (`speech_16000_hz_mono.wav`).
  public func locateBundledSampleURL() throws -> URL {
    do {
      return try locator.locate(
        resourceName: Self.sampleClipResourceName,
        fileExtension: Self.sampleClipFileExtension
      )
    } catch {
      throw AudioTranscriberError.audioLoadFailed(
        "Sample audio clip '\(Self.sampleClipResourceName).\(Self.sampleClipFileExtension)' not found. Run RunScripts/download_models.sh and rebuild."
      )
    }
  }

  /// Decodes an audio file at `url` and transcribes it in either full-clip or streaming mode.
  public func transcribe(
    url: URL,
    stream: Bool,
    chunkDurationSeconds: Double = AudioTranscriberHelper.defaultStreamChunkDurationSeconds,
    onProgress: (@Sendable (AudioTranscriberStreamUpdate) async -> Void)? = nil
  ) async throws -> AudioTranscriptionResult {
    let decoded = try AudioClipDecoder.decode(
      url: url,
      targetSampleRateHz: Self.targetSampleRateHz
    )
    return try await transcribe(
      samples: decoded.samples,
      sampleRateHz: decoded.sampleRateHz,
      stream: stream,
      chunkDurationSeconds: chunkDurationSeconds,
      onProgress: onProgress
    )
  }

  /// Transcribes mono float32 PCM samples in either single-call (`stream == false`) or chunked
  /// streaming (`stream == true`) mode.
  public func transcribe(
    samples: [Float],
    sampleRateHz: Double = AudioTranscriberHelper.targetSampleRateHz,
    stream: Bool,
    chunkDurationSeconds: Double = AudioTranscriberHelper.defaultStreamChunkDurationSeconds,
    onProgress: (@Sendable (AudioTranscriberStreamUpdate) async -> Void)? = nil
  ) async throws -> AudioTranscriptionResult {
    guard let transcriber else {
      throw AudioTranscriberError.notInitialized
    }
    guard !samples.isEmpty else {
      throw AudioTranscriberError.audioLoadFailed("Audio buffer is empty.")
    }

    let preparedSamples: [Float]
    if abs(sampleRateHz - Self.targetSampleRateHz) < 0.5 {
      preparedSamples = samples
    } else {
      preparedSamples = try AudioClipDecoder.resampleMono(
        samples,
        from: sampleRateHz,
        to: Self.targetSampleRateHz
      )
    }

    let audioDurationSeconds = Double(preparedSamples.count) / Self.targetSampleRateHz
    nextSegmentOrdinal = 0

    let start = ContinuousClock.now
    var finalizedSegments: [TranscribedSegment] = []
    let chunkCount: Int

    if !stream {
      chunkCount = 1
      let rawResult: AudioTranscriberResult
      do {
        rawResult = try Self.invokeTranscribe(
          on: transcriber,
          samples: preparedSamples,
          endOfStream: true
        )
      } catch {
        throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
      }

      let mapped = mapSegments(from: rawResult, finalOnly: false)
      finalizedSegments.append(contentsOf: mapped)

      do {
        try transcriber.prewarm()
      } catch {
        throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
      }
    } else {
      let clampedChunkDuration = max(0.1, chunkDurationSeconds)
      let chunkSize = max(1, Int((Self.targetSampleRateHz * clampedChunkDuration).rounded()))
      var offset = 0
      var chunksSent = 0
      var latestInterim = ""

      while offset < preparedSamples.count {
        try Task.checkCancellation()
        let end = min(offset + chunkSize, preparedSamples.count)
        let chunk = Array(preparedSamples[offset..<end])
        chunksSent += 1

        let partialResult: AudioTranscriberResult
        do {
          partialResult = try Self.invokeTranscribe(
            on: transcriber,
            samples: chunk,
            endOfStream: false
          )
        } catch {
          throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
        }

        for rawSegment in partialResult.segments {
          let trimmed = rawSegment.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
          guard !trimmed.isEmpty else { continue }
          if rawSegment.isFinal {
            finalizedSegments.append(makeSegment(from: rawSegment, transcript: trimmed))
            latestInterim = ""
          } else {
            latestInterim = trimmed
          }
        }

        if let onProgress {
          let update = AudioTranscriberStreamUpdate(
            finalizedSegments: finalizedSegments,
            interimTranscript: latestInterim,
            chunkRmsLevel: AudioClipDecoder.normalizedRmsLevel(for: chunk),
            processedSeconds: Double(end) / Self.targetSampleRateHz,
            totalSeconds: audioDurationSeconds
          )
          await onProgress(update)
        }

        offset = end
      }

      chunkCount = chunksSent

      let finalResult: AudioTranscriberResult
      do {
        finalResult = try transcriber.finishStream()
      } catch {
        throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
      }

      let flushedSegments = mapSegments(from: finalResult, finalOnly: false)
      finalizedSegments.append(contentsOf: flushedSegments)

      do {
        try transcriber.prewarm()
      } catch {
        throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
      }
    }

    let elapsedMs = Self.elapsedMilliseconds(since: start)
    let rtf = audioDurationSeconds > 0 ? (elapsedMs / 1_000.0) / audioDurationSeconds : 0.0
    let transcript = Self.joinTranscript(from: finalizedSegments)

    return AudioTranscriptionResult(
      transcript: transcript,
      segments: finalizedSegments,
      audioDurationSeconds: audioDurationSeconds,
      inferenceTimeMs: elapsedMs,
      realTimeFactor: rtf,
      mode: stream ? .streaming : .fullClip,
      chunkCount: chunkCount
    )
  }

  /// Processes a single live microphone PCM chunk (16 kHz mono float32) during streaming capture.
  public func processLiveChunk(
    _ samples: [Float]
  ) throws -> (finalized: [TranscribedSegment], interim: String?) {
    guard let transcriber else {
      throw AudioTranscriberError.notInitialized
    }
    guard !samples.isEmpty else {
      return ([], nil)
    }

    let result: AudioTranscriberResult
    do {
      result = try Self.invokeTranscribe(
        on: transcriber,
        samples: samples,
        endOfStream: false
      )
    } catch {
      throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
    }

    var finalized: [TranscribedSegment] = []
    var interim: String?
    for rawSegment in result.segments {
      let trimmed = rawSegment.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
      guard !trimmed.isEmpty else { continue }
      if rawSegment.isFinal {
        finalized.append(makeSegment(from: rawSegment, transcript: trimmed))
        interim = ""
      } else {
        interim = trimmed
      }
    }
    return (finalized, interim)
  }

  /// Finalizes an active live microphone stream and prewarms for the next session.
  public func finishLiveStream() throws -> [TranscribedSegment] {
    guard let transcriber else {
      throw AudioTranscriberError.notInitialized
    }
    do {
      let finalResult = try transcriber.finishStream()
      let segments = mapSegments(from: finalResult, finalOnly: false)
      try transcriber.prewarm()
      return segments
    } catch {
      throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
    }
  }

  /// Resets any pending streaming buffer and prewarms the engine.
  public func resetStream() throws {
    guard let transcriber else { return }
    do {
      _ = try transcriber.finishStream()
      try transcriber.prewarm()
      nextSegmentOrdinal = 0
    } catch {
      throw AudioTranscriberError.transcriptionFailed(error.localizedDescription)
    }
  }

  // MARK: - Private Helpers

  private func closeExistingTranscriber() {
    if let existing = transcriber {
      _ = try? existing.close()
    }
    transcriber = nil
  }

  private func mapSegments(
    from result: AudioTranscriberResult,
    finalOnly: Bool
  ) -> [TranscribedSegment] {
    var mapped: [TranscribedSegment] = []
    for rawSegment in result.segments {
      if finalOnly && !rawSegment.isFinal { continue }
      let trimmed = rawSegment.transcript.trimmingCharacters(in: .whitespacesAndNewlines)
      guard !trimmed.isEmpty else { continue }
      mapped.append(makeSegment(from: rawSegment, transcript: trimmed))
    }

    if mapped.isEmpty {
      let fallback = result.text.trimmingCharacters(in: .whitespacesAndNewlines)
      if !fallback.isEmpty {
        let ordinal = nextSegmentOrdinal
        nextSegmentOrdinal += 1
        mapped.append(
          TranscribedSegment(
            index: ordinal,
            transcript: fallback,
            isFinal: true,
            fromTimestampMs: 0,
            toTimestampMs: 0
          )
        )
      }
    }

    return mapped
  }

  private func makeSegment(
    from rawSegment: AudioTranscriberSegment,
    transcript: String
  ) -> TranscribedSegment {
    let ordinal = nextSegmentOrdinal
    nextSegmentOrdinal += 1
    return TranscribedSegment(
      index: ordinal,
      transcript: transcript,
      isFinal: rawSegment.isFinal,
      fromTimestampMs: rawSegment.fromTimestampInMilliseconds,
      toTimestampMs: rawSegment.toTimestampInMilliseconds
    )
  }

  private static func invokeTranscribe(
    on transcriber: AudioTranscriber,
    samples: [Float],
    endOfStream: Bool
  ) throws -> AudioTranscriberResult {
    try samples.withUnsafeBufferPointer { buffer in
      try transcriber.transcribe(
        samples: buffer.baseAddress,
        count: UInt(buffer.count),
        endOfStream: endOfStream
      )
    }
  }

  private static func joinTranscript(from segments: [TranscribedSegment]) -> String {
    segments
      .map { $0.transcript.trimmingCharacters(in: .whitespacesAndNewlines) }
      .filter { !$0.isEmpty }
      .joined(separator: " ")
  }

  private static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}
