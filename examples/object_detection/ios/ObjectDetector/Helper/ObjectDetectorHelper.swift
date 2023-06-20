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

class ObjectDetectorHelper {

  var objectDetector: ObjectDetector?

  init(model: Model, maxResults: Int, scoreThreshold: Float, runningModel: RunningMode) {
    guard let modelPath = model.modelPath else { return }
    let objectDetectorOptions = ObjectDetectorOptions()
    objectDetectorOptions.runningMode = runningModel
    objectDetectorOptions.maxResults = maxResults
    objectDetectorOptions.scoreThreshold = scoreThreshold
    objectDetectorOptions.baseOptions.modelAssetPath = modelPath
    do {
      objectDetector = try ObjectDetector(options: objectDetectorOptions)
    } catch {
      print(error)
    }
  }

  /**
   This method return ObjectDetectorResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ObjectDetectorHelperResult? {
    guard let mpImage = try? MPImage(uiImage: image) else { return nil }
    do {
      let startDate = Date()
      let result = try objectDetector?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ObjectDetectorHelperResult(inferenceTime: inferenceTime, objectDetectorResult: result)
    } catch {
      print(error)
      return nil
    }
  }

  /**
   This method return ObjectDetectorResult and infrenceTime when receive videoFrame
   **/
  func detect(videoFrame: CVPixelBuffer, timeStamps: Int) -> ObjectDetectorHelperResult? {
    guard let objectDetector = objectDetector,
          let image = try? MPImage(pixelBuffer: videoFrame) else { return nil }
    do {
      let startDate = Date()
      let result = try objectDetector.detect(videoFrame: image, timestampInMilliseconds: timeStamps)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ObjectDetectorHelperResult(inferenceTime: inferenceTime, objectDetectorResult: result)
    } catch {
      print(error)
      return nil
    }
  }
}
