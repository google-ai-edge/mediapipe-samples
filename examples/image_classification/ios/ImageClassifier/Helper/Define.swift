// Copyright 2023 The MediaPipe Authors.
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
import MediaPipeTasksVision

enum Model: String, CaseIterable {
    case efficientnetLite0 = "Efficientnet lite 0"
    case efficientnetLite2 = "Efficientnet lite 2"

    var modelPath: String? {
        switch self {
        case .efficientnetLite0:
            return Bundle.main.path(
                forResource: "efficientnet_lite0", ofType: "tflite")
        case .efficientnetLite2:
            return Bundle.main.path(
                forResource: "efficientnet_lite2", ofType: "tflite")
        }
    }
}

// MARK: Define default constants
enum DefaultConstants {
  static let maxResults = 3
  static let scoreThreshold: Float = 0.2
  static let model: Model = .efficientnetLite0
}

/// A result from the `ImageClassifierHelper`.
struct ImageClassifierHelperResult {
  let inferenceTime: Double
  let imageClassifierResult: ImageClassifierResult?
}
