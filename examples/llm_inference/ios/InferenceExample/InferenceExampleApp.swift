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

import SwiftUI

@main
struct InferenceExampleApp: App {
  
  private let hasLaunchedBeforeKey = "com.mediapipe.InferenceExampleApp.hasLaunchedBefore"
  
  init() {
    /// Delete keys if this is the first launch of the app. Since the app is not explicitly handling logout, user can delete the app to clear
    /// the current session if they want to login to a new account.
    /// Any keys saved to the key chain will be persisted by iOS inspite of the app being uninstalled.
    defer {
      UserDefaults.standard.set(true, forKey: hasLaunchedBeforeKey)
    }
    
    guard !UserDefaults.standard.bool(forKey: hasLaunchedBeforeKey) else {
      return
    }
    
    let keys =
    [HuggingFaceAuthConfig.accessTokenKeychainKey, HuggingFaceAuthConfig.codeVerifierKeychainKey]
    + Model.allCases.map { $0.licenseAcnowledgedKey }
    KeychainHelper.clear(keys: keys)
  }
  
  var body: some Scene {
    WindowGroup {
      ModelSelectionScreen()
    }
  }
}
