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
import Observation

/// Input source tab selectable in the main UI.
public enum AudioInputSourceMode: String, CaseIterable, Identifiable, Sendable {
  case clip = "clip"
  case microphone = "microphone"

  public var id: String { rawValue }

  public var label: String {
    switch self {
    case .clip: return "Audio Clip"
    case .microphone: return "Microphone"
    }
  }

  public var systemImage: String {
    switch self {
    case .clip: return "waveform"
    case .microphone: return "mic.fill"
    }
  }
}

/// Chunk of captured 16 kHz mono PCM microphone audio with its normalized RMS level.
public struct AudioCaptureChunk: Sendable, Equatable {
  public let samples: [Float]
  public let rmsLevel: Float

  public init(samples: [Float], rmsLevel: Float? = nil) {
    self.samples = samples
    self.rmsLevel = rmsLevel ?? AudioClipDecoder.normalizedRmsLevel(for: samples)
  }
}

/// Errors emitted while configuring live microphone audio capture.
public enum AudioCaptureError: LocalizedError, Equatable, Sendable {
  case permissionDenied
  case invalidInputFormat
  case targetFormatCreationFailed
  case converterCreationFailed

  public var errorDescription: String? {
    switch self {
    case .permissionDenied:
      return "Microphone permission is required for live recording. Enable it in Settings."
    case .invalidInputFormat:
      return "Hardware microphone reported an invalid sample rate or channel count."
    case .targetFormatCreationFailed:
      return "Failed to configure 16 kHz mono float32 audio capture format."
    case .converterCreationFailed:
      return "Failed to create resampler for microphone input."
    }
  }
}

/// Captures microphone audio via `AVAudioEngine` and resamples to 16 kHz mono float32 PCM.
private final class LiveAudioCaptureEngine {
  private static let targetSampleRateHz: Double = AudioTranscriberHelper.targetSampleRateHz
  private static let tapBufferFrames: AVAudioFrameCount = 4_096
  private static let converterHeadroomFrames: Double = 512.0

  private final class TapContext: @unchecked Sendable {
    let inputFormat: AVAudioFormat
    let targetFormat: AVAudioFormat
    let converter: AVAudioConverter?
    let continuation: AsyncStream<AudioCaptureChunk>.Continuation

    init(
      inputFormat: AVAudioFormat,
      targetFormat: AVAudioFormat,
      converter: AVAudioConverter?,
      continuation: AsyncStream<AudioCaptureChunk>.Continuation
    ) {
      self.inputFormat = inputFormat
      self.targetFormat = targetFormat
      self.converter = converter
      self.continuation = continuation
    }
  }

  private let audioEngine = AVAudioEngine()
  private var continuation: AsyncStream<AudioCaptureChunk>.Continuation?
  private var isCapturing = false

  func startCapture() throws -> AsyncStream<AudioCaptureChunk> {
    if isCapturing {
      stopCapture()
    }

    #if os(iOS)
    let session = AVAudioSession.sharedInstance()
    try session.setCategory(
      .playAndRecord,
      mode: .measurement,
      options: [.duckOthers, .defaultToSpeaker]
    )
    try session.setActive(true, options: .notifyOthersOnDeactivation)
    #endif

    let inputNode = audioEngine.inputNode
    let inputFormat = inputNode.outputFormat(forBus: 0)
    guard inputFormat.sampleRate > 0, inputFormat.channelCount > 0 else {
      throw AudioCaptureError.invalidInputFormat
    }

    guard
      let targetFormat = AVAudioFormat(
        commonFormat: .pcmFormatFloat32,
        sampleRate: Self.targetSampleRateHz,
        channels: 1,
        interleaved: false
      )
    else {
      throw AudioCaptureError.targetFormatCreationFailed
    }

    let needsConversion =
      abs(inputFormat.sampleRate - Self.targetSampleRateHz) >= 0.5
      || inputFormat.channelCount != 1
    let converter: AVAudioConverter?
    if needsConversion {
      guard let created = AVAudioConverter(from: inputFormat, to: targetFormat) else {
        throw AudioCaptureError.converterCreationFailed
      }
      converter = created
    } else {
      converter = nil
    }

    let (stream, streamContinuation) = AsyncStream<AudioCaptureChunk>.makeStream(
      bufferingPolicy: .bufferingNewest(64)
    )
    continuation = streamContinuation

    let context = TapContext(
      inputFormat: inputFormat,
      targetFormat: targetFormat,
      converter: converter,
      continuation: streamContinuation
    )

    inputNode.removeTap(onBus: 0)
    inputNode.installTap(
      onBus: 0,
      bufferSize: Self.tapBufferFrames,
      format: inputFormat
    ) { buffer, _ in
      Self.handleBuffer(buffer, context: context)
    }

    audioEngine.prepare()
    do {
      try audioEngine.start()
    } catch {
      inputNode.removeTap(onBus: 0)
      streamContinuation.finish()
      continuation = nil
      throw error
    }

    isCapturing = true
    return stream
  }

  func stopCapture() {
    guard isCapturing else { return }
    audioEngine.inputNode.removeTap(onBus: 0)
    audioEngine.stop()
    continuation?.finish()
    continuation = nil
    isCapturing = false

    #if os(iOS)
    try? AVAudioSession.sharedInstance().setActive(
      false,
      options: .notifyOthersOnDeactivation
    )
    #endif
  }

  private static func handleBuffer(_ buffer: AVAudioPCMBuffer, context: TapContext) {
    let sourceBuffer: AVAudioPCMBuffer
    if let converter = context.converter {
      let ratio = Self.targetSampleRateHz / context.inputFormat.sampleRate
      let capacity = AVAudioFrameCount(
        Double(buffer.frameLength) * ratio + Self.converterHeadroomFrames
      )
      guard
        let converted = AVAudioPCMBuffer(
          pcmFormat: context.targetFormat,
          frameCapacity: max(1, capacity)
        )
      else {
        return
      }

      final class BufferBox: @unchecked Sendable {
        let buffer: AVAudioPCMBuffer
        var consumed = false
        init(_ buffer: AVAudioPCMBuffer) { self.buffer = buffer }
      }
      let box = BufferBox(buffer)
      var conversionError: NSError?
      let status = converter.convert(to: converted, error: &conversionError) { _, outStatus in
        if box.consumed {
          outStatus.pointee = .noDataNow
          return nil
        }
        box.consumed = true
        outStatus.pointee = .haveData
        return box.buffer
      }
      guard status != .error, conversionError == nil else { return }
      sourceBuffer = converted
    } else {
      sourceBuffer = buffer
    }

    let frameCount = Int(sourceBuffer.frameLength)
    guard frameCount > 0, let channelData = sourceBuffer.floatChannelData?[0] else { return }
    let samples = Array(UnsafeBufferPointer(start: channelData, count: frameCount))
    context.continuation.yield(AudioCaptureChunk(samples: samples))
  }
}

/// Observable MainActor ViewModel driving the MediaPipe Audio Transcriber sample.
@Observable
@MainActor
public final class AudioTranscriberViewModel {
  /// Whether Settings exposes a GPU delegate picker in the user-facing UI.
  ///
  /// Verified on iOS: Parakeet TDT 0.6B v3 loads and transcribes cleanly on both CPU and GPU (Metal).
  public static let supportsGpuDelegateInUI = true

  private static let levelBarCount = 24

  public private(set) var selectedModel: AudioTranscriberModelOption
  public private(set) var selectedDelegate: AudioTranscriberDelegateOption
  public private(set) var clipMode: AudioClipTranscriptionMode
  public var inputMode: AudioInputSourceMode = .clip

  public private(set) var activeModelName: String
  public private(set) var activeClipLabel: String =
    "\(AudioTranscriberHelper.sampleClipResourceName).\(AudioTranscriberHelper.sampleClipFileExtension)"
  public private(set) var activeClipURL: URL?
  public private(set) var isUsingCustomClip: Bool = false

  public private(set) var isModelLoading: Bool = false
  public private(set) var isTranscribing: Bool = false
  public private(set) var isRecording: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var modelLoadTimeMs: Double?
  public private(set) var audioLevel: Float = 0.0
  public private(set) var levelHistory: [Float] = Array(repeating: 0.0, count: levelBarCount)
  public private(set) var streamProgress: Double = 0.0
  public private(set) var recordingDurationSeconds: Double = 0.0

  public private(set) var finalizedSegments: [TranscribedSegment] = []
  public private(set) var interimTranscript: String = ""
  public private(set) var lastResult: AudioTranscriptionResult?
  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  @ObservationIgnored private let helper: AudioTranscriberHelper
  @ObservationIgnored private let captureEngine = LiveAudioCaptureEngine()
  @ObservationIgnored private let autoTranscribeSampleOnLaunch: Bool
  @ObservationIgnored private var hasCompletedInitialSample: Bool = false
  @ObservationIgnored private var activeTask: Task<Void, Never>?
  @ObservationIgnored private var liveStreamTask: Task<Void, Never>?
  @ObservationIgnored private var recordingTimerTask: Task<Void, Never>?
  @ObservationIgnored private var generation: UInt64 = 0

  public var isBusy: Bool {
    isModelLoading || isTranscribing
  }

  public var canTranscribeClip: Bool {
    isReady && !isBusy && !isRecording
  }

  public var canToggleRecording: Bool {
    isReady && !isModelLoading && !isTranscribing
  }

  public var fullTranscript: String {
    finalizedSegments
      .map { $0.transcript.trimmingCharacters(in: .whitespacesAndNewlines) }
      .filter { !$0.isEmpty }
      .joined(separator: " ")
  }

  public var hasTranscript: Bool {
    !fullTranscript.isEmpty || !interimTranscript.isEmpty
  }

  public var headerSubtitle: String {
    "\(selectedModel.displayName) · 16 kHz · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading \(selectedModel.displayName)…")
    }
    if isRecording {
      return .ready("Listening · Live Mic")
    }
    if isTranscribing {
      return .loading(
        clipMode == .streaming ? "Streaming audio…" : "Transcribing clip…"
      )
    }
    if errorMessage != nil && !isReady {
      return .failed("Model unavailable")
    }
    if isReady {
      return .ready("Ready · On-Device")
    }
    return .loading("Initializing…")
  }

  public init(
    model: AudioTranscriberModelOption = .parakeetTdtV3,
    delegate: AudioTranscriberDelegateOption = .cpu,
    clipMode: AudioClipTranscriptionMode = .fullClip,
    locator: SampleModelLocator = SampleModelLocator(),
    autoLoad: Bool = true,
    autoTranscribeSampleOnLaunch: Bool = false
  ) {
    self.selectedModel = model
    self.selectedDelegate = delegate
    self.clipMode = clipMode
    self.activeModelName = model.assetFilename
    self.helper = AudioTranscriberHelper(locator: locator)
    self.autoTranscribeSampleOnLaunch = autoTranscribeSampleOnLaunch

    if autoLoad {
      scheduleReload()
    }
  }

  // MARK: - Synchronous UI Actions

  public func selectInputMode(_ mode: AudioInputSourceMode) {
    guard inputMode != mode else { return }
    if isRecording {
      activeTask?.cancel()
      activeTask = Task { [weak self] in
        await self?.stopRecording()
      }
    }
    inputMode = mode
    errorMessage = nil
  }

  public func selectClipMode(_ mode: AudioClipTranscriptionMode) {
    guard clipMode != mode else { return }
    clipMode = mode
  }

  public func selectDelegate(_ delegate: AudioTranscriberDelegateOption) {
    guard selectedDelegate != delegate else { return }
    selectedDelegate = delegate
    scheduleReload()
  }

  public func switchToCpuDelegate() {
    if selectedDelegate == .cpu {
      scheduleReload()
    } else {
      selectDelegate(.cpu)
    }
  }

  public func retryInitialization() {
    scheduleReload()
  }

  public func resetToBundledSample() {
    activeClipURL = nil
    isUsingCustomClip = false
    activeClipLabel =
      "\(AudioTranscriberHelper.sampleClipResourceName).\(AudioTranscriberHelper.sampleClipFileExtension)"
    errorMessage = nil
  }

  public func triggerTranscribeActiveClip() {
    guard canTranscribeClip else { return }
    let targetURL = activeClipURL
    let shouldStream = clipMode.isStreaming
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      if let targetURL {
        _ = await self.transcribeFile(url: targetURL, stream: shouldStream)
      } else {
        _ = await self.transcribeBundledSample(stream: shouldStream)
      }
    }
  }

  public func triggerTranscribeBundledSample() {
    guard canTranscribeClip else { return }
    resetToBundledSample()
    let shouldStream = clipMode.isStreaming
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      _ = await self?.transcribeBundledSample(stream: shouldStream)
    }
  }

  public func triggerImportAndTranscribeFile(url: URL) {
    guard !isRecording && !isModelLoading else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let accessed = url.startAccessingSecurityScopedResource()
      defer {
        if accessed {
          url.stopAccessingSecurityScopedResource()
        }
      }
      do {
        let localCopy = try Self.stageImportedAudioFile(from: url)
        self.activeClipURL = localCopy
        self.activeClipLabel = url.lastPathComponent
        self.isUsingCustomClip = true
        _ = await self.transcribeFile(url: localCopy, stream: self.clipMode.isStreaming)
      } catch {
        self.errorMessage = "Could not import audio file: \(error.localizedDescription)"
      }
    }
  }

  public func triggerToggleRecording() {
    guard canToggleRecording else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      if self.isRecording {
        await self.stopRecording()
      } else {
        await self.startRecording()
      }
    }
  }

  public func triggerClearTranscript() {
    guard !isRecording && !isTranscribing else { return }
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      await self?.clearTranscript()
    }
  }

  // MARK: - Awaitable Operations (for Tests & Validation Hosts)

  /// Loads and prewarms the transcriber with the current configuration.
  @discardableResult
  public func loadTranscriber(customModelURL: URL? = nil) async -> Bool {
    if isRecording {
      await stopRecording()
    }
    generation &+= 1
    let currentGen = generation
    let model = selectedModel
    let delegate = selectedDelegate

    isModelLoading = true
    isTranscribing = false
    isReady = false
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(
        model: model,
        delegate: delegate,
        enableInterimResults: true,
        customModelURL: customModelURL
      )
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      activeModelName = customModelURL?.lastPathComponent ?? model.assetFilename
      isModelLoading = false
      isReady = true
      return true
    } catch let error as AudioTranscriberError {
      guard generation == currentGen else { return false }
      isModelLoading = false
      isReady = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return false
    } catch {
      guard generation == currentGen else { return false }
      isModelLoading = false
      isReady = false
      errorMessage = error.localizedDescription
      isGpuError = delegate == .gpu
      return false
    }
  }

  /// Updates configuration fields in one batch and reloads if the delegate changed or not ready.
  @discardableResult
  public func configure(
    delegate: AudioTranscriberDelegateOption? = nil,
    clipMode: AudioClipTranscriptionMode? = nil
  ) async -> Bool {
    activeTask?.cancel()
    let delegateChanged = delegate != nil && delegate != selectedDelegate
    if let delegate { selectedDelegate = delegate }
    if let clipMode { self.clipMode = clipMode }
    if delegateChanged || !isReady {
      return await loadTranscriber()
    }
    return true
  }

  /// Transcribes the bundled `speech_16000_hz_mono.wav` sample clip and awaits the result.
  @discardableResult
  public func transcribeBundledSample(
    stream: Bool? = nil
  ) async -> AudioTranscriptionResult? {
    do {
      let url = try await helper.locateBundledSampleURL()
      activeClipLabel = url.lastPathComponent
      isUsingCustomClip = false
      activeClipURL = nil
      return await transcribeFile(url: url, stream: stream ?? clipMode.isStreaming)
    } catch {
      errorMessage = error.localizedDescription
      return nil
    }
  }

  /// Transcribes an audio file at `url` in either single-call (`stream == false`) or chunked
  /// streaming (`stream == true`) mode and awaits the result.
  @discardableResult
  public func transcribeFile(
    url: URL,
    stream: Bool
  ) async -> AudioTranscriptionResult? {
    if !isReady {
      let loaded = await loadTranscriber()
      guard loaded else { return nil }
    }
    if isRecording {
      await stopRecording()
    }

    generation &+= 1
    let currentGen = generation

    isTranscribing = true
    errorMessage = nil
    isGpuError = false
    finalizedSegments = []
    interimTranscript = ""
    streamProgress = 0.0
    resetLevelHistory()

    do {
      let result = try await helper.transcribe(
        url: url,
        stream: stream
      ) { [weak self] update in
        await MainActor.run { [weak self] in
          guard let self, self.generation == currentGen else { return }
          self.finalizedSegments = update.finalizedSegments
          self.interimTranscript = update.interimTranscript
          self.pushAudioLevel(update.chunkRmsLevel)
          if update.totalSeconds > 0 {
            self.streamProgress = min(1.0, max(0.0, update.processedSeconds / update.totalSeconds))
          }
        }
      }

      guard generation == currentGen else { return nil }
      isTranscribing = false
      finalizedSegments = result.segments
      interimTranscript = ""
      streamProgress = 1.0
      audioLevel = 0.0
      lastResult = result
      return result
    } catch is CancellationError {
      guard generation == currentGen else { return nil }
      isTranscribing = false
      audioLevel = 0.0
      return nil
    } catch let error as AudioTranscriberError {
      guard generation == currentGen else { return nil }
      isTranscribing = false
      audioLevel = 0.0
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isTranscribing = false
      audioLevel = 0.0
      errorMessage = error.localizedDescription
      return nil
    }
  }

  /// Requests microphone permission (on iOS) and begins live streaming transcription.
  public func startRecording() async {
    guard !isRecording, !isTranscribing else { return }
    if !isReady {
      let loaded = await loadTranscriber()
      guard loaded else { return }
    }

    errorMessage = nil
    isGpuError = false

    #if os(iOS)
    let granted = await requestMicrophonePermission()
    guard granted else {
      errorMessage = AudioCaptureError.permissionDenied.localizedDescription
      return
    }
    #endif

    finalizedSegments = []
    interimTranscript = ""
    lastResult = nil
    recordingDurationSeconds = 0.0
    resetLevelHistory()

    let stream: AsyncStream<AudioCaptureChunk>
    do {
      stream = try captureEngine.startCapture()
    } catch {
      errorMessage = "Microphone error: \(error.localizedDescription)"
      return
    }

    generation &+= 1
    let currentGen = generation
    isRecording = true

    let sessionStart = ContinuousClock.now
    recordingTimerTask?.cancel()
    recordingTimerTask = Task { [weak self] in
      while !Task.isCancelled {
        try? await Task.sleep(for: .milliseconds(200))
        guard let self, self.isRecording, self.generation == currentGen else { break }
        let elapsed = sessionStart.duration(to: .now)
        self.recordingDurationSeconds =
          Double(elapsed.components.seconds) + Double(elapsed.components.attoseconds) / 1e18
      }
    }

    liveStreamTask?.cancel()
    liveStreamTask = Task { [weak self] in
      guard let self else { return }
      for await chunk in stream {
        guard !Task.isCancelled, self.isRecording, self.generation == currentGen else { break }
        self.pushAudioLevel(chunk.rmsLevel)
        do {
          let step = try await self.helper.processLiveChunk(chunk.samples)
          guard self.isRecording, self.generation == currentGen else { break }
          if !step.finalized.isEmpty {
            self.finalizedSegments.append(contentsOf: step.finalized)
            self.interimTranscript = ""
          }
          if let interim = step.interim {
            self.interimTranscript = interim
          }
        } catch {
          guard self.generation == currentGen else { break }
          self.errorMessage = "Transcription error: \(error.localizedDescription)"
          break
        }
      }
    }
  }

  /// Stops microphone capture and flushes remaining audio into finalized segments.
  public func stopRecording() async {
    guard isRecording else { return }
    isRecording = false
    captureEngine.stopCapture()
    recordingTimerTask?.cancel()
    recordingTimerTask = nil
    liveStreamTask?.cancel()
    await liveStreamTask?.value
    liveStreamTask = nil
    audioLevel = 0.0

    let start = ContinuousClock.now
    do {
      let flushed = try await helper.finishLiveStream()
      finalizedSegments.append(contentsOf: flushed)
      interimTranscript = ""

      let finalizeMs =
        Double(start.duration(to: .now).components.seconds) * 1_000.0
        + Double(start.duration(to: .now).components.attoseconds) / 1e15
      let duration = max(recordingDurationSeconds, 0.1)
      let transcript = fullTranscript
      if !transcript.isEmpty {
        lastResult = AudioTranscriptionResult(
          transcript: transcript,
          segments: finalizedSegments,
          audioDurationSeconds: duration,
          inferenceTimeMs: finalizeMs,
          realTimeFactor: (finalizeMs / 1_000.0) / duration,
          mode: .streaming,
          chunkCount: max(1, finalizedSegments.count)
        )
      }
    } catch {
      errorMessage = "Finalization error: \(error.localizedDescription)"
    }
  }

  /// Clears all interim and finalized transcripts and resets the underlying transcriber stream.
  public func clearTranscript() async {
    finalizedSegments = []
    interimTranscript = ""
    lastResult = nil
    errorMessage = nil
    streamProgress = 0.0
    recordingDurationSeconds = 0.0
    resetLevelHistory()
    do {
      try await helper.resetStream()
    } catch {
      errorMessage = "Reset error: \(error.localizedDescription)"
    }
  }

  // MARK: - Private Helpers

  private func scheduleReload() {
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let loaded = await self.loadTranscriber()
      if loaded && self.autoTranscribeSampleOnLaunch && !self.hasCompletedInitialSample {
        self.hasCompletedInitialSample = true
        _ = await self.transcribeBundledSample(stream: self.clipMode.isStreaming)
      }
    }
  }

  private func pushAudioLevel(_ level: Float) {
    let clamped = min(max(level, 0.0), 1.0)
    audioLevel = clamped
    var updated = levelHistory
    if updated.count >= Self.levelBarCount {
      updated.removeFirst()
    }
    updated.append(clamped)
    levelHistory = updated
  }

  private func resetLevelHistory() {
    audioLevel = 0.0
    levelHistory = Array(repeating: 0.0, count: Self.levelBarCount)
  }

  #if os(iOS)
  private func requestMicrophonePermission() async -> Bool {
    switch AVAudioApplication.shared.recordPermission {
    case .granted:
      return true
    case .denied:
      return false
    case .undetermined:
      return await AVAudioApplication.requestRecordPermission()
    @unknown default:
      return false
    }
  }
  #endif

  private static func stageImportedAudioFile(from sourceURL: URL) throws -> URL {
    let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent(
      "AudioTranscriberImports",
      isDirectory: true
    )
    try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    let safeName = sourceURL.lastPathComponent.isEmpty ? "imported_audio.wav" : sourceURL.lastPathComponent
    let destURL = tempDir.appendingPathComponent(safeName)
    if FileManager.default.fileExists(atPath: destURL.path) {
      try FileManager.default.removeItem(at: destURL)
    }
    try FileManager.default.copyItem(at: sourceURL, to: destURL)
    return destURL
  }
}
