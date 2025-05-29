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

struct ModelMetadata {
  let pathName: String
  let pathExtension: String
  let licenseAcknowledgedKey: String
  let displayName: String
  let downloadUrlString: String
  let licenseUrlString: String?
  let canReason: Bool
  let thinkingMarkerEnd: String?
  let authRequired: Bool
  let temperature: Float
  let topK: Int
  let topP: Float
  
  init(
    pathName: String, pathExtension: String, licenseAcknowledgedKey: String = "",
    displayName: String, downloadUrlString: String, licenseUrlString: String? = nil,
    canReason: Bool = false, thinkingMarkerEnd: String? = nil, authRequired: Bool = false,
    temperature: Float, topK: Int, topP: Float
  ) {
    self.pathName = pathName
    self.pathExtension = pathExtension
    self.licenseAcknowledgedKey = licenseAcknowledgedKey
    self.displayName = displayName
    self.downloadUrlString = downloadUrlString
    self.licenseUrlString = licenseUrlString
    self.canReason = canReason
    self.thinkingMarkerEnd = thinkingMarkerEnd
    self.authRequired = authRequired
    self.temperature = temperature
    self.topK = topK
    self.topP = topP
  }
}

/// Holds the metadata of the models that can be used.
enum Model: CaseIterable {
  case gemma3
  case gemma2
  case deepSeek
  case qwen_2_5_0_5B_Instruct
  case qwen_2_5_1_5B_Instruct
  case tinyLlama_1_1B
  case llama_3_2_1B
  
  private var metadata: ModelMetadata {
    switch self {
      case .gemma3:
        return ModelMetadata(
          pathName: "gemma3-1b-it-int4",
          pathExtension: "task",
          licenseAcknowledgedKey: "gemma-license",
          displayName: "Gemma 3 1B CPU",
          downloadUrlString:
            "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task",
          licenseUrlString: "https://huggingface.co/litert-community/Gemma3-1B-IT",
          authRequired: true,
          temperature: 1.0,
          topK: 64,
          topP: 0.95
        )
      case .gemma2:
        return ModelMetadata(
          pathName: "Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280",
          pathExtension: "task",
          licenseAcknowledgedKey: "gemma-license",
          displayName: "Gemma 2 2B CPU",
          downloadUrlString:
            "https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q8_ekv1280.task",
          licenseUrlString: "https://huggingface.co/litert-community/Gemma2-2B-IT",
          authRequired: true,
          temperature: 0.6,
          topK: 50,
          topP: 0.9
        )
      case .deepSeek:
        return ModelMetadata(
          pathName: "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280",
          pathExtension: "task",
          displayName: "Deep Seek R1 Distill Qwen 1.5B",
          downloadUrlString:
            "https://huggingface.co/litert-community/DeepSeek-R1-Distill-Qwen-1.5B/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv1280.task",
          canReason: true,
          thinkingMarkerEnd: "</think>",
          temperature: 0.6,
          topK: 40,
          topP: 0.7
        )
      case .llama_3_2_1B:
        return ModelMetadata(
          pathName: "Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280",
          pathExtension: "task",
          licenseAcknowledgedKey: "llama3.2-1B-license",
          displayName: "Llama 3.2 1B Instruct",
          downloadUrlString:
            "https://huggingface.co/litert-community/Llama-3.2-1B-Instruct/resolve/main/Llama-3.2-1B-Instruct_multi-prefill-seq_q8_ekv1280.task",
          licenseUrlString: "https://huggingface.co/litert-community/Llama-3.2-1B-Instruct",
          authRequired: true,
          temperature: 0.6,
          topK: 64,
          topP: 0.9
        )
      case .qwen_2_5_0_5B_Instruct:
        return ModelMetadata(
          pathName: "Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280",
          pathExtension: "task",
          displayName: "Qwen 2.5 0.5B Instruct",
          downloadUrlString:
            "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
          temperature: 0.95,
          topK: 40,
          topP: 1.0
        )
      case .qwen_2_5_1_5B_Instruct:
        return ModelMetadata(
          pathName: "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280",
          pathExtension: "task",
          displayName: "Qwen 2.5 1.5B Instruct",
          downloadUrlString:
            "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
          temperature: 0.95,
          topK: 40,
          topP: 1.0
        )
      case .tinyLlama_1_1B:
        return ModelMetadata(
          pathName: "TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280",
          pathExtension: "task",
          displayName: "Tiny Llama 1.1B v1.0",
          downloadUrlString:
            "https://huggingface.co/litert-community/TinyLlama-1.1B-Chat-v1.0/resolve/main/TinyLlama-1.1B-Chat-v1.0_multi-prefill-seq_q8_ekv1280.task",
          temperature: 0.95,
          topK: 40,
          topP: 1.0
        )
    }
  }
  
  var licenseAcnowledgedKey: String { metadata.licenseAcknowledgedKey }
  var name: String { metadata.displayName }
  var canReason: Bool { metadata.canReason }
  var thinkingMarkerEnd: String? { metadata.thinkingMarkerEnd }
  var authRequired: Bool { metadata.authRequired }
  var temperature: Float { metadata.temperature }
  var topK: Int { metadata.topK }
  var topP: Float { metadata.topP }
  
  private var path: (name: String, extension: String) {
    (metadata.pathName, metadata.pathExtension)
  }
  
  var downloadUrl: URL {
    URL(string: metadata.downloadUrlString)!
  }
  
  var licenseUrl: URL? {
    guard let urlString = metadata.licenseUrlString else { return nil }
    return URL(string: urlString)
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
}
