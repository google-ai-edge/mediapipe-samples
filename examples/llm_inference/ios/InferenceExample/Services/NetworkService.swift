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

class NetworkService {
  static let shared = NetworkService()

  private init() {}

  /// Send any post request.
  func postRequest(url: URL, body: Data, headers: [String: String]) async throws -> [String: Any] {
    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.httpBody = body

    for (key, value) in headers {
      request.addValue(value, forHTTPHeaderField: key)
    }

    var (data, response): (Data, URLResponse)
    do {
      (data, response) = try await URLSession.shared.data(for: request)
    } catch {
      throw NetworkError.requestFailed(error)
    }

    guard let httpResponse = response as? HTTPURLResponse else {
      throw NetworkError.noResponse
    }

    try Self.validate(httpResponse: httpResponse)

    guard let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any]
    else {
      throw NetworkError.invalidJson
    }

    return json
  }

  /// Downloads the file at `sourceUrl`and saves it to the `destinationURL`.  Returns an cancellable
  /// `AsyncThrowingStream`
  func downloadFile(from sourceUrl: URL, to destinationURL: URL, headers: [String: String])
    -> AsyncThrowingStream<DownloadEvent, Error>
  {
    AsyncThrowingStream { continuation in
      var request = URLRequest(url: sourceUrl)

      for (key, value) in headers {
        request.addValue(value, forHTTPHeaderField: key)
      }

      let downloadTask = URLSession.shared.downloadTask(with: request) { tempURL, response, error in
        if let error = error {
          continuation.finish(throwing: NetworkError.requestFailed(error))
          return
        }

        guard let httpResponse = response as? HTTPURLResponse, let tempURL = tempURL else {
          continuation.finish(throwing: NetworkError.noResponse)
          return
        }

        do {
          try NetworkService.validate(httpResponse: httpResponse)
        } catch {
          continuation.finish(throwing: error)
        }

        do {
          try NetworkService.moveFile(from: tempURL, to: destinationURL)
          continuation.yield(.progress(100.0))
          continuation.yield(.completed)
          continuation.finish()
        } catch {
          continuation.finish(throwing: NetworkError.postprocessingFailed(error))
        }
      }

      let observation = downloadTask.progress.observe(\.fractionCompleted) { progress, _ in
        let percentage = progress.fractionCompleted * 100
        continuation.yield(.progress(percentage))
      }

      // Start the download
      downloadTask.resume()

      // Handle cancellation
      continuation.onTermination = { @Sendable _ in  // Explicitly mark as Sendable
        downloadTask.cancel()
        observation.invalidate()
      }
    }
  }

  /// Utility to move file from source to destination URL.
  private static func moveFile(from sourceURL: URL, to destinationURL: URL) throws {
    let dir = destinationURL.deletingLastPathComponent()
    try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
    if FileManager.default.fileExists(atPath: destinationURL.path) {
      try FileManager.default.removeItem(at: destinationURL)
    }

    try FileManager.default.moveItem(at: sourceURL, to: destinationURL)
  }

  private static func validate(httpResponse: HTTPURLResponse) throws {
    let localizedMessage = HTTPURLResponse.localizedString(forStatusCode: httpResponse.statusCode)
    guard httpResponse.statusCode != 401 else {
      throw NetworkError.unauthorized(localizedMessage)
    }

    guard httpResponse.statusCode != 403 else {
      throw NetworkError.forbidden(localizedMessage)
    }

    guard (200...299).contains(httpResponse.statusCode) else {
      throw NetworkError.invalidResponseCode(localizedMessage)
    }
  }
}

// MARK: - Network Errors
extension NetworkService {
  enum NetworkError: LocalizedError {

    case invalidURL
    case unauthorized(String)
    case forbidden(String)
    case invalidResponseCode(String)
    case invalidJson
    case noResponse
    case postprocessingFailed(Error)
    case requestFailed(Error)

    public var errorDescription: String? {
      switch self {
      case .invalidURL:
          return "Invalid URL"
      case .unauthorized: 
          return "Unauthorized Request"
      case .forbidden: 
          return "Forbidden Request"
      case .invalidResponseCode: 
          return "Invalid Server Response"
      case .invalidJson: 
          return "Invalid JSON Response"
      case .noResponse: 
          return "No Response Received"
      case .postprocessingFailed: 
          return "Post processing failed."
      case .requestFailed: 
          return "Request Failed"
      }
    }

    public var failureReason: String {
      switch self {
      case .invalidURL:
        return "The request URL provided was invalid."
      case .unauthorized(let response):
        return "Authentication failed (401). \(response)"
      case .forbidden(let response):
        return "Access denied (403). \(response)"
      case .invalidResponseCode(let response):
        return "The server returned an unexpected status code. \(response)"
      case .invalidJson:
        return "The server's response could not be parsed as valid JSON."
      case .noResponse:
        return "No response was received from the server."
      case .postprocessingFailed(let underlyingError):
        return
          "Some error occured while handling the request. Error: \(underlyingError.localizedDescription)"
      case .requestFailed(let underlyingError):
        return
          "The file download could not be initiated. Error: \(underlyingError.localizedDescription)"
      }
    }
  }

  // MARK: - Download Events
  enum DownloadEvent {
    case progress(Double)
    case completed
  }
}
