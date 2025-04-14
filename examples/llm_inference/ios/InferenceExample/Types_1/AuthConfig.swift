// Copyright 2025 The MediaPipe Authors.
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

import Foundation

struct HuggingFaceAuthConfig {
  static let clientId = "19943f22-042c-43f8-96bd-6522ffa8bdfe"
  static var redirectUri = "com.google.mediapipe.examples.llminference://oauth2callback"
  static let authEndpoint = URL(string: "https://huggingface.co/oauth/authorize")!
  static var tokenEndpoint = URL(string: "https://huggingface.co/oauth/token")!
  static let defaultScopes = ["read-repos"]

  // Keychain keys specific to this service
  static let accessTokenKeychainKey = "com.yourapp.huggingface.accessToken"
  static let codeVerifierKeychainKey = "com.yourapp.huggingface.codeVerifier"
}
