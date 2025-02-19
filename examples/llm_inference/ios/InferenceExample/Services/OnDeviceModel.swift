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
import MediaPipeTasksGenAIC

/// Represents the LLM that will be used for inference.  It manages a MediaPipe `LlmInference` under the hood.
struct OnDeviceModel {
  /// MediaPipe LlmInference.
  private(set) var inference: LlmInference
  private static let maxTokens = 1024

  init(model: Model) throws {
    let options = LlmInference.Options(modelPath: try model.modelPath)
    options.maxTokens = OnDeviceModel.maxTokens

    inference = try LlmInference(options: options)
  }
}

/// Represents a chat session using an instance of `OnDeviceModel`.  It manages a MediaPipe
/// `LlmInference.Session` under the hood and passes all response generation queries to the session.
final class Chat {
  /// The on device model using which this chat session was created.
  private let model: OnDeviceModel

  /// MediaPipe session managed by the current instance.
  private var session: LlmInference.Session

  init(model: OnDeviceModel) throws {
    self.model = model
    session = try LlmInference.Session(llmInference: model.inference)
  }

  /// Sends a streaming response generation query to the underlying MediaPipe
  /// `LlmInference.Session`.
  /// - Parameters:
  ///   - text: Query to the underlying LLM.
  /// - Returns: An async throwing stream that contains the partial responses from the LLM.
  /// - Throws: A MediaPipe `GenAiInferenceError` if the query cannot be added to the current
  /// session.
  func sendMessage(_ text: String) async throws -> AsyncThrowingStream<String, any Error> {
    try session.addQueryChunk(inputText: text)
    let resultStream = session.generateResponseAsync()
    return resultStream
  }
}
