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
import CryptoKit
import AuthenticationServices

class OAuthService: NSObject {
  private let config: HuggingFaceAuthConfig.Type
  /// Keep track of the state locally during the auth flow.
  private var currentState: String?

  init(config: HuggingFaceAuthConfig.Type = HuggingFaceAuthConfig.self) {
    self.config = config
  }

  /// Builds an authorization URL from the stored `HuggingFaceAuthConfig` and generated challenge.
  /// Stores the code verifier for later use when exchanging the returned code for access token.
  func buildAuthorizationURL() throws -> URL {
    
    /// Generate PKCE challenge and state
    let (verifier, challenge) = try generatePKCE()
    
    /// Store code verifier securely
    if !KeychainHelper.save(key: config.codeVerifierKeychainKey, value: verifier) {
      throw OAuthError.internalError("Could not save code verifier to keychain.")
    }
    
    let state = Self.generateState()
      
      /// Construct Authorization URL
      var components = URLComponents(url: config.authEndpoint, resolvingAgainstBaseURL: false)
      let scopeString = config.defaultScopes.joined(separator: " ")

      components?.queryItems = [
        URLQueryItem(name: "client_id", value: config.clientId),
        URLQueryItem(name: "redirect_uri", value: config.redirectUri),
        URLQueryItem(name: "response_type", value: "code"),
        URLQueryItem(name: "scope", value: scopeString),
        URLQueryItem(name: "code_challenge", value: challenge),
        URLQueryItem(name: "code_challenge_method", value: "S256"),
        URLQueryItem(name: "state", value: state),  // Include state
      ]

      guard let authUrl = components?.url else {
        throw OAuthError.internalError("Could not create authorization URL.")
      }
      
      // Store state for validation
      self.currentState = state

      return authUrl
  }

  /// Retrieves the current access token from the keychain.
  func getAccessToken() throws -> String {
    guard let accessToken = KeychainHelper.load(key: config.accessTokenKeychainKey) else {
      throw OAuthError.internalError("Access token not found.")
    }
    
    return accessToken
  }

  /// Checks if an access token exists in the keychain.
  func hasAccessToken() -> Bool {
    return KeychainHelper.load(key: config.accessTokenKeychainKey) != nil
  }

  /// Clears the stored access token from the keychain.
  func clearAccessToken() throws {
    guard KeychainHelper.delete(key: config.accessTokenKeychainKey) else {
      throw OAuthError.internalError("Unexpected error clearing access token.")
    }
  }

  /// Handles the callback to the app from the OAuth authorization endpoint.
  /// Validates that a code and state is present in the callback URL and checks for any state mismatch.
  /// Exchanges the code for the  access token.
  func handleCallback(_ url: URL?) async throws {
    defer {
      cleanupStateAndVerifier()
    }

    guard let callbackURL = url else {
      throw OAuthError.invalidCallbackURL("Authentication callback URL was missing.")
    }

    guard let components = URLComponents(url: callbackURL, resolvingAgainstBaseURL: false),
      let queryItems = components.queryItems
    else {
      throw OAuthError.internalError("Could not parse callback URL components")
    }

    // Extract code and state
    let code = queryItems.first { $0.name == "code" }?.value
    let returnedState = queryItems.first { $0.name == "state" }?.value

    // Validate state (CSRF protection)
    guard let receivedState = returnedState else {
      throw OAuthError.invalidCallbackURL("Authentication callback URL missing 'state' parameter.")
    }

    guard let expectedState = self.currentState, receivedState == expectedState else {
      throw OAuthError.invalidCallbackURL("Authentication callback state parameter did not match.")
    }

    guard let authorizationCode = code else {
      throw OAuthError.invalidCallbackURL("Authentication callback URL missing 'code' parameter.")
    }

    try await exchangeCodeForToken(code: authorizationCode)
  }

  /// Exchanges the code for access token by making a post request to the token endpoint.
  func exchangeCodeForToken(code: String) async throws {
    guard let codeVerifier = KeychainHelper.load(key: config.codeVerifierKeychainKey) else {
      throw OAuthError.internalError("Could not retrieve code verifier for token exchange.")
    }

    let postString =
      "grant_type=authorization_code&code=\(code)&redirect_uri=\(HuggingFaceAuthConfig.redirectUri)&client_id=\(HuggingFaceAuthConfig.clientId)&code_verifier=\(codeVerifier)"

    guard let postData = postString.data(using: .utf8) else {
      throw OAuthError.internalError("Failed to encode token request body.")
    }

    do {

      let accessTokenKey = "access_token"
      let response = try await NetworkService.shared.postRequest(
        url: config.tokenEndpoint,
        body: postData,
        headers: ["Content-Type": "application/x-www-form-urlencoded"]
      )

      guard let accessToken = response[accessTokenKey] as? String else {
        throw OAuthError.missingAccessTokenInResponse
      }
      
      /// Store the access token securely
      if !KeychainHelper.save(key: config.accessTokenKeychainKey, value: accessToken) {
        throw OAuthError.internalError("Could not save access token.")
      }
      
    } catch let error as NetworkService.NetworkError{
      /// Catches NetworkService errors or JSON parsing errors
      throw OAuthError.tokenRequestFailed(error)
    }
  }
  
  /// Generates the verifier and challenge for the OAuth flow.
  private func generatePKCE() throws -> (verifier: String, challenge: String) {
    var bytes = [UInt8](repeating: 0, count: 32)
    let status = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)

    guard status == errSecSuccess else {
      throw OAuthError.internalError("Failed to generate random bytes for PKCE. Status: \(status)")
    }

    let verifier = Data(bytes).base64URLEncodedString()

    guard let verifierData = verifier.data(using: .utf8) else {
      throw OAuthError.internalError("Failed to encode verifier string to data.")
    }

    let challengeData = SHA256.hash(data: verifierData)
    let challenge = Data(challengeData).base64URLEncodedString()

    return (verifier, challenge)
  }

  /// Generates the state for verifying the CSRF protection.
  static func generateState() -> String {
    var bytes = [UInt8](repeating: 0, count: 32)  // 16 bytes for a decent random string
    _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
    return Data(bytes).base64URLEncodedString()
  }

  /// Cleans up the temporary state and code verifier from memory and keychain.
  private func cleanupStateAndVerifier() {
    self.currentState = nil
    
    if !KeychainHelper.delete(key: config.codeVerifierKeychainKey) {
      /// Log this error, but don't throw, as the main flow might have already failed.
      /// Failing to delete the verifier is less critical than failing the auth flow.
      print("OAuthService: Warning - Failed to delete code verifier from keychain.")
    }
  }
}

extension OAuthService {
  enum OAuthError: LocalizedError {
    case pkceGenerationFailed(Error?)
    case invalidCallbackURL(String)
    case tokenRequestFailed(Error)
    case missingAccessTokenInResponse
    case internalError(String)
    
    var errorDescription: String? {
      switch self {
        case .pkceGenerationFailed: return "Failed to generate PKCE challenge."
        case .invalidCallbackURL(let context):
          return context
        case .tokenRequestFailed: return "Request to exchange code for token failed."
        case .missingAccessTokenInResponse: return "Token response did not contain an access token."
        case .internalError(let context): return "An internal error occurred: \(context)"
      }
    }
  }
}

// MARK: - Data Helpers
extension Data {
  func base64URLEncodedString() -> String {
    return self.base64EncodedString()
      .replacingOccurrences(of: "+", with: "-")
      .replacingOccurrences(of: "/", with: "_")
      .replacingOccurrences(of: "=", with: "")
  }
}
