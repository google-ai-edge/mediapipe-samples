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

import UIKit
import MediaPipeTasksVision

class FaceDetectorHelper {

  var faceDetector: FaceDetector?

  init(modelPath: String?, minDetectionConfidence: Float, minSuppressionThreshold: Float, runningModel: RunningMode) {
    guard let modelPath = modelPath else { return }
    let faceDetectorOptions = FaceDetectorOptions()
    faceDetectorOptions.runningMode = runningModel
    faceDetectorOptions.minDetectionConfidence = minDetectionConfidence
    faceDetectorOptions.minSuppressionThreshold = minSuppressionThreshold
    faceDetectorOptions.baseOptions.modelAssetPath = modelPath
    do {
      faceDetector = try FaceDetector(options: faceDetectorOptions)
    } catch {
      print(error)
    }
  }

  /**
   This method return FaceDetectorResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> FaceDetectorHelperResult? {
    guard let mpImage = try? MPImage(uiImage: image) else { return nil }
    do {
      let startDate = Date()
      let result = try faceDetector?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return FaceDetectorHelperResult(inferenceTime: inferenceTime, faceDetectorResult: result)
    } catch {
      print(error)
      return nil
    }
  }

  /**
   This method return FaceDetectorResult and infrenceTime when receive videoFrame
   **/
  func detect(videoFrame: CVPixelBuffer, timeStamps: Int) -> FaceDetectorHelperResult? {
    guard let faceDetector = faceDetector,
          let image = try? MPImage(pixelBuffer: videoFrame) else { return nil }
    do {
      let startDate = Date()
      let result = try faceDetector.detect(videoFrame: image, timestampInMilliseconds: timeStamps)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return FaceDetectorHelperResult(inferenceTime: inferenceTime, faceDetectorResult: result)
    } catch {
      print(error)
      return nil
    }
  }

  func detect(videoFrame: UIImage, timeStamps: Int) -> FaceDetectorHelperResult? {
    guard let faceDetector = faceDetector,
          let image = try? MPImage(uiImage: videoFrame) else { return nil }
    do {
      let startDate = Date()
      let result = try faceDetector.detect(videoFrame: image, timestampInMilliseconds: timeStamps)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return FaceDetectorHelperResult(inferenceTime: inferenceTime, faceDetectorResult: result)
    } catch {
      print(error)
      return nil
    }
  }
}
