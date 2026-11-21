// Copyright 2026 The MediaPipe Authors.
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
#if canImport(UIKit)
import UIKit
#elseif canImport(AppKit)
import AppKit
#endif

/// Shared MediaPipe brand palette aligned across light and dark appearances.
public enum MediaPipePalette {
  /// Primary MediaPipe teal (`#007F8B` light / `#12B5CB` dark).
  public static let tealPrimary = Color.adaptive(
    light: (0.0, 0.498, 0.545),
    dark: (0.071, 0.710, 0.796)
  )

  /// Vibrant MediaPipe cyan accent (`#12B5CB`).
  public static let tealBright = Color(red: 0.071, green: 0.710, blue: 0.796)

  /// Deep MediaPipe teal (`#005F68`).
  public static let tealDeep = Color(red: 0.0, green: 0.373, blue: 0.408)

  /// Soft tinted pill / badge surface.
  public static let tealContainer = Color.adaptive(
    light: (0.855, 0.949, 0.961),
    dark: (0.055, 0.275, 0.306)
  )

  /// Foreground text on ``tealContainer``.
  public static let onTealContainer = Color.adaptive(
    light: (0.0, 0.310, 0.337),
    dark: (0.745, 0.957, 0.984)
  )

  /// Page canvas background.
  public static var canvasBackground: Color {
    #if canImport(UIKit)
    Color(uiColor: .systemGroupedBackground)
    #elseif canImport(AppKit)
    Color(nsColor: .windowBackgroundColor)
    #else
    Color.gray.opacity(0.1)
    #endif
  }

  /// Elevated card background.
  public static var cardBackground: Color {
    #if canImport(UIKit)
    Color(uiColor: .secondarySystemGroupedBackground)
    #elseif canImport(AppKit)
    Color(nsColor: .controlBackgroundColor)
    #else
    Color.white
    #endif
  }

  /// Subtle recessed surface inside cards (e.g. telemetry tiles, code chips).
  public static var recessedSurface: Color {
    #if canImport(UIKit)
    Color(uiColor: .tertiarySystemGroupedBackground)
    #elseif canImport(AppKit)
    Color(nsColor: .underPageBackgroundColor)
    #else
    Color.gray.opacity(0.14)
    #endif
  }

  /// Subtle hairline border for crisp card definition in both light and dark modes.
  public static let cardStroke = Color.adaptive(
    light: (0.0, 0.0, 0.0, alpha: 0.06),
    dark: (1.0, 1.0, 1.0, alpha: 0.09)
  )

  /// Signature MediaPipe diagonal brand gradient.
  public static var brandGradient: LinearGradient {
    LinearGradient(
      colors: [tealDeep, tealPrimary, tealBright],
      startPoint: .topLeading,
      endPoint: .bottomTrailing
    )
  }

  /// Semantic status color for high confidence / strong match.
  public static let statusPositive = Color.adaptive(
    light: (0.055, 0.569, 0.365),
    dark: (0.255, 0.835, 0.592)
  )

  /// Semantic status color for moderate confidence / warning.
  public static let statusModerate = Color.adaptive(
    light: (0.761, 0.451, 0.0),
    dark: (0.980, 0.722, 0.263)
  )

  /// Semantic status color for errors / destructive alerts.
  public static let statusError = Color.adaptive(
    light: (0.749, 0.110, 0.176),
    dark: (1.0, 0.435, 0.412)
  )
}

extension Color {
  static func adaptive(
    light: (Double, Double, Double, alpha: Double),
    dark: (Double, Double, Double, alpha: Double)
  ) -> Color {
    #if canImport(UIKit)
    Color(
      uiColor: UIColor { traits in
        let c = traits.userInterfaceStyle == .dark ? dark : light
        return UIColor(red: c.0, green: c.1, blue: c.2, alpha: c.alpha)
      }
    )
    #elseif canImport(AppKit)
    Color(
      nsColor: NSColor(name: nil) { appearance in
        let isDark = appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
        let c = isDark ? dark : light
        return NSColor(red: c.0, green: c.1, blue: c.2, alpha: c.alpha)
      }
    )
    #else
    Color(red: light.0, green: light.1, blue: light.2, opacity: light.alpha)
    #endif
  }

  static func adaptive(
    light: (Double, Double, Double),
    dark: (Double, Double, Double)
  ) -> Color {
    adaptive(
      light: (light.0, light.1, light.2, alpha: 1.0),
      dark: (dark.0, dark.1, dark.2, alpha: 1.0)
    )
  }
}

/// Resolution-independent vector MediaPipe mark (7 rounded bars/dots on an 88×88 grid).
public struct MediaPipeLogoMark: Shape {
  public init() {}

  public func path(in rect: CGRect) -> Path {
    let scale = min(rect.width, rect.height) / 88.0
    let originX = rect.midX - 44.0 * scale
    let originY = rect.midY - 44.0 * scale

    func capsule(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGRect {
      CGRect(
        x: originX + x * scale,
        y: originY + y * scale,
        width: w * scale,
        height: h * scale
      )
    }

    let radius = 8.0 * scale
    var path = Path()
    // Column 0: upper bar + lower bar
    path.addRoundedRect(in: capsule(0, 0, 16, 40), cornerSize: CGSize(width: radius, height: radius))
    path.addRoundedRect(in: capsule(0, 48, 16, 40), cornerSize: CGSize(width: radius, height: radius))
    // Column 1: tall upper bar + bottom dot
    path.addRoundedRect(in: capsule(24, 0, 16, 64), cornerSize: CGSize(width: radius, height: radius))
    path.addRoundedRect(in: capsule(24, 72, 16, 16), cornerSize: CGSize(width: radius, height: radius))
    // Column 2: top dot + tall lower bar
    path.addRoundedRect(in: capsule(48, 0, 16, 16), cornerSize: CGSize(width: radius, height: radius))
    path.addRoundedRect(in: capsule(48, 24, 16, 64), cornerSize: CGSize(width: radius, height: radius))
    // Column 3: full-height bar
    path.addRoundedRect(in: capsule(72, 0, 16, 88), cornerSize: CGSize(width: radius, height: radius))
    return path
  }
}

/// Elevated rounded surface card with subtle border and shadow for grouped content.
public struct MediaPipeCardModifier: ViewModifier {
  let cornerRadius: CGFloat
  let isHighlighted: Bool

  public func body(content: Content) -> some View {
    content
      .background(
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
          .fill(MediaPipePalette.cardBackground)
      )
      .overlay(
        RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
          .strokeBorder(
            isHighlighted ? MediaPipePalette.tealPrimary.opacity(0.6) : MediaPipePalette.cardStroke,
            lineWidth: isHighlighted ? 1.5 : 1
          )
      )
      .shadow(color: .black.opacity(0.04), radius: 8, x: 0, y: 2)
  }
}

/// Adaptive glass pill container: uses native Liquid Glass on iOS 26+ and thin material on iOS 18.
public struct MediaPipeGlassPillModifier: ViewModifier {
  let tint: Color?

  public func body(content: Content) -> some View {
    #if os(iOS)
    if #available(iOS 26.0, *) {
      if let tint {
        content.glassEffect(.regular.tint(tint.opacity(0.18)), in: .capsule)
      } else {
        content.glassEffect(.regular, in: .capsule)
      }
    } else {
      content
        .background(.ultraThinMaterial, in: Capsule())
        .overlay(
          Capsule().strokeBorder(
            (tint ?? MediaPipePalette.tealPrimary).opacity(0.22),
            lineWidth: 0.75
          )
        )
    }
    #else
    content
      .background(.ultraThinMaterial, in: Capsule())
      .overlay(
        Capsule().strokeBorder(
          (tint ?? MediaPipePalette.tealPrimary).opacity(0.22),
          lineWidth: 0.75
        )
      )
    #endif
  }
}

extension View {
  /// Styles a view as an elevated MediaPipe sample card.
  public func mediaPipeCard(
    cornerRadius: CGFloat = 20,
    isHighlighted: Bool = false
  ) -> some View {
    modifier(MediaPipeCardModifier(cornerRadius: cornerRadius, isHighlighted: isHighlighted))
  }

  /// Styles a view as a floating glass/material pill (Liquid Glass on iOS 26+).
  public func mediaPipeGlassPill(tint: Color? = nil) -> some View {
    modifier(MediaPipeGlassPillModifier(tint: tint))
  }
}
