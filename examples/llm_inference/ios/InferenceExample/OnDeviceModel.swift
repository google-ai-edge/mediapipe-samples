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

  private var inference: LlmInference! = {
    let path = Bundle.main.path(forResource: "gemma-2b-it-cpu-int4", ofType: "bin")!
    let llmOptions = LlmInference.Options(modelPath: path)
    return LlmInference(options: llmOptions)
  }()

  func generateResponse(prompt: String) async throws -> String {
    var partialResult = ""
    return try await withCheckedThrowingContinuation { continuation in
      do {
        try inference.generateResponseAsync(inputText: prompt) { partialResponse, error in
          if let error = error {
            continuation.resume(throwing: error)
            return
          }
          if let partial = partialResponse {
            partialResult += partial
          }
        } completion: {
          let aggregate = partialResult.trimmingCharacters(in: .whitespacesAndNewlines)
          continuation.resume(returning: aggregate)
          partialResult = ""
        }
      } catch let error {
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

  private func compositePrompt(newMessage: String) -> String {
    return history.joined(separator: "\n") + "\n" + newMessage
  }

  func sendMessage(_ text: String) async throws -> String {
    let prompt = compositePrompt(newMessage: text)
    let reply = try await model.generateResponse(prompt: prompt)

    history.append(text)
    history.append(reply)

    print("Prompt: \(prompt)")
    print("Reply: \(reply)")
    return reply
  }

}
