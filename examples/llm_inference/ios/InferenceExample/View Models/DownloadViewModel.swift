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

import AuthenticationServices
import Foundation

@MainActor
class DownloadViewModel: ObservableObject {
  @Published var state = State.notInitiated
  @Published var progress: Double = 0.0

  let modelName: String
  var authRequired: Bool {
    /// Restrict models that don't need auth (Deep seek, phi 4) and check for the presence of access token.
    return modelCategory.authRequired && !oauthService.hasAccessToken()
  }

  private let oauthService = OAuthService()
  private let modelCategory: Model

  /// Store the download task for cancellation.
  private var downloadTask: Task<Void, Never>?

  init(modelCategory: Model) {
    self.modelCategory = modelCategory
    modelName = self.modelCategory.name
  }

  /// Downloads the model and updates the state continuously with the progress. Also updates state when download is completed or
  /// an error is encountered.
  func download() {
    guard downloadTask == nil else { return }

    downloadTask = Task { [weak self] in
      guard let self = self else { return }
      do {
        try await performDownload(
          downloadUrl: modelCategory.downloadUrl,
          destinationURL: try modelCategory.downloadDestination)
      } catch let error as NetworkService.NetworkError {
        defer {
          self.state = .error(error: DownloadError.from(networkError: error))
        }
        handleNetworkError(error)
      } catch {
        state = .error(error: DownloadError.generic(error))
      }
      self.downloadTask = nil
    }
  }

  func cancelDownload() {
    downloadTask?.cancel()
    downloadTask = nil
    updateStatesOnCancellation()
  }

  func handleDownloadErrorDismissed() -> Bool {
    guard case let .error(error) = self.state else {
      return false
    }
    switch error.networkError {
    case .unauthorized, .forbidden:
      return true
    default:
      return false
    }
  }

  func getAuthorizationUrl() -> URL? {
    do {
      return try oauthService.buildAuthorizationURL()
    } catch let error as OAuthService.OAuthError {
      state = .error(error: DownloadError.from(oauthError: error))
    } catch {
      state = .error(error: DownloadError.generic(error))
    }

    return nil
  }

  /// Handles the callback URL from the web authentication session.
  /// Returns true on successful token exchange, false otherwise.
  func handleAuthenticationCallback(_ callbackURL: URL?) async -> Bool {
    do {
      try await oauthService.handleCallback(callbackURL)
      return true
    } catch let error as OAuthService.OAuthError {
      state = .error(error: DownloadError.from(oauthError: error))
    } catch {
      state = .error(error: DownloadError.generic(error))
    }

    return false
  }

  func handleWebAuthenticationError(_ error: Error) {
    if error is ASWebAuthenticationSessionError {
      state = .error(
        error: DownloadError.from(webAuthError: error as! ASWebAuthenticationSessionError))
    } else {
      state = .error(error: DownloadError.generic(error))
    }
  }

  /// Utility for handling download using the `NetworkService`.
  private func performDownload(downloadUrl: URL, destinationURL: URL)
    async throws
  {
    var headers = [String: String]()

    if modelCategory.authRequired {
      let accessToken = try oauthService.getAccessToken()
      headers = ["Authorization": "Bearer " + accessToken]
    }

    for try await event in NetworkService.shared.downloadFile(
      from: downloadUrl, to: destinationURL, headers: headers)
    {
      guard !Task.isCancelled else {
        updateStatesOnCancellation()
        return
      }
      state = .progress
      switch event {
      case .progress(let percentage):
        progress = percentage
      case .completed:
        progress = 100.0
        state = .completed
      }
    }
  }

  private func handleNetworkError(_ error: NetworkService.NetworkError) {
    switch error {
    case .forbidden:
      _ = KeychainHelper.delete(key: modelCategory.licenseAcnowledgedKey)
      fallthrough
    case .unauthorized:
      try? oauthService.clearAccessToken()
    default:
      return
    }
  }

  private func updateStatesOnCancellation() {
    self.state = .notInitiated
    self.progress = 0.0
  }
}

// MARK: - Download State and Errors
extension DownloadViewModel {
  enum State: Equatable {
    case notInitiated
    case loginRequired
    case progress
    case completed
    case error(error: DownloadError)

    static func == (lhs: State, rhs: State) -> Bool {
      switch (lhs, rhs) {
      case (.notInitiated, .notInitiated),
        (.progress, .progress),
        (.completed, .completed),
        (.loginRequired, .loginRequired),
        (.error, .error):
        return true
      default:
        return false
      }
    }

    var error: DownloadError? {
      switch self {
      case let .error(error):
        return error
      default:
        return nil
      }
    }
  }

  struct DownloadError: LocalizedError {
    let title: String
    let description: String
    var networkError: NetworkService.NetworkError?

    var errorDescription: String? {
      title
    }

    var failureReason: String? {
      description
    }

    static func from(networkError: NetworkService.NetworkError) -> DownloadError {
      switch networkError {
      case .unauthorized:
        return DownloadError(
          title: "Unauthorized Request",
          description: """
            The request could not be authorized.
            Please click retry by clicking on the download button to refresh the access session.
            """,
          networkError: networkError
        )
      case .forbidden(let response):
        return DownloadError(
          title: "Forbidden Request",
          description: """
            \(response).
            You may not have accepted the license agreement of the model on Hugging Face.
            You will be redirected to the license acknowledgement screen when you click "OK".
            """,
          networkError: networkError
        )
      default:
        return DownloadError(
          title: networkError.errorDescription!,
          description: networkError.failureReason,
          networkError: networkError
        )
      }
    }

    static func from(oauthError: OAuthService.OAuthError) -> DownloadError {
      return DownloadError(
        title: "OAuth Error",
        description: oauthError.errorDescription
          ?? "Some error occurred while processing the OAuth flow."
      )
    }

    static func from(webAuthError: ASWebAuthenticationSessionError) -> DownloadError {
      switch webAuthError.code {
      case .canceledLogin:
        return DownloadError(
          title: "Authentication Canceled",
          description: "The login was canceled by the user."
        )
      default:
        return DownloadError(
          title: "Authentication Error",
          description: webAuthError.localizedDescription + " Check your network or try again later."
        )
      }
    }

    static func generic(_ error: Error) -> DownloadError {
      DownloadError(
        title: "Unexpected Error",
        description: error.localizedDescription
      )
    }
  }
}
