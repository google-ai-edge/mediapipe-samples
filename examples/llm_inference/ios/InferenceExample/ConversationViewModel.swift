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

/// Represents a single message in the chat.
struct ChatMessage: Identifiable, Equatable {

  /// Represents the type of message.
  enum Participant {
    case system
    case user
  }

  /// Unique identifier for the message.
  let id = UUID().uuidString
  /// Text contained in the message.
  var text: String
  /// Indicates if user or system (LLM) has sent the message.
  let participant: Participant

  init(text: String = "", participant: Participant) {
    self.text = text
    self.participant = participant
  }

}

/// Represents any error thrown by this application.
enum InferenceError: LocalizedError {
  /// Wraps an error thrown by MediaPipe.
  case mediaPipeTasksError(error: Error)
  case modelFileNotFound(modelName: String)
  case onDeviceModelNotInitialized

  public var errorDescription: String? {
    switch self {
    case .mediaPipeTasksError:
      return "Internal error"
    case .modelFileNotFound:
      return "Model not found"
    case .onDeviceModelNotInitialized:
      return "Model Unitialized"
    }
  }

  public var failureReason: String {
    switch self {
    case .mediaPipeTasksError(let error):
      return error.localizedDescription
    case .modelFileNotFound(let modelName):
      return "Model with name \(modelName) not found in your app."
    case .onDeviceModelNotInitialized:
      return "A valid on device model has not been initalized."
    }
  }

}

/// Holds the names of the models names that can be  used.
enum Model: CaseIterable {
  case gemma

  private var path: (name: String, extension: String) {
    switch self {
    case .gemma:
      return ("gemma-2b-it-cpu-int4", "bin")
    }
  }

  var modelPath: String {
    get throws {
      guard
        let path = Bundle.main.path(
          forResource: path.name, ofType: path.extension)
      else {
        throw InferenceError.modelFileNotFound(modelName: "\(path.name).\(path.extension)")
      }
      return path
    }
  }
}

@MainActor
class ConversationViewModel: ObservableObject {
  /// This array holds both the user's and the system's chat messages.
  @Published var messages = [ChatMessage]()

  /// Indicates if we're waiting for the model to be initialized or finish generating a response.  
  /// Based on this value `ConversationScreen` disables or enables messaging.
  @Published var busy = true

  /// Indicates if we're waiting for the model to be initialized or finish generating a response.  
  /// Based on this value `ConversationScreen` disables or enables messaging.
  @Published var error: InferenceError?

  /// Model used for inference. Wraps around a MediaPipe `LlmInference`.
  private var model: OnDeviceModel?

  /// Current conversation with the LLM that preserves history. Wraps around a MediaPipe 
  /// `LlmInference.Session`. 
  private var chat: Chat?

  init() {
    defer {
      busy = false
    }
    do {
      let model = try OnDeviceModel(model: Model.gemma)
      self.model = model
      chat = try Chat(inference: model.inference)
    } catch let error as InferenceError {
      self.error = error
    } catch {
      self.error = InferenceError.mediaPipeTasksError(error: error)
    }
  }

  /// Queries the LLM session with the given text prompt asynchronously. If the prompt completes 
  /// successfully, it updates the published `messages` with the new partial response 
  /// continuously until the response generation completes. In case of an error, sets the 
  /// published `error.
  /// - Parameters:
  ///   - text: Prompt to be sent to the model.
  func sendMessage(_ text: String) {
    busy = true
    Task {
      await internalSendMessage(text)
      busy = false
    }
  }

  /// Clears the current conversation and stats a new chat.
  func startNewChat() {
    /// Setting busy to `true` to indicate no UI updates must be made until this method returns.
    busy = true
    defer {
      busy = false
    }

    guard let model else {
      error = InferenceError.onDeviceModelNotInitialized
      return
    }
    do {
      chat = try Chat(inference: model.inference)
      messages.removeAll()
    } catch {
      self.error = InferenceError.mediaPipeTasksError(error: error)
    }
  }

   /// Sends the message to the currently active instance of `Chat` which in turn queries the
   /// underlying MediaPipe LlmInference.Session to asynchronously stream the response to the 
   /// prompt. The method adds the new message from  the user to the published `messages` and 
   /// ontinuously updates the streamed response. In case of an error the published `error` is set.
   /// - Parameters: 
   ///   - text: Prompt to be sent to the underlying MediaPipe session.
  private func internalSendMessage(_ text: String) async {
    guard let chat else {
      error = InferenceError.onDeviceModelNotInitialized
      return
    }
    ///Add the user's message to the chat .
    let userMessage = ChatMessage(text: text, participant: .user)
    messages.append(userMessage)

    do {
      /// Send the message to the chat session to query the model.
      let responseStream = try await chat.sendMessage(text)

      /// Await for the partial responses from the model. */
      for try await partialResult in responseStream {
        /// For the sake of optional unwrapping. Will never happen. An error need not be thrown.
        guard let lastMessage = self.messages.last else {
          break
        }

        /// If this is the first partial response, add a new LLM message to the chat, otherwise 
        /// append to the existing last message. Currently messaging is blocked while a response 
        /// is being generated, so if this is not the first partial response, the last message is 
        /// guarenteed to be the current message being generated by the LLM.
        if lastMessage.participant == .user {
          messages.append(ChatMessage(participant: .system))
        }
        let systemMessageText = messages[messages.count - 1].text

        /// Trim any leading characters in whole message. Note: can safely access the index 
        /// `messages.count - 1` since the presence of a last message has already been guaranteed 
        /// by the previous code snippet.
        messages[messages.count - 1].text = String(
          (systemMessageText + partialResult).drop(while: { $0.isWhitespace || $0.isNewline }))
      }
    } catch {
      /// `chat.sendMessage(text)` throws only MediaPipeTask errors. Hence all errors thrown from 
      /// can be assumed.
      self.error = InferenceError.mediaPipeTasksError(error: error)
      messages.removeLast()
    }
  }
}
