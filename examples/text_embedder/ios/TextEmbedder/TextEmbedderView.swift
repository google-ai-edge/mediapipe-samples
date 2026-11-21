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

/// Sleek SwiftUI screen for the MediaPipe Text Embedder sample app.
public struct TextEmbedderView: View {
  @State private var viewModel: TextEmbedderViewModel
  @State private var showSettings = false
  @State private var swapCount = 0

  public init(viewModel: TextEmbedderViewModel = TextEmbedderViewModel()) {
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
            items: TextPairPreset.defaults,
            selectedID: viewModel.activePresetID,
            label: \.label,
            accessibilityID: { "preset_\($0.id)" },
            onSelect: { preset in
              dismissKeyboard()
              viewModel.applyPreset(preset)
            }
          )

          if viewModel.selectedModel.supportsFormatContext {
            VStack(alignment: .leading, spacing: 6) {
              MediaPipeChipBar(
                title: "Task Context",
                items: TextEmbedderContextMode.allCases,
                selectedID: viewModel.contextMode.id,
                label: \.label,
                accessibilityID: { "context_\($0.id)" },
                onSelect: { mode in
                  dismissKeyboard()
                  viewModel.selectContextMode(mode)
                }
              )

              Text(viewModel.contextMode.summary)
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            .transition(.opacity.combined(with: .move(edge: .top)))
          }

          textInputsSection

          MediaPipePrimaryButton(
            title: "Compare Similarity",
            busyTitle: viewModel.isModelLoading ? "Loading Model…" : "Comparing…",
            systemImage: "sparkles",
            isBusy: viewModel.isBusy,
            isEnabled: viewModel.canCompare
          ) {
            dismissKeyboard()
            viewModel.triggerCompare()
          }
          .accessibilityIdentifier("compare_button")

          if let result = viewModel.result {
            SimilarityResultCard(
              result: result,
              modelName: viewModel.selectedModel.displayName,
              l2Normalized: viewModel.l2Normalize
            )
            .transition(.scale(scale: 0.96).combined(with: .opacity))
          }
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 20)
        .animation(.snappy(duration: 0.28), value: viewModel.errorMessage != nil)
        .animation(.snappy(duration: 0.28), value: viewModel.selectedModel.supportsFormatContext)
        .animation(.spring(response: 0.36, dampingFraction: 0.84), value: viewModel.result)
      }
      .background(MediaPipePalette.canvasBackground.ignoresSafeArea())
      .scrollDismissesKeyboard(.interactively)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          MediaPipeNavigationTitle(
            title: "Text Embedder",
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
        TextEmbedderSettingsSheet(viewModel: viewModel)
      }
    }
  }

  private var usesDocumentRole: Bool {
    viewModel.selectedModel.supportsFormatContext && viewModel.contextMode.usesDocumentTitle
  }

  private var textInputsSection: some View {
    VStack(spacing: 7) {
      MediaPipeTextInputCard(
        label: usesDocumentRole ? "Query" : "Text A",
        placeholder: "Enter first sentence or passage…",
        text: $viewModel.firstText,
        inputAccessibilityID: "input_text_a",
        clearAccessibilityID: "clear_text_a"
      )

      swapDividerRow

      MediaPipeTextInputCard(
        label: usesDocumentRole ? "Document" : "Text B",
        placeholder: "Enter second sentence or passage…",
        text: $viewModel.secondText,
        optionalTitleLabel: usesDocumentRole ? "Title" : nil,
        optionalTitlePlaceholder: usesDocumentRole ? "Optional document title" : nil,
        optionalTitle: usesDocumentRole ? $viewModel.documentTitle : nil,
        inputAccessibilityID: "input_text_b",
        clearAccessibilityID: "clear_text_b"
      )
    }
  }

  private var swapDividerRow: some View {
    HStack(spacing: 12) {
      Rectangle()
        .fill(MediaPipePalette.cardStroke)
        .frame(height: 1)

      Button {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.72)) {
          swapCount += 1
          viewModel.swapTexts()
        }
      } label: {
        Image(systemName: "arrow.up.arrow.down")
          .font(.footnote.weight(.semibold))
          .foregroundStyle(MediaPipePalette.tealPrimary)
          .rotationEffect(.degrees(Double(swapCount) * 180))
          .frame(width: 34, height: 34)
          .mediaPipeGlassPill(tint: MediaPipePalette.tealPrimary)
      }
      .buttonStyle(.plain)
      .sensoryFeedback(.impact(flexibility: .soft), trigger: swapCount)
      .accessibilityLabel("Swap texts")
      .accessibilityIdentifier("swap_button")

      Rectangle()
        .fill(MediaPipePalette.cardStroke)
        .frame(height: 1)
    }
    .padding(.horizontal, 6)
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

// MARK: - Similarity Result Card

private struct SimilarityResultCard: View {
  let result: TextEmbedderComparisonResult
  let modelName: String
  let l2Normalized: Bool

  private var clampedFraction: Double {
    min(max(result.similarity, 0.0), 1.0)
  }

  private var matchTier: (title: String, color: Color, icon: String) {
    if result.similarity >= 0.80 {
      return ("Very High Match", MediaPipePalette.statusPositive, "checkmark.seal.fill")
    } else if result.similarity >= 0.55 {
      return ("Moderate Match", MediaPipePalette.tealPrimary, "circle.lefthalf.filled")
    } else {
      return ("Low Similarity", MediaPipePalette.statusModerate, "arrow.left.and.right")
    }
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      HStack {
        Text("Cosine Similarity")
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(.secondary)

        Spacer()

        HStack(spacing: 5) {
          Image(systemName: matchTier.icon)
            .font(.caption2.weight(.semibold))
          Text(matchTier.title)
            .font(.caption.weight(.semibold))
        }
        .foregroundStyle(matchTier.color)
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(matchTier.color.opacity(0.14), in: Capsule())
      }

      HStack(alignment: .center, spacing: 16) {
        ZStack {
          Circle()
            .stroke(MediaPipePalette.recessedSurface, lineWidth: 9)

          Circle()
            .trim(from: 0, to: clampedFraction)
            .stroke(
              MediaPipePalette.brandGradient,
              style: StrokeStyle(lineWidth: 9, lineCap: .round)
            )
            .rotationEffect(.degrees(-90))
            .animation(.spring(response: 0.5, dampingFraction: 0.8), value: clampedFraction)

          Text("\(Int((min(max(result.similarity, -1.0), 1.0) * 100).rounded()))%")
            .font(.headline.weight(.bold).monospacedDigit())
            .foregroundStyle(.primary)
            .contentTransition(.numericText())
        }
        .frame(width: 72, height: 72)

        VStack(alignment: .leading, spacing: 4) {
          Text(String(format: "%.3f", result.similarity))
            .font(.system(size: 38, weight: .bold, design: .rounded).monospacedDigit())
            .foregroundStyle(MediaPipePalette.tealPrimary)
            .contentTransition(.numericText())
            .accessibilityIdentifier("similarity_score")

          HStack(spacing: 6) {
            if l2Normalized {
              optionPill("L2 Normalized")
            }
            if result.isQuantized {
              optionPill("Quantized")
            }
            if !l2Normalized && !result.isQuantized {
              Text("Cosine score in [-1.0, 1.0]")
                .font(.caption)
                .foregroundStyle(.secondary)
            }
          }
        }

        Spacer(minLength: 0)
      }

      HStack(spacing: 8) {
        MediaPipeMetricTile(
          label: "Latency",
          value: String(format: "%.1f ms", result.inferenceTimeMs),
          systemImage: "bolt.fill"
        )
        .accessibilityIdentifier("metric_inference_time")

        if result.dimensions > 0 {
          MediaPipeMetricTile(
            label: "Dimensions",
            value: "\(result.dimensions)d",
            systemImage: "square.grid.3x3"
          )
          .accessibilityIdentifier("metric_dimensions")
        }

        MediaPipeMetricTile(
          label: "Model",
          value: modelName,
          systemImage: "cube.fill"
        )
      }
    }
    .padding(14)
    .mediaPipeCard(cornerRadius: 22)
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("result_card")
  }

  private func optionPill(_ text: String) -> some View {
    Text(text)
      .font(.caption2.weight(.semibold))
      .foregroundStyle(MediaPipePalette.onTealContainer)
      .padding(.horizontal, 8)
      .padding(.vertical, 3)
      .background(MediaPipePalette.tealContainer, in: Capsule())
  }
}

// MARK: - Settings Sheet

private struct TextEmbedderSettingsSheet: View {
  @Bindable var viewModel: TextEmbedderViewModel

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
          ForEach(TextEmbedderModelOption.allCases) { model in
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
        Picker(
          "Hardware Delegate",
          selection: Binding(
            get: { viewModel.selectedDelegate },
            set: { viewModel.selectDelegate($0) }
          )
        ) {
          ForEach(TextEmbedderDelegateOption.allCases) { delegate in
            Text(delegate.displayName).tag(delegate)
          }
        }
        .pickerStyle(.segmented)
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_delegate")
      } header: {
        Text("Accelerator")
      } footer: {
        Text("Text embedders run natively on CPU. Selecting GPU tests the Metal delegate without silent fallback.")
      }

      Section("Post-Processing") {
        Toggle(
          isOn: Binding(
            get: { viewModel.l2Normalize },
            set: { viewModel.setL2Normalize($0) }
          )
        ) {
          VStack(alignment: .leading, spacing: 2) {
            Text("L2 Normalization")
            Text("Normalize output vectors to unit length.")
              .font(.caption)
              .foregroundStyle(.secondary)
          }
        }
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("switch_l2_normalize")

        Toggle(
          isOn: Binding(
            get: { viewModel.quantize },
            set: { viewModel.setQuantize($0) }
          )
        ) {
          VStack(alignment: .leading, spacing: 2) {
            Text("Scalar Quantization")
            Text("Quantize embeddings to 8-bit integers.")
              .font(.caption)
              .foregroundStyle(.secondary)
          }
        }
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("switch_quantize")
      }

      Section("Diagnostics") {
        LabeledContent("Asset", value: viewModel.selectedModel.assetFilename)
        LabeledContent("Dimensions", value: "\(viewModel.selectedModel.dimensions)d")
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent("Model Load", value: String(format: "%.1f ms", loadMs))
        }
      }
    }
  }
}

#Preview {
  TextEmbedderView()
}
