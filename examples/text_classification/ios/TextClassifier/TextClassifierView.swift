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

/// Sleek SwiftUI screen for the MediaPipe Text Classifier sample app.
public struct TextClassifierView: View {
  @State private var viewModel: TextClassifierViewModel
  @State private var showSettings = false

  public init(viewModel: TextClassifierViewModel = TextClassifierViewModel()) {
    _viewModel = State(initialValue: viewModel)
  }

  public var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 12) {
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
            items: TextClassificationPreset.defaults,
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
            placeholder: "Enter a sentence, review, or paragraph to analyze sentiment…",
            text: $viewModel.inputText,
            inputAccessibilityID: "input_text",
            clearAccessibilityID: "clear_text"
          )

          MediaPipePrimaryButton(
            title: "Classify Sentiment",
            busyTitle: viewModel.isModelLoading ? "Loading Model…" : "Classifying…",
            systemImage: "sparkles",
            isBusy: viewModel.isBusy,
            isEnabled: viewModel.canClassify
          ) {
            dismissKeyboard()
            viewModel.triggerClassify()
          }
          .accessibilityIdentifier("classify_button")

          if let result = viewModel.result {
            SentimentResultCard(
              result: result,
              modelName: viewModel.selectedModel.displayName
            )
            .transition(.scale(scale: 0.96).combined(with: .opacity))
          }
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 20)
        .animation(.snappy(duration: 0.28), value: viewModel.errorMessage != nil)
        .animation(.spring(response: 0.36, dampingFraction: 0.84), value: viewModel.result)
      }
      .background(MediaPipePalette.canvasBackground.ignoresSafeArea())
      .scrollDismissesKeyboard(.interactively)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          MediaPipeNavigationTitle(
            title: "Text Classifier",
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
        TextClassifierSettingsSheet(viewModel: viewModel)
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

// MARK: - Hero Sentiment Result Card

private struct SentimentResultCard: View {
  let result: TextClassifierOutput
  let modelName: String

  private var topCategory: TextClassifierCategory? {
    result.topCategory
  }

  private var topFraction: Double {
    guard let score = topCategory?.score else { return 0 }
    return min(max(Double(score), 0.0), 1.0)
  }

  private func style(for polarity: SentimentPolarity) -> (color: Color, icon: String) {
    switch polarity {
    case .positive:
      return (MediaPipePalette.statusPositive, "hand.thumbsup.fill")
    case .negative:
      return (MediaPipePalette.statusError, "hand.thumbsdown.fill")
    case .other:
      return (MediaPipePalette.tealPrimary, "tag.fill")
    }
  }

  private func ringGradient(for polarity: SentimentPolarity) -> LinearGradient {
    switch polarity {
    case .positive:
      return LinearGradient(
        colors: [MediaPipePalette.tealPrimary, MediaPipePalette.statusPositive],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
    case .negative:
      return LinearGradient(
        colors: [MediaPipePalette.statusModerate, MediaPipePalette.statusError],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
      )
    case .other:
      return MediaPipePalette.brandGradient
    }
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 14) {
      HStack {
        Text("Sentiment Analysis")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(.secondary)

        Spacer()

        if let top = topCategory {
          let badge = style(for: top.polarity)
          HStack(spacing: 5) {
            Image(systemName: badge.icon)
              .font(.caption2.weight(.semibold))
            Text(top.displayLabel)
              .font(.caption.weight(.semibold))
          }
          .foregroundStyle(badge.color)
          .padding(.horizontal, 10)
          .padding(.vertical, 4)
          .background(badge.color.opacity(0.14), in: Capsule())
          .accessibilityIdentifier("top_category")
        }
      }

      if let top = topCategory {
        let accent = style(for: top.polarity)
        HStack(alignment: .center, spacing: 16) {
          ZStack {
            Circle()
              .stroke(MediaPipePalette.recessedSurface, lineWidth: 9)

            Circle()
              .trim(from: 0, to: topFraction)
              .stroke(
                ringGradient(for: top.polarity),
                style: StrokeStyle(lineWidth: 9, lineCap: .round)
              )
              .rotationEffect(.degrees(-90))
              .animation(.spring(response: 0.5, dampingFraction: 0.8), value: topFraction)

            Text("\(Int((topFraction * 100).rounded()))%")
              .font(.headline.weight(.bold).monospacedDigit())
              .foregroundStyle(.primary)
              .contentTransition(.numericText())
          }
          .frame(width: 72, height: 72)

          VStack(alignment: .leading, spacing: 4) {
            Text(top.displayLabel)
              .font(.system(size: 32, weight: .bold, design: .rounded))
              .foregroundStyle(accent.color)
              .lineLimit(1)
              .minimumScaleFactor(0.8)

            Text(String(format: "Confidence %.3f", top.score))
              .font(.subheadline.weight(.medium).monospacedDigit())
              .foregroundStyle(.secondary)
              .contentTransition(.numericText())
              .accessibilityIdentifier("top_score")
          }

          Spacer(minLength: 0)
        }
      }

      VStack(spacing: 10) {
        ForEach(result.categories) { category in
          CategoryConfidenceRow(
            category: category,
            accentColor: style(for: category.polarity).color
          )
        }
      }

      HStack(spacing: 8) {
        MediaPipeMetricTile(
          label: "Latency",
          value: String(format: "%.1f ms", result.inferenceTimeMs),
          systemImage: "bolt.fill"
        )
        .accessibilityIdentifier("metric_inference_time")

        if let top = topCategory {
          MediaPipeMetricTile(
            label: "Confidence",
            value: String(format: "%.1f%%", min(max(Double(top.score), 0.0), 1.0) * 100.0),
            systemImage: "chart.bar.fill"
          )
          .accessibilityIdentifier("metric_confidence")
        }

        MediaPipeMetricTile(
          label: "Model",
          value: modelName,
          systemImage: "cube.fill"
        )
        .accessibilityIdentifier("metric_model")
      }
    }
    .padding(16)
    .mediaPipeCard(cornerRadius: 22)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("result_card")
  }
}

private struct CategoryConfidenceRow: View {
  let category: TextClassifierCategory
  let accentColor: Color

  private var clampedFraction: Double {
    min(max(Double(category.score), 0.0), 1.0)
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 5) {
      HStack(alignment: .firstTextBaseline) {
        HStack(spacing: 6) {
          Circle()
            .fill(accentColor)
            .frame(width: 7, height: 7)
          Text(category.displayLabel)
            .font(.subheadline.weight(.medium))
            .foregroundStyle(.primary)
        }

        Spacer()

        Text(String(format: "%.1f%% · %.3f", clampedFraction * 100.0, category.score))
          .font(.caption.weight(.medium).monospacedDigit())
          .foregroundStyle(.secondary)
          .contentTransition(.numericText())
      }

      GeometryReader { proxy in
        ZStack(alignment: .leading) {
          Capsule()
            .fill(MediaPipePalette.recessedSurface)

          Capsule()
            .fill(accentColor)
            .frame(width: max(proxy.size.width * clampedFraction, clampedFraction > 0 ? 6 : 0))
            .animation(.spring(response: 0.45, dampingFraction: 0.82), value: clampedFraction)
        }
      }
      .frame(height: 8)
    }
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("category_row_\(category.displayLabel)")
  }
}

// MARK: - Settings Sheet

private struct TextClassifierSettingsSheet: View {
  @Bindable var viewModel: TextClassifierViewModel

  var body: some View {
    MediaPipeSettingsSheet(title: "Model & Settings") {
      Section {
        Picker(
          "Model",
          selection: Binding(
            get: { viewModel.selectedModel },
            set: { viewModel.selectModel($0) }
          )
        ) {
          ForEach(TextClassifierModelOption.allCases) { model in
            Text(model.fullName).tag(model)
          }
        }
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_model")
      } header: {
        Text("Model")
      } footer: {
        Text(viewModel.selectedModel.summary)
      }

      Section {
        LabeledContent("Accelerator", value: "CPU")
        LabeledContent("Architecture", value: viewModel.selectedModel.architectureLabel)
        LabeledContent("Asset", value: viewModel.selectedModel.assetFilename)
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent("Model Load", value: String(format: "%.1f ms", loadMs))
        }
      } header: {
        Text("Diagnostics")
      } footer: {
        Text("Text classification models use integer token inputs and execute on CPU on iOS.")
      }
    }
  }
}

#Preview {
  TextClassifierView()
}
