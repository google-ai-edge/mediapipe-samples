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

/// Compact MediaPipe branded navigation title view with logo mark and live config subtitle.
public struct MediaPipeNavigationTitle: View {
  let title: String
  let subtitle: String?

  public init(title: String, subtitle: String? = nil) {
    self.title = title
    self.subtitle = subtitle
  }

  public var body: some View {
    HStack(spacing: 10) {
      ZStack {
        RoundedRectangle(cornerRadius: 8, style: .continuous)
          .fill(MediaPipePalette.brandGradient)
          .frame(width: 28, height: 28)
        MediaPipeLogoMark()
          .fill(.white)
          .frame(width: 16, height: 16)
      }

      VStack(alignment: .leading, spacing: 1) {
        Text(title)
          .font(.headline.weight(.semibold))
          .foregroundStyle(.primary)
          .lineLimit(1)

        if let subtitle, !subtitle.isEmpty {
          Text(subtitle)
            .font(.caption2.weight(.medium))
            .foregroundStyle(.secondary)
            .lineLimit(1)
            .accessibilityIdentifier("top_bar_subtitle")
        }
      }
    }
  }
}

/// Compact runtime status strip shown directly beneath the navigation bar.
///
/// Surfaces the active model/delegate, readiness state, latency, and on-device badge in one
/// scannable row without competing with the main workspace.
public struct MediaPipeStatusStrip: View {
  public enum Status: Equatable, Sendable {
    case loading(String)
    case ready(String)
    case failed(String)
  }

  let status: Status
  let delegateLabel: String
  let latencyMs: Double?

  public init(
    status: Status,
    delegateLabel: String,
    latencyMs: Double? = nil
  ) {
    self.status = status
    self.delegateLabel = delegateLabel
    self.latencyMs = latencyMs
  }

  public var body: some View {
    HStack(spacing: 8) {
      statusBadge

      Spacer(minLength: 4)

      if let latencyMs {
        HStack(spacing: 4) {
          Image(systemName: "bolt.fill")
            .font(.caption2.weight(.semibold))
          Text(String(format: "%.1f ms", latencyMs))
            .font(.caption2.weight(.semibold).monospacedDigit())
        }
        .foregroundStyle(MediaPipePalette.tealPrimary)
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(MediaPipePalette.tealContainer.opacity(0.75), in: Capsule())
        .accessibilityIdentifier("status_latency_pill")
      }

      HStack(spacing: 4) {
        Image(systemName: delegateLabel.localizedCaseInsensitiveContains("GPU") ? "gpu" : "cpu")
          .font(.caption2)
        Text(delegateLabel)
          .font(.caption2.weight(.medium))
      }
      .foregroundStyle(.secondary)
      .padding(.horizontal, 8)
      .padding(.vertical, 4)
      .background(MediaPipePalette.cardBackground, in: Capsule())
      .overlay(Capsule().strokeBorder(MediaPipePalette.cardStroke, lineWidth: 0.75))
      .accessibilityIdentifier("status_delegate_pill")
    }
  }

  @ViewBuilder
  private var statusBadge: some View {
    switch status {
    case .loading(let text):
      HStack(spacing: 6) {
        ProgressView()
          .controlSize(.mini)
          .tint(MediaPipePalette.tealPrimary)
        Text(text)
          .font(.caption.weight(.medium))
          .foregroundStyle(.secondary)
          .lineLimit(1)
      }
    case .ready(let text):
      HStack(spacing: 6) {
        Circle()
          .fill(MediaPipePalette.statusPositive)
          .frame(width: 7, height: 7)
        Text(text)
          .font(.caption.weight(.medium))
          .foregroundStyle(.secondary)
          .lineLimit(1)
      }
    case .failed(let text):
      HStack(spacing: 6) {
        Circle()
          .fill(MediaPipePalette.statusError)
          .frame(width: 7, height: 7)
        Text(text)
          .font(.caption.weight(.medium))
          .foregroundStyle(MediaPipePalette.statusError)
          .lineLimit(1)
      }
    }
  }
}

/// Standard settings toolbar button with consistent icon and accessibility identifier.
public struct MediaPipeSettingsButton: View {
  let accessibilityLabel: String
  let action: () -> Void

  public init(
    accessibilityLabel: String = "Model & hardware settings",
    action: @escaping () -> Void
  ) {
    self.accessibilityLabel = accessibilityLabel
    self.action = action
  }

  public var body: some View {
    Button(action: action) {
      Image(systemName: "slider.horizontal.3")
        .font(.body.weight(.medium))
        .foregroundStyle(MediaPipePalette.tealPrimary)
    }
    .accessibilityLabel(accessibilityLabel)
    .accessibilityIdentifier("settings_button")
  }
}
