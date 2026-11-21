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

/// Top-level workspace tab in the Audio Classifier sample.
public enum AudioClassifierInputTab: String, CaseIterable, Identifiable, Sendable {
  case clip = "Audio Clip"
  case microphone = "Live Mic"

  public var id: String { rawValue }
}

/// Selectable audio clip source for clip classification.
public enum AudioClipSource: Identifiable, Equatable, Sendable {
  case bundledSpeech
  case syntheticTone
  case customFile(url: URL, displayName: String)

  public var id: String {
    switch self {
    case .bundledSpeech: return "speech"
    case .syntheticTone: return "tone"
    case .customFile(let url, _): return "custom_\(url.lastPathComponent)"
    }
  }

  public var label: String {
    switch self {
    case .bundledSpeech: return "Speech (4.3 s)"
    case .syntheticTone: return "440 Hz Tone"
    case .customFile(_, let displayName): return displayName
    }
  }

  public var detailName: String {
    switch self {
    case .bundledSpeech: return AudioClassifierHelper.bundledSpeechFilename
    case .syntheticTone: return "Synthetic 440 Hz sine wave"
    case .customFile(let url, _): return url.lastPathComponent
    }
  }

  public static var builtInPresets: [AudioClipSource] {
    [.bundledSpeech, .syntheticTone]
  }
}

/// Current iOS microphone authorization status.
public enum MicrophonePermissionState: Equatable, Sendable {
  case undetermined
  case granted
  case denied
}

/// Observable MainActor ViewModel driving the MediaPipe Audio Classifier sample.
@Observable
@MainActor
public final class AudioClassifierViewModel {
  public private(set) var selectedTab: AudioClassifierInputTab = .clip
  public private(set) var selectedClipSource: AudioClipSource = .bundledSpeech
  public private(set) var selectedModel: AudioClassifierModelOption
  public private(set) var selectedDelegate: AudioClassifierDelegateOption
  public private(set) var clipRunningMode: AudioClassifierRunningModeOption
  public private(set) var maxResults: Int
  public private(set) var scoreThreshold: Float
  public private(set) var liveOverlap: AudioOverlapOption

  public private(set) var isModelLoading: Bool = false
  public private(set) var isClassifying: Bool = false
  public private(set) var isReady: Bool = false
  public private(set) var isPlayingClip: Bool = false
  public private(set) var isListeningMic: Bool = false
  public private(set) var microphonePermission: MicrophonePermissionState = .undetermined

  public private(set) var modelLoadTimeMs: Double?
  public private(set) var clipResult: AudioClassificationRunResult?
  public private(set) var activeWindowIndex: Int = 0
  public private(set) var waveformBars: [Float] = []
  public private(set) var activeClipDurationMs: Int?
  public private(set) var activeClipSampleRate: Double?

  public private(set) var liveWindow: AudioClassificationWindow?
  public private(set) var liveInferenceTimeMs: Double?

  public private(set) var errorMessage: String?
  public private(set) var isGpuError: Bool = false

  private let helper: AudioClassifierHelper
  private let autoClassifyOnInitialLoad: Bool
  private var hasCompletedInitialClassification: Bool = false
  private var cachedDecodedClip: DecodedAudioClip?
  private var cachedClipSource: AudioClipSource?
  private var customDecodedClip: DecodedAudioClip?
  private var activeTask: Task<Void, Never>?
  private var playbackTask: Task<Void, Never>?
  private var audioPlayer: AVAudioPlayer?
  private var generation: UInt64 = 0

  public var isBusy: Bool {
    isModelLoading || isClassifying
  }

  public var canClassifyClip: Bool {
    !isBusy && isReady
  }

  public var activeClipWindow: AudioClassificationWindow? {
    guard let windows = clipResult?.windows, !windows.isEmpty else { return nil }
    let clamped = min(max(activeWindowIndex, 0), windows.count - 1)
    return windows[clamped]
  }

  public var displayedCategories: [AudioClassificationCategory] {
    switch selectedTab {
    case .clip:
      return activeClipWindow?.categories ?? []
    case .microphone:
      return liveWindow?.categories ?? []
    }
  }

  public var hasResult: Bool {
    switch selectedTab {
    case .clip:
      return clipResult != nil
    case .microphone:
      return liveWindow != nil
    }
  }

  public var latestInferenceTimeMs: Double? {
    switch selectedTab {
    case .clip:
      return clipResult?.inferenceTimeMs
    case .microphone:
      return liveInferenceTimeMs
    }
  }

  public var clipPresetOptions: [AudioClipSource] {
    var items = AudioClipSource.builtInPresets
    if case .customFile = selectedClipSource {
      items.append(selectedClipSource)
    }
    return items
  }

  public var headerSubtitle: String {
    let modeText = selectedTab == .clip ? clipRunningMode.shortName : "Live"
    return "\(selectedModel.displayName) · \(modeText) · Top \(maxResults) · \(selectedDelegate.shortName)"
  }

  public var statusBadge: MediaPipeStatusStrip.Status {
    if isModelLoading {
      return .loading("Loading \(selectedModel.displayName)…")
    }
    if isClassifying {
      return .loading(
        clipRunningMode == .audioStream ? "Streaming audio clip…" : "Classifying audio clip…"
      )
    }
    if isListeningMic {
      return .ready("Listening · \(liveOverlap.label) overlap")
    }
    if errorMessage != nil && !isReady {
      return .failed("Classifier unavailable")
    }
    if isReady {
      return .ready("Ready · On-Device")
    }
    return .loading("Initializing…")
  }

  public init(
    model: AudioClassifierModelOption = .yamnet,
    delegate: AudioClassifierDelegateOption = .cpu,
    clipRunningMode: AudioClassifierRunningModeOption = .audioClips,
    maxResults: Int = 4,
    scoreThreshold: Float = 0.0,
    liveOverlap: AudioOverlapOption = .fifty,
    locator: SampleModelLocator = SampleModelLocator(),
    autoLoad: Bool = true,
    autoClassifyOnInitialLoad: Bool = false
  ) {
    self.selectedModel = model
    self.selectedDelegate = delegate
    self.clipRunningMode = clipRunningMode
    self.maxResults = min(max(maxResults, 1), 5)
    self.scoreThreshold = min(max(scoreThreshold, 0.0), 0.9)
    self.liveOverlap = liveOverlap
    self.helper = AudioClassifierHelper(locator: locator)
    self.autoClassifyOnInitialLoad = autoClassifyOnInitialLoad
    self.microphonePermission = Self.currentMicrophonePermission()

    if autoLoad {
      scheduleReload(reclassifyIfClipTab: autoClassifyOnInitialLoad)
    }
  }

  // MARK: - Synchronous UI Actions

  public func selectTab(_ tab: AudioClassifierInputTab) {
    guard selectedTab != tab else { return }
    stopPlayback()
    if isListeningMic {
      Task { [weak self] in
        await self?.stopMicrophone()
      }
    }
    selectedTab = tab
    errorMessage = nil
    microphonePermission = Self.currentMicrophonePermission()
  }

  public func selectClipSource(_ source: AudioClipSource) {
    guard selectedClipSource != source else { return }
    stopPlayback()
    selectedClipSource = source
    clipResult = nil
    activeWindowIndex = 0
    errorMessage = nil

    if isReady && !isBusy {
      triggerClassifyCurrentClip()
    }
  }

  public func selectWindow(_ index: Int) {
    guard let count = clipResult?.windows.count, index >= 0, index < count else { return }
    activeWindowIndex = index
  }

  public func selectClipRunningMode(_ mode: AudioClassifierRunningModeOption) {
    guard clipRunningMode != mode else { return }
    stopPlayback()
    clipRunningMode = mode
    clipResult = nil
    activeWindowIndex = 0
    scheduleReload(reclassifyIfClipTab: selectedTab == .clip)
  }

  public func setMaxResults(_ value: Int) {
    let clamped = min(max(value, 1), 5)
    guard maxResults != clamped else { return }
    let wasListening = isListeningMic
    stopPlayback()
    maxResults = clamped
    clipResult = nil
    liveWindow = nil
    activeWindowIndex = 0
    if wasListening {
      restartMicrophone()
    } else {
      scheduleReload(reclassifyIfClipTab: selectedTab == .clip)
    }
  }

  public func setScoreThreshold(_ value: Float) {
    let rounded = (min(max(value, 0.0), 0.9) * 10.0).rounded() / 10.0
    guard abs(scoreThreshold - rounded) > 0.001 else { return }
    let wasListening = isListeningMic
    stopPlayback()
    scoreThreshold = rounded
    clipResult = nil
    liveWindow = nil
    activeWindowIndex = 0
    if wasListening {
      restartMicrophone()
    } else {
      scheduleReload(reclassifyIfClipTab: selectedTab == .clip)
    }
  }

  public func setLiveOverlap(_ option: AudioOverlapOption) {
    guard liveOverlap != option else { return }
    liveOverlap = option
    if isListeningMic {
      restartMicrophone()
    }
  }

  public func selectDelegate(_ delegate: AudioClassifierDelegateOption) {
    guard selectedDelegate != delegate else { return }
    stopPlayback()
    selectedDelegate = delegate
    clipResult = nil
    liveWindow = nil
    scheduleReload(reclassifyIfClipTab: selectedTab == .clip)
  }

  public func switchToCpuDelegate() {
    if selectedDelegate == .cpu {
      scheduleReload(reclassifyIfClipTab: selectedTab == .clip)
    } else {
      selectDelegate(.cpu)
    }
  }

  public func retryInitialization() {
    scheduleReload(reclassifyIfClipTab: selectedTab == .clip)
  }

  public func triggerClassifyCurrentClip() {
    guard canClassifyClip else { return }
    stopPlayback()
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      _ = await self?.classifyCurrentClip()
    }
  }

  public func importCustomAudioFile(from securityScopedURL: URL) {
    stopPlayback()
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let accessed = securityScopedURL.startAccessingSecurityScopedResource()
      defer {
        if accessed {
          securityScopedURL.stopAccessingSecurityScopedResource()
        }
      }
      do {
        let decoded = try AudioClassifierHelper.decodeAudioFile(at: securityScopedURL)
        let displayName = securityScopedURL.deletingPathExtension().lastPathComponent
        self.customDecodedClip = decoded
        self.cachedDecodedClip = decoded
        self.selectedClipSource = .customFile(
          url: securityScopedURL,
          displayName: displayName.isEmpty ? securityScopedURL.lastPathComponent : displayName
        )
        self.cachedClipSource = self.selectedClipSource
        self.waveformBars = decoded.waveformBars()
        self.activeClipDurationMs = decoded.durationMs
        self.activeClipSampleRate = decoded.sampleRate
        self.clipResult = nil
        self.activeWindowIndex = 0
        self.errorMessage = nil
        if self.isReady {
          _ = await self.classifyCurrentClip()
        }
      } catch {
        self.errorMessage = error.localizedDescription
      }
    }
  }

  public func togglePlayback() {
    if isPlayingClip {
      stopPlayback()
    } else {
      startPlayback()
    }
  }

  public func stopPlayback() {
    playbackTask?.cancel()
    playbackTask = nil
    audioPlayer?.stop()
    audioPlayer = nil
    isPlayingClip = false
  }

  public func toggleMicrophone() {
    if isListeningMic {
      Task { [weak self] in
        await self?.stopMicrophone()
      }
    } else {
      Task { [weak self] in
        await self?.startMicrophone()
      }
    }
  }

  public func handleSceneDisappear() {
    stopPlayback()
    if isListeningMic {
      Task { [weak self] in
        await self?.stopMicrophone()
      }
    }
  }

  // MARK: - Awaitable Operations (for Tests & Validation Hosts)

  /// Loads or reloads the classifier with the current configuration and awaits completion.
  @discardableResult
  public func loadClassifier() async -> Bool {
    generation &+= 1
    let currentGen = generation
    let model = selectedModel
    let delegate = selectedDelegate
    let runningMode = clipRunningMode
    let maxRes = maxResults
    let threshold = scoreThreshold

    isModelLoading = true
    isClassifying = false
    isReady = false
    errorMessage = nil
    isGpuError = false

    do {
      let loadMs = try await helper.load(
        model: model,
        delegate: delegate,
        runningMode: runningMode,
        maxResults: maxRes,
        scoreThreshold: threshold
      )
      guard generation == currentGen else { return false }
      modelLoadTimeMs = loadMs
      isModelLoading = false
      isReady = true
      return true
    } catch let error as AudioClassifierError {
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

  /// Updates configuration fields in one batch and reloads the classifier once.
  @discardableResult
  public func configure(
    model: AudioClassifierModelOption? = nil,
    delegate: AudioClassifierDelegateOption? = nil,
    clipRunningMode: AudioClassifierRunningModeOption? = nil,
    maxResults: Int? = nil,
    scoreThreshold: Float? = nil,
    liveOverlap: AudioOverlapOption? = nil
  ) async -> Bool {
    activeTask?.cancel()
    stopPlayback()
    if isListeningMic {
      await stopMicrophone()
    }
    if let model { selectedModel = model }
    if let delegate { selectedDelegate = delegate }
    if let clipRunningMode { self.clipRunningMode = clipRunningMode }
    if let maxResults { self.maxResults = min(max(maxResults, 1), 5) }
    if let scoreThreshold { self.scoreThreshold = min(max(scoreThreshold, 0.0), 0.9) }
    if let liveOverlap { self.liveOverlap = liveOverlap }
    clipResult = nil
    liveWindow = nil
    activeWindowIndex = 0
    return await loadClassifier()
  }

  /// Decodes (if needed) and classifies the currently selected clip source, awaiting completion.
  @discardableResult
  public func classifyCurrentClip() async -> AudioClassificationRunResult? {
    guard canClassifyClip else { return nil }
    generation &+= 1
    let currentGen = generation
    let source = selectedClipSource

    isClassifying = true
    errorMessage = nil
    isGpuError = false

    do {
      let decoded = try await resolveDecodedClip(for: source)
      guard generation == currentGen else { return nil }
      cachedDecodedClip = decoded
      cachedClipSource = source
      waveformBars = decoded.waveformBars()
      activeClipDurationMs = decoded.durationMs
      activeClipSampleRate = decoded.sampleRate

      let runResult = try await helper.classifyClip(decoded)
      guard generation == currentGen else { return nil }

      isClassifying = false
      clipResult = runResult
      activeWindowIndex =
        runResult.windows.firstIndex(where: { !$0.categories.isEmpty }) ?? 0
      return runResult
    } catch let error as AudioClassifierError {
      guard generation == currentGen else { return nil }
      isClassifying = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isClassifying = false
      errorMessage = error.localizedDescription
      isGpuError = false
      return nil
    }
  }

  /// Decodes and classifies an explicit audio file URL using the current configuration.
  @discardableResult
  public func classifyAudioFile(at url: URL) async -> AudioClassificationRunResult? {
    guard canClassifyClip else { return nil }
    generation &+= 1
    let currentGen = generation

    isClassifying = true
    errorMessage = nil
    isGpuError = false

    do {
      let decoded = try AudioClassifierHelper.decodeAudioFile(at: url)
      let displayName = url.deletingPathExtension().lastPathComponent
      let source = AudioClipSource.customFile(
        url: url,
        displayName: displayName.isEmpty ? url.lastPathComponent : displayName
      )
      guard generation == currentGen else { return nil }
      selectedClipSource = source
      customDecodedClip = decoded
      cachedDecodedClip = decoded
      cachedClipSource = source
      waveformBars = decoded.waveformBars()
      activeClipDurationMs = decoded.durationMs
      activeClipSampleRate = decoded.sampleRate

      let runResult = try await helper.classifyClip(decoded)
      guard generation == currentGen else { return nil }
      isClassifying = false
      clipResult = runResult
      activeWindowIndex =
        runResult.windows.firstIndex(where: { !$0.categories.isEmpty }) ?? 0
      return runResult
    } catch let error as AudioClassifierError {
      guard generation == currentGen else { return nil }
      isClassifying = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
      return nil
    } catch {
      guard generation == currentGen else { return nil }
      isClassifying = false
      errorMessage = error.localizedDescription
      isGpuError = false
      return nil
    }
  }

  /// Requests microphone permission (if undetermined) and starts live microphone streaming.
  public func startMicrophone() async {
    guard !isListeningMic else { return }
    stopPlayback()
    errorMessage = nil
    isGpuError = false

    let granted = await requestMicrophonePermissionIfNeeded()
    microphonePermission = granted ? .granted : .denied
    guard granted else {
      errorMessage = AudioClassifierError.microphonePermissionDenied.localizedDescription
      return
    }

    let model = selectedModel
    let delegate = selectedDelegate
    let maxRes = maxResults
    let threshold = scoreThreshold
    let overlap = liveOverlap

    do {
      try await helper.startLiveMicrophone(
        model: model,
        delegate: delegate,
        maxResults: maxRes,
        scoreThreshold: threshold,
        overlap: overlap
      ) { [weak self] event in
        Task { @MainActor [weak self] in
          self?.handleLiveAudioEvent(event)
        }
      }
      isListeningMic = true
    } catch let error as AudioClassifierError {
      isListeningMic = false
      errorMessage = error.localizedDescription
      isGpuError = error.isGpuError
    } catch {
      isListeningMic = false
      errorMessage = error.localizedDescription
    }
  }

  /// Stops live microphone streaming cleanly.
  public func stopMicrophone() async {
    await helper.stopLiveMicrophone()
    isListeningMic = false
  }

  // MARK: - Private Helpers

  private func scheduleReload(reclassifyIfClipTab: Bool) {
    activeTask?.cancel()
    activeTask = Task { [weak self] in
      guard let self else { return }
      let loaded = await self.loadClassifier()
      guard loaded else { return }

      let shouldAutoClassify =
        reclassifyIfClipTab
        || (self.autoClassifyOnInitialLoad && !self.hasCompletedInitialClassification)
      if shouldAutoClassify && self.selectedTab == .clip {
        self.hasCompletedInitialClassification = true
        _ = await self.classifyCurrentClip()
      }
    }
  }

  private func restartMicrophone() {
    Task { [weak self] in
      guard let self else { return }
      await self.stopMicrophone()
      await self.startMicrophone()
    }
  }

  private func resolveDecodedClip(for source: AudioClipSource) async throws -> DecodedAudioClip {
    if cachedClipSource == source, let cached = cachedDecodedClip {
      return cached
    }
    switch source {
    case .bundledSpeech:
      let (clip, _) = try await helper.loadBundledSpeechClip()
      return clip
    case .syntheticTone:
      return AudioClassifierHelper.generateSineTone(
        frequencyHz: 440.0,
        durationSeconds: 2.0,
        sampleRate: selectedModel.expectedSampleRate
      )
    case .customFile(let url, _):
      if let customDecodedClip {
        return customDecodedClip
      }
      return try AudioClassifierHelper.decodeAudioFile(at: url)
    }
  }

  private func startPlayback() {
    stopPlayback()
    guard let clip = cachedDecodedClip else { return }

    do {
      let session = AVAudioSession.sharedInstance()
      try session.setCategory(.playback, mode: .default)
      try session.setActive(true)

      let wavData = clip.makeWavData()
      let player = try AVAudioPlayer(data: wavData)
      player.prepareToPlay()
      guard player.play() else {
        errorMessage = "Unable to start audio playback."
        return
      }
      audioPlayer = player
      isPlayingClip = true

      let windowSeconds = selectedModel.windowDurationSeconds
      let windowCount = clipResult?.windows.count ?? 0

      playbackTask = Task { [weak self] in
        while !Task.isCancelled {
          guard let self, let activePlayer = self.audioPlayer else { break }
          if !activePlayer.isPlaying {
            break
          }
          if windowCount > 0 {
            let idx = min(
              max(Int(activePlayer.currentTime / windowSeconds), 0),
              windowCount - 1
            )
            if self.activeWindowIndex != idx {
              self.activeWindowIndex = idx
            }
          }
          try? await Task.sleep(nanoseconds: 60_000_000)
        }
        guard let self else { return }
        self.audioPlayer = nil
        self.isPlayingClip = false
      }
    } catch {
      errorMessage = "Audio playback failed: \(error.localizedDescription)"
      isPlayingClip = false
    }
  }

  private func handleLiveAudioEvent(_ event: LiveAudioEvent) {
    guard isListeningMic else { return }
    switch event {
    case .classification(let window, let inferenceTimeMs):
      liveWindow = window
      liveInferenceTimeMs = inferenceTimeMs
      errorMessage = nil
    case .error(let message):
      errorMessage = message
      Task { [weak self] in
        await self?.stopMicrophone()
      }
    }
  }

  private static func currentMicrophonePermission() -> MicrophonePermissionState {
    switch AVAudioApplication.shared.recordPermission {
    case .granted: return .granted
    case .denied: return .denied
    case .undetermined: return .undetermined
    @unknown default: return .undetermined
    }
  }

  private func requestMicrophonePermissionIfNeeded() async -> Bool {
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
}
