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

class TextProofreaderHelper {
  private var textProofreader: TextProofreader?

  init(modelPath: String) {
    do {
      textProofreader = try TextProofreader(modelPath: modelPath)
    } catch {
      print("Failed to initialize TextProofreader: \(error)")
    }
  }

  func proofread(text: String) -> TextProofreaderResult? {
    guard let textProofreader = textProofreader else { return nil }
    do {
      return try textProofreader.proofread(text)
    } catch {
      print("Proofreading error: \(error)")
      return nil
    }
  }

  func proofreadStreaming(text: String, completion: @escaping (String?, Bool, Error?) -> Void) {
    guard let textProofreader = textProofreader else {
      completion(nil, false, NSError(domain: "TextProofreaderHelper", code: -1, userInfo: [NSLocalizedDescriptionKey: "Proofreader not initialized"]))
      return
    }
    do {
      try textProofreader.proofreadStreaming(text) { result, error in
        completion(result?.chunk, result?.done ?? false, error)
      }
    } catch {
      completion(nil, false, error)
    }
  }
}
