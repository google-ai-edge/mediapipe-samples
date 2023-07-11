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
import AVFoundation

protocol ObjectDetectorHelperDelegate: AnyObject {
  func objectDetectorHelper(_ objectDetectorHelper: ObjectDetectorHelper,
                             didFinishDetection result: ResultBundle?,
                             error: Error?)
}

class ObjectDetectorHelper: NSObject {

  weak var delegate: ObjectDetectorHelperDelegate?
  var objectDetector: ObjectDetector?

  init(model: Model, maxResults: Int, scoreThreshold: Float, runningModel: RunningMode, delegate: ObjectDetectorHelperDelegate?) {
    super.init()
    guard let modelPath = model.modelPath else { return }
    let objectDetectorOptions = ObjectDetectorOptions()
    objectDetectorOptions.runningMode = runningModel
    objectDetectorOptions.maxResults = maxResults
    objectDetectorOptions.scoreThreshold = scoreThreshold
    objectDetectorOptions.baseOptions.modelAssetPath = modelPath
    objectDetectorOptions.objectDetectorLiveStreamDelegate = runningModel == .liveStream ? self : nil
    do {
      objectDetector = try ObjectDetector(options: objectDetectorOptions)
    } catch {
      print(error)
    }
    self.delegate = delegate
  }

  /**
   This method return ObjectDetectorResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else { return nil }
    do {
      let startDate = Date()
      let result = try objectDetector?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, objectDetectorResults: [result])
    } catch {
      print(error)
      return nil
    }
  }

  func detectAsync(videoFrame: CMSampleBuffer, orientation: UIDeviceOrientation, timeStamps: Int) {
    var uiimageOrientation: UIImage.Orientation = .up
    switch orientation {
    case .landscapeLeft:
      uiimageOrientation = .left
    case .landscapeRight:
      uiimageOrientation = .right
    default:
      uiimageOrientation = .up
    }
    guard let objectDetector = objectDetector,
          let image = try? MPImage(sampleBuffer: videoFrame, orientation: uiimageOrientation) else { return }
    do {
      try objectDetector.detectAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }

  func detectVideoFile(url: URL, inferenceIntervalMs: Double) async -> ResultBundle? {
    guard let objectDetector = objectDetector else { return nil }
    let startDate = Date()
    let asset: AVAsset = AVAsset(url: url)
    let generator = AVAssetImageGenerator(asset:asset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true
    guard let videoDurationMs = try? await asset.load(.duration).seconds * 1000 else { return nil }
    let frameCount = Int(videoDurationMs / inferenceIntervalMs)
    var objectDetectorResults: [ObjectDetectorResult?] = []
    for i in 0..<frameCount {
      let timestampMs = Int(inferenceIntervalMs) * i // ms
      let image:CGImage?
      do {
        let time = CMTime(seconds: Double(timestampMs) / 1000, preferredTimescale: 600)
        try image = generator.copyCGImage(at: time, actualTime:nil)
      } catch {
        print(error)
         return nil
      }
      guard let image = image else { return nil }
      let uiImage = UIImage(cgImage:image)
      let result = try? objectDetector.detect(videoFrame: MPImage(uiImage: uiImage), timestampInMilliseconds: timestampMs)
      objectDetectorResults.append(result)
    }
    let inferenceTime = Date().timeIntervalSince(startDate) / Double(frameCount) * 1000
    return ResultBundle(inferenceTime: inferenceTime, objectDetectorResults: objectDetectorResults)
  }
}

// MARK: - ObjectDetectorLiveStreamDelegate
extension ObjectDetectorHelper: ObjectDetectorLiveStreamDelegate {
  func objectDetector(_ objectDetector: ObjectDetector, didFinishDetection result: ObjectDetectorResult?, timestampInMilliseconds: Int, error: Error?) {
    guard let result = result else {
      delegate?.objectDetectorHelper(self, didFinishDetection: nil, error: error)
      return
    }
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      objectDetectorResults: [result])
    delegate?.objectDetectorHelper(self, didFinishDetection: resultBundle, error: nil)
  }
}

/// A result from the `ObjectDetectorHelper`.
struct ResultBundle {
  let inferenceTime: Double
  let objectDetectorResults: [ObjectDetectorResult?]
}
