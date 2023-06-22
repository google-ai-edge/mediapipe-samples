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

// MARK: Define default constants
enum DefaultConstants {
  static let minSuppressionThreshold: Float = 0.5
  static let minDetectionConfidence: Float = 0.5
  static let modelPath: String? = Bundle.main.path(forResource: "blaze_face_short_range", ofType: "tflite")
}

/// A result from the `FaceDetectorHelper`.
struct FaceDetectorHelperResult {
  let inferenceTime: Double
  let faceDetectorResult: FaceDetectorResult?
}
