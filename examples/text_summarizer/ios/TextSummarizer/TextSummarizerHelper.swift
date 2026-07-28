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

import Foundation
import MediaPipeTasksText

class TextSummarizerHelper {
  private var textSummarizer: TextSummarizer?

  init(modelPath: String, mode: TextSummarizerMode) {
    let options = TextSummarizerOptions()
    options.baseOptions.modelAssetPath = modelPath
    options.mode = mode
    do {
      textSummarizer = try TextSummarizer(options: options)
    } catch {
      print("Failed to initialize TextSummarizer: \(error)")
    }
  }

  func summarize(text: String) -> String? {
    guard let textSummarizer = textSummarizer else { return nil }
    do {
      return try textSummarizer.summarize(text: text).summary
    } catch {
      print("Summarization error: \(error)")
      return nil
    }
  }

  func summarizeStreaming(text: String, completion: @escaping (String?, Bool, Error?) -> Void) {
    guard let textSummarizer = textSummarizer else {
      completion(nil, false, NSError(domain: "TextSummarizerHelper", code: -1, userInfo: [NSLocalizedDescriptionKey: "Summarizer not initialized"]))
      return
    }
    do {
      try textSummarizer.summarizeStreaming(text: text) { result, error in
        completion(result?.chunk, result?.done ?? false, error)
      }
    } catch {
      completion(nil, false, error)
    }
  }
}
