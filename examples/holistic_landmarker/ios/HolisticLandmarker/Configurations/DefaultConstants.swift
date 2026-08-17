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

  static let lineWidth: CGFloat = 2
  static let pointRadius: CGFloat = 4
  static let pointColor = UIColor.yellow
  static let pointFillColor = UIColor.red

  static let poseLineColor = UIColor(red: 0, green: 127/255.0, blue: 139/255.0, alpha: 1)
  static let faceLineColor = UIColor.cyan
  static let leftHandLineColor = UIColor.green
  static let rightHandLineColor = UIColor.magenta

  static var minFaceDetectionConfidence: Float = 0.5
  static var minFacePresenceConfidence: Float = 0.5
  static var minPoseDetectionConfidence: Float = 0.5
  static var minPosePresenceConfidence: Float = 0.5
  static var minHandLandmarksConfidence: Float = 0.5
  static let model: Model = .holistic_landmarker
  static let delegate: HolisticLandmarkerDelegate = .CPU
}

// MARK: Model
enum Model: Int, CaseIterable {
  case holistic_landmarker

  var name: String {
    switch self {
    case .holistic_landmarker:
      return "Holistic Landmarker"
    }
  }

  var modelPath: String? {
    switch self {
    case .holistic_landmarker:
      return Bundle.main.path(
        forResource: "holistic_landmarker", ofType: "task")
    }
  }

  init?(name: String) {
    switch name {
    case Model.holistic_landmarker.name:
      self = Model.holistic_landmarker
    default:
      return nil
    }
  }
}

// MARK: HolisticLandmarkerDelegate
enum HolisticLandmarkerDelegate: CaseIterable {
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
    case HolisticLandmarkerDelegate.CPU.name:
      self = HolisticLandmarkerDelegate.CPU
    case HolisticLandmarkerDelegate.GPU.name:
      self = HolisticLandmarkerDelegate.GPU
    default:
      return nil
    }
  }
}
