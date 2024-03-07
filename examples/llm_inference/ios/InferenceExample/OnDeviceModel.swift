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
    let path = Bundle.main.path(forResource: "model_cpu", ofType: "tflite")!
    let llmOptions = LlmInference.Options(modelPath: path)
    return LlmInference(options: llmOptions)
  }()

  // Temporary to remove extra replace characters left over in model output.
  // Remove this function once no longer necessary.
  private func cleanOutput(_ string: String) -> String {
    let replaceCharacter: Character = "\u{FFFD}"
    return string.reduce("") { partialResult, next in
      guard let last = partialResult.last else {
        if next == replaceCharacter {
          return partialResult
        }
        return String(next)
      }

      let lastCharacterIsWhitespace = last.unicodeScalars.reduce(false, {
        $0 || CharacterSet.whitespacesAndNewlines.contains($1)
      })
      if next == replaceCharacter {
        if lastCharacterIsWhitespace {
          return partialResult
        }
        return partialResult + " "
      }
      return partialResult + String(next)
    }
  }

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
            partialResult += partial.trimmingCharacters(in: .illegalCharacters)
          }
        } completion: {
          let aggregate = self.cleanOutput(partialResult)
            .trimmingCharacters(in: .whitespacesAndNewlines)
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

  private let basePrompt =
      "You are a helpful chatbot. Respond to the latest message given the chat history below:\n"

  private var history = [String]()

  init(model: OnDeviceModel) {
    self.model = model
  }

  private func compositePrompt(newMessage: String) -> String {
    return basePrompt + history.joined(separator: "\n") + "\n" + newMessage
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
