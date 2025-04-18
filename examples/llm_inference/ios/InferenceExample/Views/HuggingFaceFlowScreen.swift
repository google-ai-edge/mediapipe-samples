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
import CryptoKit
import SwiftUI

struct HuggingFaceFlowScreen: View {
  @ObservedObject var viewModel: HuggingFaceFlowViewModel
  @Environment(\.dismiss) var dismiss

  /// Persists the Download VM. `HuggingFaceFlowScreen` gets refreshed by SwiftUI based on its internal view life cycle.
  /// Eg: presenting alerts refreshes the view. VM is being persisted to persist its state through refreshes.
  @StateObject var downloadViewModel: DownloadViewModel

  init(viewModel: HuggingFaceFlowViewModel) {
    self._viewModel = ObservedObject(wrappedValue: viewModel)
    self._downloadViewModel = StateObject(
      wrappedValue: DownloadViewModel(
        modelCategory: viewModel.modelCategory
      ))
  }

  var body: some View {
    VStack {
      switch viewModel.action {
      case .acknowledgeLicense:
        /// Cannot persist `acknowledgeLicenseViewModel` similar to the download VM since some models don't have an acknowledge license view.
        /// @StateObject cannot be an optional. Hence recreating VM each time the view is refreshed.
        /// The enabling and disabling of continue button on refresh is handled internally.
        let acknowledgeLicenseViewModel = AcknowledgeLicenseViewModel(
          url: viewModel.modelCategory.licenseUrl!,
          licenseAcknowledgedKey: viewModel.modelCategory.licenseAcnowledgedKey)
        AcknowledgeLicenseView(viewModel: acknowledgeLicenseViewModel) {
          viewModel.updateState()
        }
      case .download:
        DownloadView(viewModel: downloadViewModel) {
          viewModel.updateState()
          dismiss()
        }
      }
    }
    .alert(
      error: downloadViewModel.state.error,
      action: { [weak downloadViewModel] in
        guard let networkError = downloadViewModel?.state.error?.networkError else {
          return
        }
        switch networkError {
        case .forbidden:
          viewModel.updateState()
        default:
          downloadViewModel?.state = .notInitiated
          break
        }
      })
  }
}
