// Copyright 2025 The Mediapipe Authors.
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

struct RoundedRectButtonStyle: ButtonStyle {
  private struct Constants {
    static let horizontalPadding: CGFloat = 20
    static let verticalPadding: CGFloat = 10
    static let shadowColor: Color = .black.opacity(0.2)
    static let animationDuration: CGFloat = 0.2
    static let disabledBackgroundColor: Color = .gray.opacity(0.5)
    static let disabledForegroundColor: Color = .gray
  }

  var backgroundColor: Color
  var foregroundColor: Color
  var cornerRadius: CGFloat = 10
  var shadowRadius: CGFloat = 3
  var isDisabled: Bool = false

  func makeBody(configuration: Configuration) -> some View {
    HStack(spacing: 8) {
      configuration.label
    }
    .padding(.horizontal, Constants.horizontalPadding)
    .padding(.vertical, Constants.verticalPadding)
    .background(isDisabled ? Constants.disabledBackgroundColor : backgroundColor)
    .foregroundColor(isDisabled ? Constants.disabledForegroundColor : foregroundColor)
    .cornerRadius(cornerRadius)
    .shadow(color: Constants.shadowColor, radius: shadowRadius, x: 0, y: 2)
    .scaleEffect(configuration.isPressed && !isDisabled ? 0.95 : 1.0)
    .animation(.easeOut(duration: Constants.animationDuration), value: configuration.isPressed)
  }
}

struct RoundedRectButton: View {
  private struct Constants {
    static let logoSpacing: CGFloat = 8
    static let logoSize: CGFloat = 20
  }

  var title: String
  var action: () -> Void
  var cornerRadius: CGFloat = 30
  var shadowRadius: CGFloat = 3
  var disabled: Bool = false
  var logo: Image? = nil
  var backgroundColor: Color = Metadata.globalColor
  var foregroundColor: Color = Color.white

  var body: some View {
    Button(action: action) {
      HStack(spacing: Constants.logoSpacing) {
        if let logo = logo {
          logo
            .resizable()
            .scaledToFit()
            .frame(width: Constants.logoSize, height: Constants.logoSize)
        }
        Text(title)
      }
    }
    .buttonStyle(
      RoundedRectButtonStyle(
        backgroundColor: backgroundColor, foregroundColor: foregroundColor,
        cornerRadius: cornerRadius, shadowRadius: shadowRadius, isDisabled: disabled)
    )
    .disabled(disabled)
  }
}

struct HuggingFaceButton: View {
  var title: String
  var action: () -> Void
  var disabled: Bool = false
  
  var body: some View {
    RoundedRectButton(
      title: title,
      action: action,
      disabled: disabled,
      logo: Image("HfLogo"), // Hardcoded logo
      backgroundColor: .black, // Fixed black background
      foregroundColor: .white // Fixed white foreground
    )
  }
}
