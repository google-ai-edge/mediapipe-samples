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

protocol FaceDetectorHelperDelegate: AnyObject {
  func faceDetectorHelper(_ faceDetectorHelper: FaceDetectorHelper,
                             didFinishDetection result: ResultBundle?,
                             error: Error?)
}

class FaceDetectorHelper: NSObject {

  weak var delegate: FaceDetectorHelperDelegate?
  var faceDetector: FaceDetector?

  init(modelPath: String?, minDetectionConfidence: Float, minSuppressionThreshold: Float, runningMode: RunningMode, delegate: FaceDetectorHelperDelegate?) {
    super.init()
    guard let modelPath = modelPath else { return }
    let faceDetectorOptions = FaceDetectorOptions()
    faceDetectorOptions.runningMode = runningMode
    faceDetectorOptions.faceDetectorLiveStreamDelegate = runningMode == .liveStream ? self : nil
    faceDetectorOptions.minDetectionConfidence = minDetectionConfidence
    faceDetectorOptions.minSuppressionThreshold = minSuppressionThreshold
    faceDetectorOptions.baseOptions.modelAssetPath = modelPath
    do {
      faceDetector = try FaceDetector(options: faceDetectorOptions)
    } catch {
      print(error)
    }
    self.delegate = delegate
  }

  /**
   This method return FaceDetectorResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else { return nil }
    do {
      let startDate = Date()
      let result = try faceDetector?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, faceDetectorResults: [result])
    } catch {
      print(error)
      return nil
    }
  }

  /**
   This method return FaceDetectorResult and infrenceTime when receive videoFrame
   **/
  func detectAsync(videoFrame: CMSampleBuffer, orientation: UIImage.Orientation, timeStamps: Int) {
    guard let faceDetector = faceDetector,
          let image = try? MPImage(sampleBuffer: videoFrame, orientation: orientation) else { return }
    do {
      try faceDetector.detectAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }

  func detectVideoFile(url: URL, inferenceIntervalMs: Double) async -> ResultBundle? {
    guard let faceDetector = faceDetector else { return nil }
    let startDate = Date()
    var size: CGSize = .zero
    let asset: AVAsset = AVAsset(url: url)
    let generator = AVAssetImageGenerator(asset:asset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true
    guard let videoDurationMs = try? await asset.load(.duration).seconds * 1000 else { return nil }
    let frameCount = Int(videoDurationMs / inferenceIntervalMs)
    var faceDetectorResults: [FaceDetectorResult?] = []
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
      size = uiImage.size
      do {
        let result = try faceDetector.detect(videoFrame: MPImage(uiImage: uiImage), timestampInMilliseconds: timestampMs)
        faceDetectorResults.append(result)
      } catch {
        print(error)
        faceDetectorResults.append(nil)
      }
    }
    let inferenceTime = Date().timeIntervalSince(startDate) / Double(frameCount) * 1000
    return ResultBundle(inferenceTime: inferenceTime, faceDetectorResults: faceDetectorResults, imageSize: size)
  }
}

// MARK: - FaceDetectorLiveStreamDelegate
extension FaceDetectorHelper: FaceDetectorLiveStreamDelegate {
  func faceDetector(_ faceDetector: FaceDetector, didFinishDetection result: FaceDetectorResult?, timestampInMilliseconds: Int, error: Error?) {
    guard let result = result else {
      delegate?.faceDetectorHelper(self, didFinishDetection: nil, error: error)
      return
    }
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      faceDetectorResults: [result])
    delegate?.faceDetectorHelper(self, didFinishDetection: resultBundle, error: nil)
  }
}

/// A result from the `FaceDetectorHelper`.
struct ResultBundle {
  let inferenceTime: Double
  let faceDetectorResults: [FaceDetectorResult?]
  var imageSize: CGSize = .zero
}
