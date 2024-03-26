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

/**
 This protocol must be adopted by any class that wants to get the detection results of the face detector in live stream mode.
 */
protocol FaceDetectorServiceLiveStreamDelegate: AnyObject {
  func faceDetectorService(_ faceDetectorService: FaceDetectorService,
                             didFinishDetection result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of face detection on videos.
 */
protocol FaceDetectorServiceVideoDelegate: AnyObject {
 func faceDetectorService(_ faceDetectorService: FaceDetectorService,
                                  didFinishDetectionOnVideoFrame index: Int)
 func faceDetectorService(_ faceDetectorService: FaceDetectorService,
                             willBeginDetection totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for detection.
class FaceDetectorService: NSObject {

  weak var liveStreamDelegate: FaceDetectorServiceLiveStreamDelegate?
  weak var videoDelegate: FaceDetectorServiceVideoDelegate?

  var faceDetector: FaceDetector?
  private(set) var runningMode = RunningMode.image
  private var minDetectionConfidence: Float = 0.5
  private var minSuppressionThreshold: Float = 0.5
  private var modelPath: String
  private var delegate: Delegate

  // MARK: - Custom Initializer
  private init?(modelPath: String?, minDetectionConfidence: Float, minSuppressionThreshold: Float, runningMode:RunningMode, delegate: Delegate) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.minDetectionConfidence = minDetectionConfidence
    self.minSuppressionThreshold = minSuppressionThreshold
    self.runningMode = runningMode
    self.delegate = delegate
    super.init()

    createFaceDetector()
  }

  private func createFaceDetector() {
    let faceDetectorOptions = FaceDetectorOptions()
    faceDetectorOptions.runningMode = runningMode
    faceDetectorOptions.minDetectionConfidence = minDetectionConfidence
    faceDetectorOptions.minSuppressionThreshold = minSuppressionThreshold
    faceDetectorOptions.baseOptions.modelAssetPath = modelPath
    faceDetectorOptions.baseOptions.delegate = delegate
    if runningMode == .liveStream {
      faceDetectorOptions.faceDetectorLiveStreamDelegate = self
    }
    do {
      faceDetector = try FaceDetector(options: faceDetectorOptions)
    }
    catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoFaceDetectorService(
    modelPath: String?,
    minDetectionConfidence: Float,
    minSuppressionThreshold: Float,
    videoDelegate: FaceDetectorServiceVideoDelegate?,
    delegate: Delegate) -> FaceDetectorService? {
    let faceDetectorService = FaceDetectorService(
      modelPath: modelPath,
      minDetectionConfidence: minDetectionConfidence,
      minSuppressionThreshold: minSuppressionThreshold,
      runningMode: .video,
      delegate: delegate)
    faceDetectorService?.videoDelegate = videoDelegate

    return faceDetectorService
  }

  static func liveStreamDetectorService(
    modelPath: String?,
    minDetectionConfidence: Float,
    minSuppressionThreshold: Float,
    liveStreamDelegate: FaceDetectorServiceLiveStreamDelegate?,
    delegate: Delegate) -> FaceDetectorService? {
    let faceDetectorService = FaceDetectorService(
      modelPath: modelPath,
      minDetectionConfidence: minDetectionConfidence,
      minSuppressionThreshold: minSuppressionThreshold,
      runningMode: .liveStream,
      delegate: delegate)
    faceDetectorService?.liveStreamDelegate = liveStreamDelegate

    return faceDetectorService
  }

  static func stillImageDetectorService(
    modelPath: String?,
    minDetectionConfidence: Float,
    minSuppressionThreshold: Float,
    delegate: Delegate) -> FaceDetectorService? {
    let faceDetectorService = FaceDetectorService(
      modelPath: modelPath,
      minDetectionConfidence: minDetectionConfidence,
      minSuppressionThreshold: minSuppressionThreshold,
      runningMode: .image,
      delegate: delegate)

    return faceDetectorService
  }

  // MARK: - Detection Methods for Different Modes
  /**
   This method return FaceDetectorResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
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

  func detectAsync(
    sampleBuffer: CMSampleBuffer,
    orientation: UIImage.Orientation,
    timeStamps: Int) {
    guard let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
      return
    }
    do {
      try faceDetector?.detectAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }

  func detect(
    videoAsset: AVAsset,
    durationInMilliseconds: Double,
    inferenceIntervalInMilliseconds: Double) async -> ResultBundle? {
    let startDate = Date()
    let assetGenerator = imageGenerator(with: videoAsset)

    let frameCount = Int(durationInMilliseconds / inferenceIntervalInMilliseconds)
    Task { @MainActor in
      videoDelegate?.faceDetectorService(self, willBeginDetection: frameCount)
    }

    let faceDetectorResultTuple = detectObjectsInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      faceDetectorResults: faceDetectorResultTuple.faceDetectorResults,
      size: faceDetectorResultTuple.videoSize)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func detectObjectsInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (faceDetectorResults: [FaceDetectorResult?], videoSize: CGSize)  {
    var faceDetectorResults: [FaceDetectorResult?] = []
    var videoSize = CGSize.zero

    for i in 0..<frameCount {
      let timestampMs = Int(inferenceIntervalMs) * i // ms
      let image: CGImage
      do {
        let time = CMTime(value: Int64(timestampMs), timescale: 1000)
          //        CMTime(seconds: Double(timestampMs) / 1000, preferredTimescale: 1000)
        image = try assetGenerator.copyCGImage(at: time, actualTime: nil)
      } catch {
        print(error)
        return (faceDetectorResults, videoSize)
      }

      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size

      do {
        let result = try faceDetector?.detect(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          faceDetectorResults.append(result)
        Task { @MainActor in
          videoDelegate?.faceDetectorService(self, didFinishDetectionOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }

    return (faceDetectorResults, videoSize)
  }
}

// MARK: - FaceDetectorLiveStreamDelegate
extension FaceDetectorService: FaceDetectorLiveStreamDelegate {
  func faceDetector(
    _ faceDetector: FaceDetector,
    didFinishDetection result: FaceDetectorResult?,
    timestampInMilliseconds: Int,
    error: Error?) {
    guard let result = result else {
      liveStreamDelegate?.faceDetectorService(self, didFinishDetection: nil, error: error)
      return
    }
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      faceDetectorResults: [result])
    liveStreamDelegate?.faceDetectorService(self, didFinishDetection: resultBundle, error: nil)
  }
}

/// A result from the `FaceDetectorService`.
struct ResultBundle {
  let inferenceTime: Double
  let faceDetectorResults: [FaceDetectorResult?]
  var size: CGSize = .zero
}
