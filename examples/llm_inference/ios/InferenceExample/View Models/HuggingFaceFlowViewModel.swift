// Copyright 2024 The MediaPipe Authors.
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

import CryptoKit
import Foundation
import SwiftUI

@MainActor
class HuggingFaceFlowViewModel: ObservableObject {
  enum Action {
    case acknowledgeLicense
    case download
  }

  @Published var action: Action
  let modelCategory: Model

  init(modelCategory: Model) {
    self.modelCategory = modelCategory
    self.action = HuggingFaceFlowViewModel.newAction(modelCategory: modelCategory)
  }

  func updateState() {
    action = HuggingFaceFlowViewModel.newAction(modelCategory: self.modelCategory)
  }

  static func newAction(modelCategory: Model) -> Action {
    if !modelCategory.licenseAcnowledgedKey.isEmpty,
      KeychainHelper.load(key: modelCategory.licenseAcnowledgedKey) == nil
    {
      return .acknowledgeLicense
    } else {
      return .download
    }
  }
}
