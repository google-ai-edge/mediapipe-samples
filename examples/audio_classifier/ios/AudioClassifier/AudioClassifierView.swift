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

import MediaPipeSampleUI
import SwiftUI
import UniformTypeIdentifiers

/// Sleek SwiftUI screen for the MediaPipe Audio Classifier sample app.
public struct AudioClassifierView: View {
  @State private var viewModel: AudioClassifierViewModel
  @State private var showSettings = false
  @State private var showFileImporter = false

  public init(viewModel: AudioClassifierViewModel = AudioClassifierViewModel()) {
    _viewModel = State(initialValue: viewModel)
  }

  public var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 11) {
          MediaPipeStatusStrip(
            status: viewModel.statusBadge,
            delegateLabel: viewModel.selectedDelegate.shortName,
            latencyMs: viewModel.latestInferenceTimeMs
          )

          if let error = viewModel.errorMessage {
            MediaPipeErrorBanner(
              message: error,
              actionLabel: viewModel.isGpuError ? "Use CPU" : "Retry",
              onAction: {
                if viewModel.isGpuError {
                  viewModel.switchToCpuDelegate()
                } else {
                  viewModel.retryInitialization()
                }
              }
            )
            .transition(.move(edge: .top).combined(with: .opacity))
          }

          Picker(
            "Input Mode",
            selection: Binding(
              get: { viewModel.selectedTab },
              set: { viewModel.selectTab($0) }
            )
          ) {
            ForEach(AudioClassifierInputTab.allCases) { tab in
              Text(tab.rawValue).tag(tab)
            }
          }
          .pickerStyle(.segmented)
          .accessibilityIdentifier("input_mode_picker")

          switch viewModel.selectedTab {
          case .clip:
            clipControlsCard
          case .microphone:
            microphoneControlsCard
          }

          ClassificationResultsCard(viewModel: viewModel)
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 20)
        .animation(.snappy(duration: 0.25), value: viewModel.errorMessage != nil)
        .animation(.snappy(duration: 0.25), value: viewModel.selectedTab)
        .animation(.spring(response: 0.34, dampingFraction: 0.85), value: viewModel.activeWindowIndex)
      }
      .background(MediaPipePalette.canvasBackground.ignoresSafeArea())
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          MediaPipeNavigationTitle(
            title: "Audio Classifier",
            subtitle: viewModel.headerSubtitle
          )
        }
        ToolbarItem(placement: .topBarTrailing) {
          MediaPipeSettingsButton {
            showSettings = true
          }
        }
      }
      .sheet(isPresented: $showSettings) {
        AudioClassifierSettingsSheet(viewModel: viewModel)
      }
      .fileImporter(
        isPresented: $showFileImporter,
        allowedContentTypes: [.audio],
        allowsMultipleSelection: false
      ) { result in
        switch result {
        case .success(let urls):
          if let url = urls.first {
            viewModel.importCustomAudioFile(from: url)
          }
        case .failure:
          break
        }
      }
      .onDisappear {
        viewModel.handleSceneDisappear()
      }
    }
  }

  // MARK: - Audio Clip Card

  private var clipControlsCard: some View {
    VStack(alignment: .leading, spacing: 10) {
      HStack(alignment: .center, spacing: 8) {
        VStack(alignment: .leading, spacing: 2) {
          Text("Audio Clip")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(MediaPipePalette.tealPrimary)

          Text(clipMetadataCaption)
            .font(.caption2.monospacedDigit())
            .foregroundStyle(.secondary)
            .lineLimit(1)
        }

        Spacer(minLength: 4)

        Button {
          showFileImporter = true
        } label: {
          HStack(spacing: 5) {
            Image(systemName: "folder.badge.plus")
              .font(.caption.weight(.semibold))
            Text("Import")
              .font(.caption.weight(.semibold))
          }
          .foregroundStyle(MediaPipePalette.tealPrimary)
          .padding(.horizontal, 11)
          .padding(.vertical, 6)
          .mediaPipeGlassPill(tint: MediaPipePalette.tealPrimary)
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("import_audio_button")
      }

      ScrollView(.horizontal, showsIndicators: false) {
        HStack(spacing: 8) {
          ForEach(viewModel.clipPresetOptions) { source in
            let isSelected = source == viewModel.selectedClipSource
            Button {
              viewModel.selectClipSource(source)
            } label: {
              Text(source.label)
                .font(.caption.weight(isSelected ? .semibold : .medium))
                .foregroundStyle(isSelected ? .white : .primary)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(
                  Capsule()
                    .fill(
                      isSelected
                        ? AnyShapeStyle(MediaPipePalette.brandGradient)
                        : AnyShapeStyle(MediaPipePalette.recessedSurface)
                    )
                )
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isBusy)
            .accessibilityIdentifier("clip_source_\(source.id)")
          }
        }
      }
      .scrollClipDisabled()

      waveformPlayerRow

      MediaPipePrimaryButton(
        title: "Classify Audio Clip",
        busyTitle: viewModel.isModelLoading
          ? "Loading Model…"
          : (viewModel.clipRunningMode == .audioStream ? "Streaming Clip…" : "Classifying…"),
        systemImage: "waveform.badge.magnifyingglass",
        isBusy: viewModel.isBusy,
        isEnabled: viewModel.canClassifyClip
      ) {
        viewModel.triggerClassifyCurrentClip()
      }
      .accessibilityIdentifier("classify_clip_button")
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 20)
  }

  private var clipMetadataCaption: String {
    if let durationMs = viewModel.activeClipDurationMs,
       let sampleRate = viewModel.activeClipSampleRate
    {
      let seconds = Double(durationMs) / 1000.0
      let khz = sampleRate / 1000.0
      let rateText =
        abs(khz.rounded() - khz) < 0.05
        ? "\(Int(khz.rounded())) kHz"
        : String(format: "%.1f kHz", khz)
      return String(format: "%.2f s · %@ · Mono", seconds, rateText)
    }
    return viewModel.selectedClipSource.detailName
  }

  private var waveformPlayerRow: some View {
    HStack(spacing: 10) {
      Button {
        viewModel.togglePlayback()
      } label: {
        Image(systemName: viewModel.isPlayingClip ? "stop.fill" : "play.fill")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(.white)
          .frame(width: 38, height: 38)
          .background(
            Circle().fill(
              viewModel.isPlayingClip
                ? AnyShapeStyle(MediaPipePalette.statusError)
                : AnyShapeStyle(MediaPipePalette.brandGradient)
            )
          )
      }
      .buttonStyle(.plain)
      .disabled(viewModel.waveformBars.isEmpty || viewModel.isBusy)
      .opacity(viewModel.waveformBars.isEmpty ? 0.45 : 1.0)
      .accessibilityLabel(viewModel.isPlayingClip ? "Stop playback" : "Play audio clip")
      .accessibilityIdentifier("play_clip_button")

      WaveformTimelineView(
        bars: viewModel.waveformBars,
        windowCount: viewModel.clipResult?.windows.count ?? 0,
        activeWindowIndex: viewModel.activeWindowIndex,
        durationSeconds: Double(viewModel.activeClipDurationMs ?? 0) / 1000.0,
        windowDurationSeconds: viewModel.selectedModel.windowDurationSeconds,
        onSelectWindow: { index in
          viewModel.selectWindow(index)
        }
      )
      .frame(height: 36)
    }
    .padding(.horizontal, 10)
    .padding(.vertical, 8)
    .background(
      RoundedRectangle(cornerRadius: 14, style: .continuous)
        .fill(MediaPipePalette.recessedSurface)
    )
  }

  // MARK: - Live Microphone Card

  private var microphoneControlsCard: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack(spacing: 12) {
        ZStack {
          Circle()
            .fill(
              viewModel.isListeningMic
                ? MediaPipePalette.statusError.opacity(0.16)
                : MediaPipePalette.tealContainer
            )
            .frame(width: 44, height: 44)

          Image(systemName: viewModel.isListeningMic ? "waveform" : "mic.fill")
            .font(.title3.weight(.semibold))
            .foregroundStyle(
              viewModel.isListeningMic
                ? MediaPipePalette.statusError
                : MediaPipePalette.tealPrimary
            )
            .symbolEffect(.variableColor.iterative, isActive: viewModel.isListeningMic)
        }

        VStack(alignment: .leading, spacing: 3) {
          Text("Live Microphone Stream")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.primary)

          Text(microphoneStatusHint)
            .font(.caption)
            .foregroundStyle(.secondary)
            .fixedSize(horizontal: false, vertical: true)
        }
      }

      if viewModel.microphonePermission == .denied {
        Button {
          openSystemSettings()
        } label: {
          HStack(spacing: 6) {
            Image(systemName: "gear")
              .font(.footnote.weight(.semibold))
            Text("Open Microphone Settings")
              .font(.footnote.weight(.semibold))
          }
          .foregroundStyle(MediaPipePalette.tealPrimary)
          .frame(maxWidth: .infinity)
          .padding(.vertical, 10)
          .background(MediaPipePalette.tealContainer, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("open_mic_settings_button")
      }

      Button {
        viewModel.toggleMicrophone()
      } label: {
        HStack(spacing: 8) {
          Image(systemName: viewModel.isListeningMic ? "stop.fill" : "mic.fill")
            .font(.headline.weight(.semibold))
          Text(microphoneButtonTitle)
            .font(.headline.weight(.semibold))
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 48)
        .background(
          RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(
              viewModel.isListeningMic
                ? AnyShapeStyle(MediaPipePalette.statusError)
                : AnyShapeStyle(MediaPipePalette.brandGradient)
            )
        )
        .shadow(
          color: (viewModel.isListeningMic
            ? MediaPipePalette.statusError
            : MediaPipePalette.tealPrimary).opacity(0.28),
          radius: 10,
          x: 0,
          y: 4
        )
      }
      .buttonStyle(.plain)
      .disabled(viewModel.isBusy)
      .accessibilityIdentifier("mic_toggle_button")
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 20)
  }

  private var microphoneStatusHint: String {
    if viewModel.microphonePermission == .denied {
      return "Microphone permission is turned off. Enable access in iOS Settings to classify ambient audio."
    }
    if viewModel.isListeningMic {
      return "Streaming 16 kHz audio in 0.975 s windows every \(viewModel.liveOverlap.intervalMs) ms."
    }
    return "Tap Start Listening to classify live speech, music, and ambient sounds on-device."
  }

  private var microphoneButtonTitle: String {
    if viewModel.isListeningMic {
      return "Stop Listening"
    }
    if viewModel.microphonePermission == .undetermined {
      return "Enable Mic & Start Listening"
    }
    return "Start Listening"
  }

  private func openSystemSettings() {
    #if canImport(UIKit)
    if let url = URL(string: UIApplication.openSettingsURLString) {
      UIApplication.shared.open(url)
    }
    #endif
  }
}

// MARK: - Interactive Waveform Timeline

private struct WaveformTimelineView: View {
  let bars: [Float]
  let windowCount: Int
  let activeWindowIndex: Int
  let durationSeconds: Double
  let windowDurationSeconds: Double
  let onSelectWindow: (Int) -> Void

  var body: some View {
    GeometryReader { proxy in
      let displayBars = bars.isEmpty ? Self.placeholderBars : bars
      let barCount = displayBars.count
      let activeRange = activeBarRange(totalBars: barCount)

      HStack(alignment: .center, spacing: 2.5) {
        ForEach(0..<barCount, id: \.self) { idx in
          let heightFraction = CGFloat(displayBars[idx])
          let isInActiveWindow = activeRange.contains(idx)

          Capsule()
            .fill(
              isInActiveWindow && !bars.isEmpty
                ? AnyShapeStyle(MediaPipePalette.brandGradient)
                : AnyShapeStyle(MediaPipePalette.tealPrimary.opacity(bars.isEmpty ? 0.14 : 0.24))
            )
            .frame(maxWidth: .infinity)
            .frame(height: max(4, proxy.size.height * heightFraction))
        }
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity)
      .contentShape(Rectangle())
      .gesture(
        DragGesture(minimumDistance: 0)
          .onEnded { value in
            guard windowCount > 0, proxy.size.width > 0 else { return }
            let fraction = min(max(value.location.x / proxy.size.width, 0), 0.9999)
            let targetIndex: Int
            if durationSeconds > 0 {
              let timeSec = fraction * durationSeconds
              targetIndex = min(max(Int(timeSec / windowDurationSeconds), 0), windowCount - 1)
            } else {
              targetIndex = min(max(Int(fraction * Double(windowCount)), 0), windowCount - 1)
            }
            onSelectWindow(targetIndex)
          }
      )
    }
  }

  private func activeBarRange(totalBars: Int) -> Range<Int> {
    guard totalBars > 0, windowCount > 0 else { return 0..<0 }
    if durationSeconds > 0 {
      let startSec = Double(activeWindowIndex) * windowDurationSeconds
      let endSec = min(durationSeconds, startSec + windowDurationSeconds)
      let startIdx = min(totalBars - 1, max(0, Int((startSec / durationSeconds) * Double(totalBars))))
      let endIdx = min(totalBars, max(startIdx + 1, Int((endSec / durationSeconds) * Double(totalBars))))
      return startIdx..<endIdx
    }
    let span = max(1, totalBars / windowCount)
    let start = min(totalBars - 1, activeWindowIndex * span)
    let end = activeWindowIndex == windowCount - 1 ? totalBars : min(totalBars, start + span)
    return start..<end
  }

  private static let placeholderBars: [Float] = [
    0.18, 0.25, 0.40, 0.30, 0.55, 0.72, 0.48, 0.36, 0.64, 0.80,
    0.58, 0.42, 0.28, 0.52, 0.76, 0.66, 0.40, 0.32, 0.60, 0.85,
    0.70, 0.45, 0.30, 0.50, 0.74, 0.62, 0.38, 0.26, 0.46, 0.68,
    0.54, 0.34, 0.22, 0.44, 0.60, 0.48, 0.30, 0.24, 0.36, 0.20,
  ]
}

// MARK: - Classification Results Card

private struct ClassificationResultsCard: View {
  let viewModel: AudioClassifierViewModel

  private var windowSubtitle: String {
    switch viewModel.selectedTab {
    case .clip:
      guard let windows = viewModel.clipResult?.windows,
            let active = viewModel.activeClipWindow
      else {
        return "Tap Classify to inspect 0.975 s windows"
      }
      let tsLabel =
        active.timestampMs < 3_600_000
        ? "\(active.timestampMs) ms"
        : "final flush"
      return "Window \(active.windowIndex + 1) of \(windows.count) · \(tsLabel)"
    case .microphone:
      if viewModel.isListeningMic {
        return "Live stream · \(viewModel.liveOverlap.label) overlap"
      }
      return "Real-time audio event rankings"
    }
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack(alignment: .center, spacing: 8) {
        VStack(alignment: .leading, spacing: 2) {
          Text("Detected Sounds")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.secondary)

          Text(windowSubtitle)
            .font(.caption.monospacedDigit())
            .foregroundStyle(.secondary)
            .accessibilityIdentifier("window_timestamp_subtitle")
        }

        Spacer(minLength: 4)

        if let top = viewModel.displayedCategories.first {
          HStack(spacing: 5) {
            Image(systemName: top.score >= 0.5 ? "checkmark.seal.fill" : "waveform")
              .font(.caption2.weight(.semibold))
            Text("\(top.label) · \(Int((top.score * 100).rounded()))%")
              .font(.caption.weight(.semibold).monospacedDigit())
              .lineLimit(1)
          }
          .foregroundStyle(
            top.score >= 0.5 ? MediaPipePalette.statusPositive : MediaPipePalette.onTealContainer
          )
          .padding(.horizontal, 10)
          .padding(.vertical, 5)
          .background(
            (top.score >= 0.5
              ? MediaPipePalette.statusPositive.opacity(0.14)
              : MediaPipePalette.tealContainer),
            in: Capsule()
          )
          .accessibilityIdentifier("top_category_badge")
        }
      }

      if viewModel.selectedTab == .clip,
         let windows = viewModel.clipResult?.windows,
         windows.count > 1
      {
        windowScrubberBar(windows: windows)
      }

      if !viewModel.hasResult {
        HStack(spacing: 10) {
          Image(systemName: "ear.and.waveform")
            .font(.title3)
            .foregroundStyle(MediaPipePalette.tealPrimary.opacity(0.75))
          Text(
            viewModel.selectedTab == .clip
              ? "Select or import an audio clip and tap Classify to view ranked YAMNet categories."
              : "Tap Start Listening to classify live ambient sounds from your microphone."
          )
          .font(.footnote)
          .foregroundStyle(.secondary)
        }
        .padding(.vertical, 10)
      } else if viewModel.displayedCategories.isEmpty {
        Text(
          String(
            format: "No sound classes scored above the %.1f threshold in this window.",
            viewModel.scoreThreshold
          )
        )
        .font(.footnote)
        .foregroundStyle(.secondary)
        .padding(.vertical, 8)
      } else {
        VStack(spacing: 10) {
          ForEach(Array(viewModel.displayedCategories.enumerated()), id: \.element.id) { rank, category in
            CategoryProbabilityRow(category: category, isTop: rank == 0)
          }
        }
      }

      HStack(spacing: 8) {
        MediaPipeMetricTile(
          label: "Latency",
          value: viewModel.latestInferenceTimeMs.map { String(format: "%.1f ms", $0) } ?? "--",
          systemImage: "bolt.fill"
        )
        .accessibilityIdentifier("metric_inference_time")

        MediaPipeMetricTile(
          label: "Mode",
          value: viewModel.selectedTab == .clip
            ? viewModel.clipRunningMode.shortName
            : "Live",
          systemImage: "waveform.path"
        )
        .accessibilityIdentifier("metric_running_mode")

        MediaPipeMetricTile(
          label: "Rate",
          value: sampleRateLabel,
          systemImage: "dot.radiowaves.left.and.right"
        )
        .accessibilityIdentifier("metric_sample_rate")
      }
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 22)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("result_card")
  }

  private var sampleRateLabel: String {
    let rate =
      viewModel.selectedTab == .clip
      ? (viewModel.clipResult?.sampleRate ?? viewModel.activeClipSampleRate ?? 16_000.0)
      : viewModel.selectedModel.expectedSampleRate
    let khz = rate / 1000.0
    if abs(khz.rounded() - khz) < 0.05 {
      return "\(Int(khz.rounded())) kHz"
    }
    return String(format: "%.1f kHz", khz)
  }

  private func windowScrubberBar(windows: [AudioClassificationWindow]) -> some View {
    ScrollView(.horizontal, showsIndicators: false) {
      HStack(spacing: 7) {
        ForEach(windows) { window in
          let isSelected = window.windowIndex == viewModel.activeWindowIndex
          let roundedSeconds = (window.displayTimestampSeconds * 100.0).rounded() / 100.0
          Button {
            viewModel.selectWindow(window.windowIndex)
          } label: {
            Text(String(format: "%.2fs", roundedSeconds))
              .font(.caption.weight(isSelected ? .semibold : .medium).monospacedDigit())
              .foregroundStyle(isSelected ? .white : .primary)
              .padding(.horizontal, 11)
              .padding(.vertical, 5.5)
              .background(
                Capsule()
                  .fill(
                    isSelected
                      ? AnyShapeStyle(MediaPipePalette.brandGradient)
                      : AnyShapeStyle(MediaPipePalette.recessedSurface)
                  )
              )
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier("window_chip_\(window.windowIndex)")
        }
      }
    }
    .scrollClipDisabled()
    .sensoryFeedback(.selection, trigger: viewModel.activeWindowIndex)
  }
}

private struct CategoryProbabilityRow: View {
  let category: AudioClassificationCategory
  let isTop: Bool

  private var clampedScore: CGFloat {
    CGFloat(min(max(category.score, 0.0), 1.0))
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 5) {
      HStack(alignment: .firstTextBaseline, spacing: 8) {
        Text(category.label)
          .font(.subheadline.weight(isTop ? .semibold : .medium))
          .foregroundStyle(.primary)
          .lineLimit(1)

        Spacer(minLength: 4)

        Text(String(format: "%.1f%%", category.score * 100.0))
          .font(.footnote.weight(.semibold).monospacedDigit())
          .foregroundStyle(isTop ? MediaPipePalette.tealPrimary : .secondary)
          .contentTransition(.numericText())
      }

      GeometryReader { proxy in
        ZStack(alignment: .leading) {
          Capsule()
            .fill(MediaPipePalette.recessedSurface)

          Capsule()
            .fill(
              isTop
                ? AnyShapeStyle(MediaPipePalette.brandGradient)
                : AnyShapeStyle(MediaPipePalette.tealPrimary.opacity(0.62))
            )
            .frame(width: max(proxy.size.width * clampedScore, clampedScore > 0 ? 6 : 0))
            .animation(.spring(response: 0.36, dampingFraction: 0.84), value: clampedScore)
        }
      }
      .frame(height: 8)
    }
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("category_row_\(category.label)")
  }
}

// MARK: - Settings Sheet

private struct AudioClassifierSettingsSheet: View {
  @Bindable var viewModel: AudioClassifierViewModel

  var body: some View {
    MediaPipeSettingsSheet(title: "Classifier Settings") {
      Section {
        Picker(
          "Clip Running Mode",
          selection: Binding(
            get: { viewModel.clipRunningMode },
            set: { viewModel.selectClipRunningMode($0) }
          )
        ) {
          ForEach(AudioClassifierRunningModeOption.allCases) { mode in
            Text(mode.displayName).tag(mode)
          }
        }
        .pickerStyle(.segmented)
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_running_mode")
      } header: {
        Text("Clip Running Mode")
      } footer: {
        Text(viewModel.clipRunningMode.summary)
      }

      Section("Classification") {
        Picker(
          "Max Results",
          selection: Binding(
            get: { viewModel.maxResults },
            set: { viewModel.setMaxResults($0) }
          )
        ) {
          ForEach(1...5, id: \.self) { count in
            Text("\(count)").tag(count)
          }
        }
        .pickerStyle(.segmented)
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_max_results")

        VStack(alignment: .leading, spacing: 6) {
          HStack {
            Text("Score Threshold")
            Spacer()
            Text(String(format: "%.1f", viewModel.scoreThreshold))
              .font(.subheadline.weight(.semibold).monospacedDigit())
              .foregroundStyle(MediaPipePalette.tealPrimary)
              .accessibilityIdentifier("threshold_value")
          }

          Slider(
            value: Binding(
              get: { Double(viewModel.scoreThreshold) },
              set: { viewModel.setScoreThreshold(Float($0)) }
            ),
            in: 0.0...0.9,
            step: 0.1
          )
          .disabled(viewModel.isBusy)
          .accessibilityIdentifier("slider_score_threshold")
        }
      }

      Section {
        Picker(
          "Window Overlap",
          selection: Binding(
            get: { viewModel.liveOverlap },
            set: { viewModel.setLiveOverlap($0) }
          )
        ) {
          ForEach(AudioOverlapOption.allCases) { option in
            Text(option.label).tag(option)
          }
        }
        .pickerStyle(.segmented)
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_overlap")
      } header: {
        Text("Live Microphone")
      } footer: {
        Text("Classifies a 0.975 s audio window every \(viewModel.liveOverlap.intervalMs) ms.")
      }

      Section("Diagnostics") {
        LabeledContent("Model", value: viewModel.selectedModel.fullName)
        LabeledContent("Accelerator", value: viewModel.selectedDelegate.displayName)
        LabeledContent("Asset", value: viewModel.selectedModel.assetFilename)
        LabeledContent("Window", value: "0.975 s · 16 kHz")
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent("Model Load", value: String(format: "%.1f ms", loadMs))
        }
      }
    }
  }
}

#Preview {
  AudioClassifierView()
}
