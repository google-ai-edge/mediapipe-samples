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

/// Holds the metadata of the models that can be used.
enum Model: CaseIterable {
  case gemma
  case phi4
  case deepSeek

  private var path: (name: String, extension: String) {
    switch self {
    case .gemma:
      return ("Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280", "task")
    case .phi4:
      return ("Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280", "task")
    case .deepSeek:
      return ("DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280", "task")
    }
  }

  var licenseAcnowledgedKey: String {
    switch self {
    case .gemma:
      return "gemma-license"
    case .phi4:
      return ""
    case .deepSeek:
      return ""
    }
  }

  var modelPath: String {
    get throws {
      let docsURL = try downloadDestination
      if FileManager.default.fileExists(atPath: docsURL.path) {
        return docsURL.relativePath
      }
      guard
        let path = Bundle.main.path(
          forResource: path.name, ofType: path.extension)
      else {
        throw InferenceError.modelFileNotFound(modelName: "\(path.name).\(path.extension)")
      }

      return path
    }
  }

  var name: String {
    switch self {
    case .gemma:
      return "Gemma 2 2B CPU"
    case .phi4:
      return "Phi 4"
    case .deepSeek:
      return "Deep Seek"
    }
  }

  var downloadUrl: URL {
    switch self {
    case .gemma:
      return URL(
        string:
          "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task"
      )!
    case .phi4:
      return URL(
        string:
          "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/Phi-4-mini-instruct_multi-prefill-seq_q8_ekv1280.task"
      )!
    case .deepSeek:
      return URL(
        string:
          "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task"
      )!
    }
  }

  var downloadDestination: URL {
    get throws {
      let path = try FileManager.default.url(
        for: .documentDirectory,
        in: .userDomainMask,
        appropriateFor: nil,
        create: true
      ).appendingPathComponent("\(path.name).\(path.extension)")

      return path
    }
  }

  var licenseUrl: URL? {
    switch self {
    case .gemma:
      return URL(string: "https://huggingface.co/litert-community/Gemma2-2B-IT")
    case .phi4:
      return nil
    case .deepSeek:
      return nil
    }
  }

  var canReason: Bool {
    if case .deepSeek = self {
      return true
    }

    return false
  }

  var thinkingMarkerEnd: String? {
    if case .deepSeek = self {
      return "</think>"
    }

    return nil
  }

  var authRequired: Bool {
    switch self {
    case .gemma:
      return true
    case .deepSeek, .phi4:
      return false
    }
  }

  var temperature: Float {
    switch self {
    case .gemma:
      return 1.0
    case .deepSeek:
      return 0.6
    case .phi4:
      return 0.0
    }
  }

  var topK: Int {
    switch self {
    case .gemma:
      return 64
    case .deepSeek:
      return 40
    case .phi4:
      return 40
    }
  }

  var topP: Float {
    switch self {
    case .gemma:
      return 0.95
    case .deepSeek:
      return 0.7
    case .phi4:
      return 0.1
    }
  }
}
