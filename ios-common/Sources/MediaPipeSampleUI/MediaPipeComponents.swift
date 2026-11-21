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

import SwiftUI

/// Inline alert banner with an optional one-tap recovery action (e.g., "Use CPU" or "Retry").
public struct MediaPipeErrorBanner: View {
  let message: String
  let actionLabel: String?
  let onAction: (() -> Void)?

  public init(
    message: String,
    actionLabel: String? = nil,
    onAction: (() -> Void)? = nil
  ) {
    self.message = message
    self.actionLabel = actionLabel
    self.onAction = onAction
  }

  public var body: some View {
    HStack(alignment: .center, spacing: 12) {
      Image(systemName: "exclamationmark.triangle.fill")
        .font(.subheadline.weight(.semibold))
        .foregroundStyle(MediaPipePalette.statusError)

      Text(message)
        .font(.footnote)
        .foregroundStyle(.primary)
        .multilineTextAlignment(.leading)
        .frame(maxWidth: .infinity, alignment: .leading)

      if let actionLabel, let onAction {
        Button(action: onAction) {
          Text(actionLabel)
            .font(.footnote.weight(.semibold))
            .foregroundStyle(.white)
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(MediaPipePalette.statusError, in: Capsule())
        }
        .buttonStyle(.plain)
        .accessibilityIdentifier("error_banner_action")
      }
    }
    .padding(14)
    .background(
      RoundedRectangle(cornerRadius: 16, style: .continuous)
        .fill(MediaPipePalette.statusError.opacity(0.11))
    )
    .overlay(
      RoundedRectangle(cornerRadius: 16, style: .continuous)
        .strokeBorder(MediaPipePalette.statusError.opacity(0.28), lineWidth: 1)
    )
    .accessibilityElement(children: .contain)
    .accessibilityIdentifier("error_banner")
  }
}

/// Compact telemetry tile for displaying key/value metrics such as latency, dimensions, or model.
public struct MediaPipeMetricTile: View {
  let label: String
  let value: String
  let systemImage: String?

  public init(label: String, value: String, systemImage: String? = nil) {
    self.label = label
    self.value = value
    self.systemImage = systemImage
  }

  public var body: some View {
    VStack(alignment: .leading, spacing: 4) {
      HStack(spacing: 4) {
        if let systemImage {
          Image(systemName: systemImage)
            .font(.caption2)
            .foregroundStyle(MediaPipePalette.tealPrimary)
        }
        Text(label)
          .font(.caption2.weight(.medium))
          .foregroundStyle(.secondary)
          .lineLimit(1)
      }

      Text(value)
        .font(.subheadline.weight(.semibold).monospacedDigit())
        .foregroundStyle(.primary)
        .lineLimit(1)
        .minimumScaleFactor(0.8)
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(.horizontal, 12)
    .padding(.vertical, 10)
    .background(
      RoundedRectangle(cornerRadius: 12, style: .continuous)
        .fill(MediaPipePalette.recessedSurface)
    )
    .accessibilityElement(children: .combine)
  }
}

/// Horizontally scrollable capsule selector for example presets or prompt modes.
public struct MediaPipeChipBar<Item: Identifiable & Equatable>: View {
  let title: String
  let items: [Item]
  let selectedID: Item.ID?
  let label: (Item) -> String
  let accessibilityID: (Item) -> String
  let onSelect: (Item) -> Void

  public init(
    title: String,
    items: [Item],
    selectedID: Item.ID?,
    label: @escaping (Item) -> String,
    accessibilityID: @escaping (Item) -> String = { "\($0.id)" },
    onSelect: @escaping (Item) -> Void
  ) {
    self.title = title
    self.items = items
    self.selectedID = selectedID
    self.label = label
    self.accessibilityID = accessibilityID
    self.onSelect = onSelect
  }

  public var body: some View {
    VStack(alignment: .leading, spacing: 6) {
      Text(title)
        .font(.caption.weight(.semibold))
        .foregroundStyle(.secondary)
        .textCase(.uppercase)
        .tracking(0.5)

      ScrollView(.horizontal, showsIndicators: false) {
        HStack(spacing: 8) {
          ForEach(items) { item in
            let isSelected = item.id == selectedID
            Button {
              onSelect(item)
            } label: {
              Text(label(item))
                .font(.subheadline.weight(isSelected ? .semibold : .medium))
                .foregroundStyle(isSelected ? .white : .primary)
                .padding(.horizontal, 13)
                .padding(.vertical, 6.5)
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
            .accessibilityIdentifier(accessibilityID(item))
          }
        }
        .padding(.vertical, 1)
      }
      .scrollClipDisabled()
    }
    .sensoryFeedback(.selection, trigger: selectedID)
  }
}

/// Prominent full-width MediaPipe primary action button with integrated loading state.
public struct MediaPipePrimaryButton: View {
  let title: String
  let busyTitle: String?
  let systemImage: String?
  let isBusy: Bool
  let isEnabled: Bool
  let action: () -> Void

  public init(
    title: String,
    busyTitle: String? = nil,
    systemImage: String? = nil,
    isBusy: Bool = false,
    isEnabled: Bool = true,
    action: @escaping () -> Void
  ) {
    self.title = title
    self.busyTitle = busyTitle
    self.systemImage = systemImage
    self.isBusy = isBusy
    self.isEnabled = isEnabled
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      HStack(spacing: 8) {
        if isBusy {
          ProgressView()
            .controlSize(.small)
            .tint(.white)
          Text(busyTitle ?? title)
            .font(.headline.weight(.semibold))
        } else {
          if let systemImage {
            Image(systemName: systemImage)
              .font(.headline.weight(.semibold))
          }
          Text(title)
            .font(.headline.weight(.semibold))
        }
      }
      .foregroundStyle(.white)
      .frame(maxWidth: .infinity)
      .frame(minHeight: 48)
      .background(
        RoundedRectangle(cornerRadius: 16, style: .continuous)
          .fill(
            isEnabled
              ? AnyShapeStyle(MediaPipePalette.brandGradient)
              : AnyShapeStyle(Color.secondary.opacity(0.32))
          )
      )
      .shadow(
        color: isEnabled ? MediaPipePalette.tealPrimary.opacity(0.28) : .clear,
        radius: 10,
        x: 0,
        y: 4
      )
    }
    .buttonStyle(.plain)
    .disabled(!isEnabled || isBusy)
    .animation(.snappy(duration: 0.2), value: isBusy)
    .animation(.snappy(duration: 0.2), value: isEnabled)
  }
}

/// Sleek multiline text input card with inline header, character count, clear action, and
/// optional document title field.
public struct MediaPipeTextInputCard: View {
  let label: String
  let placeholder: String
  @Binding var text: String
  let optionalTitleLabel: String?
  let optionalTitlePlaceholder: String?
  let optionalTitle: Binding<String>?
  let inputAccessibilityID: String
  let clearAccessibilityID: String

  @FocusState private var isFocused: Bool

  public init(
    label: String,
    placeholder: String,
    text: Binding<String>,
    optionalTitleLabel: String? = nil,
    optionalTitlePlaceholder: String? = nil,
    optionalTitle: Binding<String>? = nil,
    inputAccessibilityID: String,
    clearAccessibilityID: String
  ) {
    self.label = label
    self.placeholder = placeholder
    self._text = text
    self.optionalTitleLabel = optionalTitleLabel
    self.optionalTitlePlaceholder = optionalTitlePlaceholder
    self.optionalTitle = optionalTitle
    self.inputAccessibilityID = inputAccessibilityID
    self.clearAccessibilityID = clearAccessibilityID
  }

  public var body: some View {
    VStack(alignment: .leading, spacing: 6) {
      HStack(spacing: 8) {
        Text(label)
          .font(.subheadline.weight(.semibold))
          .foregroundStyle(MediaPipePalette.tealPrimary)

        Spacer()

        Text("\(text.count) chars")
          .font(.caption2.monospacedDigit())
          .foregroundStyle(.secondary)

        if !text.isEmpty {
          Button {
            text = ""
          } label: {
            Image(systemName: "xmark.circle.fill")
              .font(.subheadline)
              .foregroundStyle(.tertiary)
          }
          .buttonStyle(.plain)
          .accessibilityLabel("Clear \(label)")
          .accessibilityIdentifier(clearAccessibilityID)
        }
      }

      if let optionalTitle, let optionalTitleLabel {
        HStack(spacing: 8) {
          Text(optionalTitleLabel)
            .font(.caption.weight(.medium))
            .foregroundStyle(.secondary)
          TextField(optionalTitlePlaceholder ?? "Optional", text: optionalTitle)
            .font(.subheadline)
            .focused($isFocused)
            .accessibilityIdentifier("input_document_title")
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
        .background(
          RoundedRectangle(cornerRadius: 10, style: .continuous)
            .fill(MediaPipePalette.recessedSurface)
        )
      }

      TextField(placeholder, text: $text, axis: .vertical)
        .font(.subheadline)
        .lineLimit(2...5)
        .focused($isFocused)
        .accessibilityIdentifier(inputAccessibilityID)
    }
    .padding(12)
    .mediaPipeCard(cornerRadius: 18, isHighlighted: isFocused)
    .animation(.easeInOut(duration: 0.16), value: isFocused)
  }
}

/// Native iOS grouped settings sheet wrapper with Done toolbar button and detents.
public struct MediaPipeSettingsSheet<Content: View>: View {
  @Environment(\.dismiss) private var dismiss
  let title: String
  @ViewBuilder let content: () -> Content

  public init(title: String = "Settings", @ViewBuilder content: @escaping () -> Content) {
    self.title = title
    self.content = content
  }

  public var body: some View {
    NavigationStack {
      Form {
        content()
      }
      .navigationTitle(title)
      #if os(iOS)
      .navigationBarTitleDisplayMode(.inline)
      #endif
      .toolbar {
        ToolbarItem(placement: .confirmationAction) {
          Button("Done") {
            dismiss()
          }
          .fontWeight(.semibold)
          .accessibilityIdentifier("settings_done_button")
        }
      }
    }
    .tint(MediaPipePalette.tealPrimary)
    .presentationDetents([.medium, .large])
    .presentationDragIndicator(.visible)
  }
}
