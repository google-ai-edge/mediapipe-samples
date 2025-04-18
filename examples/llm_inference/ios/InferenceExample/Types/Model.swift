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

  struct ConversationMarkers: Equatable {
    let userPrefix: String
    let modelPrefix: String
    let startOfTurn: String
    let endOfTurn: String
    let thinkingStart: String?
    let thinkingEnd: String?
  }

  private var path: (name: String, extension: String) {
    switch self {
    case .gemma:
      return ("gemma2_q8_multi-prefill-seq_ekv1280", "task")
    case .phi4:
      return ("phi4_q8_ekv1280", "task")
    }
  }

  var licenseAcnowledgedKey: String {
    switch self {
    case .gemma:
      return "gemma-license"
    case .phi4:
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
      return "Gemma 2"
    case .phi4:
      return "Phi 4"
    }
  }

  var downloadUrl: URL {
    switch self {
    case .gemma:
      return URL(
        string:
          "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/gemma2_q8_multi-prefill-seq_ekv1280.task"
      )!
    case .phi4:
      return URL(
        string:
          "https://huggingface.co/litert-community/Phi-4-mini-instruct/resolve/main/phi4_q8_ekv1280.task"
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
    }
  }

  var conversationMarkers: ConversationMarkers {
    switch self {
    case .gemma:
      return ConversationMarkers(
        userPrefix: "user", modelPrefix: "model", startOfTurn: "<start_of_turn>",
        endOfTurn: "<end_of_turn>", thinkingStart: nil, thinkingEnd: nil)
    case .phi4:
      return ConversationMarkers(
        userPrefix: "user", modelPrefix: "model", startOfTurn: "<start_of_turn>",
        endOfTurn: "<end_of_turn>", thinkingStart: nil, thinkingEnd: nil)
    }
  }
}
