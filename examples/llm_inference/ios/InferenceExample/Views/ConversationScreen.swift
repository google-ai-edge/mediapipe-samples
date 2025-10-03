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
    static let newChatSystemSymbolName = "arrow.clockwise"
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

  @State private var isSheetPresented: Bool = false  // Local state

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
          },
          onChangeOfTextAction: { [weak viewModel] prompt in
            viewModel?.recomputeSizeInTokens(prompt: prompt)
          })
      }
      .navigationTitle("Chat with \(viewModel.modelCategory.name) here")
      .toolbar {
        ToolbarItem(placement: .primaryAction) {
          Button(action: viewModel.startNewChat) {
            Image(systemName: Constants.newChatSystemSymbolName)
          }
          .environment(\.isEnabled, !viewModel.shouldDisableClicksForStartNewChat())
        }
      }
      .navigationBarTitleDisplayMode(.inline)
      .toolbarBackground(Metadata.globalColor, for: .navigationBar)
      .toolbarBackground(.visible, for: .navigationBar)
      .toolbarColorScheme(.dark, for: .navigationBar)
      .disabled(viewModel.shouldDisableClicks())

      if viewModel.currentState == .loadingModel {
        Constants.alertBackgroundColor
          .edgesIgnoringSafeArea(.all)
        ProgressView(Constants.modelInitializationAlertText)
          .tint(Metadata.globalColor)
      }
    }
    .safeAreaInset(edge: .top) {
      if viewModel.remainingSizeInTokens != -1 {
        ModelAccessoryView(
          modelName: viewModel.modelCategory.name,
          remainingTokenCount: $viewModel.remainingSizeInTokens
        )
      }
    }
    .alert(
      error: viewModel.currentState.inferenceError,
      action: { [weak viewModel] in
        if shouldDismiss() {
          dismiss()
        } else {
          viewModel?.resetStateAfterErrorIntimation()
        }
      }
    )
    .sheet(
      isPresented: $viewModel.downloadRequired, onDismiss: didDismissDownloadSheet,
      content: {
        HuggingFaceFlowScreen(
          viewModel: HuggingFaceFlowViewModel(modelCategory: self.viewModel.modelCategory)
        )
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
      }
    )
    .onAppear { [weak viewModel] in
      viewModel?.loadModel()
    }
    .onDisappear { [weak viewModel] in
      viewModel?.clearModel()
    }
  }

  func didDismissDownloadSheet() {
    DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
      viewModel.handleModelDownloadedCompleted()
    }
  }

  private func shouldDismiss() -> Bool {
    if case .criticalError = viewModel.currentState { return true }
    return false
  }
}

/// View that displays a message.
struct MessageView: View {
  private struct Constants {
    static let textMessagePadding: CGFloat = 10.0
    static let foregroundColor = Color(red: 0.0, green: 0.0, blue: 0.0)
    static let systemMessageBackgroundColor = Color("SystemColor")
    static let userMessageBackgroundColor = Color("UserColor")
    static let thinkingMessageBackgroundColor = Color("ThinkingColor")
    static let errorBackgroundColor = Color.red.opacity(0.1)
    static let messageBackgroundCornerRadius: CGFloat = 16.0
    static let generationErrorText = "Could not generate response"
    static let font = Font.system(size: 10, weight: .regular, design: .default)
    static let tint = Metadata.globalColor
  }

  @ObservedObject var messageViewModel: MessageViewModel
  var onTextUpdate: (String) -> Void

  var body: some View {
    HStack {
      if messageViewModel.chatMessage.participant == .user {
        Spacer()
      }
      VStack(alignment: messageViewModel.chatMessage.participant == .user ? .trailing : .leading) {
        Text(messageViewModel.chatMessage.title)
          .font(Constants.font)
          .frame(
            alignment: messageViewModel.chatMessage.participant == .user ? .trailing : .leading)
        switch messageViewModel.chatMessage.participant {
        case .user:
          MessageContentView(
            text: messageViewModel.chatMessage.text,
            backgroundColor: Constants.userMessageBackgroundColor)
        case .system(value: .response):
          if messageViewModel.chatMessage.isLoading {
            ProgressView().tint(Constants.tint)
          } else {
            MessageContentView(
              text: messageViewModel.chatMessage.text,
              backgroundColor: Constants.systemMessageBackgroundColor)
          }
        case .system(value: .thinking):
          if messageViewModel.chatMessage.isLoading {
            ProgressView().tint(Constants.tint)
          } else {
            MessageContentView(
              text: messageViewModel.chatMessage.text,
              backgroundColor: Constants.thinkingMessageBackgroundColor)
          }
        case .system(value: .error):
          MessageContentView(
            text: Constants.generationErrorText, backgroundColor: Constants.errorBackgroundColor)
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
    static let buttonEnabledColor = Metadata.globalColor
    static let padding = 10.0
  }

  @Environment(\.isEnabled) private var isEnabled
  @Environment(\.colorScheme) var colorScheme
  @Binding var state: ConversationViewModel.State

  var onSubmitAction: (String) -> Void
  var onChangeOfTextAction: (String) -> Void

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
        .onChange(of: content) { oldValue, newValue in
          /// Only trigger updates when the VM is not generating response.
          /// Specifically to handle the case when the content is set to "" after prompt is submitted for inference.
          /// Recomputation should only happen from the VM during response generation.
          guard state == .done else {
            return
          }
          onChangeOfTextAction(newValue)
        }
        .padding([.leading, .top], Constants.padding)
      Button(action: sendMessage) {
        Image(systemName: Constants.sendButtonImage)
          .resizable()
          .scaledToFit()
          .frame(width: Constants.buttonSize, height: Constants.buttonSize)
          .foregroundColor(isEnabled ? Constants.buttonEnabledColor : Constants.buttonDisabledColor)
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
    onSubmitAction(prompt)
    content = ""
  }
}

/// View that displays token count information and refresh session button.
struct ModelAccessoryView: View {
  private struct Constants {
    static let refreshIcon = "arrow.triangle.2.circlepath"
    static let backgroundColor = Color(uiColor: .systemGroupedBackground)
    static let font = Font.system(size: 14.0)
  }

  let modelName: String

  @Binding var remainingTokenCount: Int

  private var tokenCountString: String {
    if remainingTokenCount == -1 {
      return ""
    }

    return "\(remainingTokenCount) tokens remaining."
      + (remainingTokenCount == 0 ? "Please refresh the session." : "")
  }

  var body: some View {
    HStack {
      Spacer()
      Text(tokenCountString)
        .font(Constants.font)
      Spacer()
        .tint(Metadata.globalColor)
    }
    .padding()
    .background(Constants.backgroundColor)
    .buttonStyle(.bordered)
    .controlSize(.mini)
  }
}

extension View {
  /// Displays error alert based on the value of the binding error. This function is invoked when the value of the binding error changes.
  /// - Parameters:
  ///   - error: Binding error based on which the alert is displayed.
  /// - Returns: The error alert.
  func alert<E: LocalizedError>(
    error: E?, buttonTitle: String = "OK",
    action: @escaping () -> Void
  ) -> some View {

    return alert(isPresented: .constant(error != nil), error: error) { _ in
      Button(buttonTitle) {
        action()
      }
    } message: { error in
      Text(error.failureReason ?? "Some error occured")
    }
  }
}
