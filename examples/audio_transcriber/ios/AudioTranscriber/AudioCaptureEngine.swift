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
import OSLog

private enum AudioCaptureConfiguration {
  static let targetSampleRate: Double = 48_000.0
  static let monoChannelCount: AVAudioChannelCount = 1
  static let mainBus: AVAudioNodeBus = 0
  static let tapBufferSize: AVAudioFrameCount = 4096
  static let converterFrameHeadroom: Double = 512.0
  static let rmsGainMultiplier: Float = 5.0
  static let maxNormalizedRMSLevel: Float = 1.0
}

extension [Float] {
  /// Normalized root-mean-square (RMS) audio level clamped to `[0.0, 1.0]`.
  fileprivate var rmsLevel: Float {
    guard !isEmpty else { return 0.0 }
    let sumOfSquares = reduce(0.0) { $0 + ($1 * $1) }
    let rawRMS = sqrt(sumOfSquares / Float(count))
    return Swift.min(
      AudioCaptureConfiguration.maxNormalizedRMSLevel,
      rawRMS * AudioCaptureConfiguration.rmsGainMultiplier
    )
  }
}

/// Cohesive chunk of resampled mono PCM audio samples and its normalized RMS level.
package struct AudioChunk: Sendable, Equatable {
  /// Mono 48,000 Hz 32-bit floating-point PCM audio samples.
  package let samples: [Float]

  /// Normalized root-mean-square (RMS) audio level in `[0.0, 1.0]`.
  package let rmsLevel: Float

  /// Creates an audio chunk from PCM samples and an optional precomputed RMS level.
  ///
  /// - Parameters:
  ///   - samples: Mono 48,000 Hz 32-bit floating-point PCM audio samples.
  ///   - rmsLevel: Normalized RMS audio level, or `nil` to compute it from `samples`.
  package init(samples: [Float], rmsLevel: Float? = nil) {
    self.samples = samples
    self.rmsLevel = rmsLevel ?? samples.rmsLevel
  }
}

/// Interface for starting and stopping real-time microphone audio capture.
package protocol AudioCapturing {
  /// Starts streaming live audio chunks from the default input device.
  ///
  /// - Parameter onChunkReceived: Closure invoked on an audio capture queue with each resampled
  ///   audio chunk.
  func startCapture(_ onChunkReceived: @Sendable @escaping (AudioChunk) -> Void) throws

  /// Stops audio capture and removes any active audio engine taps.
  func stopCapture()
}

/// Real-time microphone audio capture engine using `AVAudioEngine`.
///
/// Ingests hardware microphone input and resamples to 48,000 Hz mono 32-bit float PCM buffers.
package final class AudioCaptureEngine: AudioCapturing {
  private static let logger = Logger(
    subsystem: "com.google.mediapipe.examples.audiotranscriber",
    category: "AudioCaptureEngine"
  )

  private let audioEngine = AVAudioEngine()
  private var isCapturing = false

  /// Creates a new microphone audio capture engine.
  package init() {}

  /// Errors that can occur during audio capture setup.
  package enum CaptureError: LocalizedError, Equatable {
    /// The hardware input node reported an invalid sample rate or channel count.
    case invalidInputFormat
    /// Failed to create the target 48,000 Hz mono float32 `AVAudioFormat`.
    case targetFormatCreationFailed
    /// Failed to create an `AVAudioConverter` from the input format to the target format.
    case converterCreationFailed

    package var errorDescription: String? {
      switch self {
      case .invalidInputFormat:
        return "Hardware input audio format has an invalid sample rate or channel count."
      case .targetFormatCreationFailed:
        return "Failed to create target 48,000 Hz mono float32 audio format."
      case .converterCreationFailed:
        return "Failed to create AVAudioConverter for microphone resampling."
      }
    }
  }

  package func startCapture(_ onChunkReceived: @Sendable @escaping (AudioChunk) -> Void) throws {
    guard !isCapturing else { return }

    #if os(iOS)
      let audioSession = AVAudioSession.sharedInstance()
      try audioSession.setCategory(
        .playAndRecord,
        mode: .measurement,
        options: [.duckOthers, .defaultToSpeaker]
      )
      try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
    #endif

    let inputNode = audioEngine.inputNode
    let inputFormat = inputNode.outputFormat(forBus: AudioCaptureConfiguration.mainBus)
    guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
      throw CaptureError.invalidInputFormat
    }

    guard
      let targetFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32,
        sampleRate: AudioCaptureConfiguration.targetSampleRate,
        channels: AudioCaptureConfiguration.monoChannelCount,
        interleaved: false
      )
    else {
      throw CaptureError.targetFormatCreationFailed
    }

    let converter: AVAudioConverter?
    if inputFormat.sampleRate != AudioCaptureConfiguration.targetSampleRate
      || inputFormat.channelCount != AudioCaptureConfiguration.monoChannelCount
    {
      guard let createdConverter = AVAudioConverter(from: inputFormat, to: targetFormat) else {
        throw CaptureError.converterCreationFailed
      }
      converter = createdConverter
    } else {
      converter = nil
    }

    inputNode.removeTap(onBus: AudioCaptureConfiguration.mainBus)
    inputNode.installTap(
      onBus: AudioCaptureConfiguration.mainBus,
      bufferSize: AudioCaptureConfiguration.tapBufferSize,
      format: inputFormat
    ) { buffer, _ in
      Self.processIncomingBuffer(
        buffer,
        inputFormat: inputFormat,
        targetFormat: targetFormat,
        converter: converter,
        onChunkReceived: onChunkReceived
      )
    }

    audioEngine.prepare()
    try audioEngine.start()
    isCapturing = true
  }

  package func stopCapture() {
    guard isCapturing else { return }
    audioEngine.inputNode.removeTap(onBus: AudioCaptureConfiguration.mainBus)
    audioEngine.stop()
    isCapturing = false

    #if os(iOS)
      do {
        try AVAudioSession.sharedInstance().setActive(
          false,
          options: .notifyOthersOnDeactivation
        )
      } catch {
        Self.logger.error(
          "Failed to deactivate AVAudioSession: \(error.localizedDescription, privacy: .public)"
        )
      }
    #endif
  }

  private static func processIncomingBuffer(
    _ buffer: AVAudioPCMBuffer,
    inputFormat: AVAudioFormat,
    targetFormat: AVAudioFormat,
    converter: AVAudioConverter?,
    onChunkReceived: @Sendable @escaping (AudioChunk) -> Void
  ) {
    if inputFormat.sampleRate == AudioCaptureConfiguration.targetSampleRate
      && inputFormat.channelCount == AudioCaptureConfiguration.monoChannelCount
    {
      emitChunk(from: buffer, onChunkReceived: onChunkReceived)
      return
    }

    guard let converter else { return }

    let sampleRateRatio = AudioCaptureConfiguration.targetSampleRate / inputFormat.sampleRate
    let frameCapacity = AVAudioFrameCount(
      Double(buffer.frameLength) * sampleRateRatio
        + AudioCaptureConfiguration.converterFrameHeadroom
    )
    guard
      let convertedBuffer = AVAudioPCMBuffer(
        pcmFormat: targetFormat,
        frameCapacity: frameCapacity
      )
    else {
      return
    }

    var isConsumed = false
    var conversionError: NSError?
    let status = converter.convert(to: convertedBuffer, error: &conversionError) { _, outStatus in
      guard !isConsumed else {
        outStatus.pointee = .noDataNow
        return nil
      }
      isConsumed = true
      outStatus.pointee = .haveData
      return buffer
    }

    guard status != .error, conversionError == nil else {
      let errorDescription = conversionError?.localizedDescription ?? "unknown error"
      Self.logger.error(
        "Audio conversion failed: \(errorDescription, privacy: .public)"
      )
      return
    }

    emitChunk(from: convertedBuffer, onChunkReceived: onChunkReceived)
  }

  private static func emitChunk(
    from buffer: AVAudioPCMBuffer,
    onChunkReceived: @Sendable @escaping (AudioChunk) -> Void
  ) {
    guard let channelData = buffer.floatChannelData?[0] else { return }
    let frameCount = Int(buffer.frameLength)
    guard frameCount > 0 else { return }
    let samples = Array(UnsafeBufferPointer(start: channelData, count: frameCount))
    onChunkReceived(AudioChunk(samples: samples))
  }
}
