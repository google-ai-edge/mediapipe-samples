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

import Foundation

/// Errors thrown while resolving a bundled or sideloaded MediaPipe model file.
public enum SampleModelError: LocalizedError, Equatable, Sendable {
  case missing(filename: String)
  case emptyStub(filename: String)

  public var errorDescription: String? {
    switch self {
    case .missing(let filename):
      return "Model '\(filename)' was not found in the app bundle. Run RunScripts/download_models.sh and rebuild."
    case .emptyStub(let filename):
      return "Model '\(filename)' is empty (0 bytes). Delete the stub file and run RunScripts/download_models.sh."
    }
  }
}

/// Locates MediaPipe model assets consistently across standalone apps, unit tests, and
/// multi-sample validation hosts.
///
/// Search order:
/// 1. Explicit ``overrideDirectories`` (e.g. a shared validation asset folder)
/// 2. App Documents directory (for developer sideloading)
/// 3. The provided `bundle` and `Bundle.main`
public struct SampleModelLocator: Sendable {
  public let overrideDirectories: [URL]
  public let bundle: Bundle

  public init(bundle: Bundle = .main, overrideDirectories: [URL] = []) {
    self.bundle = bundle
    self.overrideDirectories = overrideDirectories
  }

  /// Resolves the on-disk file URL for `resourceName`.`fileExtension` and verifies it is non-empty.
  public func locate(
    resourceName: String,
    fileExtension: String
  ) throws -> URL {
    let filename = fileExtension.isEmpty ? resourceName : "\(resourceName).\(fileExtension)"
    let fm = FileManager.default

    var candidates: [URL] = overrideDirectories.map { $0.appendingPathComponent(filename) }

    if let docsDir = fm.urls(for: .documentDirectory, in: .userDomainMask).first {
      candidates.append(docsDir.appendingPathComponent(filename))
    }

    if let bundled = bundle.url(forResource: resourceName, withExtension: fileExtension) {
      candidates.append(bundled)
    }

    if bundle != Bundle.main,
      let mainBundled = Bundle.main.url(forResource: resourceName, withExtension: fileExtension)
    {
      candidates.append(mainBundled)
    }

    var sawEmptyStub = false
    for url in candidates where fm.fileExists(atPath: url.path) {
      let size = (try? fm.attributesOfItem(atPath: url.path)[.size] as? NSNumber)?.int64Value ?? 0
      if size > 0 {
        return url
      }
      sawEmptyStub = true
    }

    if sawEmptyStub {
      throw SampleModelError.emptyStub(filename: filename)
    }
    throw SampleModelError.missing(filename: filename)
  }
}
