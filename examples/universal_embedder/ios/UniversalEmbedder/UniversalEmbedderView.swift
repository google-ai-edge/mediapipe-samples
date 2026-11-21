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
import PhotosUI
import SwiftUI

/// Sleek SwiftUI screen for the MediaPipe Universal Embedder sample app.
public struct UniversalEmbedderView: View {
  @State private var viewModel: UniversalEmbedderViewModel
  @State private var showSettings = false
  @State private var swapCount = 0

  public init(viewModel: UniversalEmbedderViewModel = UniversalEmbedderViewModel()) {
    _viewModel = State(initialValue: viewModel)
  }

  public var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 10) {
          MediaPipeStatusStrip(
            status: viewModel.statusBadge,
            delegateLabel: viewModel.selectedDelegate.shortName,
            latencyMs: viewModel.result?.totalInferenceTimeMs
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
            items: MultimodalPairPreset.defaults,
            selectedID: viewModel.activePresetID,
            label: \.label,
            accessibilityID: { "preset_\($0.id)" },
            onSelect: { preset in
              dismissKeyboard()
              viewModel.applyPreset(preset)
            }
          )

          multimodalInputsSection

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
              l2Normalized: viewModel.l2Normalize
            )
            .transition(.scale(scale: 0.96).combined(with: .opacity))
          }
        }
        .padding(.horizontal, 16)
        .padding(.top, 6)
        .padding(.bottom, 20)
        .animation(.snappy(duration: 0.26), value: viewModel.errorMessage != nil)
        .animation(.spring(response: 0.36, dampingFraction: 0.84), value: viewModel.result)
      }
      .background(MediaPipePalette.canvasBackground.ignoresSafeArea())
      .scrollDismissesKeyboard(.interactively)
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .principal) {
          MediaPipeNavigationTitle(
            title: "Universal Embedder",
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
        UniversalEmbedderSettingsSheet(viewModel: viewModel)
      }
    }
  }

  private var multimodalInputsSection: some View {
    VStack(spacing: 6) {
      MultimodalSlotCard(
        label: "Input A",
        accessibilityPrefix: "slot_a",
        slot: $viewModel.slotA,
        thumbnailProvider: { viewModel.thumbnail(for: $0) },
        displayImage: viewModel.displayImage(for: viewModel.slotA),
        onSelectSample: { viewModel.selectSlotASamplePhoto($0) },
        onPickedImageData: { _ = viewModel.setSlotACustomImageData($0) }
      )

      swapDividerRow

      MultimodalSlotCard(
        label: "Input B",
        accessibilityPrefix: "slot_b",
        slot: $viewModel.slotB,
        thumbnailProvider: { viewModel.thumbnail(for: $0) },
        displayImage: viewModel.displayImage(for: viewModel.slotB),
        onSelectSample: { viewModel.selectSlotBSamplePhoto($0) },
        onPickedImageData: { _ = viewModel.setSlotBCustomImageData($0) }
      )
    }
  }

  private var swapDividerRow: some View {
    HStack(spacing: 12) {
      Rectangle()
        .fill(MediaPipePalette.cardStroke)
        .frame(height: 1)

      Button {
        dismissKeyboard()
        withAnimation(.spring(response: 0.3, dampingFraction: 0.72)) {
          swapCount += 1
          viewModel.swapInputs()
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
      .accessibilityLabel("Swap inputs")
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

// MARK: - Multimodal Input Slot Card

private struct MultimodalSlotCard: View {
  let label: String
  let accessibilityPrefix: String
  @Binding var slot: EmbedderSlotState
  let thumbnailProvider: (BundledSamplePhoto) -> UIImage?
  let displayImage: UIImage?
  let onSelectSample: (BundledSamplePhoto) -> Void
  let onPickedImageData: (Data) -> Void

  @State private var pickedPhotoItem: PhotosPickerItem?
  @FocusState private var isTextFocused: Bool

  var body: some View {
    VStack(alignment: .leading, spacing: 8) {
      headerRow

      switch slot.modality {
      case .text:
        textEditorContent
      case .image:
        imagePickerContent
      }
    }
    .padding(12)
    .mediaPipeCard(cornerRadius: 18, isHighlighted: isTextFocused && slot.modality == .text)
    .animation(.easeInOut(duration: 0.16), value: isTextFocused)
    .animation(.snappy(duration: 0.22), value: slot.modality)
    .onChange(of: pickedPhotoItem) { _, newItem in
      guard let newItem else { return }
      Task {
        if let data = try? await newItem.loadTransferable(type: Data.self) {
          onPickedImageData(data)
        }
        pickedPhotoItem = nil
      }
    }
  }

  private var headerRow: some View {
    HStack(spacing: 8) {
      Text(label)
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(MediaPipePalette.tealPrimary)

      Spacer(minLength: 4)

      if slot.modality == .text && !slot.text.isEmpty {
        Text("\(slot.text.count) chars")
          .font(.caption2.monospacedDigit())
          .foregroundStyle(.secondary)
      }

      modalityToggle
    }
  }

  private var modalityToggle: some View {
    HStack(spacing: 2) {
      ForEach(UniversalEmbedderModality.allCases) { mode in
        let isSelected = slot.modality == mode
        Button {
          if slot.modality != mode {
            isTextFocused = false
            slot.modality = mode
          }
        } label: {
          HStack(spacing: 4) {
            Image(systemName: mode.systemImage)
              .font(.caption2.weight(.semibold))
            Text(mode.label)
              .font(.caption.weight(isSelected ? .semibold : .medium))
          }
          .foregroundStyle(isSelected ? .white : .secondary)
          .padding(.horizontal, 10)
          .padding(.vertical, 5)
          .background(
            Capsule()
              .fill(
                isSelected
                  ? AnyShapeStyle(MediaPipePalette.brandGradient)
                  : AnyShapeStyle(Color.clear)
              )
          )
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("\(accessibilityPrefix)_mode_\(mode.rawValue)")
      }
    }
    .padding(2)
    .background(MediaPipePalette.recessedSurface, in: Capsule())
    .sensoryFeedback(.selection, trigger: slot.modality)
  }

  private var textEditorContent: some View {
    HStack(alignment: .top, spacing: 8) {
      TextField(
        "Enter text to embed…",
        text: $slot.text,
        axis: .vertical
      )
      .font(.subheadline)
      .lineLimit(2...4)
      .focused($isTextFocused)
      .accessibilityIdentifier("\(accessibilityPrefix)_text")

      if !slot.text.isEmpty {
        Button {
          slot.text = ""
        } label: {
          Image(systemName: "xmark.circle.fill")
            .font(.subheadline)
            .foregroundStyle(.tertiary)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Clear \(label)")
        .accessibilityIdentifier("\(accessibilityPrefix)_clear")
      }
    }
  }

  private var imagePickerContent: some View {
    VStack(alignment: .leading, spacing: 8) {
      HStack(spacing: 12) {
        Group {
          if let displayImage {
            Image(uiImage: displayImage)
              .resizable()
              .scaledToFill()
          } else {
            ZStack {
              MediaPipePalette.recessedSurface
              Image(systemName: "photo")
                .foregroundStyle(.secondary)
            }
          }
        }
        .frame(width: 58, height: 58)
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(
          RoundedRectangle(cornerRadius: 12, style: .continuous)
            .strokeBorder(MediaPipePalette.cardStroke, lineWidth: 1)
        )
        .accessibilityIdentifier("\(accessibilityPrefix)_image_preview")

        VStack(alignment: .leading, spacing: 2) {
          Text(slot.activeImageLabel)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.primary)
            .lineLimit(1)

          Text(slot.isUsingCustomImage ? "Photo library" : "Bundled sample")
            .font(.caption)
            .foregroundStyle(.secondary)
            .lineLimit(1)
        }

        Spacer(minLength: 4)

        PhotosPicker(
          selection: $pickedPhotoItem,
          matching: .images,
          photoLibrary: .shared()
        ) {
          HStack(spacing: 5) {
            Image(systemName: "photo.on.rectangle.angled")
              .font(.caption.weight(.semibold))
            Text("Photos")
              .font(.caption.weight(.semibold))
          }
          .foregroundStyle(MediaPipePalette.onTealContainer)
          .padding(.horizontal, 11)
          .padding(.vertical, 7)
          .background(MediaPipePalette.tealContainer, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Pick photo from library for \(label)")
        .accessibilityIdentifier("\(accessibilityPrefix)_pick_photo")
      }

      ScrollView(.horizontal, showsIndicators: false) {
        HStack(spacing: 7) {
          ForEach(BundledSamplePhoto.all) { photo in
            let isSelected = !slot.isUsingCustomImage && slot.samplePhoto == photo
            Button {
              onSelectSample(photo)
            } label: {
              Group {
                if let thumb = thumbnailProvider(photo) {
                  Image(uiImage: thumb)
                    .resizable()
                    .scaledToFill()
                } else {
                  MediaPipePalette.recessedSurface
                }
              }
              .frame(width: 44, height: 44)
              .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
              .overlay(
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                  .strokeBorder(
                    isSelected ? MediaPipePalette.tealPrimary : MediaPipePalette.cardStroke,
                    lineWidth: isSelected ? 2.5 : 1
                  )
              )
              .overlay(alignment: .topTrailing) {
                if isSelected {
                  Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.white, MediaPipePalette.tealPrimary)
                    .offset(x: 2, y: -2)
                }
              }
            }
            .buttonStyle(.plain)
            .accessibilityLabel(photo.label)
            .accessibilityIdentifier("\(accessibilityPrefix)_sample_\(photo.id)")
          }
        }
        .padding(.top, 4)
        .padding(.bottom, 2)
        .padding(.horizontal, 2)
      }
      .sensoryFeedback(.selection, trigger: slot.samplePhoto)
    }
  }
}

// MARK: - Similarity Result Card

private struct SimilarityResultCard: View {
  let result: UniversalEmbedderComparisonResult
  let l2Normalized: Bool

  private var clampedFraction: Double {
    min(max(result.similarity, 0.0), 1.0)
  }

  private var isCrossModal: Bool {
    result.firstModality != result.secondModality
  }

  private var matchTier: (title: String, color: Color, icon: String) {
    let strongThreshold = isCrossModal ? 0.55 : 0.78
    let moderateThreshold = isCrossModal ? 0.35 : 0.50
    if result.similarity >= strongThreshold {
      return ("Strong Match", MediaPipePalette.statusPositive, "checkmark.seal.fill")
    } else if result.similarity >= moderateThreshold {
      return ("Moderate Match", MediaPipePalette.tealPrimary, "circle.lefthalf.filled")
    } else {
      return ("Low Similarity", MediaPipePalette.statusModerate, "arrow.left.and.right")
    }
  }

  private var formattedLatency: String {
    if result.totalInferenceTimeMs >= 1_000 {
      return String(format: "%.2f s", result.totalInferenceTimeMs / 1_000.0)
    }
    return String(format: "%.0f ms", result.totalInferenceTimeMs)
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 11) {
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
            .stroke(MediaPipePalette.recessedSurface, lineWidth: 8.5)

          Circle()
            .trim(from: 0, to: clampedFraction)
            .stroke(
              MediaPipePalette.brandGradient,
              style: StrokeStyle(lineWidth: 8.5, lineCap: .round)
            )
            .rotationEffect(.degrees(-90))
            .animation(.spring(response: 0.5, dampingFraction: 0.8), value: clampedFraction)

          Text("\(Int((min(max(result.similarity, -1.0), 1.0) * 100).rounded()))%")
            .font(.headline.weight(.bold).monospacedDigit())
            .foregroundStyle(.primary)
            .contentTransition(.numericText())
        }
        .frame(width: 68, height: 68)

        VStack(alignment: .leading, spacing: 4) {
          Text(String(format: "%.3f", result.similarity))
            .font(.system(size: 36, weight: .bold, design: .rounded).monospacedDigit())
            .foregroundStyle(MediaPipePalette.tealPrimary)
            .contentTransition(.numericText())
            .accessibilityIdentifier("similarity_score")

          HStack(spacing: 6) {
            if l2Normalized {
              optionPill("L2 Normalized")
            } else {
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
          value: formattedLatency,
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
          label: "Modalities",
          value: result.modalitiesLabel,
          systemImage: "arrow.left.arrow.right"
        )
        .accessibilityIdentifier("metric_modalities")
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

private struct UniversalEmbedderSettingsSheet: View {
  @Bindable var viewModel: UniversalEmbedderViewModel

  var body: some View {
    MediaPipeSettingsSheet(title: "Model & Settings") {
      Section {
        Picker(
          "Accelerator",
          selection: Binding(
            get: { viewModel.selectedDelegate },
            set: { viewModel.selectDelegate($0) }
          )
        ) {
          ForEach(UniversalEmbedderDelegateOption.allCases) { delegate in
            Text(delegate.displayName).tag(delegate)
          }
        }
        .pickerStyle(.segmented)
        .disabled(viewModel.isBusy)
        .accessibilityIdentifier("picker_delegate")
      } header: {
        Text("Hardware")
      } footer: {
        Text(
          "Both text and vision encoders run on the selected accelerator. First GPU launch compiles Metal shaders into the app cache."
        )
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
      }

      Section("Diagnostics") {
        LabeledContent("Model", value: viewModel.selectedModel.fullName)
        LabeledContent("Accelerator", value: viewModel.selectedDelegate.displayName)
        LabeledContent("Asset", value: viewModel.selectedModel.assetFilename)
        LabeledContent(
          "Dimensions",
          value: "\(viewModel.result?.dimensions ?? viewModel.selectedModel.dimensions)d"
        )
        if let loadMs = viewModel.modelLoadTimeMs {
          LabeledContent(
            "Model Load",
            value: loadMs >= 1_000
              ? String(format: "%.2f s", loadMs / 1_000.0)
              : String(format: "%.0f ms", loadMs)
          )
        }
      }
    }
  }
}

#Preview {
  UniversalEmbedderView()
}
