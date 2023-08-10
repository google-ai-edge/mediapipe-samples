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

protocol FaceLandmarkerHelperDelegate: AnyObject {
  func faceLandmarkerHelper(_ faceLandmarkerHelper: FaceLandmarkerHelper,
                            didFinishDetection result: ResultBundle?,
                            error: Error?)
}

class FaceLandmarkerHelper: NSObject {

  weak var delegate: FaceLandmarkerHelperDelegate?
  var faceLandmarker: FaceLandmarker?

  init(modelPath: String?, numFaces: Int, minFaceDetectionConfidence: Float, minFacePresenceConfidence: Float, minTrackingConfidence: Float, runningModel: RunningMode, delegate: FaceLandmarkerHelperDelegate?) {
    super.init()
    guard let modelPath = modelPath else { return }
    let faceLandmarkerOptions = FaceLandmarkerOptions()
    faceLandmarkerOptions.runningMode = runningModel
    faceLandmarkerOptions.numFaces = numFaces
    faceLandmarkerOptions.minFaceDetectionConfidence = minFaceDetectionConfidence
    faceLandmarkerOptions.minFacePresenceConfidence = minFacePresenceConfidence
    faceLandmarkerOptions.minTrackingConfidence = minTrackingConfidence
    faceLandmarkerOptions.faceLandmarkerLiveStreamDelegate = runningModel == .liveStream ? self : nil
    self.delegate = delegate
    faceLandmarkerOptions.baseOptions.modelAssetPath = modelPath
    do {
      faceLandmarker = try FaceLandmarker(options: faceLandmarkerOptions)
    } catch {
      print(error)
    }
  }

  /**
   This method returns a FaceLandmarkerResult object and infrenceTime after receiving an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else { return nil }
    do {
      let startDate = Date()
      let result = try faceLandmarker?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, faceLandmarkerResults: [result])
    } catch {
      print(error)
      return nil
    }
  }

  /**
   This method return FaceLandmarkerResult and infrenceTime when receive videoFrame
   **/
  func detectAsync(videoFrame: CMSampleBuffer, orientation: UIImage.Orientation, timeStamps: Int) {
    guard let faceLandmarker = faceLandmarker,
          let image = try? MPImage(sampleBuffer: videoFrame, orientation: orientation) else { return }
    do {
      try faceLandmarker.detectAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }

  /**
   This method returns a FaceLandmarkerResults object and infrenceTime when receiving videoUrl and inferenceIntervalMs
   **/
  func detectVideoFile(url: URL, inferenceIntervalMs: Double) async -> ResultBundle? {
    guard let faceLandmarker = faceLandmarker else { return nil }
    let asset: AVAsset = AVAsset(url: url)
    guard let videoDurationMs = try? await asset.load(.duration).seconds * 1000 else { return nil }

    // Using AVAssetImageGenerator to produce images from video content
    let generator = AVAssetImageGenerator(asset:asset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true
    let frameCount = Int(videoDurationMs / inferenceIntervalMs)
    var faceLandmarkerResults: [FaceLandmarkerResult?] = []
    var size: CGSize = .zero
    let startDate = Date()
    // Go through each frame and detect it
    for i in 0 ..< frameCount {
      let timestampMs = inferenceIntervalMs * Double(i) // ms
      let time = CMTime(seconds: timestampMs / 1000, preferredTimescale: 600)
      if let image = getImageFromVideo(generator, atTime: time) {
        size = image.size
        do {
          let result = try faceLandmarker.detect(videoFrame: MPImage(uiImage: image), timestampInMilliseconds: Int(timestampMs))
          faceLandmarkerResults.append(result)
        } catch {
          print(error)
          faceLandmarkerResults.append(nil)
        }
      } else {
        faceLandmarkerResults.append(nil)
      }
    }
    let inferenceTime = Date().timeIntervalSince(startDate) / Double(frameCount) * 1000
    return ResultBundle(inferenceTime: inferenceTime, faceLandmarkerResults: faceLandmarkerResults, imageSize: size)
  }

  /**
   This method returns an image object and  when receiving assetImageGenerator and time
   **/
  private func getImageFromVideo(_ generator: AVAssetImageGenerator, atTime time: CMTime) -> UIImage? {
    let image:CGImage?
    do {
      try image = generator.copyCGImage(at: time, actualTime:nil)
    } catch {
      print(error)
      return nil
    }
    guard let image = image else { return nil }
    return UIImage(cgImage: image)
  }
}

extension FaceLandmarkerHelper: FaceLandmarkerLiveStreamDelegate {
  func faceLandmarker(_ faceLandmarker: FaceLandmarker, didFinishDetection result: FaceLandmarkerResult?, timestampInMilliseconds: Int, error: Error?) {
    guard let result = result else {
      delegate?.faceLandmarkerHelper(self, didFinishDetection: nil, error: error)
      return
    }
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      faceLandmarkerResults: [result])
    delegate?.faceLandmarkerHelper(self, didFinishDetection: resultBundle, error: nil)
  }


}

/// A result from the `FaceLandmarkerHelper`.
struct ResultBundle {
  let inferenceTime: Double
  let faceLandmarkerResults: [FaceLandmarkerResult?]
  var imageSize: CGSize = .zero
}
