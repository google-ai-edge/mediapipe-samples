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
import QuartzCore
import MediaPipeTasksText

struct LanguageDetectionResultBundle {
  let predictions: [LanguagePrediction]
  let inferenceTime: Double
}

class LanguageDetectorHelper {
  private var languageDetector: LanguageDetector?

  init?(modelPath: String, scoreThreshold: Float = 0.0, maxResults: Int = -1) {
    var options = LanguageDetectorOptions()
    options.baseOptions.modelAssetPath = modelPath
    options.scoreThreshold = scoreThreshold
    if maxResults > 0 {
      options.maxResults = maxResults
    }
    do {
      languageDetector = try LanguageDetector(options: options)
    } catch {
      print("Failed to initialize LanguageDetector: \(error)")
      return nil
    }
  }

  func detect(text: String) -> LanguageDetectionResultBundle? {
    guard let languageDetector = languageDetector else { return nil }
    let startTime = CACurrentMediaTime()
    do {
      let result = try languageDetector.detect(text: text)
      let inferenceTime = (CACurrentMediaTime() - startTime) * 1000.0
      return LanguageDetectionResultBundle(
        predictions: result.languages,
        inferenceTime: inferenceTime
      )
    } catch {
      print("Language detection error: \(error)")
      return nil
    }
  }
}
