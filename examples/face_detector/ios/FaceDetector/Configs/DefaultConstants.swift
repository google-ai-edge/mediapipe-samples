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
import UIKit
import MediaPipeTasksVision

// MARK: Define default constants
struct DefaultConstants {
  static let minSuppressionThreshold: Float = 0.2
  static let minDetectionConfidence: Float = 0.2
  static let labelColors = [
    UIColor.red,
    UIColor(displayP3Red: 90.0/255.0, green: 200.0/255.0, blue: 250.0/255.0, alpha: 1.0),
    UIColor.green,
    UIColor.orange,
    UIColor.blue,
    UIColor.purple,
    UIColor.magenta,
    UIColor.yellow,
    UIColor.cyan,
    UIColor.brown
  ]
  static let ovelayColor = UIColor(red: 0, green: 127/255.0, blue: 139/255.0, alpha: 1)
  static let displayFont = UIFont.systemFont(ofSize: 14.0, weight: .medium)
  static let modelPath: String? = Bundle.main.path(forResource: "blaze_face_short_range", ofType: "tflite")
  static let delegate: Delegate = .CPU
}
