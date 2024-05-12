// Copyright 2024 The Mediapipe Authors.
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

import SwiftUI

struct ConversationScreen: View {
  @EnvironmentObject
  var viewModel: ConversationViewModel

  @State
  private var userPrompt = ""

  enum FocusedField: Hashable {
    case message
  }

  @FocusState
  var focusedField: FocusedField?

  var body: some View {
    VStack {
      ScrollViewReader { scrollViewProxy in
        List {
          ForEach(viewModel.messages) { message in
            MessageView(message: message)
          }
          if let error = viewModel.error {
            ErrorView(error: error)
              .tag("errorView")
          }
        }
        .listStyle(.plain)
        .onChange(of: viewModel.messages) { _, newValue in
          if viewModel.hasError {
            // wait for a short moment to make sure we can actually scroll to the bottom
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
              withAnimation {
                scrollViewProxy.scrollTo("errorView", anchor: .bottom)
              }
              focusedField = .message
            }
          } else {
            guard let lastMessage = viewModel.messages.last else { return }

            // wait for a short moment to make sure we can actually scroll to the bottom
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
              withAnimation {
                scrollViewProxy.scrollTo(lastMessage.id, anchor: .bottom)
              }
              focusedField = .message
            }
          }
        }
      }
      TextField("Message...", text: $userPrompt)
        .focused($focusedField, equals: .message)
        .onSubmit { sendOrStop() }
        .submitLabel(.send)
        .disabled(viewModel.busy)
        .padding()
    }
    .toolbar {
      ToolbarItem(placement: .primaryAction) {
        Button(action: newChat) {
          Image(systemName: "square.and.pencil")
        }
      }
    }
    .navigationTitle("Chat sample")
    .onAppear {
      focusedField = .message
    }
  }

  private func sendMessage() {
    Task {
      let prompt = userPrompt
      userPrompt = ""
      await viewModel.sendMessage(prompt)
    }
  }

  private func sendOrStop() {
    if viewModel.busy {
      viewModel.stop()
    } else {
      sendMessage()
    }
  }

  private func newChat() {
    viewModel.startNewChat()
  }
}

struct MessageView: View {
  var message: ChatMessage

  var body: some View {
    HStack {
      if message.participant == .user {
        Spacer()
      }
      Text(message.message)
        .padding(10)
        .foregroundStyle(Color(red: 0.0, green: 0.0, blue: 0.0))
        .background(message.participant == .system
                    ? Color(white: 0.9231)
          : Color(red: 0.8627, green: 0.9725, blue: 0.7764))
        .clipShape(RoundedRectangle(cornerRadius: 16))
      if message.participant == .system {
        Spacer()
      }
    }
    .listRowSeparator(.hidden)
  }
}

struct ErrorView: View {
  var error: Error

  var body: some View {
    HStack {
      Text("An error occurred: \(error.localizedDescription)")
    }
    .frame(maxWidth: .infinity, alignment: .center)
    .listRowSeparator(.hidden)
  }
}
