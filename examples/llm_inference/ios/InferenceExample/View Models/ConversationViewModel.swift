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

@MainActor
class ConversationViewModel: ObservableObject {
  /// This array holds both the view models responsible for presentation of both user and system messages
  @Published var messageViewModels = [MessageViewModel]()

  /// .error state is set only when there are errors in loading the model and creating a new chat session.
  /// Response generation errors are relayed as messages from the model.
  enum State: Equatable {
    case loadingModel
    case promptSubmitted
    case streamingResponse
    case error
    case done
  }

  /// Based on this property updates are made to the UI State including enabling and disabling of messaging, other buttons etc.
  @Published var currentState: State = .loadingModel {
    didSet {
      guard currentState != .error else {
        return
      }
      
      criticalError = nil
    }
  }

  /// If this error is updated an alert is displayed on the screen.
  @Published var criticalError: InferenceError? {
    didSet {
      guard let _ = criticalError else {
        return
      }
      
      currentState = .error
    }
  }
  
  @Published var createChatError: InferenceError? {
    didSet {
      guard let _ = createChatError, chat == nil else {
        currentState = .done
        return
      }

      currentState = .error
    }
  }

  /// Model used for inference. Wraps around a MediaPipe `LlmInference`.
  private var model: OnDeviceModel?

  /// Current conversation with the LLM that preserves history. Wraps around a MediaPipe `LlmInference.Session`.
  private var chat: Chat?

  init(modelCategory: Model) {
    Task {
      load(modelCategory: modelCategory)
    }
  }

  func load(modelCategory: Model) {
    do {
      /// Gets updated to done or error in `startNewChat()` if there is an error in chat initialization.
      currentState = .loadingModel
      self.model = try OnDeviceModel(model: modelCategory)

      startNewChat()
    } catch let error as InferenceError {
      criticalError = error
    } catch {
      criticalError = InferenceError.mediaPipeTasksError(error: error)
    }
  }

  func updateCriticalError(_ error: InferenceError) {
    currentState = .error
    self.criticalError = error
  }

  /// Queries the LLM session with the given text prompt asynchronously. If the prompt completes
  /// successfully, it updates the published `messages` with the new partial response
  /// continuously until the response generation completes. In case of an error, sets the
  /// published `error.
  /// - Parameters:
  ///   - text: Prompt to be sent to the model.
  func sendMessage(_ text: String) {
    Task {
      await internalSendMessage(text)
      currentState = .done
    }
  }

  /// Clears the current conversation and stats a new chat.
  func startNewChat() {
    /// Setting critical error so that if user tries to click on new chat when no model is initialized the alert is displayed again.
    guard let model else {
      criticalError = InferenceError.onDeviceModelNotInitialized
      return
    }

    currentState = .loadingModel

    do {
      chat = try Chat(model: model)
      messageViewModels.removeAll()
      currentState = .done
    } catch {
      createChatError = InferenceError.mediaPipeTasksError(error: error)
    }
  }

  private func updateSystemViewModel(
    _ messageVM: MessageViewModel, responseStream: AsyncThrowingStream<String, any Error>
  ) async {
    defer {
      currentState = .done
    }

    currentState = .streamingResponse
    do {
      for try await partialResult in responseStream {
        messageVM.update(text: partialResult)
      }

      messageVM.closeSystemMessage()
    } catch {
      self.messageViewModels.append(
        MessageViewModel(chatMessage: ChatMessage(participant: .system(.error))))
    }

  }

  /// Sends the message to the currently active instance of `Chat` which in turn queries the underlying MediaPipe
  /// LlmInference.Session to asynchronously stream the response to the prompt. The method adds the new Message VM  with the
  /// message from  the user to the published `messageViewModels` and continuously updates the streamed response.
  private func internalSendMessage(_ text: String) async {
    guard let chat else {
      criticalError = InferenceError.onDeviceModelNotInitialized
      return
    }

    defer {
      currentState = .done
    }

    currentState = .promptSubmitted

    ///Add the user's message to the chat .
    let userViewModel = MessageViewModel(chatMessage: ChatMessage(text: text, participant: .user))
    messageViewModels.append(userViewModel)

    let systemViewModel = MessageViewModel(
      chatMessage: ChatMessage(participant: .system(.generating)))
    messageViewModels.append(systemViewModel)
    
    do {
      let responseStream = try await chat.sendMessage(text)
      
      await updateSystemViewModel(systemViewModel, responseStream: responseStream)
    } catch {
      systemViewModel.update(participant: .system(.error))
    }

  }
}

@MainActor
class MessageViewModel: ObservableObject, Identifiable {
  @Published var chatMessage: ChatMessage

  init(chatMessage: ChatMessage) {
    self.chatMessage = chatMessage
  }

  func update(participant: ChatMessage.Participant) {
    guard chatMessage.participant != .user && participant != .user else {
      return
    }

    chatMessage.participant = participant
  }

  func update(text: String) {
    switch chatMessage.participant {
    case .user:
      chatMessage.text = text
    default:
      /// Trim any leading characters in whole message. 
      chatMessage.text = String(
        (chatMessage.text + text).drop(while: { $0.isWhitespace || $0.isNewline }))
      chatMessage.participant = .system(.response)
    }
  }

  func closeSystemMessage() {
    update(participant: chatMessage.text.count > 0 ? .system(.done) : .system(.error))
  }
}

/// Holds the names of the models names that can be  used.
enum Model: CaseIterable {
  case gemmaCPU
  case gemmaGPU

  private var path: (name: String, extension: String) {
    switch self {
    case .gemmaCPU:
        return ("gemma-2b-it-cpu-int4", "bin")
    case .gemmaGPU:
      return ("gemma-2b-it-gpu-int4", "bin")
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

  var name: String {
    switch self {
    case .gemmaCPU:
      return "Gemma CPU"
    case .gemmaGPU:
      return "Gemma GPU"
    }
  }
}

/// Represents a single message in the chat.
struct ChatMessage: Identifiable, Equatable {
  /// Unique identifier for the message.
  let id = UUID().uuidString
  
  /// Text contained in the message.
  var text: String
  
  /// Indicates if user or system (LLM) has sent the message.
  var participant: Participant

  init(text: String = "", participant: Participant) {
    self.text = text
    self.participant = participant
  }
  
  /// Represents the type of message.
  enum Participant: Equatable {
    
    enum System: Equatable {
      case thinking
      case generating
      case response
      case done
      case error
    }
    
    case system(_ value: System)
    case user
    
    var title: String {
      switch self {
        case .system(.generating):
          return "Generating"
        case .system(.thinking):
          return "Thinking"
        case .system(.response), .system(.done), .system(.error):
          return "Model"
        case .user:
          return "User"
      }
    }
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
