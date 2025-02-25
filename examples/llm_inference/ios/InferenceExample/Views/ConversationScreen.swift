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

import LaTeXSwiftUI
import SwiftUI

struct ConversationScreen: View {
  private struct Constants {
    static let scrollDelayInSeconds = 0.05
    static let alertBackgroundColor = Color.black.opacity(0.3)
    static let newChatSystemSymbolName = "square.and.pencil"
    static let navigationTitle = "Chat with your LLM here"
    static let modelInitializationAlertText = "Model initialization in progress."
  }

  @Environment(\.dismiss) var dismiss

  @ObservedObject
  var viewModel: ConversationViewModel

  @State
  private var currentUserPrompt = ""

  private enum FocusedField: Hashable {
    case message
  }

  @FocusState
  private var focusedField: FocusedField?

  var body: some View {
    ZStack {
      VStack {
        ScrollViewReader { scrollViewProxy in
          List {
            ForEach(viewModel.messageViewModels) { vm in
              MessageView(messageViewModel: vm) { messageId in
                DispatchQueue.main.async {
                  scrollViewProxy.scrollTo(messageId, anchor: .bottom)
                }
              }
            }
          }
          .listStyle(.plain)
        }
        .scrollDismissesKeyboard(.immediately)
        TextTypingView(
          state: $viewModel.currentState,
          onSubmitAction: { [weak viewModel] prompt in
            viewModel?.sendMessage(prompt)
          })
      }.toolbar {
        ToolbarItem(placement: .primaryAction) {
          Button(action: viewModel.startNewChat) {
            Image(systemName: Constants.newChatSystemSymbolName)
          }
        }
      }
      .navigationTitle(Constants.navigationTitle)
      .navigationBarTitleDisplayMode(.inline)
      .disabled(shouldDisableClicks())

      if viewModel.currentState == .loadingModel {
        Constants.alertBackgroundColor
          .edgesIgnoringSafeArea(.all)
        ProgressView(Constants.modelInitializationAlertText)
          .tint(.accentColor)
      }
    }
    .alert(
      state: $viewModel.currentState,
      action: { [weak viewModel] in
        if shouldDismiss() {
          dismiss()
        } else {
          viewModel?.resetStateAfterErrorIntimation()
        }
      })
    .onAppear { [weak viewModel] in
      viewModel?.loadModel()
    }
    .onDisappear { [weak viewModel] in
      viewModel?.clearModel()
    }
    
  }

  private func shouldDismiss() -> Bool {
    if case .criticalError = viewModel.currentState { return true }
    return false
  }

  private func shouldDisableClicks() -> Bool {
    if case .createChatError = viewModel.currentState { return true }
    return false
  }
}

/// View that displays a message.
struct MessageView: View {
  private struct Constants {
    static let textMessagePadding: CGFloat = 10.0
    static let foregroundColor = Color(red: 0.0, green: 0.0, blue: 0.0)
    static let systemMessageBackgroundColor = Color(white: 0.9231)
    static let userMessageBackgroundColor = Color(red: 0.8627, green: 0.9725, blue: 0.7764)
    static let errorBackgroundColor = Color.red.opacity(0.1)
    static let messageBackgroundCornerRadius: CGFloat = 16.0
    static let generationErrorText = "Could not generate response"
    static let font = Font.system(size: 10, weight: .regular, design: .default)
    static let tint = Color.green
  }

  @ObservedObject var messageViewModel: MessageViewModel
  var onTextUpdate: (String) -> Void

  var body: some View {
    HStack {
      if messageViewModel.chatMessage.participant == .user {
        Spacer()
      }
      VStack(alignment: messageViewModel.chatMessage.participant == .user ? .trailing : .leading) {
        Text(messageViewModel.chatMessage.participant.title)
          .font(Constants.font)
          .frame(
            alignment: messageViewModel.chatMessage.participant == .user ? .trailing : .leading)
        switch messageViewModel.chatMessage.participant {
        case .user:
          MessageContentView(
            text: messageViewModel.chatMessage.text,
            backgroundColor: Constants.userMessageBackgroundColor)
        case .system(value: .response), .system(value: .thinking), .system(value: .done):
          MessageContentView(
            text: messageViewModel.chatMessage.text,
            backgroundColor: Constants.systemMessageBackgroundColor)
        case .system(value: .error):
          MessageContentView(
            text: Constants.generationErrorText, backgroundColor: Constants.errorBackgroundColor)
        case .system(value: .generating):
          ProgressView().tint(Constants.tint)
        }
      }
    }
    .listRowSeparator(.hidden)
    .id(messageViewModel.chatMessage.id)
    .onReceive(messageViewModel.$chatMessage) { [weak messageViewModel] _ in
      guard let chatMessageId = messageViewModel?.chatMessage.id else {
        return
      }
      onTextUpdate(chatMessageId)
    }
  }
}

/// Content of a message view which applies attributed string and LaTex modifications for display.
struct MessageContentView: View {
  private struct Constants {
    static let textMessagePadding: CGFloat = 10.0
    static let foregroundColor = Color(red: 0.0, green: 0.0, blue: 0.0)
    static let messageBackgroundCornerRadius: CGFloat = 16.0
  }

  var text: String
  var backgroundColor: Color

  var body: some View {
    VStack(alignment: .leading, spacing: 0.0) {
      ForEach(text.mathTextSplits, id: \.id) { item in
        if item.isMath {
          LaTeX(item.content).parsingMode(.onlyEquations)
        } else {
          Text(item.content.attributedString)
        }
      }
    }
    .padding(Constants.textMessagePadding)
    .foregroundStyle(Constants.foregroundColor)
    .background(
      backgroundColor
    )
    .clipShape(RoundedRectangle(cornerRadius: Constants.messageBackgroundCornerRadius))
  }

}

/// Bottom view that displays text field and button.
struct TextTypingView: View {
  private struct Constants {
    static let messageFieldPlaceHolder = "Message..."
    static let textFieldCornerRadius = 16.0
    static let textFieldHeight = 55.0
    static let textFieldBackgroundColor = Color.white
    static let buttonSize = 30.0
    static let viewBackgroundColor = Color.gray.opacity(0.1)
    static let textFieldStrokeColor = Color.gray
    static let sendButtonImage = "arrow.up.circle.fill"
    static let buttonDisabledColor = Color.gray
    static let buttonEnabledColor = Color.green
    static let padding = 10.0
  }

  @Environment(\.colorScheme) var colorScheme
  @Binding var state: ConversationViewModel.State

  var onSubmitAction: (String) -> Void

  @State private var content: String = ""

  enum FocusedField: Hashable {
    case message
  }
  private var backgroundColor: Color {
    colorScheme == .dark ? Color(.systemGray6) : Color(.systemGray5)
  }

  private var textColor: Color {
    colorScheme == .dark ? .white : .black
  }

  @FocusState
  var focusedField: FocusedField?

  var body: some View {
    HStack(spacing: Constants.padding) {
      TextField(Constants.messageFieldPlaceHolder, text: $content)
        .padding()
        .background(backgroundColor)
        .foregroundStyle(textColor)
        .frame(height: Constants.textFieldHeight)
        .textFieldStyle(PlainTextFieldStyle())
        .clipShape(RoundedRectangle(cornerRadius: Constants.textFieldCornerRadius))
        .overlay(
          RoundedRectangle(cornerRadius: Constants.textFieldCornerRadius).stroke(
            Constants.textFieldStrokeColor)
        )
        .focused($focusedField, equals: .message)
        .onSubmit {
          focusedField = nil
        }
        .submitLabel(.return)
        .onChange(of: state) { oldValue, newValue in
          focusedField = state == .done ? .message : nil
        }
        .padding([.leading, .top], Constants.padding)
      Button(action: sendMessage) {
        Image(systemName: Constants.sendButtonImage)
          .resizable()
          .scaledToFit()
          .frame(width: Constants.buttonSize, height: Constants.buttonSize)
          .foregroundColor(
            state == .done ? Constants.buttonEnabledColor : Constants.buttonDisabledColor)
      }
      .padding([.trailing, .top], Constants.padding)
    }
    .background(Constants.viewBackgroundColor)
  }

  private func sendMessage() {
    guard !content.isEmpty else {
      return
    }
    let prompt = content
    content = ""
    onSubmitAction(prompt)
  }

}

extension View {
  /// Displays error alert based on the value of the binding error. This function is invoked when the value of the binding error changes.
  /// - Parameters:
  ///   - error: Binding error based on which the alert is displayed.
  /// - Returns: The error alert.
  func alert(
    state: Binding<ConversationViewModel.State>, buttonTitle: String = "OK",
    action: @escaping () -> Void
  ) -> some View {

    let inferenceError = state.wrappedValue.inferenceError

    return alert(isPresented: .constant(inferenceError != nil), error: inferenceError) { _ in
      Button(buttonTitle) {
        action()
      }
    } message: { error in
      Text(error.failureReason)
    }
  }
}
