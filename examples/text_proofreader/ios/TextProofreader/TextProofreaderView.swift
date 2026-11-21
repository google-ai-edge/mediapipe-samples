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

/// Sleek SwiftUI screen for the MediaPipe Text Proofreader sample app.
public struct TextProofreaderView: View {
  @State private var viewModel: TextProofreaderViewModel
  @State private var showSettings = false

  public init(viewModel: TextProofreaderViewModel = TextProofreaderViewModel()) {
    _viewModel = State(initialValue: viewModel)
  }

  public var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 11) {
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

          MediaPipeChipBar(
            title: "Examples",
            items: ProofreaderPreset.defaults,
            selectedID: viewModel.activePresetID,
            label: \.label,
            accessibilityID: { "preset_\($0.id)" },
            onSelect: { preset in
              dismissKeyboard()
              viewModel.applyPreset(preset)
            }
          )

          MediaPipeTextInputCard(
            label: "Input Text",
            placeholder: "Enter text with grammar, spelling, or punctuation errors…",
            text: $viewModel.inputText,
            inputAccessibilityID: "input_text",
            clearAccessibilityID: "clear_text"
          )

          MediaPipePrimaryButton(
            title: "Proofread",
            busyTitle: viewModel.isModelLoading
              ? "Loading Model…"
              : (viewModel.isStreamingEnabled ? "Streaming…" : "Proofreading…"),
            systemImage: "text.badge.checkmark",
            isBusy: viewModel.isBusy,
            isEnabled: viewModel.canProofread
          ) {
            dismissKeyboard()
            viewModel.triggerProofread()
          }
          .accessibilityIdentifier("proofread_button")

          if viewModel.result != nil || !viewModel.streamingText.isEmpty {
            ProofreadResultCard(
              streamingText: viewModel.streamingText,
              result: viewModel.result,
              displayMode: $viewModel.displayMode,
              modelName: viewModel.selectedModel.displayName,
              onApplyFix: {
                dismissKeyboard()
                viewModel.applyCorrectedToInput()
              }
            )
            .transition(.scale(scale: 0.96).combined(with: .opacity))
          }
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 20)
        .animation(.snappy(duration: 0.28), value: viewModel.errorMessage != nil)
        .animation(
          .spring(response: 0.36, dampingFraction: 0.84),
          value: viewModel.result != nil || !viewModel.streamingText.isEmpty
        )
      }
      .background(MediaPipePalette.canvasBackground.ignoresSafeArea())
      .scrollDismissesKeyboard(.interactively)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          MediaPipeNavigationTitle(
            title: "Text Proofreader",
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
        TextProofreaderSettingsSheet(viewModel: viewModel)
      }
    }
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

// MARK: - Proofread Result Card

private struct ProofreadResultCard: View {
  @Environment(\.colorScheme) private var colorScheme

  let streamingText: String
  let result: TextProofreaderOutput?
  @Binding var displayMode: ProofreadDisplayMode
  let modelName: String
  let onApplyFix: () -> Void

  @State private var copiedFeedback = false
  @State private var copyTrigger = 0

  private var textToCopy: String {
    result?.proofreadText ?? streamingText
  }

  private var hasDiffCorrections: Bool {
    guard let result else { return false }
    return !result.corrections.isEmpty
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      headerRow

      if result != nil && hasDiffCorrections {
        Picker("View Mode", selection: $displayMode) {
          ForEach(ProofreadDisplayMode.allCases) { mode in
            Text(mode.rawValue).tag(mode)
          }
        }
        .pickerStyle(.segmented)
        .accessibilityIdentifier("result_display_mode_picker")
      }

      outputSurface

      if let result {
        if result.hasEdits {
          actionLegendRow(for: result)
        }

        HStack(spacing: 8) {
          MediaPipeMetricTile(
            label: "Latency",
            value: String(format: "%.1f ms", result.inferenceTimeMs),
            systemImage: "bolt.fill"
          )
          .accessibilityIdentifier("metric_inference_time")

          MediaPipeMetricTile(
            label: "Edits",
            value: "\(result.editCount)",
            systemImage: "pencil.line"
          )
          .accessibilityIdentifier("metric_edit_count")

          MediaPipeMetricTile(
            label: "Model",
            value: modelName,
            systemImage: "cube.fill"
          )
        }
      }
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 22)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("result_card")
  }

  private var headerRow: some View {
    HStack(spacing: 8) {
      Text("Proofread Result")
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(.secondary)

      Spacer(minLength: 4)

      if let result {
        statusPill(for: result)
      } else {
        HStack(spacing: 5) {
          ProgressView()
            .controlSize(.mini)
            .tint(MediaPipePalette.tealPrimary)
          Text("Streaming")
            .font(.caption.weight(.semibold))
        }
        .foregroundStyle(MediaPipePalette.tealPrimary)
        .padding(.horizontal, 9)
        .padding(.vertical, 4)
        .background(MediaPipePalette.tealContainer.opacity(0.8), in: Capsule())
      }

      Button {
        copyToClipboard()
      } label: {
        Image(systemName: copiedFeedback ? "checkmark" : "doc.on.doc")
          .font(.footnote.weight(.semibold))
          .foregroundStyle(
            copiedFeedback ? MediaPipePalette.statusPositive : MediaPipePalette.tealPrimary
          )
          .frame(width: 30, height: 30)
          .mediaPipeGlassPill(
            tint: copiedFeedback ? MediaPipePalette.statusPositive : MediaPipePalette.tealPrimary
          )
      }
      .buttonStyle(.plain)
      .disabled(textToCopy.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
      .sensoryFeedback(.success, trigger: copyTrigger)
      .accessibilityLabel(copiedFeedback ? "Copied proofread text" : "Copy proofread text")
      .accessibilityIdentifier("copy_result_button")
    }
  }

  @ViewBuilder
  private func statusPill(for result: TextProofreaderOutput) -> some View {
    let noEdits = !result.hasEdits
    let badgeColor = noEdits ? MediaPipePalette.statusPositive : MediaPipePalette.tealPrimary
    let badgeIcon = noEdits ? "checkmark.seal.fill" : "wand.and.stars"
    let badgeTitle =
      noEdits
      ? "Clean"
      : "\(result.editCount) \(result.editCount == 1 ? "edit" : "edits")"

    HStack(spacing: 4) {
      Image(systemName: badgeIcon)
        .font(.caption2.weight(.semibold))
      Text(badgeTitle)
        .font(.caption.weight(.semibold))
    }
    .foregroundStyle(badgeColor)
    .padding(.horizontal, 9)
    .padding(.vertical, 4)
    .background(badgeColor.opacity(0.14), in: Capsule())
  }

  private var outputSurface: some View {
    VStack(alignment: .leading, spacing: 8) {
      if let result {
        if displayMode == .diff && hasDiffCorrections {
          Text(buildDiffAttributedString(from: result.corrections))
            .font(.body)
            .lineSpacing(3)
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("diff_output_text")
        } else {
          Text(result.proofreadText)
            .font(.body)
            .lineSpacing(3)
            .foregroundStyle(.primary)
            .textSelection(.enabled)
            .frame(maxWidth: .infinity, alignment: .leading)
            .accessibilityIdentifier("proofread_output_text")
        }
      } else {
        Text(streamingText)
          .font(.body)
          .lineSpacing(3)
          .foregroundStyle(.primary)
          .frame(maxWidth: .infinity, alignment: .leading)
          .accessibilityIdentifier("streaming_output_text")
      }
    }
    .padding(13)
    .frame(maxWidth: .infinity, alignment: .leading)
    .background(
      RoundedRectangle(cornerRadius: 14, style: .continuous)
        .fill(MediaPipePalette.recessedSurface)
    )
  }

  private func actionLegendRow(for result: TextProofreaderOutput) -> some View {
    HStack(spacing: 12) {
      if displayMode == .diff && result.editCount > 0 {
        HStack(spacing: 5) {
          Circle()
            .fill(deletionForeground)
            .frame(width: 7, height: 7)
          Text("Removed")
            .font(.caption2.weight(.medium))
            .foregroundStyle(.secondary)
        }

        HStack(spacing: 5) {
          Circle()
            .fill(insertionForeground)
            .frame(width: 7, height: 7)
          Text("Added")
            .font(.caption2.weight(.medium))
            .foregroundStyle(.secondary)
        }
      }

      Spacer()

      Button(action: onApplyFix) {
        HStack(spacing: 4) {
          Image(systemName: "arrow.uturn.up")
            .font(.caption2.weight(.semibold))
          Text("Apply Fix")
            .font(.caption.weight(.semibold))
        }
        .foregroundStyle(MediaPipePalette.tealPrimary)
      }
      .buttonStyle(.plain)
      .accessibilityIdentifier("apply_fix_button")
    }
    .padding(.horizontal, 2)
  }

  private var isDarkMode: Bool {
    colorScheme == .dark
  }

  private var insertionForeground: Color {
    isDarkMode
      ? Color(red: 0.56, green: 0.95, blue: 0.72)
      : Color(red: 0.04, green: 0.44, blue: 0.24)
  }

  private var insertionBackground: Color {
    isDarkMode
      ? Color(red: 0.10, green: 0.32, blue: 0.20)
      : Color(red: 0.84, green: 0.96, blue: 0.88)
  }

  private var deletionForeground: Color {
    isDarkMode
      ? Color(red: 1.0, green: 0.68, blue: 0.70)
      : Color(red: 0.69, green: 0.11, blue: 0.18)
  }

  private var deletionBackground: Color {
    isDarkMode
      ? Color(red: 0.40, green: 0.13, blue: 0.16)
      : Color(red: 0.99, green: 0.87, blue: 0.88)
  }

  private func buildDiffAttributedString(
    from corrections: [TextProofreaderCorrection]
  ) -> AttributedString {
    var combined = AttributedString()
    for correction in corrections {
      var segment = AttributedString(correction.text)
      switch correction.type {
      case .same:
        segment.foregroundColor = .primary
      case .insertion:
        segment.foregroundColor = insertionForeground
        segment.backgroundColor = insertionBackground
        segment.font = .body.weight(.semibold)
      case .deletion:
        segment.foregroundColor = deletionForeground
        segment.backgroundColor = deletionBackground
        segment.strikethroughStyle = Text.LineStyle(pattern: .solid, color: deletionForeground)
      }
      combined.append(segment)
    }
    return combined
  }

  private func copyToClipboard() {
    let text = textToCopy.trimmingCharacters(in: .whitespacesAndNewlines)
    guard !text.isEmpty else { return }
    #if canImport(UIKit)
    UIPasteboard.general.string = text
    #endif
    copyTrigger += 1
    withAnimation(.snappy(duration: 0.2)) {
      copiedFeedback = true
    }
    Task { @MainActor in
      try? await Task.sleep(for: .seconds(1.5))
      withAnimation(.snappy(duration: 0.2)) {
        copiedFeedback = false
      }
    }
  }
}

// MARK: - Settings Sheet

private struct TextProofreaderSettingsSheet: View {
  @Bindable var viewModel: TextProofreaderViewModel

  var body: some View {
    MediaPipeSettingsSheet(title: "Model & Settings") {
      Section {
        VStack(alignment: .leading, spacing: 8) {
          Text("Max Tokens")
            .font(.subheadline.weight(.medium))
          Picker(
            "Max Tokens",
            selection: Binding(
              get: { viewModel.maxTokensOption },
              set: { viewModel.selectMaxTokens($0) }
            )
          ) {
            ForEach(TextProofreaderMaxTokensOption.allCases) { option in
              Text(option.label).tag(option)
            }
          }
          .pickerStyle(.segmented)
          .labelsHidden()
          .disabled(viewModel.isBusy)
          .accessibilityIdentifier("picker_max_tokens")
        }
        .padding(.vertical, 2)

        Toggle(
          isOn: Binding(
            get: { viewModel.isStreamingEnabled },
            set: { viewModel.setStreamingEnabled($0) }
          )
        ) {
          VStack(alignment: .leading, spacing: 2) {
            Text("Stream Output")
            Text("Display proofread text incrementally as tokens are generated.")
              .font(.caption)
              .foregroundStyle(.secondary)
          }
        }
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("switch_streaming")
      } header: {
        Text("Generation")
      } footer: {
        Text(
          "Maximum token budget allocated for the Gemma 200M proofreading context. Changing Max Tokens reloads the model."
        )
      }

      Section("Diagnostics") {
        LabeledContent("Accelerator", value: viewModel.selectedDelegate.displayName)
        LabeledContent("Model", value: viewModel.selectedModel.fullName)
        LabeledContent("Asset", value: viewModel.selectedModel.assetFilename)
        LabeledContent("Max Tokens", value: "\(viewModel.maxTokensOption.tokenCount)")
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent("Model Load", value: String(format: "%.1f ms", loadMs))
        }
      }
    }
  }
}

#Preview {
  TextProofreaderView()
}
