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

/// Supported MediaPipe audio classification models bundled with this sample.
public enum AudioClassifierModelOption: String, CaseIterable, Identifiable, Sendable {
  case yamnet = "yamnet"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .yamnet: return "YAMNet"
    }
  }

  public var fullName: String {
    switch self {
    case .yamnet: return "YAMNet (AudioSet)"
    }
  }

  public var summary: String {
    switch self {
    case .yamnet:
      return "Pretrained 16 kHz classifier recognizing 521 everyday AudioSet sound events in 0.975 s windows."
    }
  }

  public var resourceName: String {
    switch self {
    case .yamnet: return "yamnet"
    }
  }

  public var fileExtension: String {
    switch self {
    case .yamnet: return "tflite"
    }
  }

  public var assetFilename: String {
    "\(resourceName).\(fileExtension)"
  }

  public var expectedSampleRate: Double {
    16_000.0
  }

  public var windowDurationSeconds: Double {
    0.975
  }

  public var windowDurationMs: Int {
    975
  }
}

/// Hardware execution delegate supported programmatically by MediaPipe Tasks.
public enum AudioClassifierDelegateOption: String, CaseIterable, Identifiable, Sendable {
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

  fileprivate var mediaPipeDelegate: Delegate {
    switch self {
    case .cpu: return .CPU
    case .gpu: return .GPU
    }
  }
}

/// MediaPipe AudioClassifier running mode for clip classification.
public enum AudioClassifierRunningModeOption: String, CaseIterable, Identifiable, Sendable {
  case audioClips = "AUDIO_CLIPS"
  case audioStream = "AUDIO_STREAM"

  public var id: String { rawValue }

  public var displayName: String {
    switch self {
    case .audioClips: return "Audio Clips"
    case .audioStream: return "Audio Stream"
    }
  }

  public var shortName: String {
    switch self {
    case .audioClips: return "Clips"
    case .audioStream: return "Stream"
    }
  }

  public var summary: String {
    switch self {
    case .audioClips:
      return "Classifies the full clip in one synchronous call via classify(audioClip:)."
    case .audioStream:
      return "Streams 0.975 s chunks asynchronously via classifyAsync(audioBlock:timestampInMilliseconds:) and AudioClassifierStreamDelegate."
    }
  }

  fileprivate var mediaPipeRunningMode: MediaPipeTasksAudio.RunningMode {
    switch self {
    case .audioClips: return .audioClips
    case .audioStream: return .audioStream
    }
  }
}

/// Window overlap cadence for live microphone streaming.
public enum AudioOverlapOption: Int, CaseIterable, Identifiable, Sendable {
  case zero = 0
  case twentyFive = 1
  case fifty = 2
  case seventyFive = 3

  public var id: Int { rawValue }

  public var label: String {
    switch self {
    case .zero: return "0%"
    case .twentyFive: return "25%"
    case .fifty: return "50%"
    case .seventyFive: return "75%"
    }
  }

  public var intervalSeconds: Double {
    max(0.1, 0.975 * (1.0 - Double(rawValue) * 0.25))
  }

  public var intervalMs: Int {
    Int((intervalSeconds * 1000.0).rounded())
  }
}

/// Decoded mono float32 PCM audio clip ready for classification and playback.
public struct DecodedAudioClip: Equatable, Sendable {
  public let samples: [Float]
  public let sampleRate: Double
  public let channelCount: Int

  public init(samples: [Float], sampleRate: Double, channelCount: Int = 1) {
    self.samples = samples
    self.sampleRate = sampleRate
    self.channelCount = channelCount
  }

  public var durationSeconds: Double {
    guard sampleRate > 0 else { return 0 }
    return Double(samples.count) / sampleRate
  }

  public var durationMs: Int {
    Int((durationSeconds * 1000.0).rounded())
  }

  /// Downsamples amplitude peaks into `barCount` normalized bars in `[0.08, 1.0]` for UI preview.
  public func waveformBars(count barCount: Int = 44) -> [Float] {
    guard barCount > 0, !samples.isEmpty else { return [] }
    let bucketSize = max(1, samples.count / barCount)
    var peaks = [Float](repeating: 0, count: barCount)
    var globalMax: Float = 0.001

    for bar in 0..<barCount {
      let start = min(bar * bucketSize, samples.count)
      let end = bar == barCount - 1 ? samples.count : min(start + bucketSize, samples.count)
      var peak: Float = 0
      if start < end {
        for idx in start..<end {
          let mag = abs(samples[idx])
          if mag > peak { peak = mag }
        }
      }
      peaks[bar] = peak
      if peak > globalMax { globalMax = peak }
    }

    return peaks.map { max(0.08, min(1.0, $0 / globalMax)) }
  }

  /// Encodes the mono PCM samples as an in-memory 16-bit RIFF/WAVE file for playback.
  public func makeWavData() -> Data {
    let sampleCount = samples.count
    let numChannels: UInt16 = 1
    let bitsPerSample: UInt16 = 16
    let sampleRateInt = UInt32(max(1, Int(sampleRate.rounded())))
    let byteRate = sampleRateInt * UInt32(numChannels) * UInt32(bitsPerSample / 8)
    let blockAlign = numChannels * (bitsPerSample / 8)
    let dataSize = UInt32(sampleCount * Int(blockAlign))
    let chunkSize = 36 + dataSize

    var data = Data(capacity: Int(44 + dataSize))
    data.append(contentsOf: "RIFF".utf8)
    data.appendLittleEndian(chunkSize)
    data.append(contentsOf: "WAVE".utf8)
    data.append(contentsOf: "fmt ".utf8)
    data.appendLittleEndian(UInt32(16))
    data.appendLittleEndian(UInt16(1)) // PCM
    data.appendLittleEndian(numChannels)
    data.appendLittleEndian(sampleRateInt)
    data.appendLittleEndian(byteRate)
    data.appendLittleEndian(blockAlign)
    data.appendLittleEndian(bitsPerSample)
    data.append(contentsOf: "data".utf8)
    data.appendLittleEndian(dataSize)

    for sample in samples {
      let clamped = max(-1.0, min(1.0, sample))
      let scaled = Int16((clamped * Float(Int16.max)).rounded())
      data.appendLittleEndian(scaled)
    }
    return data
  }
}

/// Ranked audio event category returned by MediaPipe `AudioClassifier`.
public struct AudioClassificationCategory: Identifiable, Equatable, Sendable {
  public let id: String
  public let index: Int
  public let categoryName: String
  public let displayName: String?
  public let score: Float

  public var label: String {
    if let displayName, !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
      return displayName
    }
    if !categoryName.isEmpty {
      return categoryName
    }
    return "Category \(index)"
  }

  public init(
    index: Int,
    categoryName: String,
    displayName: String?,
    score: Float
  ) {
    self.index = index
    self.categoryName = categoryName
    self.displayName = displayName
    self.score = score
    let resolvedLabel = (displayName?.isEmpty == false ? displayName! : categoryName)
    self.id = "\(index)-\(resolvedLabel)"
  }
}

/// Classification result for a single time window of an audio clip or live stream.
public struct AudioClassificationWindow: Identifiable, Equatable, Sendable {
  public var id: Int { windowIndex }
  public let windowIndex: Int
  /// Exact raw timestamp in milliseconds reported by MediaPipe (`timestampInMilliseconds`).
  public let timestampMs: Int
  /// Display-friendly start time in seconds for UI timeline chips.
  public let displayTimestampSeconds: Double
  public let categories: [AudioClassificationCategory]

  public var topCategory: AudioClassificationCategory? {
    categories.first
  }

  public init(
    windowIndex: Int,
    timestampMs: Int,
    displayTimestampSeconds: Double,
    categories: [AudioClassificationCategory]
  ) {
    self.windowIndex = windowIndex
    self.timestampMs = timestampMs
    self.displayTimestampSeconds = displayTimestampSeconds
    self.categories = categories
  }
}

/// Structured output of classifying an audio clip in either `AUDIO_CLIPS` or `AUDIO_STREAM` mode.
public struct AudioClassificationRunResult: Equatable, Sendable {
  public let runningMode: AudioClassifierRunningModeOption
  public let windows: [AudioClassificationWindow]
  public let inferenceTimeMs: Double
  public let sampleRate: Double
  public let durationMs: Int

  public init(
    runningMode: AudioClassifierRunningModeOption,
    windows: [AudioClassificationWindow],
    inferenceTimeMs: Double,
    sampleRate: Double,
    durationMs: Int
  ) {
    self.runningMode = runningMode
    self.windows = windows
    self.inferenceTimeMs = inferenceTimeMs
    self.sampleRate = sampleRate
    self.durationMs = durationMs
  }
}

/// Asynchronous events emitted during live microphone classification.
public enum LiveAudioEvent: Sendable {
  case classification(window: AudioClassificationWindow, inferenceTimeMs: Double)
  case error(String)
}

/// Errors surfaced by ``AudioClassifierHelper``.
public enum AudioClassifierError: LocalizedError, Equatable, Sendable {
  case gpuUnsupported(String)
  case initializationFailed(String)
  case classificationFailed(String)
  case audioLoadFailed(String)
  case microphonePermissionDenied
  case microphoneFailed(String)
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
    case .classificationFailed(let message):
      return message
    case .audioLoadFailed(let message):
      return message
    case .microphonePermissionDenied:
      return "Microphone access is required for live audio classification. Enable permission in Settings."
    case .microphoneFailed(let message):
      return message
    case .notInitialized:
      return "Audio Classifier is not initialized."
    }
  }
}

/// Thread-safe actor wrapper around `MediaPipeTasksAudio.AudioClassifier`.
///
/// Runs model loading, `AUDIO_CLIPS` inference, `AUDIO_STREAM` chunked clip streaming, and live
/// microphone recording off the main actor, surfacing explicit delegate errors without silently
/// falling back from GPU to CPU.
public actor AudioClassifierHelper {
  public static let bundledSpeechResourceName = "speech_16000_hz_mono"
  public static let bundledSpeechFileExtension = "wav"
  public static let bundledSpeechFilename = "speech_16000_hz_mono.wav"

  private struct Configuration: Equatable {
    let model: AudioClassifierModelOption
    let modelPath: String
    let delegate: AudioClassifierDelegateOption
    let runningMode: AudioClassifierRunningModeOption
    let maxResults: Int
    let scoreThreshold: Float
  }

  private let locator: SampleModelLocator
  private var activeConfig: Configuration?
  private var clipsClassifier: AudioClassifier?

  // Live microphone streaming state
  private var liveClassifier: AudioClassifier?
  private var liveStreamProxy: LiveStreamDelegateProxy?
  private var liveAudioRecord: AudioRecord?
  private var livePollingTask: Task<Void, Never>?
  private var liveWindowCounter: Int = 0
  private var lastLiveTimestampMs: Int = -1

  public init(locator: SampleModelLocator = SampleModelLocator()) {
    self.locator = locator
  }

  /// Loads or reloads `AudioClassifier` with the requested options and returns load time in ms.
  public func load(
    model: AudioClassifierModelOption = .yamnet,
    delegate: AudioClassifierDelegateOption = .cpu,
    runningMode: AudioClassifierRunningModeOption = .audioClips,
    maxResults: Int = 4,
    scoreThreshold: Float = 0.0
  ) throws -> Double {
    closeClipsClassifier()

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: model.resourceName,
        fileExtension: model.fileExtension
      )
    } catch {
      throw AudioClassifierError.initializationFailed(error.localizedDescription)
    }

    let config = Configuration(
      model: model,
      modelPath: modelURL.path,
      delegate: delegate,
      runningMode: runningMode,
      maxResults: maxResults,
      scoreThreshold: scoreThreshold
    )

    let start = ContinuousClock.now
    switch runningMode {
    case .audioClips:
      clipsClassifier = try createClassifier(config: config, streamDelegate: nil)
    case .audioStream:
      // Validate initialization immediately (including explicit GPU rejection) without retaining a
      // closed stream instance; each stream clip classification creates a clean task instance.
      let probeDelegate = ClipStreamCollectorProxy(
        windowDurationSeconds: model.windowDurationSeconds
      )
      let probe = try createClassifier(config: config, streamDelegate: probeDelegate)
      try? probe.close()
    }

    activeConfig = config
    return Self.elapsedMilliseconds(since: start)
  }

  /// Releases all active MediaPipe classifier and recording resources.
  public func close() {
    stopLiveMicrophone()
    closeClipsClassifier()
    activeConfig = nil
  }

  /// Locates and decodes the bundled `speech_16000_hz_mono.wav` sample clip.
  public func loadBundledSpeechClip() throws -> (clip: DecodedAudioClip, url: URL) {
    let url: URL
    do {
      url = try locator.locate(
        resourceName: Self.bundledSpeechResourceName,
        fileExtension: Self.bundledSpeechFileExtension
      )
    } catch {
      throw AudioClassifierError.audioLoadFailed(error.localizedDescription)
    }
    let clip = try Self.decodeAudioFile(at: url)
    return (clip, url)
  }

  /// Classifies a decoded audio clip using the currently configured running mode
  /// (`AUDIO_CLIPS` or `AUDIO_STREAM`).
  public func classifyClip(_ clip: DecodedAudioClip) async throws -> AudioClassificationRunResult {
    guard let config = activeConfig else {
      throw AudioClassifierError.notInitialized
    }
    guard !clip.samples.isEmpty, clip.sampleRate > 0 else {
      throw AudioClassifierError.audioLoadFailed("Audio clip is empty.")
    }

    switch config.runningMode {
    case .audioClips:
      return try classifyClipSynchronously(clip, config: config)
    case .audioStream:
      return try await classifyClipAsStream(clip, config: config)
    }
  }

  /// Decodes and classifies an audio file URL using the currently configured running mode.
  public func classifyAudioFile(at url: URL) async throws -> AudioClassificationRunResult {
    let clip = try Self.decodeAudioFile(at: url)
    return try await classifyClip(clip)
  }

  /// Starts live microphone audio streaming in `.audioStream` mode.
  public func startLiveMicrophone(
    model: AudioClassifierModelOption = .yamnet,
    delegate: AudioClassifierDelegateOption = .cpu,
    maxResults: Int = 4,
    scoreThreshold: Float = 0.0,
    overlap: AudioOverlapOption = .fifty,
    onEvent: @escaping @Sendable (LiveAudioEvent) -> Void
  ) throws {
    stopLiveMicrophone()

    let modelURL: URL
    do {
      modelURL = try locator.locate(
        resourceName: model.resourceName,
        fileExtension: model.fileExtension
      )
    } catch {
      throw AudioClassifierError.initializationFailed(error.localizedDescription)
    }

    let config = Configuration(
      model: model,
      modelPath: modelURL.path,
      delegate: delegate,
      runningMode: .audioStream,
      maxResults: maxResults,
      scoreThreshold: scoreThreshold
    )

    let proxy = LiveStreamDelegateProxy(
      windowDurationSeconds: model.windowDurationSeconds,
      onEvent: onEvent
    )
    let classifier = try createClassifier(config: config, streamDelegate: proxy)

    let sampleRate = model.expectedSampleRate
    let bufferLength = UInt(sampleRate * 2.0)
    let audioFormat = AudioDataFormat(channelCount: 1, sampleRate: sampleRate)
    let audioRecord: AudioRecord
    do {
      audioRecord = try AudioRecord(
        audioDataFormat: audioFormat,
        bufferLength: bufferLength
      )
      try audioRecord.startRecording()
    } catch {
      try? classifier.close()
      throw AudioClassifierError.microphoneFailed(
        "Could not start microphone capture: \(error.localizedDescription)"
      )
    }

    liveClassifier = classifier
    liveStreamProxy = proxy
    liveAudioRecord = audioRecord
    liveWindowCounter = 0
    lastLiveTimestampMs = -1

    let intervalNs = UInt64(max(100.0, overlap.intervalSeconds * 1000.0) * 1_000_000.0)
    let windowSampleCount = UInt((sampleRate * model.windowDurationSeconds).rounded())
    let startInstant = ContinuousClock.now

    livePollingTask = Task { [weak self] in
      // Allow a brief initial fill so the first read contains real microphone samples.
      try? await Task.sleep(nanoseconds: min(intervalNs, 300_000_000))
      while !Task.isCancelled {
        guard let self else { break }
        await self.pollLiveMicrophoneOnce(
          sampleRate: sampleRate,
          windowSampleCount: windowSampleCount,
          streamStart: startInstant,
          onEvent: onEvent
        )
        try? await Task.sleep(nanoseconds: intervalNs)
      }
    }
  }

  /// Stops live microphone recording and closes the live stream classifier.
  public func stopLiveMicrophone() {
    livePollingTask?.cancel()
    livePollingTask = nil

    if let record = liveAudioRecord {
      try? record.stop()
      liveAudioRecord = nil
    }
    if let classifier = liveClassifier {
      try? classifier.close()
      liveClassifier = nil
    }
    liveStreamProxy = nil
    liveWindowCounter = 0
    lastLiveTimestampMs = -1
  }

  // MARK: - Static Audio Decoding & Tone Generation

  /// Decodes any iOS-supported audio file (WAV, M4A, MP3, AIFF, CAF) into mono Float32 PCM.
  public static func decodeAudioFile(at url: URL) throws -> DecodedAudioClip {
    let audioFile: AVAudioFile
    do {
      audioFile = try AVAudioFile(forReading: url)
    } catch {
      throw AudioClassifierError.audioLoadFailed(
        "Could not open audio file '\(url.lastPathComponent)': \(error.localizedDescription)"
      )
    }

    let format = audioFile.processingFormat
    let sampleRate = format.sampleRate
    let channelCount = Int(format.channelCount)
    guard sampleRate > 0, channelCount > 0 else {
      throw AudioClassifierError.audioLoadFailed("Audio file has an unsupported audio format.")
    }

    let chunkCapacity: AVAudioFrameCount = 16_384
    guard let buffer = AVAudioPCMBuffer(pcmFormat: format, frameCapacity: chunkCapacity) else {
      throw AudioClassifierError.audioLoadFailed("Unable to allocate PCM buffer.")
    }

    var monoSamples: [Float] = []
    if audioFile.length > 0 {
      monoSamples.reserveCapacity(Int(audioFile.length))
    }

    while true {
      if audioFile.length > 0 {
        let remaining = audioFile.length - audioFile.framePosition
        if remaining <= 0 { break }
        let framesToRead = AVAudioFrameCount(min(Int64(chunkCapacity), remaining))
        buffer.frameLength = 0
        do {
          try audioFile.read(into: buffer, frameCount: framesToRead)
        } catch {
          throw AudioClassifierError.audioLoadFailed(
            "Failed while reading '\(url.lastPathComponent)': \(error.localizedDescription)"
          )
        }
      } else {
        buffer.frameLength = 0
        do {
          try audioFile.read(into: buffer, frameCount: chunkCapacity)
        } catch {
          throw AudioClassifierError.audioLoadFailed(
            "Failed while reading '\(url.lastPathComponent)': \(error.localizedDescription)"
          )
        }
      }

      let framesRead = Int(buffer.frameLength)
      if framesRead == 0 { break }
      guard let floatChannelData = buffer.floatChannelData else {
        throw AudioClassifierError.audioLoadFailed("Decoded buffer did not contain float PCM data.")
      }

      if channelCount == 1 {
        let ptr = UnsafeBufferPointer(start: floatChannelData[0], count: framesRead)
        monoSamples.append(contentsOf: ptr)
      } else {
        let scale = 1.0 / Float(channelCount)
        for frame in 0..<framesRead {
          var sum: Float = 0
          for ch in 0..<channelCount {
            sum += floatChannelData[ch][frame]
          }
          monoSamples.append(max(-1.0, min(1.0, sum * scale)))
        }
      }
    }

    guard !monoSamples.isEmpty else {
      throw AudioClassifierError.audioLoadFailed("Audio file contains no playable samples.")
    }

    return DecodedAudioClip(samples: monoSamples, sampleRate: sampleRate, channelCount: 1)
  }

  /// Generates a clean mono sine wave clip (useful for quick interactive audio testing).
  public static func generateSineTone(
    frequencyHz: Double = 440.0,
    durationSeconds: Double = 2.0,
    sampleRate: Double = 16_000.0,
    amplitude: Float = 0.45
  ) -> DecodedAudioClip {
    let count = max(1, Int((durationSeconds * sampleRate).rounded()))
    let angularFrequency = 2.0 * Double.pi * frequencyHz / sampleRate
    var samples = [Float](repeating: 0, count: count)
    for i in 0..<count {
      samples[i] = amplitude * Float(sin(angularFrequency * Double(i)))
    }
    return DecodedAudioClip(samples: samples, sampleRate: sampleRate, channelCount: 1)
  }

  // MARK: - Private Classification Helpers

  private func createClassifier(
    config: Configuration,
    streamDelegate: (any AudioClassifierStreamDelegate)?
  ) throws -> AudioClassifier {
    let options = AudioClassifierOptions()
    options.baseOptions.modelAssetPath = config.modelPath
    options.baseOptions.delegate = config.delegate.mediaPipeDelegate
    options.runningMode = config.runningMode.mediaPipeRunningMode
    options.maxResults = config.maxResults
    if config.scoreThreshold > 0 {
      options.scoreThreshold = config.scoreThreshold
    }
    if config.runningMode == .audioStream {
      options.audioClassifierStreamDelegate = streamDelegate
    }

    do {
      return try AudioClassifier(options: options)
    } catch {
      if config.delegate == .gpu {
        throw AudioClassifierError.gpuUnsupported(error.localizedDescription)
      }
      throw AudioClassifierError.initializationFailed(error.localizedDescription)
    }
  }

  private func closeClipsClassifier() {
    if let classifier = clipsClassifier {
      try? classifier.close()
      clipsClassifier = nil
    }
  }

  private func classifyClipSynchronously(
    _ clip: DecodedAudioClip,
    config: Configuration
  ) throws -> AudioClassificationRunResult {
    let classifier: AudioClassifier
    if let cached = clipsClassifier {
      classifier = cached
    } else {
      let created = try createClassifier(config: config, streamDelegate: nil)
      clipsClassifier = created
      classifier = created
    }

    let format = AudioDataFormat(channelCount: 1, sampleRate: clip.sampleRate)
    let audioData = AudioData(format: format, sampleCount: UInt(clip.samples.count))
    let floatBuffer: MPPFloatBuffer = clip.samples.withUnsafeBufferPointer { ptr in
      MPPFloatBuffer(data: ptr.baseAddress, length: UInt(ptr.count))
    }

    let start = ContinuousClock.now
    let result: AudioClassifierResult
    do {
      try audioData.load(buffer: floatBuffer, offset: 0, length: UInt(clip.samples.count))
      result = try classifier.classify(audioClip: audioData)
    } catch {
      if config.delegate == .gpu {
        throw AudioClassifierError.gpuUnsupported(error.localizedDescription)
      }
      throw AudioClassifierError.classificationFailed(error.localizedDescription)
    }
    let elapsedMs = Self.elapsedMilliseconds(since: start)

    let windows = result.classificationResults.enumerated().map { index, classificationResult in
      let rawTs = classificationResult.timestampInMilliseconds
      let fallbackMs = Int((Double(index) * config.model.windowDurationSeconds * 1000.0).rounded())
      let effectiveTs = rawTs >= 0 ? rawTs : fallbackMs
      let displaySec = Self.displaySeconds(
        rawTimestampMs: effectiveTs,
        windowIndex: index,
        windowDurationSeconds: config.model.windowDurationSeconds
      )
      return AudioClassificationWindow(
        windowIndex: index,
        timestampMs: effectiveTs,
        displayTimestampSeconds: displaySec,
        categories: Self.extractCategories(from: classificationResult)
      )
    }

    return AudioClassificationRunResult(
      runningMode: .audioClips,
      windows: windows,
      inferenceTimeMs: elapsedMs,
      sampleRate: clip.sampleRate,
      durationMs: clip.durationMs
    )
  }

  private func classifyClipAsStream(
    _ clip: DecodedAudioClip,
    config: Configuration
  ) async throws -> AudioClassificationRunResult {
    let collector = ClipStreamCollectorProxy(
      windowDurationSeconds: config.model.windowDurationSeconds
    )
    let streamClassifier = try createClassifier(config: config, streamDelegate: collector)

    let format = AudioDataFormat(channelCount: 1, sampleRate: clip.sampleRate)
    let chunkSize = max(1, Int(clip.sampleRate * config.model.windowDurationSeconds))
    let expectedWindowCount = (clip.samples.count + chunkSize - 1) / chunkSize

    let start = ContinuousClock.now
    do {
      var offset = 0
      while offset < clip.samples.count {
        let end = min(offset + chunkSize, clip.samples.count)
        let length = end - offset
        let slice = clip.samples[offset..<end]
        let block = AudioData(format: format, sampleCount: UInt(length))
        let floatBuffer: MPPFloatBuffer = slice.withUnsafeBufferPointer { ptr in
          MPPFloatBuffer(data: ptr.baseAddress, length: UInt(ptr.count))
        }
        try block.load(buffer: floatBuffer, offset: 0, length: UInt(length))
        let timestampMs = Int((Double(offset) * 1000.0 / clip.sampleRate).rounded())
        try streamClassifier.classifyAsync(audioBlock: block, timestampInMilliseconds: timestampMs)
        offset = end
      }
      // Close the stream classifier so MediaPipe flushes the final partial window.
      try streamClassifier.close()
    } catch {
      try? streamClassifier.close()
      if config.delegate == .gpu {
        throw AudioClassifierError.gpuUnsupported(error.localizedDescription)
      }
      throw AudioClassifierError.classificationFailed(error.localizedDescription)
    }

    // Drain any remaining delegate callbacks queued on MediaPipe's private serial queue.
    let drainDeadline = ContinuousClock.now + .milliseconds(1_500)
    while collector.windowCount < expectedWindowCount
      && collector.firstError == nil
      && ContinuousClock.now < drainDeadline
    {
      try? await Task.sleep(nanoseconds: 10_000_000)
    }

    let elapsedMs = Self.elapsedMilliseconds(since: start)

    if let streamError = collector.firstError {
      if config.delegate == .gpu {
        throw AudioClassifierError.gpuUnsupported(streamError.localizedDescription)
      }
      throw AudioClassifierError.classificationFailed(streamError.localizedDescription)
    }

    let windows = collector.snapshotWindows()
    guard !windows.isEmpty else {
      throw AudioClassifierError.classificationFailed(
        "Audio stream finished without returning any classification windows."
      )
    }

    return AudioClassificationRunResult(
      runningMode: .audioStream,
      windows: windows,
      inferenceTimeMs: elapsedMs,
      sampleRate: clip.sampleRate,
      durationMs: clip.durationMs
    )
  }

  private func pollLiveMicrophoneOnce(
    sampleRate: Double,
    windowSampleCount: UInt,
    streamStart: ContinuousClock.Instant,
    onEvent: @escaping @Sendable (LiveAudioEvent) -> Void
  ) {
    guard let classifier = liveClassifier, let record = liveAudioRecord else { return }
    do {
      let format = AudioDataFormat(channelCount: 1, sampleRate: sampleRate)
      let audioData = AudioData(format: format, sampleCount: windowSampleCount)
      try audioData.load(audioRecord: record)

      let elapsedMs = Int(Self.elapsedMilliseconds(since: streamStart).rounded())
      let timestampMs = max(elapsedMs, lastLiveTimestampMs + 1)
      lastLiveTimestampMs = timestampMs
      liveWindowCounter += 1

      liveStreamProxy?.markSubmission(startInstant: ContinuousClock.now, windowIndex: liveWindowCounter - 1)
      try classifier.classifyAsync(audioBlock: audioData, timestampInMilliseconds: timestampMs)
    } catch {
      onEvent(.error(error.localizedDescription))
    }
  }

  fileprivate static func extractCategories(
    from classificationResult: ClassificationResult
  ) -> [AudioClassificationCategory] {
    guard let head = classificationResult.classifications.first else { return [] }
    return head.categories
      .sorted { $0.score > $1.score }
      .map { category in
        AudioClassificationCategory(
          index: category.index,
          categoryName: category.categoryName ?? "",
          displayName: category.displayName,
          score: category.score
        )
      }
  }

  fileprivate static func displaySeconds(
    rawTimestampMs: Int,
    windowIndex: Int,
    windowDurationSeconds: Double
  ) -> Double {
    // In AUDIO_STREAM mode, closing flushes the partial trailing chunk at Timestamp::Max().
    if rawTimestampMs >= 0 && rawTimestampMs < 3_600_000 {
      return Double(rawTimestampMs) / 1000.0
    }
    return Double(windowIndex) * windowDurationSeconds
  }

  fileprivate static func elapsedMilliseconds(since start: ContinuousClock.Instant) -> Double {
    let duration = start.duration(to: .now)
    return Double(duration.components.seconds) * 1_000.0
      + Double(duration.components.attoseconds) / 1e15
  }
}

// MARK: - MediaPipe Stream Delegate Proxies

private final class ClipStreamCollectorProxy: NSObject, AudioClassifierStreamDelegate, @unchecked Sendable {
  private let lock = NSLock()
  private let windowDurationSeconds: Double
  private var collectedWindows: [AudioClassificationWindow] = []
  private var recordedError: Error?

  init(windowDurationSeconds: Double) {
    self.windowDurationSeconds = windowDurationSeconds
  }

  var windowCount: Int {
    lock.lock()
    defer { lock.unlock() }
    return collectedWindows.count
  }

  var firstError: Error? {
    lock.lock()
    defer { lock.unlock() }
    return recordedError
  }

  func snapshotWindows() -> [AudioClassificationWindow] {
    lock.lock()
    defer { lock.unlock() }
    return collectedWindows
  }

  func audioClassifier(
    _ audioClassifier: AudioClassifier,
    didFinishClassification result: AudioClassifierResult?,
    timestampInMilliseconds: Int,
    error: (any Error)?
  ) {
    lock.lock()
    defer { lock.unlock() }

    if let error {
      if recordedError == nil {
        recordedError = error
      }
      return
    }
    guard let result else { return }

    for classificationResult in result.classificationResults {
      let index = collectedWindows.count
      let rawTs =
        classificationResult.timestampInMilliseconds >= 0
        ? classificationResult.timestampInMilliseconds
        : timestampInMilliseconds
      let displaySec = AudioClassifierHelper.displaySeconds(
        rawTimestampMs: rawTs,
        windowIndex: index,
        windowDurationSeconds: windowDurationSeconds
      )
      let categories = AudioClassifierHelper.extractCategories(from: classificationResult)
      collectedWindows.append(
        AudioClassificationWindow(
          windowIndex: index,
          timestampMs: rawTs,
          displayTimestampSeconds: displaySec,
          categories: categories
        )
      )
    }
  }
}

private final class LiveStreamDelegateProxy: NSObject, AudioClassifierStreamDelegate, @unchecked Sendable {
  private let lock = NSLock()
  private let windowDurationSeconds: Double
  private let onEvent: @Sendable (LiveAudioEvent) -> Void
  private var lastStartInstant: ContinuousClock.Instant = .now
  private var nextWindowIndex: Int = 0

  init(
    windowDurationSeconds: Double,
    onEvent: @escaping @Sendable (LiveAudioEvent) -> Void
  ) {
    self.windowDurationSeconds = windowDurationSeconds
    self.onEvent = onEvent
  }

  func markSubmission(startInstant: ContinuousClock.Instant, windowIndex: Int) {
    lock.lock()
    lastStartInstant = startInstant
    nextWindowIndex = windowIndex
    lock.unlock()
  }

  func audioClassifier(
    _ audioClassifier: AudioClassifier,
    didFinishClassification result: AudioClassifierResult?,
    timestampInMilliseconds: Int,
    error: (any Error)?
  ) {
    if let error {
      onEvent(.error(error.localizedDescription))
      return
    }
    guard let result, let classificationResult = result.classificationResults.first else {
      return
    }

    lock.lock()
    let startInstant = lastStartInstant
    let windowIdx = nextWindowIndex
    lock.unlock()

    let latencyMs = AudioClassifierHelper.elapsedMilliseconds(since: startInstant)
    let rawTs =
      classificationResult.timestampInMilliseconds >= 0
      ? classificationResult.timestampInMilliseconds
      : timestampInMilliseconds
    let displaySec = AudioClassifierHelper.displaySeconds(
      rawTimestampMs: rawTs,
      windowIndex: windowIdx,
      windowDurationSeconds: windowDurationSeconds
    )
    let window = AudioClassificationWindow(
      windowIndex: windowIdx,
      timestampMs: rawTs,
      displayTimestampSeconds: displaySec,
      categories: AudioClassifierHelper.extractCategories(from: classificationResult)
    )
    onEvent(.classification(window: window, inferenceTimeMs: max(0.1, latencyMs)))
  }
}

private extension Data {
  mutating func appendLittleEndian<T: FixedWidthInteger>(_ value: T) {
    var little = value.littleEndian
    Swift.withUnsafeBytes(of: &little) { bytes in
      append(contentsOf: bytes)
    }
  }
}
