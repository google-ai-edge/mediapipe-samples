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

enum Participant {
  case system
  case user
}

struct ChatMessage: Identifiable, Equatable {
  let id = UUID().uuidString
  var message: String
  let participant: Participant
  var pending = false

  static func pending(participant: Participant) -> ChatMessage {
    self.init(message: "", participant: participant, pending: true)
  }
}

@MainActor
class ConversationViewModel: ObservableObject {
  /// This array holds both the user's and the system's chat messages
  @Published var messages = [ChatMessage]()

  /// Indicates we're waiting for the model to finish
  @Published var busy = false

  @Published var error: Error?
  var hasError: Bool {
    return error != nil
  }

  private var model: OnDeviceModel
  private var chat: Chat
  private var stopGenerating = false

  private var chatTask: Task<Void, Never>?

  init() {
    model = OnDeviceModel()
    chat = model.startChat()
  }

  func sendMessage(_ text: String) async {
    error = nil
    await internalSendMessage(text)
  }

  func startNewChat() {
    stop()
    error = nil
    chat = model.startChat()
    messages.removeAll()
  }

  func stop() {
    chatTask?.cancel()
    error = nil
  }

  private func internalSendMessage(_ text: String) async {
    chatTask?.cancel()

    chatTask = Task {
      busy = true
      defer {
        busy = false
      }

      // first, add the user's message to the chat
      let userMessage = ChatMessage(message: text, participant: .user)
      messages.append(userMessage)

      // add a pending message while we're waiting for a response from the backend
      let systemMessage = ChatMessage.pending(participant: .system)
      messages.append(systemMessage)

      do {
        let response = try await chat.sendMessage(text, progress : { [weak self] partialResult in
          guard let self = self else { return }
          DispatchQueue.main.async {
            self.messages[self.messages.count - 1].message = partialResult
          }
        })

        // replace pending message with model response
        messages[messages.count - 1].message = response
        messages[messages.count - 1].pending = false
      } catch {
        self.error = error
        print(error.localizedDescription)
        messages.removeLast()
      }
    }
  }
}
