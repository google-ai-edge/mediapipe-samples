// Copyright 2024 The MediaPipe Authors.
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
  static let maxResults: Int = 3
  static let scoreThreshold: Float = 0.2

  static let model: Model = .mobilenet_v3_small
  static let delegate: ImageEmbedderDelegate = .CPU
}

// MARK: Tflite Model
enum Model: String, CaseIterable {
    case mobilenet_v3_small = "MobileNet-V3 (small)"
    case mobilenet_v3_large = "MobileNet-V3 (large)"

    var modelPath: String? {
        switch self {
        case .mobilenet_v3_small:
            return Bundle.main.path(
                forResource: "mobilenet_v3_small", ofType: "tflite")
        case .mobilenet_v3_large:
            return Bundle.main.path(
                forResource: "mobilenet_v3_large", ofType: "tflite")
        }
    }
}

// MARK: ImageEmbedderDelegate
enum ImageEmbedderDelegate: CaseIterable {
  case GPU
  case CPU

  var name: String {
    switch self {
    case .GPU:
      return "GPU"
    case .CPU:
      return "CPU"
    }
  }

  var delegate: Delegate {
    switch self {
    case .GPU:
      return .GPU
    case .CPU:
      return .CPU
    }
  }

  init?(name: String) {
    switch name {
    case ImageEmbedderDelegate.CPU.name:
      self = ImageEmbedderDelegate.CPU
    case ImageEmbedderDelegate.GPU.name:
      self = ImageEmbedderDelegate.GPU
    default:
      return nil
    }
  }
}
