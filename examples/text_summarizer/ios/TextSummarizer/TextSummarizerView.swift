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
#if canImport(UIKit)
import UIKit
#endif

/// Sleek SwiftUI screen for the MediaPipe Text Summarizer sample app.
public struct TextSummarizerView: View {
  @State private var viewModel: TextSummarizerViewModel
  @State private var showSettings = false

  public init(viewModel: TextSummarizerViewModel = TextSummarizerViewModel()) {
    _viewModel = State(initialValue: viewModel)
  }

  public var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 10) {
          MediaPipeStatusStrip(
            status: viewModel.statusBadge,
            delegateLabel: viewModel.selectedDelegate.shortName,
            latencyMs: viewModel.result?.inferenceTimeMs
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

          modeSelectorRow

          MediaPipeChipBar(
            title: "Examples",
            items: SummarizerPreset.defaults,
            selectedID: viewModel.activePresetID,
            label: \.label,
            accessibilityID: { "preset_\($0.id)" },
            onSelect: { preset in
              dismissKeyboard()
              viewModel.applyPreset(preset)
            }
          )

          MediaPipeTextInputCard(
            label: "Source Text",
            placeholder: "Paste or type an article, meeting notes, or passage to summarize…",
            text: $viewModel.inputText,
            inputAccessibilityID: "input_text",
            clearAccessibilityID: "clear_text"
          )

          MediaPipePrimaryButton(
            title: "Summarize",
            busyTitle: viewModel.isModelLoading
              ? "Loading Model…"
              : (viewModel.streamingEnabled ? "Streaming…" : "Summarizing…"),
            systemImage: "sparkles",
            isBusy: viewModel.isBusy,
            isEnabled: viewModel.canSummarize
          ) {
            dismissKeyboard()
            viewModel.triggerSummarize()
          }
          .accessibilityIdentifier("summarize_button")

          if viewModel.result != nil || !viewModel.streamingText.isEmpty {
            SummaryResultCard(
              mode: viewModel.result?.mode ?? viewModel.selectedMode,
              modelName: viewModel.selectedModel.displayName,
              streamingText: viewModel.streamingText,
              result: viewModel.result
            )
            .transition(.scale(scale: 0.96).combined(with: .opacity))
          }
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 20)
        .animation(.snappy(duration: 0.28), value: viewModel.errorMessage != nil)
        .animation(.spring(response: 0.36, dampingFraction: 0.84), value: viewModel.result != nil)
        .animation(.easeOut(duration: 0.2), value: viewModel.streamingText.isEmpty)
      }
      .background(MediaPipePalette.canvasBackground.ignoresSafeArea())
      .scrollDismissesKeyboard(.interactively)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          MediaPipeNavigationTitle(
            title: "Text Summarizer",
            subtitle: viewModel.headerSubtitle
          )
        }
        ToolbarItem(placement: .topBarTrailing) {
          MediaPipeSettingsButton(accessibilityLabel: "Summarizer settings") {
            showSettings = true
          }
        }
      }
      .sheet(isPresented: $showSettings) {
        TextSummarizerSettingsSheet(viewModel: viewModel)
      }
    }
  }

  private var modeSelectorRow: some View {
    HStack(spacing: 8) {
      Text("SUMMARY MODE")
        .font(.caption.weight(.semibold))
        .foregroundStyle(.secondary)
        .tracking(0.5)

      Spacer(minLength: 4)

      HStack(spacing: 8) {
        ForEach(TextSummarizerModeOption.allCases) { mode in
          let isSelected = mode == viewModel.selectedMode
          Button {
            dismissKeyboard()
            viewModel.selectMode(mode)
          } label: {
            HStack(spacing: 5) {
              Image(systemName: mode == .keyPoints ? "list.bullet" : "text.alignleft")
                .font(.caption.weight(.semibold))
              Text(mode.label)
                .font(.subheadline.weight(isSelected ? .semibold : .medium))
            }
            .foregroundStyle(isSelected ? .white : .primary)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(
              Capsule()
                .fill(
                  isSelected
                    ? AnyShapeStyle(MediaPipePalette.brandGradient)
                    : AnyShapeStyle(MediaPipePalette.cardBackground)
                )
            )
            .overlay(
              Capsule()
                .strokeBorder(
                  isSelected ? .clear : MediaPipePalette.cardStroke,
                  lineWidth: 1
                )
            )
          }
          .buttonStyle(.plain)
          .accessibilityIdentifier("mode_\(mode.id)")
        }
      }
      .sensoryFeedback(.selection, trigger: viewModel.selectedMode)
    }
    .disabled(viewModel.isBusy)
    .opacity(viewModel.isBusy ? 0.65 : 1.0)
  }

  private func dismissKeyboard() {
    #if canImport(UIKit)
    UIApplication.shared.sendAction(
      #selector(UIResponder.resignFirstResponder),
      to: nil,
      from: nil,
      for: nil
    )
    #endif
  }
}

// MARK: - Summary Result Card

private struct SummaryResultCard: View {
  let mode: TextSummarizerModeOption
  let modelName: String
  let streamingText: String
  let result: TextSummarizerOutput?

  @State private var copied = false

  private var displayedText: String {
    result?.summary ?? streamingText.trimmingCharacters(in: .whitespacesAndNewlines)
  }

  private var bulletPoints: [String] {
    guard mode == .keyPoints else { return [] }
    if let result {
      return result.bulletPoints
    }
    return TextSummarizerOutput.parseBulletPoints(from: streamingText)
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack(spacing: 8) {
        Text("Summary")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(.secondary)

        Spacer(minLength: 4)

        if result == nil {
          HStack(spacing: 5) {
            ProgressView()
              .controlSize(.mini)
              .tint(MediaPipePalette.tealPrimary)
            Text("Streaming")
              .font(.caption.weight(.semibold))
          }
          .foregroundStyle(MediaPipePalette.tealPrimary)
          .padding(.horizontal, 10)
          .padding(.vertical, 4)
          .background(MediaPipePalette.tealContainer, in: Capsule())
        } else if let result, result.wordReductionPercent > 0 {
          HStack(spacing: 4) {
            Image(systemName: "arrow.down.right.and.arrow.up.left")
              .font(.caption2.weight(.semibold))
            Text("\(result.wordReductionPercent)% shorter")
              .font(.caption.weight(.semibold).monospacedDigit())
          }
          .foregroundStyle(MediaPipePalette.statusPositive)
          .padding(.horizontal, 9)
          .padding(.vertical, 4)
          .background(MediaPipePalette.statusPositive.opacity(0.14), in: Capsule())
          .accessibilityIdentifier("reduction_badge")
        } else if let result {
          Text("\(result.summaryWordCount) words")
            .font(.caption.weight(.semibold).monospacedDigit())
            .foregroundStyle(MediaPipePalette.onTealContainer)
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(MediaPipePalette.tealContainer, in: Capsule())
            .accessibilityIdentifier("reduction_badge")
        }

        if !displayedText.isEmpty {
          Button {
            copySummary()
          } label: {
            HStack(spacing: 4) {
              Image(systemName: copied ? "checkmark" : "doc.on.doc")
                .font(.caption2.weight(.semibold))
              Text(copied ? "Copied" : "Copy")
                .font(.caption.weight(.semibold))
            }
            .foregroundStyle(copied ? MediaPipePalette.statusPositive : MediaPipePalette.tealPrimary)
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .mediaPipeGlassPill(
              tint: copied ? MediaPipePalette.statusPositive : MediaPipePalette.tealPrimary
            )
          }
          .buttonStyle(.plain)
          .sensoryFeedback(.success, trigger: copied)
          .accessibilityLabel("Copy summary")
          .accessibilityIdentifier("copy_summary_button")
        }
      }

      summaryBody
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(
          RoundedRectangle(cornerRadius: 14, style: .continuous)
            .fill(MediaPipePalette.recessedSurface)
        )
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("summary_output_text")

      if let result {
        HStack(spacing: 8) {
          MediaPipeMetricTile(
            label: "Latency",
            value: Self.formattedLatency(result.inferenceTimeMs),
            systemImage: "bolt.fill"
          )
          .accessibilityIdentifier("metric_inference_time")

          MediaPipeMetricTile(
            label: "Mode",
            value: result.mode.label,
            systemImage: result.mode == .keyPoints ? "list.bullet" : "text.alignleft"
          )
          .accessibilityIdentifier("metric_mode")

          MediaPipeMetricTile(
            label: "Model",
            value: modelName,
            systemImage: "cube.fill"
          )
          .accessibilityIdentifier("metric_model")
        }
      }
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 22)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("result_card")
  }

  @ViewBuilder
  private var summaryBody: some View {
    if mode == .keyPoints && !bulletPoints.isEmpty {
      VStack(alignment: .leading, spacing: 6) {
        ForEach(Array(bulletPoints.enumerated()), id: \.offset) { _, point in
          HStack(alignment: .top, spacing: 8) {
            Circle()
              .fill(MediaPipePalette.tealPrimary)
              .frame(width: 5.5, height: 5.5)
              .padding(.top, 6.5)

            Text(point)
              .font(.subheadline)
              .foregroundStyle(.primary)
              .lineSpacing(1.5)
              .fixedSize(horizontal: false, vertical: true)
              .textSelection(.enabled)
          }
        }
      }
    } else {
      Text(displayedText)
        .font(.subheadline)
        .foregroundStyle(.primary)
        .lineSpacing(2.5)
        .fixedSize(horizontal: false, vertical: true)
        .textSelection(.enabled)
    }
  }

  private func copySummary() {
    #if canImport(UIKit)
    UIPasteboard.general.string = displayedText
    #endif
    withAnimation(.snappy(duration: 0.2)) {
      copied = true
    }
    Task {
      try? await Task.sleep(for: .seconds(1.8))
      withAnimation(.snappy(duration: 0.2)) {
        copied = false
      }
    }
  }

  private static func formattedLatency(_ ms: Double) -> String {
    if ms >= 1000.0 {
      return String(format: "%.2f s", ms / 1000.0)
    }
    return String(format: "%.0f ms", ms)
  }
}

// MARK: - Settings Sheet

private struct TextSummarizerSettingsSheet: View {
  @Bindable var viewModel: TextSummarizerViewModel

  var body: some View {
    MediaPipeSettingsSheet(title: "Summarizer Settings") {
      Section {
        Picker(
          "Summary Mode",
          selection: Binding(
            get: { viewModel.selectedMode },
            set: { viewModel.selectMode($0) }
          )
        ) {
          ForEach(TextSummarizerModeOption.allCases) { mode in
            Text(mode.label).tag(mode)
          }
        }
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_mode")
      } header: {
        Text("Summary Style")
      } footer: {
        Text(viewModel.selectedMode.summary)
      }

      Section {
        Picker(
          "Max Tokens",
          selection: Binding(
            get: { viewModel.maxTokens },
            set: { viewModel.selectMaxTokens($0) }
          )
        ) {
          ForEach(TextSummarizerMaxTokensOption.allCases) { option in
            Text(option.displayLabel).tag(option)
          }
        }
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_max_tokens")

        Toggle(
          isOn: Binding(
            get: { viewModel.streamingEnabled },
            set: { viewModel.setStreamingEnabled($0) }
          )
        ) {
          VStack(alignment: .leading, spacing: 2) {
            Text("Stream Response")
            Text("Display summary tokens incrementally as they generate.")
              .font(.caption)
              .foregroundStyle(.secondary)
          }
        }
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("switch_streaming")
      } header: {
        Text("Generation")
      } footer: {
        Text("Maximum combined input and output token budget allocated for Gemma 200M.")
      }

      Section("Diagnostics") {
        LabeledContent("Accelerator", value: "CPU")
        LabeledContent("Model", value: viewModel.selectedModel.fullName)
        LabeledContent("Asset", value: viewModel.selectedModel.assetFilename)
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent(
            "Model Load",
            value: loadMs >= 1000.0
              ? String(format: "%.2f s", loadMs / 1000.0)
              : String(format: "%.1f ms", loadMs)
          )
        }
      }
    }
  }
}

#Preview {
  TextSummarizerView()
}
