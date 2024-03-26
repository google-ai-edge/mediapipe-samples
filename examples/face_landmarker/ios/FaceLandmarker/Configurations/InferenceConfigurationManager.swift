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

/**
 * Singleton storing the configs needed to initialize an MediaPipe Tasks object and run inference.
 * Controllers can observe the `InferenceConfigurationManager.notificationName` for any changes made by the user.
 */

class InferenceConfigurationManager: NSObject {
  var modelPath: String? = DefaultConstants.modelPath

  var numFaces: Int = DefaultConstants.numFaces {
    didSet { postConfigChangedNotification() }
  }

  var minFaceDetectionConfidence: Float = DefaultConstants.minFaceDetectionConfidence {
    didSet { postConfigChangedNotification() }
  }

  var minFacePresenceConfidence: Float = DefaultConstants.minFacePresenceConfidence {
    didSet { postConfigChangedNotification() }
  }
  
  var minTrackingConfidence: Float = DefaultConstants.minTrackingConfidence {
    didSet { postConfigChangedNotification() }
  }

  var delegate: FaceLandmarkerDelegate = DefaultConstants.delegate {
    didSet { postConfigChangedNotification() }
  }

  static let sharedInstance = InferenceConfigurationManager()
  
  static let notificationName = Notification.Name.init(rawValue: "com.google.mediapipe.inferenceConfigChanged")
  
  private func postConfigChangedNotification() {
    NotificationCenter.default
      .post(name: InferenceConfigurationManager.notificationName, object: nil)
  }
}
