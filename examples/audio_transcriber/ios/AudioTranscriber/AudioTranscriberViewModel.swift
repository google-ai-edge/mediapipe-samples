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
#if canImport(MediaPipeTasksAudio)
import MediaPipeTasksAudio
#elseif canImport(MPPAudioTranscriber)
import MPPAudioTranscriber
#endif
import OSLog
import Observation

/// Configuration constants for the MediaPipe `AudioTranscriber` sample.
package enum TranscriberConfiguration {
  static let modelName = "parakeet_tdt_0.6b_v3_5s_i8_stateful"
  /// File extension for LiteRT-LM transcription model bundles.
  package static let modelExtension = "litertlm"
  /// Default bundled model filename including extension.
  package static let defaultModelFileName = "\(modelName).\(modelExtension)"
  static let sampleRateHz: Double = 48_000.0
  static let millisecondsPerSecond: Double = 1_000.0
}

/// User-facing status messages displayed by `AudioTranscriberViewModel`.
package enum StatusMessage {
  package static let ready = "Ready"
  package static let listening = "Listening..."
  package static let finalized = "Recording finalized"
  package static let cleared = "Cleared"

  /// Returns a status message indicating that the given model filename was loaded.
  package static func loaded(_ modelName: String) -> String {
    "Loaded \(modelName)"
  }
}

extension AudioTranscriberSegment: @retroactive Identifiable {
  /// Stable composite identifier combining the segment's timestamp range and transcript text.
  public var id: String {
    "\(fromTimestampInMilliseconds)_\(toTimestampInMilliseconds)_\(transcript)"
  }
}

extension AudioTranscriberSegment {
  /// Duration of the transcribed audio segment in seconds.
  package var durationSeconds: Double {
    Double(toTimestampInMilliseconds - fromTimestampInMilliseconds)
      / TranscriberConfiguration.millisecondsPerSecond
  }
}

/// Interface for streaming speech-to-text transcription and lifecycle management.
package protocol AudioTranscribing {
  /// Preloads the transcription model and initializes the processing pipeline.
  func prewarm() throws

  /// Streams a buffer of mono float32 PCM audio samples into the transcriber.
  ///
  /// - Parameters:
  ///   - samples: Mono 48,000 Hz float32 PCM samples.
  ///   - endOfStream: Whether this buffer marks the end of the audio stream.
  /// - Returns: Partial or finalized transcription segments produced so far.
  func transcribe(samples: [Float], endOfStream: Bool) throws -> AudioTranscriberResult

  /// Signals the end of the audio stream and flushes any remaining transcription segments.
  func finishStream() throws -> AudioTranscriberResult

  /// Releases native resources held by the transcriber.
  func close() throws
}

extension AudioTranscriber: AudioTranscribing {
  package func transcribe(samples: [Float], endOfStream: Bool) throws -> AudioTranscriberResult {
    try samples.withUnsafeBufferPointer { buffer in
      try transcribe(
        samples: buffer.baseAddress,
        count: UInt(buffer.count),
        endOfStream: endOfStream
      )
    }
  }
}

/// Observable view model coordinating live microphone capture and MediaPipe `AudioTranscriber`
/// streaming speech recognition.
@Observable
@MainActor
package final class AudioTranscriberViewModel {
  nonisolated private static let logger = Logger(
    subsystem: "com.google.mediapipe.examples.audiotranscriber",
    category: "AudioTranscriberViewModel"
  )

  /// Errors that can occur when creating the default MediaPipe `AudioTranscriber`.
  package enum TranscriberError: LocalizedError, Equatable {
    /// The bundled LiteRT-LM model asset could not be located.
    case modelNotFound(name: String, fileExtension: String)

    package var errorDescription: String? {
      switch self {
      case .modelNotFound(let name, let fileExtension):
        return "Model not found in bundle: \(name).\(fileExtension)"
      }
    }
  }

  /// Whether microphone audio capture and streaming transcription are currently active.
  package private(set) var isRecording = false

  /// Most recent non-finalized partial transcript for the active utterance.
  package private(set) var interimTranscript = ""

  /// Completed transcription segments accumulated across the current session.
  package private(set) var finalizedResults: [AudioTranscriberSegment] = []

  /// Normalized root-mean-square (RMS) microphone input level in `[0.0, 1.0]`.
  package private(set) var audioLevel: Float = 0.0

  /// Filename of the currently active transcription model displayed in the UI.
  package private(set) var activeModelName = TranscriberConfiguration.defaultModelFileName

  /// Human-readable status string describing the current transcriber state.
  package private(set) var statusMessage = StatusMessage.ready

  @ObservationIgnored private var transcriber: (any AudioTranscribing)?
  @ObservationIgnored private let captureEngine: any AudioCapturing
  @ObservationIgnored private let documentsDirectory: URL?
  @ObservationIgnored private let transcriberFactory: () throws -> any AudioTranscribing
  @ObservationIgnored private let transcriberLoader: (String) throws -> any AudioTranscribing

  /// Creates a view model with the given audio capture engine and transcriber factories.
  ///
  /// - Parameters:
  ///   - captureEngine: Engine used to capture resampled microphone audio chunks.
  ///   - documentsDirectory: Directory searched first for a sideloaded model.
  ///   - transcriberFactory: Creates the initial `AudioTranscribing` instance during setup.
  ///   - transcriberLoader: Creates an `AudioTranscribing` instance for a model at a given path.
  package init(
    captureEngine: any AudioCapturing = AudioCaptureEngine(),
    documentsDirectory: URL? = FileManager.default.urls(
      for: .documentDirectory,
      in: .userDomainMask
    ).first,
    transcriberFactory: (() throws -> any AudioTranscribing)? = nil,
    transcriberLoader: @escaping (String) throws -> any AudioTranscribing =
      AudioTranscriberViewModel.makeTranscriber(modelPath:)
  ) {
    self.captureEngine = captureEngine
    self.documentsDirectory = documentsDirectory
    self.transcriberFactory =
      transcriberFactory ?? {
        try transcriberLoader(Self.resolveDefaultModelPath(in: documentsDirectory))
      }
    self.transcriberLoader = transcriberLoader
    setUpTranscriber()
  }

  deinit {
    guard let transcriber else { return }
    do {
      try transcriber.close()
    } catch {
      Self.logger.error(
        """
        Failed to close AudioTranscriber during deinitialization: \
        \(error.localizedDescription, privacy: .public)
        """
      )
    }
  }

  /// Initializes and prewarms the `AudioTranscribing` instance.
  package func setUpTranscriber() {
    do {
      let newTranscriber = try transcriberFactory()
      try newTranscriber.prewarm()
      transcriber = newTranscriber
      if let sideloadedPath = Self.resolveSideloadedModelPath(in: documentsDirectory) {
        activeModelName = URL(fileURLWithPath: sideloadedPath).lastPathComponent
      } else {
        activeModelName = TranscriberConfiguration.defaultModelFileName
      }
      statusMessage = StatusMessage.ready
    } catch let error as TranscriberError {
      Self.logger.error(
        "Transcriber model missing: \(error.localizedDescription, privacy: .public)"
      )
      statusMessage = error.localizedDescription
    } catch {
      Self.logger.error(
        "Failed to initialize AudioTranscriber: \(error.localizedDescription, privacy: .public)"
      )
      statusMessage = "Initialization error: \(error.localizedDescription)"
    }
  }

  /// Loads and prewarms a model from the specified file URL, replacing the current transcriber.
  ///
  /// - Parameter url: Local file URL of the `.litertlm` model to load.
  package func loadModel(at url: URL) {
    if isRecording {
      stopRecording()
    }

    do {
      try transcriber?.close()
    } catch {
      Self.logger.error(
        """
        Failed to close previous AudioTranscriber: \
        \(error.localizedDescription, privacy: .public)
        """
      )
    }
    transcriber = nil

    do {
      let newTranscriber = try transcriberLoader(url.path)
      try newTranscriber.prewarm()
      interimTranscript = ""
      finalizedResults.removeAll()
      transcriber = newTranscriber
      activeModelName = url.lastPathComponent
      statusMessage = StatusMessage.loaded(url.lastPathComponent)
    } catch {
      Self.logger.error(
        """
        Failed to load AudioTranscriber model at \(url.path, privacy: .public): \
        \(error.localizedDescription, privacy: .public)
        """
      )
      statusMessage = "Initialization error: \(error.localizedDescription)"
    }
  }

  /// Toggles active microphone recording state.
  package func toggleRecording() {
    if isRecording {
      stopRecording()
    } else {
      startRecording()
    }
  }

  /// Starts capturing microphone audio and streaming samples to the transcriber.
  package func startRecording() {
    guard !isRecording else { return }
    do {
      if transcriber == nil {
        setUpTranscriber()
      }
      guard transcriber != nil else { return }

      try captureEngine.startCapture { [weak self] chunk in
        Task { @MainActor [weak self] in
          guard let self, isRecording, let transcriber else { return }
          audioLevel = chunk.rmsLevel
          do {
            let result = try transcriber.transcribe(
              samples: chunk.samples,
              endOfStream: false
            )
            for segment in result.segments {
              if segment.isFinal {
                finalizedResults.append(segment)
                interimTranscript = ""
              } else {
                interimTranscript = segment.transcript
              }
            }
          } catch {
            Self.logger.error(
              "Transcription failed: \(error.localizedDescription, privacy: .public)"
            )
            statusMessage = "Transcription error: \(error.localizedDescription)"
          }
        }
      }

      isRecording = true
      statusMessage = StatusMessage.listening
    } catch {
      Self.logger.error(
        "Failed to start microphone capture: \(error.localizedDescription, privacy: .public)"
      )
      statusMessage = "Microphone error: \(error.localizedDescription)"
    }
  }

  /// Stops microphone capture and flushes remaining audio in the stream.
  package func stopRecording() {
    guard isRecording else { return }
    captureEngine.stopCapture()
    isRecording = false
    audioLevel = 0.0

    guard let transcriber else { return }
    do {
      // Signal that the stream has finished. This processes any remaining audio and returns final
      // segments.
      let finalResult = try transcriber.finishStream()
      finalizedResults.append(contentsOf: finalResult.segments)
      interimTranscript = ""

      // Prewarm again to prepare the transcriber for the next recording session.
      try transcriber.prewarm()
      statusMessage = StatusMessage.finalized
    } catch {
      Self.logger.error(
        "Failed to finalize transcription stream: \(error.localizedDescription, privacy: .public)"
      )
      statusMessage = "Finalization error: \(error.localizedDescription)"
    }
  }

  /// Clears all interim and finalized transcripts and resets the transcription stream.
  package func clearTranscripts() {
    interimTranscript = ""
    finalizedResults.removeAll()

    guard let transcriber else {
      statusMessage = StatusMessage.cleared
      return
    }

    do {
      _ = try transcriber.finishStream()
      try transcriber.prewarm()
      statusMessage = StatusMessage.cleared
    } catch {
      Self.logger.error(
        """
        Failed to reset stream while clearing transcripts: \
        \(error.localizedDescription, privacy: .public)
        """
      )
      statusMessage = "Reset error: \(error.localizedDescription)"
    }
  }

  /// Resolves the default `.litertlm` model path, preferring any sideloaded model in the
  /// application's `Documents` directory before falling back to the bundled model resource.
  ///
  /// - Parameters:
  ///   - documentsDirectory: Optional `Documents` directory URL to search first.
  ///   - bundles: Candidate bundles searched when no sideloaded model is present.
  /// - Returns: File system path to the resolved `.litertlm` model file.
  /// - Throws: `TranscriberError.modelNotFound` if no `.litertlm` model is found in the
  ///   `Documents` directory or candidate bundles.
  nonisolated package static func resolveDefaultModelPath(
    in documentsDirectory: URL? = FileManager.default.urls(
      for: .documentDirectory,
      in: .userDomainMask
    ).first,
    bundles: [Bundle] = [Bundle.main, Bundle(for: AudioTranscriberViewModel.self)]
  ) throws -> String {
    if let sideloadedPath = resolveSideloadedModelPath(in: documentsDirectory) {
      return sideloadedPath
    }

    if let modelPath = bundles.lazy.compactMap({
        $0.path(
          forResource: TranscriberConfiguration.modelName,
          ofType: TranscriberConfiguration.modelExtension
        )
      }).first {
      return modelPath
    }

    #if os(macOS)
    let homeModel = FileManager.default.homeDirectoryForCurrentUser
      .appendingPathComponent("models")
      .appendingPathComponent(TranscriberConfiguration.defaultModelFileName).path
    if FileManager.default.fileExists(atPath: homeModel) {
      return homeModel
    }
    #else
    let homeModel = (NSHomeDirectory() as NSString).appendingPathComponent("models/\(TranscriberConfiguration.defaultModelFileName)")
    if FileManager.default.fileExists(atPath: homeModel) {
      return homeModel
    }
    #endif

    throw TranscriberError.modelNotFound(
      name: TranscriberConfiguration.modelName,
      fileExtension: TranscriberConfiguration.modelExtension
    )
  }

  nonisolated private static func resolveSideloadedModelPath(
    in documentsDirectory: URL?
  ) -> String? {
    guard let documentsDirectory else { return nil }
    do {
      let fileURLs = try FileManager.default.contentsOfDirectory(
        at: documentsDirectory,
        includingPropertiesForKeys: nil,
        options: [.skipsHiddenFiles]
      )
      return
        fileURLs
        .filter { $0.pathExtension == TranscriberConfiguration.modelExtension }
        .min { $0.lastPathComponent < $1.lastPathComponent }?
        .path
    } catch {
      Self.logger.debug(
        """
        Could not inspect Documents directory for sideloaded models: \
        \(error.localizedDescription, privacy: .public)
        """
      )
      return nil
    }
  }

  nonisolated private static func makeTranscriber(
    modelPath: String
  ) throws -> any AudioTranscribing {
    let options = AudioTranscriberOptions()
    options.baseOptions.modelAssetPath = modelPath
    options.baseOptions.delegate = .CPU
    options.sampleRateHz = TranscriberConfiguration.sampleRateHz
    options.enableInterimResults = true
    options.modelArchitecture = .auto

    return try AudioTranscriber(options: options)
  }
}
