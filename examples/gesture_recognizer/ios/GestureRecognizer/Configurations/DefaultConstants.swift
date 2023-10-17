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

// MARK: Define default constants
struct DefaultConstants {

  static let lineWidth: CGFloat = 2
  static let pointRadius: CGFloat = 5
  static let pointColor = UIColor.yellow
  static let pointFillColor = UIColor.red
  static let lineColor = UIColor(red: 0, green: 127/255.0, blue: 139/255.0, alpha: 1)

  static var minHandDetectionConfidence: Float = 0.5
  static var minHandPresenceConfidence: Float = 0.5
  static var minTrackingConfidence: Float = 0.5
  static let modelPath: String? = Bundle.main.path(forResource: "gesture_recognizer", ofType: "task")
}
