// Copyright 2025 The Mediapipe Authors.
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

import AuthenticationServices
import SwiftUI

struct DownloadView: View {
  private struct Constants {
    static let progressViewTitle = "Downloading..."
  }

  // State to control alert presentation.
  @State private var showErrorAlert = false
  @ObservedObject var viewModel: DownloadViewModel
  let onDownloadCompletion: () -> Void

  var body: some View {
    ZStack {

      Group {
        switch viewModel.state {
        case .notInitiated, .loginRequired:
          // Show button when not started or login is needed
          DownloadButtonView(viewModel: viewModel)

        case .progress:
          // Show progress view when downloading
          DownloadProgressView(viewModel: viewModel)
            .frame(maxWidth: .infinity, maxHeight: .infinity)  // Fill available space

        case .completed:
          // Show completion view
          DownloadCompletedView(onDownloadCompletion: onDownloadCompletion)

        case .error:
          /// Show button again on error, allowing retry. For forbidden errors, the user will be redirected back to the license
          /// acknowledgement screen.
          DownloadButtonView(viewModel: viewModel)
        // Consider showing the error message below the button or via an alert
        }
      }
      .transition(.opacity)
    }
    .onDisappear { [weak viewModel] in
      viewModel?.cancelDownload()
    }
    .animation(.easeInOut(duration: 0.3), value: viewModel.state)
  }
}

/// Component for the initial/download button state
struct DownloadButtonView: View {
  @Environment(\.webAuthenticationSession) private var webAuthenticationSession
  @ObservedObject var viewModel: DownloadViewModel

  var buttonTitle: String {
    var title = "Download \(viewModel.modelName)"
    if viewModel.authRequired {
      title = "Sign in and " + title
    }

    return title
  }

  var body: some View {
    HuggingFaceButton(title: buttonTitle) {
      if viewModel.authRequired {
        Task { await performAuthentication() }
      } else {
        viewModel.download()
      }
    }
  }

  private func performAuthentication() async {
    guard let url = viewModel.getAuthorizationUrl() else { return }
    do {
      let urlWithToken = try await webAuthenticationSession.authenticate(
        using: url,
        callback: ASWebAuthenticationSession.Callback.customScheme(
          "com.google.mediapipe.examples.llminference"),
        preferredBrowserSession: .ephemeral,
        additionalHeaderFields: [:]
      )

      if await viewModel.handleAuthenticationCallback(urlWithToken) {
        viewModel.download()
      }
    } catch {
      viewModel.handleWebAuthenticationError(error)
    }
  }
}

/// Component for the progress state
struct DownloadProgressView: View {
  @ObservedObject var viewModel: DownloadViewModel

  var body: some View {
    VStack {
      ProgressView(value: viewModel.progress, total: 100.0) {
        Text("Downloading...")
      } currentValueLabel: {
        Text("Current progress: \(Int(viewModel.progress))%")
      }
      .padding()
      .accentColor(Metadata.globalColor)

      RoundedRectButton(title: "Cancel") {
        viewModel.cancelDownload()
      }
    }
  }
}

/// Component for the completed state
struct DownloadCompletedView: View {
  let onDownloadCompletion: () -> Void

  var body: some View {
    Text("Download Completed!")
      .onAppear(perform: onDownloadCompletion)
  }
}
