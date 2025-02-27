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
    case criticalError(error: InferenceError)
    case createChatError(error: InferenceError)
    case done

    /// Extracts the inference error if the current state is one of the error states.
    var inferenceError: InferenceError? {
      switch self {
      case let .criticalError(error), let .createChatError(error):
        return error
      default:
        return nil
      }
    }

    static func == (lhs: State, rhs: State) -> Bool {
      switch (lhs, rhs) {
      case (.loadingModel, .loadingModel),
        (.promptSubmitted, .promptSubmitted),
        (.streamingResponse, .streamingResponse),
        (.done, .done):
        return true
      /// Error equality checks are not required for updates to the UI at the moment. If required more fine grained equality
      /// logic can be implemented here.
      case (.criticalError, .criticalError), (.createChatError, .createChatError):
        return false
      default:
        return false
      }
    }

  }

  /// Based on this property updates are made to the UI State including enabling and disabling of messaging, other buttons etc.
  @Published var currentState: State = .loadingModel
  
  /// Model to initialize.
  private var modelCategory: Model

  /// Model used for inference. Wraps around a MediaPipe `LlmInference`.
  private var model: OnDeviceModel?

  /// Current conversation with the LLM that preserves history. Wraps around a MediaPipe `LlmInference.Session`.
  private var chat: Chat?

  init(modelCategory: Model) {
    self.modelCategory = modelCategory
  }

  func loadModel() {
    Task {
      load(modelCategory: modelCategory)
    }
  }

  func clearModel() {
    chat = nil
    model = nil
    currentState = .loadingModel
  }

  private func load(modelCategory: Model) {
    do {
      /// Gets updated to done or error in `startNewChat()` if there is an error in chat initialization.
      currentState = .loadingModel
      self.model = try OnDeviceModel(model: modelCategory)

      startNewChat()
    } catch let error as InferenceError {
      currentState = .criticalError(error: error)
    } catch {
      currentState = .criticalError(error: InferenceError.mediaPipeTasksError(error: error))
    }
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
    }
  }

  /// Clears the current conversation and stats a new chat.
  func startNewChat() {
    /// Setting critical error so that if user tries to click on new chat when no model is initialized the alert is displayed again.
    guard let model else {
      currentState = .criticalError(error: InferenceError.onDeviceModelNotInitialized)
      return
    }

    currentState = .loadingModel

    do {
      chat = try Chat(model: model)
      messageViewModels.removeAll()
      currentState = .done
    } catch {
      currentState = .createChatError(error: InferenceError.mediaPipeTasksError(error: error))
    }
  }

  /// Resets state after error alert is displayed. If it is a critical error (model loading error), then the UI remains disabled and hence the
  /// state shouldn't be reset.
  /// If  the error is a create chat error and there is an ongoing chat session, the state can be set to done since the user can be allowed
  /// to continue chatting with the current session.
  /// If the there is no chat session active, then the UI should remain disabled since it indicates an active session could not be created.
  /// Third scenario would never happen because of the UI guards. Leaving the condition here for correctness.
  func resetStateAfterErrorIntimation() {
    guard case .createChatError = currentState, chat != nil else {
      return
    }

    currentState = .done
  }

  private func formatPrompt(text: String) -> String {
    let startTurn = "<start_of_turn>"
    let endTurn = "<end_of_turn>"
    let userPrefix = "user"
    let modelPrefix = "model"

    return "\(startTurn)\(userPrefix)\n\(text)\(endTurn)\(startTurn)\(modelPrefix)"
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
    } catch {

      /// The message is partially generated when an error occurred. Add a new message indicating the error rather than updating
      /// the existing message with an error.
      /// If there is a previous message, it's state is updated as done when the messageVM is closed in the calling function.
      if messageVM.chatMessage.participant == .system(.response) {
        self.messageViewModels.append(
          MessageViewModel(chatMessage: ChatMessage(participant: .system(.error))))
      }
    }
  }

  /// Sends the message to the currently active instance of `Chat` which in turn queries the underlying MediaPipe
  /// LlmInference.Session to asynchronously stream the response to the prompt. The method adds the new Message VM  with the
  /// message from  the user to the published `messageViewModels` and continuously updates the streamed response.
  private func internalSendMessage(_ text: String) async {
    guard let chat else {
      currentState = .criticalError(error: InferenceError.onDeviceModelNotInitialized)
      return
    }

    currentState = .promptSubmitted
    defer { currentState = .done }

    ///Add the user's message to the chat .
    let userViewModel = MessageViewModel(chatMessage: ChatMessage(text: text, participant: .user))
    messageViewModels.append(userViewModel)

    /// Add a generating message to show a progress bar in the message until first token is generated.
    let systemViewModel = MessageViewModel(
      chatMessage: ChatMessage(participant: .system(.generating)))
    messageViewModels.append(systemViewModel)

    defer { systemViewModel.closeSystemMessage() }

    do {
      let prompt = formatPrompt(text:text)
      let responseStream = try await chat.sendMessage(prompt)

      await updateSystemViewModel(systemViewModel, responseStream: responseStream)
    } catch {
      /// systemViewModel is closed in a defer before exiting this scope. Any errors are handled during close.
    }
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
