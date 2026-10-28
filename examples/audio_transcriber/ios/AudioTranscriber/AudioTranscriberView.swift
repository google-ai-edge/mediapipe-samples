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

#if canImport(MediaPipeTasksAudio)
import MediaPipeTasksAudio
#elseif canImport(MPPAudioTranscriber)
import MPPAudioTranscriber
#endif
import SwiftUI

#if canImport(UIKit)
  import UIKit

  extension Color {
    fileprivate static let appBackground = Color(uiColor: .systemBackground)
    fileprivate static let appSecondaryBackground = Color(uiColor: .secondarySystemBackground)
    fileprivate static let appTertiaryFill = Color(uiColor: .tertiarySystemFill)
    fileprivate static let appSeparator = Color(uiColor: .separator)
  }
#elseif canImport(AppKit)
  import AppKit
  import UniformTypeIdentifiers

  extension Color {
    fileprivate static let appBackground = Color(nsColor: .windowBackgroundColor)
    fileprivate static let appSecondaryBackground = Color(nsColor: .controlBackgroundColor)
    fileprivate static let appTertiaryFill = Color(nsColor: .quaternaryLabelColor)
    fileprivate static let appSeparator = Color(nsColor: .separatorColor)
  }
#endif

private enum LayoutMetrics {
  static let rootSpacing: CGFloat = 20
  static let headerSpacing: CGFloat = 6
  static let headerTopPadding: CGFloat = 10
  static let statusPillSpacing: CGFloat = 8
  static let statusIndicatorSize: CGFloat = 10
  static let statusPillHorizontalPadding: CGFloat = 14
  static let statusPillVerticalPadding: CGFloat = 6
  static let statusPillCornerRadius: CGFloat = 20
  static let meterCornerRadius: CGFloat = 6
  static let meterHeight: CGFloat = 8
  static let meterAnimationDuration: Double = 0.1
  static let cardSpacing: CGFloat = 12
  static let cardCornerRadius: CGFloat = 12
  static let cardBorderWidth: CGFloat = 1
  static let transcriptListSpacing: CGFloat = 10
  static let transcriptListVerticalPadding: CGFloat = 4
  static let segmentRowSpacing: CGFloat = 8
  static let segmentTimestampTopPadding: CGFloat = 2
  static let segmentRowPadding: CGFloat = 8
  static let segmentRowCornerRadius: CGFloat = 8
  static let interimBackgroundOpacity: Double = 0.1
  static let bottomAnchorHeight: CGFloat = 1
  static let bottomAnchorID = "bottomAnchor"
  static let controlButtonsSpacing: CGFloat = 20
  static let controlButtonsBottomPadding: CGFloat = 20
  static let clearButtonHorizontalPadding: CGFloat = 24
  static let recordButtonSpacing: CGFloat = 12
  static let recordButtonHorizontalPadding: CGFloat = 32
  static let buttonVerticalPadding: CGFloat = 16
  static let buttonCornerRadius: CGFloat = 30
  static let disabledButtonOpacity: Double = 0.4
  static let enabledButtonOpacity: Double = 1.0
  static let recordButtonShadowOpacity: Double = 0.15
  static let recordButtonShadowRadius: CGFloat = 4
  static let recordButtonShadowYOffset: CGFloat = 2
}

/// Primary SwiftUI view for live on-device MediaPipe `AudioTranscriber` speech recognition.
@MainActor
package struct AudioTranscriberView: View {
  @State private var viewModel: AudioTranscriberViewModel

  /// Creates a speech recognition sample view with a default view model.
  package init() {
    self.viewModel = AudioTranscriberViewModel()
  }

  /// Creates a speech recognition sample view bound to the provided view model.
  ///
  /// - Parameter viewModel: Observable view model managing audio capture and transcription.
  package init(viewModel: AudioTranscriberViewModel) {
    self.viewModel = viewModel
  }

  package var body: some View {
    VStack(spacing: LayoutMetrics.rootSpacing) {
      headerView
      statusPillView
      audioLevelMeterView
      transcriptCardView
      controlButtonsView
    }
  }

  private var headerView: some View {
    VStack(spacing: LayoutMetrics.headerSpacing) {
      Text("MediaPipe AudioTranscriber")
        .font(.title2.bold())
      Text("On-Device Speech Recognition")
        .font(.subheadline)
        .foregroundStyle(.secondary)
      Text("Model: \(viewModel.activeModelName)")
        .font(.caption.monospaced())
        .foregroundStyle(.secondary)
    }
    .padding(.top, LayoutMetrics.headerTopPadding)
  }

  private var statusPillView: some View {
    HStack(spacing: LayoutMetrics.statusPillSpacing) {
      Circle()
        .fill(viewModel.isRecording ? Color.red : Color.green)
        .frame(
          width: LayoutMetrics.statusIndicatorSize,
          height: LayoutMetrics.statusIndicatorSize
        )
      Text(viewModel.statusMessage)
        .font(.caption.monospaced())
        .foregroundStyle(.primary)
    }
    .padding(.horizontal, LayoutMetrics.statusPillHorizontalPadding)
    .padding(.vertical, LayoutMetrics.statusPillVerticalPadding)
    .background(Color.appSecondaryBackground)
    .clipShape(RoundedRectangle(cornerRadius: LayoutMetrics.statusPillCornerRadius))
  }

  private var audioLevelMeterView: some View {
    GeometryReader { geometry in
      ZStack(alignment: .leading) {
        RoundedRectangle(cornerRadius: LayoutMetrics.meterCornerRadius)
          .fill(Color.appTertiaryFill)
          .frame(height: LayoutMetrics.meterHeight)

        let progress = CGFloat(viewModel.audioLevel)
        let barWidth = geometry.size.width * progress
        let clampedWidth = max(0, min(geometry.size.width, barWidth))

        RoundedRectangle(cornerRadius: LayoutMetrics.meterCornerRadius)
          .fill(Color.blue)
          .frame(width: clampedWidth, height: LayoutMetrics.meterHeight)
          .animation(
            .linear(duration: LayoutMetrics.meterAnimationDuration),
            value: viewModel.audioLevel
          )
      }
    }
    .frame(height: LayoutMetrics.meterHeight)
    .padding(.horizontal)
  }

  private var transcriptCardView: some View {
    VStack(alignment: .leading, spacing: LayoutMetrics.cardSpacing) {
      HStack {
        Text("Transcript")
          .font(.headline)
        Spacer()
      }

      ScrollViewReader { proxy in
        ScrollView {
          LazyVStack(alignment: .leading, spacing: LayoutMetrics.transcriptListSpacing) {
            ForEach(viewModel.finalizedResults) { segment in
              segmentRow(for: segment)
            }

            if !viewModel.interimTranscript.isEmpty {
              interimRowView
            }

            Color.clear
              .frame(height: LayoutMetrics.bottomAnchorHeight)
              .id(LayoutMetrics.bottomAnchorID)
          }
          .padding(.vertical, LayoutMetrics.transcriptListVerticalPadding)
        }
        .onChange(of: viewModel.finalizedResults.count) { _, _ in
          withAnimation {
            proxy.scrollTo(LayoutMetrics.bottomAnchorID, anchor: .bottom)
          }
        }
        .onChange(of: viewModel.interimTranscript) { _, _ in
          proxy.scrollTo(LayoutMetrics.bottomAnchorID, anchor: .bottom)
        }
      }
    }
    .padding()
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color.appBackground)
    .clipShape(RoundedRectangle(cornerRadius: LayoutMetrics.cardCornerRadius))
    .overlay(
      RoundedRectangle(cornerRadius: LayoutMetrics.cardCornerRadius)
        .stroke(Color.appSeparator, lineWidth: LayoutMetrics.cardBorderWidth)
    )
    .padding(.horizontal)
  }

  private func segmentRow(for segment: AudioTranscriberSegment) -> some View {
    HStack(alignment: .top, spacing: LayoutMetrics.segmentRowSpacing) {
      Text(String(format: "(%.1fs)", segment.durationSeconds))
        .font(.caption2.monospaced())
        .foregroundStyle(.secondary)
        .padding(.top, LayoutMetrics.segmentTimestampTopPadding)
      Text(segment.transcript)
        .font(.body)
        .foregroundStyle(.primary)
    }
    .padding(LayoutMetrics.segmentRowPadding)
    .background(Color.appSecondaryBackground)
    .clipShape(RoundedRectangle(cornerRadius: LayoutMetrics.segmentRowCornerRadius))
    .id(segment.id)
  }

  private var interimRowView: some View {
    HStack(alignment: .top, spacing: LayoutMetrics.segmentRowSpacing) {
      Text("[Live]")
        .font(.caption2.monospaced())
        .foregroundStyle(.orange)
        .padding(.top, LayoutMetrics.segmentTimestampTopPadding)
      Text(viewModel.interimTranscript)
        .font(.body.italic())
        .foregroundStyle(.secondary)
    }
    .padding(LayoutMetrics.segmentRowPadding)
    .background(Color.orange.opacity(LayoutMetrics.interimBackgroundOpacity))
    .clipShape(RoundedRectangle(cornerRadius: LayoutMetrics.segmentRowCornerRadius))
  }

  private var isClearDisabled: Bool {
    viewModel.isRecording || viewModel.finalizedResults.isEmpty
  }

  private var controlButtonsView: some View {
    HStack(spacing: LayoutMetrics.controlButtonsSpacing) {
      #if canImport(AppKit)
        Button {
          selectModelFile()
        } label: {
          Text("Load Model…")
            .font(.headline)
            .foregroundStyle(.primary)
            .padding(.horizontal, LayoutMetrics.clearButtonHorizontalPadding)
            .padding(.vertical, LayoutMetrics.buttonVerticalPadding)
            .background(Color.appSecondaryBackground)
            .clipShape(RoundedRectangle(cornerRadius: LayoutMetrics.buttonCornerRadius))
        }
        .buttonStyle(.plain)
        .disabled(viewModel.isRecording)
        .opacity(
          viewModel.isRecording
            ? LayoutMetrics.disabledButtonOpacity
            : LayoutMetrics.enabledButtonOpacity
        )
      #endif

      Button {
        viewModel.clearTranscripts()
      } label: {
        Text("Clear All")
          .font(.headline)
          .foregroundStyle(.primary)
          .padding(.horizontal, LayoutMetrics.clearButtonHorizontalPadding)
          .padding(.vertical, LayoutMetrics.buttonVerticalPadding)
          .background(Color.appSecondaryBackground)
          .clipShape(RoundedRectangle(cornerRadius: LayoutMetrics.buttonCornerRadius))
      }
      .buttonStyle(.plain)
      .disabled(isClearDisabled)
      .opacity(
        isClearDisabled
          ? LayoutMetrics.disabledButtonOpacity
          : LayoutMetrics.enabledButtonOpacity
      )

      Button {
        viewModel.toggleRecording()
      } label: {
        HStack(spacing: LayoutMetrics.recordButtonSpacing) {
          Image(systemName: viewModel.isRecording ? "stop.fill" : "mic.fill")
            .font(.title2)
          Text(viewModel.isRecording ? "Stop" : "Record")
            .font(.headline)
        }
        .foregroundStyle(.white)
        .padding(.horizontal, LayoutMetrics.recordButtonHorizontalPadding)
        .padding(.vertical, LayoutMetrics.buttonVerticalPadding)
        .background(viewModel.isRecording ? Color.red : Color.blue)
        .clipShape(RoundedRectangle(cornerRadius: LayoutMetrics.buttonCornerRadius))
        .shadow(
          color: Color.black.opacity(LayoutMetrics.recordButtonShadowOpacity),
          radius: LayoutMetrics.recordButtonShadowRadius,
          x: 0,
          y: LayoutMetrics.recordButtonShadowYOffset
        )
      }
      .buttonStyle(.plain)
    }
    .padding(.bottom, LayoutMetrics.controlButtonsBottomPadding)
  }

  #if canImport(AppKit)
    private func selectModelFile() {
      let panel = NSOpenPanel()
      panel.canChooseFiles = true
      panel.canChooseDirectories = false
      panel.allowsMultipleSelection = false
      if let modelType = UTType(
        filenameExtension: TranscriberConfiguration.modelExtension,
        conformingTo: .data
      ) {
        panel.allowedContentTypes = [modelType]
      }
      if panel.runModal() == .OK, let url = panel.url {
        viewModel.loadModel(at: url)
      }
    }
  #endif
}
