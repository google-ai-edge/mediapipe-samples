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
import SafariServices
import SwiftUI
import WebKit

struct SafariView: UIViewControllerRepresentable {
  let url: URL

  func makeUIViewController(context: Context) -> SFSafariViewController {
    SFSafariViewController(url: url)
  }

  /// For `UIViewControllerRepresentable` protocol conformance.
  func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {}
}

struct AcknowledgeLicenseView: View {
  private struct Constants {
    static let instructionFontColor = Color.secondary
    static let acknowledgeLicenseButtonTitle = "Acknowledge License"
    static let continueButtonTitle = "Continue"
    static let instructionText = """
      When you click on acknowledge license, you will be redirected to the model page on Hugging Face. \
      Please scroll down, log in to Hugging Face, and then click on "Acknowledge License".
      """
  }

  @State private var showingWebView = false
  @ObservedObject var viewModel: AcknowledgeLicenseViewModel

  let onLicenseViewed: () -> Void

  var body: some View {
    VStack {
      Text(Constants.instructionText)
        .font(.callout)
        .foregroundStyle(Constants.instructionFontColor)
        .padding()
      HuggingFaceButton(title: Constants.acknowledgeLicenseButtonTitle) {
        showingWebView = true
        viewModel.handleLicenseViewed()
      }
      .padding()
      RoundedRectButton(
        title: Constants.continueButtonTitle,
        action: {
          onLicenseViewed()
        }, disabled: viewModel.disableContinue
      )
      .padding()
    }
    .sheet(
      isPresented: $showingWebView, onDismiss: nil,
      content: {
        SafariView(url: viewModel.url)
      })
  }
}
