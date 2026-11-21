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

/// Sleek SwiftUI screen for the MediaPipe Language Detector sample app.
public struct LanguageDetectorView: View {
  @State private var viewModel: LanguageDetectorViewModel
  @State private var showSettings = false

  public init(viewModel: LanguageDetectorViewModel = LanguageDetectorViewModel()) {
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
            items: LanguagePreset.defaults,
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
            placeholder: "Enter or paste text in any language…",
            text: $viewModel.inputText,
            inputAccessibilityID: "input_text",
            clearAccessibilityID: "clear_text"
          )

          MediaPipePrimaryButton(
            title: "Detect Language",
            busyTitle: viewModel.isModelLoading ? "Loading Model…" : "Detecting…",
            systemImage: "globe",
            isBusy: viewModel.isBusy,
            isEnabled: viewModel.canDetect
          ) {
            dismissKeyboard()
            viewModel.triggerDetect()
          }
          .accessibilityIdentifier("detect_button")

          if let result = viewModel.result {
            DetectedLanguageResultCard(result: result)
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
            title: "Language Detector",
            subtitle: viewModel.headerSubtitle
          )
        }
        ToolbarItem(placement: .topBarTrailing) {
          MediaPipeSettingsButton(accessibilityLabel: "Detector settings") {
            showSettings = true
          }
        }
      }
      .sheet(isPresented: $showSettings) {
        LanguageDetectorSettingsSheet(viewModel: viewModel)
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

// MARK: - Hero Detected Language Result Card

private struct DetectedLanguageResultCard: View {
  let result: LanguageDetectorDetectionResult

  private var topPrediction: LanguageDetectorPredictionItem? {
    result.topPrediction
  }

  private var clampedTopProbability: Double {
    min(max(topPrediction?.probability ?? 0.0, 0.0), 1.0)
  }

  private func confidenceTier(for probability: Double) -> (title: String, color: Color, icon: String) {
    if probability >= 0.85 {
      return ("High Confidence", MediaPipePalette.statusPositive, "checkmark.seal.fill")
    } else if probability >= 0.50 {
      return ("Moderate Confidence", MediaPipePalette.tealPrimary, "circle.lefthalf.filled")
    } else {
      return ("Low Confidence", MediaPipePalette.statusModerate, "questionmark.circle.fill")
    }
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 14) {
      HStack(alignment: .center) {
        Text("Detected Language")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(.secondary)

        Spacer()

        if let top = topPrediction {
          Text(top.languageCode.uppercased())
            .font(.caption.weight(.bold).monospaced())
            .foregroundStyle(MediaPipePalette.onTealContainer)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(MediaPipePalette.tealContainer, in: Capsule())
            .accessibilityIdentifier("top_language_code")
        }
      }

      if let top = topPrediction {
        let tier = confidenceTier(for: top.probability)

        HStack(alignment: .center, spacing: 16) {
          ZStack {
            Circle()
              .stroke(MediaPipePalette.recessedSurface, lineWidth: 9)

            Circle()
              .trim(from: 0, to: clampedTopProbability)
              .stroke(
                MediaPipePalette.brandGradient,
                style: StrokeStyle(lineWidth: 9, lineCap: .round)
              )
              .rotationEffect(.degrees(-90))
              .animation(
                .spring(response: 0.5, dampingFraction: 0.8),
                value: clampedTopProbability
              )

            Text("\(Int((clampedTopProbability * 100.0).rounded()))%")
              .font(.headline.weight(.bold).monospacedDigit())
              .foregroundStyle(.primary)
              .contentTransition(.numericText())
          }
          .frame(width: 72, height: 72)

          VStack(alignment: .leading, spacing: 6) {
            Text(top.displayName)
              .font(.system(size: 32, weight: .bold, design: .rounded))
              .foregroundStyle(MediaPipePalette.tealPrimary)
              .lineLimit(1)
              .minimumScaleFactor(0.75)
              .contentTransition(.interpolate)
              .accessibilityIdentifier("top_language")

            HStack(spacing: 5) {
              Image(systemName: tier.icon)
                .font(.caption2.weight(.semibold))
              Text(tier.title)
                .font(.caption.weight(.semibold))
            }
            .foregroundStyle(tier.color)
            .padding(.horizontal, 9)
            .padding(.vertical, 4)
            .background(tier.color.opacity(0.14), in: Capsule())
          }

          Spacer(minLength: 0)
        }
      } else {
        HStack(spacing: 10) {
          Image(systemName: "questionmark.circle")
            .font(.title3)
            .foregroundStyle(.secondary)
          Text("No confident language match found above the score threshold.")
            .font(.subheadline)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 6)
      }

      if !result.predictions.isEmpty {
        VStack(alignment: .leading, spacing: 10) {
          Text("Language Breakdown")
            .font(.caption.weight(.semibold))
            .foregroundStyle(.secondary)
            .textCase(.uppercase)
            .tracking(0.5)

          ForEach(Array(result.predictions.enumerated()), id: \.element.id) { index, prediction in
            LanguageProbabilityRow(prediction: prediction, isTop: index == 0)
          }
        }
        .padding(.top, 2)
      }

      HStack(spacing: 8) {
        MediaPipeMetricTile(
          label: "Latency",
          value: String(format: "%.1f ms", result.inferenceTimeMs),
          systemImage: "bolt.fill"
        )
        .accessibilityIdentifier("metric_inference_time")

        if let top = topPrediction {
          MediaPipeMetricTile(
            label: "Top Code",
            value: top.languageCode,
            systemImage: "globe"
          )
          .accessibilityIdentifier("metric_top_code")

          MediaPipeMetricTile(
            label: "Score",
            value: String(format: "%.3f", top.probability),
            systemImage: "chart.bar.fill"
          )
          .accessibilityIdentifier("metric_top_score")
        }
      }
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 22)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("result_card")
  }
}

private struct LanguageProbabilityRow: View {
  let prediction: LanguageDetectorPredictionItem
  let isTop: Bool

  private var clampedProbability: Double {
    min(max(prediction.probability, 0.0), 1.0)
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 5) {
      HStack(alignment: .firstTextBaseline, spacing: 6) {
        Text(prediction.displayName)
          .font(.subheadline.weight(isTop ? .semibold : .medium))
          .foregroundStyle(.primary)
          .lineLimit(1)

        Text(prediction.languageCode)
          .font(.caption.weight(.medium).monospaced())
          .foregroundStyle(.secondary)
          .padding(.horizontal, 6)
          .padding(.vertical, 1.5)
          .background(MediaPipePalette.recessedSurface, in: Capsule())

        Spacer()

        Text(String(format: "%.1f%%", clampedProbability * 100.0))
          .font(.subheadline.weight(.medium).monospacedDigit())
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
                : AnyShapeStyle(MediaPipePalette.tealPrimary.opacity(0.55))
            )
            .frame(width: max(6, proxy.size.width * clampedProbability))
            .animation(.spring(response: 0.45, dampingFraction: 0.82), value: clampedProbability)
        }
      }
      .frame(height: 7)
    }
    .accessibilityElement(children: .combine)
    .accessibilityIdentifier("language_row_\(prediction.languageCode)")
  }
}

// MARK: - Settings Sheet

private struct LanguageDetectorSettingsSheet: View {
  @Bindable var viewModel: LanguageDetectorViewModel

  var body: some View {
    MediaPipeSettingsSheet(title: "Detector Settings") {
      Section {
        VStack(alignment: .leading, spacing: 10) {
          Text("Max Results")
            .font(.subheadline.weight(.medium))

          Picker(
            "Max Results",
            selection: Binding(
              get: { viewModel.maxResultsOption },
              set: { viewModel.selectMaxResults($0) }
            )
          ) {
            ForEach(LanguageDetectorMaxResultsOption.allCases) { option in
              Text(option.label).tag(option)
            }
          }
          .pickerStyle(.segmented)
          .disabled(viewModel.isBusy)
          .accessibilityIdentifier("picker_max_results")
        }
        .padding(.vertical, 2)
      } header: {
        Text("Predictions")
      } footer: {
        Text("Limits the maximum number of top-ranked language predictions returned per inference.")
      }

      Section {
        LabeledContent("Accelerator", value: viewModel.selectedDelegate.displayName)
        LabeledContent("Asset", value: LanguageDetectorHelper.modelAssetFilename)
        LabeledContent("Score Threshold", value: String(format: "%.3f", viewModel.scoreThreshold))
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent("Model Load", value: String(format: "%.1f ms", loadMs))
        }
      } header: {
        Text("Diagnostics")
      } footer: {
        Text(
          "Language Detector runs on CPU because string input tensors are not supported by the iOS Metal GPU delegate."
        )
      }
    }
  }
}

#Preview {
  LanguageDetectorView()
}
