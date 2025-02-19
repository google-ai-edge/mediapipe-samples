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

import Foundation

/// Represents a string as either text or LaTeX for display.
struct TextOrMath: Identifiable {
  let id = UUID()
  let content: String
  let isMath: Bool

  init(content: String, isMath: Bool) {
    self.isMath = isMath
    self.content = self.isMath ? "$$" + content + "$$" : content
  }
}

extension String {
  /// Attributed string for display.
  var attributedString: AttributedString {
    do {
      return try AttributedString(
        markdown: self,
        options: AttributedString.MarkdownParsingOptions(
          interpretedSyntax: .inlineOnlyPreservingWhitespace))
    } catch {
      return AttributedString(self)
    }
  }

  /// Text split into LaTeX and plain text.
  var mathTextSplits: [TextOrMath] {
    var parts: [TextOrMath] = []
    var components = self.components(separatedBy: "$$")

    while !components.isEmpty {
      let textPart = components.removeFirst()
      parts.append(TextOrMath(content: textPart, isMath: false))  // Text part

      if !components.isEmpty {  // Check if there's a math part
        let mathPart = components.removeFirst()
        parts.append(TextOrMath(content: mathPart, isMath: true))  // Math part with $$
      }
    }
    return parts
  }
}
