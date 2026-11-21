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
#if canImport(UIKit)
import UIKit
#endif

/// Sleek SwiftUI screen for the MediaPipe Audio Transcriber sample app.
public struct AudioTranscriberView: View {
  @State private var viewModel: AudioTranscriberViewModel
  @State private var showSettings = false
  @State private var showAudioImporter = false
  @State private var copiedTranscript = false

  public init(viewModel: AudioTranscriberViewModel = AudioTranscriberViewModel()) {
    _viewModel = State(initialValue: viewModel)
  }

  public var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 12) {
          MediaPipeStatusStrip(
            status: viewModel.statusBadge,
            delegateLabel: viewModel.selectedDelegate.shortName,
            latencyMs: viewModel.lastResult?.inferenceTimeMs
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

          inputModePicker

          if viewModel.inputMode == .clip {
            clipWorkspaceSection
          } else {
            microphoneWorkspaceSection
          }

          HeroTranscriptCard(
            fullTranscript: viewModel.fullTranscript,
            segments: viewModel.finalizedSegments,
            interimTranscript: viewModel.interimTranscript,
            result: viewModel.lastResult,
            isBusy: viewModel.isBusy,
            isRecording: viewModel.isRecording,
            copiedTranscript: copiedTranscript,
            onCopy: copyTranscript,
            onClear: { viewModel.triggerClearTranscript() }
          )
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 22)
        .animation(.snappy(duration: 0.25), value: viewModel.errorMessage != nil)
        .animation(.snappy(duration: 0.25), value: viewModel.inputMode)
        .animation(.spring(response: 0.34, dampingFraction: 0.84), value: viewModel.lastResult)
      }
      .background(MediaPipePalette.canvasBackground.ignoresSafeArea())
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          MediaPipeNavigationTitle(
            title: "Audio Transcriber",
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
        AudioTranscriberSettingsSheet(viewModel: viewModel)
      }
      .fileImporter(
        isPresented: $showAudioImporter,
        allowedContentTypes: [.audio, .wav],
        allowsMultipleSelection: false
      ) { result in
        if case .success(let urls) = result, let url = urls.first {
          viewModel.triggerImportAndTranscribeFile(url: url)
        }
      }
    }
  }

  // MARK: - Input Mode Picker

  private var inputModePicker: some View {
    Picker(
      "Input Source",
      selection: Binding(
        get: { viewModel.inputMode },
        set: { viewModel.selectInputMode($0) }
      )
    ) {
      ForEach(AudioInputSourceMode.allCases) { mode in
        Label(mode.label, systemImage: mode.systemImage).tag(mode)
      }
    }
    .pickerStyle(.segmented)
    .disabled(viewModel.isBusy)
    .accessibilityIdentifier("input_mode_picker")
  }

  // MARK: - Audio Clip Section

  private var clipWorkspaceSection: some View {
    VStack(alignment: .leading, spacing: 10) {
      VStack(alignment: .leading, spacing: 5) {
        MediaPipeChipBar(
          title: "Clip Mode",
          items: AudioClipTranscriptionMode.allCases,
          selectedID: viewModel.clipMode.id,
          label: \.label,
          accessibilityID: { "clip_mode_\($0.id)" },
          onSelect: { mode in
            viewModel.selectClipMode(mode)
          }
        )

        Text(viewModel.clipMode.summary)
          .font(.caption)
          .foregroundStyle(.secondary)
      }

      audioClipSourceCard

      MediaPipePrimaryButton(
        title: viewModel.isUsingCustomClip ? "Transcribe Clip" : "Transcribe Sample",
        busyTitle: viewModel.isModelLoading
          ? "Loading Model…"
          : (viewModel.clipMode == .streaming ? "Streaming Audio…" : "Transcribing…"),
        systemImage: viewModel.clipMode == .streaming
          ? "dot.radiowaves.left.and.right"
          : "waveform.badge.magnifyingglass",
        isBusy: viewModel.isBusy,
        isEnabled: viewModel.canTranscribeClip
      ) {
        viewModel.triggerTranscribeActiveClip()
      }
      .accessibilityIdentifier("transcribe_clip_button")
    }
  }

  private var audioClipSourceCard: some View {
    VStack(alignment: .leading, spacing: 10) {
      HStack(spacing: 12) {
        ZStack {
          RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(MediaPipePalette.tealContainer)
            .frame(width: 40, height: 40)
          Image(systemName: "waveform")
            .font(.body.weight(.semibold))
            .foregroundStyle(MediaPipePalette.tealPrimary)
        }

        VStack(alignment: .leading, spacing: 2) {
          Text(viewModel.isUsingCustomClip ? viewModel.activeClipLabel : "Sample Speech Clip")
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.primary)
            .lineLimit(1)
            .truncationMode(.middle)
            .accessibilityIdentifier("active_clip_label")

          Text(
            viewModel.isUsingCustomClip
              ? "Custom audio · 16 kHz mono"
              : "16 kHz mono WAV · 4.3 s"
          )
          .font(.caption)
          .foregroundStyle(.secondary)
          .lineLimit(1)
        }

        Spacer(minLength: 4)

        HStack(spacing: 6) {
          if viewModel.isUsingCustomClip {
            Button {
              viewModel.resetToBundledSample()
            } label: {
              Image(systemName: "arrow.uturn.backward")
                .font(.caption.weight(.semibold))
                .foregroundStyle(MediaPipePalette.tealPrimary)
                .frame(width: 30, height: 30)
                .mediaPipeGlassPill(tint: MediaPipePalette.tealPrimary)
            }
            .buttonStyle(.plain)
            .disabled(viewModel.isBusy)
            .accessibilityLabel("Use bundled sample clip")
            .accessibilityIdentifier("reset_sample_button")
          }

          Button {
            showAudioImporter = true
          } label: {
            Label("Import", systemImage: "folder")
              .font(.caption.weight(.semibold))
              .foregroundStyle(MediaPipePalette.onTealContainer)
              .padding(.horizontal, 11)
              .padding(.vertical, 7)
              .background(MediaPipePalette.tealContainer, in: Capsule())
          }
          .buttonStyle(.plain)
          .disabled(viewModel.isBusy)
          .accessibilityIdentifier("import_audio_button")
        }
      }

      if viewModel.isTranscribing && viewModel.clipMode == .streaming {
        VStack(alignment: .leading, spacing: 6) {
          AudioLevelWaveformMeter(
            levels: viewModel.levelHistory,
            isActive: true
          )
          .frame(height: 24)

          ProgressView(value: viewModel.streamProgress)
            .tint(MediaPipePalette.tealPrimary)
        }
        .transition(.opacity)
      }
    }
    .padding(12)
    .mediaPipeCard(cornerRadius: 18, isHighlighted: viewModel.isTranscribing)
  }

  // MARK: - Live Microphone Section

  private var microphoneWorkspaceSection: some View {
    VStack(alignment: .leading, spacing: 10) {
      VStack(alignment: .leading, spacing: 10) {
        HStack {
          HStack(spacing: 7) {
            Circle()
              .fill(
                viewModel.isRecording
                  ? MediaPipePalette.statusError
                  : MediaPipePalette.tealPrimary.opacity(0.45)
              )
              .frame(width: 8, height: 8)

            Text(viewModel.isRecording ? "Listening Live" : "Microphone Ready")
              .font(.subheadline.weight(.semibold))
              .foregroundStyle(.primary)
          }

          Spacer()

          Text(Self.formatRecordingClock(viewModel.recordingDurationSeconds))
            .font(.caption.weight(.semibold).monospacedDigit())
            .foregroundStyle(viewModel.isRecording ? MediaPipePalette.statusError : .secondary)
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(MediaPipePalette.recessedSurface, in: Capsule())
            .accessibilityIdentifier("recording_timer_label")
        }

        AudioLevelWaveformMeter(
          levels: viewModel.levelHistory,
          isActive: viewModel.isRecording
        )
        .frame(height: 40)

        Text(
          viewModel.isRecording
            ? "Speaking directly into Parakeet TDT at 16 kHz mono."
            : "Tap Start Recording to stream live speech on-device."
        )
        .font(.caption)
        .foregroundStyle(.secondary)
      }
      .padding(14)
      .mediaPipeCard(cornerRadius: 18, isHighlighted: viewModel.isRecording)

      Button {
        viewModel.triggerToggleRecording()
      } label: {
        HStack(spacing: 8) {
          Image(systemName: viewModel.isRecording ? "stop.fill" : "mic.fill")
            .font(.headline.weight(.semibold))
          Text(viewModel.isRecording ? "Stop Recording" : "Start Recording")
            .font(.headline.weight(.semibold))
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity)
        .frame(minHeight: 48)
        .background(
          RoundedRectangle(cornerRadius: 16, style: .continuous)
            .fill(
              viewModel.canToggleRecording
                ? (viewModel.isRecording
                  ? AnyShapeStyle(MediaPipePalette.statusError)
                  : AnyShapeStyle(MediaPipePalette.brandGradient))
                : AnyShapeStyle(Color.secondary.opacity(0.32))
            )
        )
        .shadow(
          color: viewModel.canToggleRecording
            ? (viewModel.isRecording
              ? MediaPipePalette.statusError.opacity(0.28)
              : MediaPipePalette.tealPrimary.opacity(0.28))
            : .clear,
          radius: 10,
          x: 0,
          y: 4
        )
      }
      .buttonStyle(.plain)
      .disabled(!viewModel.canToggleRecording)
      .sensoryFeedback(.impact(flexibility: .soft), trigger: viewModel.isRecording)
      .accessibilityIdentifier("record_button")
    }
  }

  // MARK: - Actions

  private func copyTranscript() {
    let text = viewModel.fullTranscript
    guard !text.isEmpty else { return }
    #if canImport(UIKit)
    UIPasteboard.general.string = text
    #endif
    withAnimation(.snappy(duration: 0.2)) {
      copiedTranscript = true
    }
    Task {
      try? await Task.sleep(for: .seconds(1.8))
      withAnimation(.snappy(duration: 0.2)) {
        copiedTranscript = false
      }
    }
  }

  private static func formatRecordingClock(_ seconds: Double) -> String {
    let total = max(0, Int(seconds.rounded(.down)))
    return String(format: "%02d:%02d", total / 60, total % 60)
  }
}

// MARK: - Animated Waveform Audio Level Meter

private struct AudioLevelWaveformMeter: View {
  let levels: [Float]
  let isActive: Bool

  var body: some View {
    GeometryReader { geometry in
      let count = max(1, levels.count)
      let spacing: CGFloat = 3
      let totalSpacing = spacing * CGFloat(count - 1)
      let barWidth = max(3, (geometry.size.width - totalSpacing) / CGFloat(count))

      HStack(alignment: .center, spacing: spacing) {
        ForEach(Array(levels.enumerated()), id: \.offset) { index, level in
          let normalized = CGFloat(min(max(level, 0.0), 1.0))
          let idleFloor: CGFloat = 0.14
          let fraction = isActive ? max(idleFloor, normalized) : idleFloor
          let height = max(4, geometry.size.height * fraction)

          Capsule(style: .continuous)
            .fill(
              isActive
                ? AnyShapeStyle(MediaPipePalette.brandGradient)
                : AnyShapeStyle(MediaPipePalette.recessedSurface)
            )
            .frame(width: barWidth, height: height)
            .animation(
              .spring(response: 0.18, dampingFraction: 0.78),
              value: levels[index]
            )
        }
      }
      .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .center)
    }
  }
}

// MARK: - Hero Transcript Card

private struct HeroTranscriptCard: View {
  let fullTranscript: String
  let segments: [TranscribedSegment]
  let interimTranscript: String
  let result: AudioTranscriptionResult?
  let isBusy: Bool
  let isRecording: Bool
  let copiedTranscript: Bool
  let onCopy: () -> Void
  let onClear: () -> Void

  private var hasContent: Bool {
    !fullTranscript.isEmpty || !interimTranscript.isEmpty
  }

  private let telemetryColumns = [
    GridItem(.flexible(), spacing: 8),
    GridItem(.flexible(), spacing: 8),
  ]

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack(spacing: 8) {
        Text("Transcript")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(.secondary)

        if let result {
          Text(result.mode.label)
            .font(.caption2.weight(.semibold))
            .foregroundStyle(MediaPipePalette.onTealContainer)
            .padding(.horizontal, 8)
            .padding(.vertical, 3)
            .background(MediaPipePalette.tealContainer, in: Capsule())
        }

        Spacer()

        if !fullTranscript.isEmpty {
          Button(action: onCopy) {
            HStack(spacing: 4) {
              Image(systemName: copiedTranscript ? "checkmark" : "doc.on.doc")
                .font(.caption2.weight(.semibold))
              Text(copiedTranscript ? "Copied" : "Copy")
                .font(.caption.weight(.semibold))
            }
            .foregroundStyle(
              copiedTranscript ? MediaPipePalette.statusPositive : MediaPipePalette.tealPrimary
            )
            .padding(.horizontal, 10)
            .padding(.vertical, 5)
            .background(
              (copiedTranscript
                ? MediaPipePalette.statusPositive.opacity(0.14)
                : MediaPipePalette.tealContainer),
              in: Capsule()
            )
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier("copy_transcript_button")
        }

        if hasContent && !isRecording && !isBusy {
          Button(action: onClear) {
            Image(systemName: "trash")
              .font(.caption.weight(.semibold))
              .foregroundStyle(.secondary)
              .frame(width: 28, height: 28)
              .background(MediaPipePalette.recessedSurface, in: Circle())
          }
          .buttonStyle(.plain)
          .accessibilityLabel("Clear transcript")
          .accessibilityIdentifier("clear_transcript_button")
        }
      }

      if !hasContent {
        emptyStateView
      } else {
        transcriptBodyView
      }

      if let result {
        LazyVGrid(columns: telemetryColumns, spacing: 8) {
          MediaPipeMetricTile(
            label: "Latency",
            value: Self.formatLatency(result.inferenceTimeMs),
            systemImage: "bolt.fill"
          )
          .accessibilityIdentifier("metric_latency")

          MediaPipeMetricTile(
            label: "RTF",
            value: String(format: "%.2f×", result.realTimeFactor),
            systemImage: "gauge.with.dots.needle.67percent"
          )
          .accessibilityIdentifier("metric_rtf")

          MediaPipeMetricTile(
            label: "Duration",
            value: String(format: "%.2f s", result.audioDurationSeconds),
            systemImage: "waveform"
          )
          .accessibilityIdentifier("metric_duration")

          MediaPipeMetricTile(
            label: "Mode",
            value: result.mode == .streaming
              ? "Stream (\(result.chunkCount))"
              : "Full Clip",
            systemImage: result.mode == .streaming
              ? "dot.radiowaves.left.and.right"
              : "doc.text"
          )
          .accessibilityIdentifier("metric_mode")
        }
      }
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 22)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("transcript_card")
  }

  private var emptyStateView: some View {
    VStack(spacing: 8) {
      Image(systemName: isBusy ? "waveform.circle" : "text.bubble")
        .font(.title2)
        .foregroundStyle(MediaPipePalette.tealPrimary.opacity(0.75))
      Text(
        isBusy
          ? "Preparing on-device speech recognition…"
          : "Tap Transcribe Sample or start recording to see on-device transcription."
      )
      .font(.subheadline)
      .foregroundStyle(.secondary)
      .multilineTextAlignment(.center)
    }
    .frame(maxWidth: .infinity)
    .padding(.vertical, 22)
    .padding(.horizontal, 12)
    .background(
      RoundedRectangle(cornerRadius: 14, style: .continuous)
        .fill(MediaPipePalette.recessedSurface)
    )
  }

  private var transcriptBodyView: some View {
    VStack(alignment: .leading, spacing: 10) {
      if !fullTranscript.isEmpty {
        VStack(alignment: .leading, spacing: 8) {
          HStack(alignment: .top, spacing: 10) {
            Capsule(style: .continuous)
              .fill(MediaPipePalette.brandGradient)
              .frame(width: 3.5)

            Text(fullTranscript)
              .font(.title3.weight(.medium))
              .foregroundStyle(.primary)
              .textSelection(.enabled)
              .fixedSize(horizontal: false, vertical: true)
              .frame(maxWidth: .infinity, alignment: .leading)
              .accessibilityIdentifier("transcript_text")
          }

          if segments.count == 1, let onlySegment = segments.first,
            onlySegment.toTimestampMs > onlySegment.fromTimestampMs
          {
            HStack(spacing: 6) {
              Image(systemName: "clock")
                .font(.caption2)
                .foregroundStyle(MediaPipePalette.tealPrimary)
              Text(onlySegment.timeRangeLabel)
                .font(.caption2.weight(.medium).monospacedDigit())
                .foregroundStyle(.secondary)
            }
            .padding(.leading, 13.5)
          }
        }
        .padding(12)
        .background(
          RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(MediaPipePalette.recessedSurface)
        )
      }

      if !interimTranscript.isEmpty {
        HStack(alignment: .top, spacing: 8) {
          Text("LIVE")
            .font(.caption2.weight(.bold).monospaced())
            .foregroundStyle(MediaPipePalette.onTealContainer)
            .padding(.horizontal, 7)
            .padding(.vertical, 3)
            .background(MediaPipePalette.tealContainer, in: Capsule())

          Text(interimTranscript)
            .font(.subheadline.italic())
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("interim_transcript_text")
        }
        .padding(10)
        .background(
          RoundedRectangle(cornerRadius: 12, style: .continuous)
            .fill(MediaPipePalette.tealContainer.opacity(0.35))
        )
      }

      if segments.count > 1 {
        VStack(alignment: .leading, spacing: 6) {
          Text("Segments (\(segments.count))")
            .font(.caption2.weight(.semibold))
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
            .tracking(0.4)

          ForEach(segments) { segment in
            HStack(alignment: .firstTextBaseline, spacing: 8) {
              Text(segment.timeRangeLabel)
                .font(.caption2.weight(.medium).monospacedDigit())
                .foregroundStyle(MediaPipePalette.tealPrimary)
                .padding(.horizontal, 7)
                .padding(.vertical, 2.5)
                .background(MediaPipePalette.tealContainer.opacity(0.75), in: Capsule())

              Text(segment.transcript)
                .font(.caption)
                .foregroundStyle(.secondary)
                .lineLimit(2)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
          }
        }
      }
    }
  }

  private static func formatLatency(_ ms: Double) -> String {
    if ms >= 1_000.0 {
      return String(format: "%.2f s", ms / 1_000.0)
    }
    return String(format: "%.0f ms", ms)
  }
}

// MARK: - Settings Sheet

private struct AudioTranscriberSettingsSheet: View {
  @Bindable var viewModel: AudioTranscriberViewModel

  var body: some View {
    MediaPipeSettingsSheet(title: "Model & Settings") {
      Section {
        Picker(
          "Clip Mode",
          selection: Binding(
            get: { viewModel.clipMode },
            set: { viewModel.selectClipMode($0) }
          )
        ) {
          ForEach(AudioClipTranscriptionMode.allCases) { mode in
            Text(mode.label).tag(mode)
          }
        }
        .disabled(viewModel.isBusy || viewModel.isRecording)
        .accessibilityIdentifier("picker_clip_mode")
      } header: {
        Text("Audio Clip Transcription")
      } footer: {
        Text(viewModel.clipMode.summary)
      }

      if AudioTranscriberViewModel.supportsGpuDelegateInUI {
        Section {
          Picker(
            "Accelerator",
            selection: Binding(
              get: { viewModel.selectedDelegate },
              set: { viewModel.selectDelegate($0) }
            )
          ) {
            ForEach(AudioTranscriberDelegateOption.allCases) { delegate in
              Text(delegate.displayName).tag(delegate)
            }
          }
          .disabled(viewModel.isBusy || viewModel.isRecording)
          .accessibilityIdentifier("picker_delegate")
        } header: {
          Text("Hardware Acceleration")
        }
      }

      Section {
        LabeledContent("Model", value: viewModel.selectedModel.fullName)
        Text(viewModel.selectedModel.summary)
          .font(.caption)
          .foregroundStyle(.secondary)
      } header: {
        Text("Model")
      }

      Section("Diagnostics") {
        LabeledContent("Accelerator", value: viewModel.selectedDelegate.displayName)
        LabeledContent("Asset", value: viewModel.activeModelName)
        LabeledContent("Input Format", value: "16,000 Hz Mono PCM")
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent(
            "Model Load",
            value: loadMs >= 1_000.0
              ? String(format: "%.2f s", loadMs / 1_000.0)
              : String(format: "%.0f ms", loadMs)
          )
        }
      }
    }
  }
}

#Preview {
  AudioTranscriberView()
}
