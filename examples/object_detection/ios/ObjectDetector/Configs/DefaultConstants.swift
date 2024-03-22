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
  static let maxResults = 3
  static let scoreThreshold: Float = 0.2
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
  static let displayFont = UIFont.systemFont(ofSize: 14.0, weight: .medium)
  static let model: Model = .efficientdetLite0
  static let delegate: Delegate = .CPU
}

// MARK: Model
enum Model: Int, CaseIterable {
  case efficientdetLite0
  case efficientdetLite2
  
  var name: String {
    switch self {
    case .efficientdetLite0:
      return "EfficientDet-Lite0"
    case .efficientdetLite2:
      return "EfficientDet-Lite2"
    }
  }
  
  var modelPath: String? {
    switch self {
    case .efficientdetLite0:
      return Bundle.main.path(
        forResource: "efficientdet_lite0", ofType: "tflite")
    case .efficientdetLite2:
      return Bundle.main.path(
        forResource: "efficientdet_lite2", ofType: "tflite")
    }
  }
  
  init?(name: String) {
    switch name {
    case "EfficientDet-Lite0":
      self.init(rawValue: 0)
    case "EfficientDet-Lite2":
      self.init(rawValue: 1)
    default:
      return nil
    }
  }
  
}
