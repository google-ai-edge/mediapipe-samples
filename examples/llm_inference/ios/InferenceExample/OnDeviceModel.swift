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

import Foundation
import MediaPipeTasksGenAI

final class OnDeviceModel {


  private var cachedInference: LlmInference?

  private var inference: LlmInference
  {
    get throws {
      if let cached = cachedInference {
         return cached
      } else {
        let path = Bundle.main.path(forResource: "gemma-1.1-2b-it-gpu-int4", ofType: "bin")!
        let llmOptions = LlmInference.Options(modelPath: path)
        cachedInference = try LlmInference(options: llmOptions)
        return cachedInference!
      }
    }
  }

  func generateResponse(prompt: String, progress: @escaping (String) -> Void) async throws -> String {
    var partialResult = ""

    let inference = try inference
    return try await withCheckedThrowingContinuation { continuation in
      do {
        try inference.generateResponseAsync(inputText: prompt) { partialResponse, error in
          if let error = error {
            print("Error 1: \(error)")
            continuation.resume(throwing: error)
            return
          }
          if let partial = partialResponse {
            partialResult += partial
            progress(partialResult.trimmingCharacters(in: .whitespacesAndNewlines))
          }
        } completion: {
          let aggregate = partialResult.trimmingCharacters(in: .whitespacesAndNewlines)
          continuation.resume(returning: aggregate)
          partialResult = ""
        }
      } catch let error {
        print("Error 2: \(error)")
        continuation.resume(throwing: error)
      }
    }
  }

  func startChat() -> Chat {
    return Chat(model: self)
  }

}

final class Chat {

  private let model: OnDeviceModel

  private var history = [String]()

  init(model: OnDeviceModel) {
    self.model = model
  }

  private func composeUserTurn(_ newMessage: String) -> String {
    return "<start_of_turn>user\n\(newMessage)<end_of_turn>\n"
  }

  private func composeModelTurn(_ newMessage: String) -> String {
    return "<start_of_turn>model\n\(newMessage)<end_of_turn>\n"
  }

  private func compositePrompt() -> String {
      return history.suffix(2).joined(separator: "\n")
  }

  func sendMessage(_ text: String, progress: @escaping (String) -> Void) async throws -> String {
    history.append(composeUserTurn(text))
    let prompt = compositePrompt()
    print("Prompt: \(prompt)")

    let reply = try await model.generateResponse(prompt: prompt, progress: progress)
    print("Reply: \(reply)")
    history.append(composeModelTurn(reply))

    return reply
  }

}
