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
 This protocol must be adopted by any class that wants to get the detection results of the hand landmarker in live stream mode.
 */
protocol HandLandmarkerServiceLiveStreamDelegate: AnyObject {
  func handLandmarkerService(_ handLandmarkerService: HandLandmarkerService,
                             didFinishDetection result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of hand landmark on videos.
 */
protocol HandLandmarkerServiceVideoDelegate: AnyObject {
 func handLandmarkerService(_ handLandmarkerService: HandLandmarkerService,
                                  didFinishDetectionOnVideoFrame index: Int)
 func handLandmarkerService(_ handLandmarkerService: HandLandmarkerService,
                             willBeginDetection totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for detection.
class HandLandmarkerService: NSObject {

  weak var liveStreamDelegate: HandLandmarkerServiceLiveStreamDelegate?
  weak var videoDelegate: HandLandmarkerServiceVideoDelegate?

  var handLandmarker: HandLandmarker?
  private(set) var runningMode = RunningMode.image
  private var numHands: Int
  private var minHandDetectionConfidence: Float
  private var minHandPresenceConfidence: Float
  private var minTrackingConfidence: Float
  var modelPath: String
  private var delegate: HandLandmarkerDelegate

  // MARK: - Custom Initializer
  private init?(modelPath: String?,
                runningMode:RunningMode,
                numHands: Int,
                minHandDetectionConfidence: Float,
                minHandPresenceConfidence: Float,
                minTrackingConfidence: Float,
                delegate: HandLandmarkerDelegate) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    self.numHands = numHands
    self.minHandDetectionConfidence = minHandDetectionConfidence
    self.minHandPresenceConfidence = minHandPresenceConfidence
    self.minTrackingConfidence = minTrackingConfidence
    self.delegate = delegate
    super.init()

    createHandLandmarker()
  }

  private func createHandLandmarker() {
    let handLandmarkerOptions = HandLandmarkerOptions()
    handLandmarkerOptions.runningMode = runningMode
    handLandmarkerOptions.numHands = numHands
    handLandmarkerOptions.minHandDetectionConfidence = minHandDetectionConfidence
    handLandmarkerOptions.minHandPresenceConfidence = minHandPresenceConfidence
    handLandmarkerOptions.minTrackingConfidence = minTrackingConfidence
    handLandmarkerOptions.baseOptions.modelAssetPath = modelPath
    handLandmarkerOptions.baseOptions.delegate = delegate.delegate
    if runningMode == .liveStream {
      handLandmarkerOptions.handLandmarkerLiveStreamDelegate = self
    }
    do {
      handLandmarker = try HandLandmarker(options: handLandmarkerOptions)
    }
    catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoHandLandmarkerService(
    modelPath: String?,
    numHands: Int,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float,
    videoDelegate: HandLandmarkerServiceVideoDelegate?,
    delegate: HandLandmarkerDelegate) -> HandLandmarkerService? {
    let handLandmarkerService = HandLandmarkerService(
      modelPath: modelPath,
      runningMode: .video,
      numHands: numHands,
      minHandDetectionConfidence: minHandDetectionConfidence,
      minHandPresenceConfidence: minHandPresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)
    handLandmarkerService?.videoDelegate = videoDelegate
    return handLandmarkerService
  }

  static func liveStreamHandLandmarkerService(
    modelPath: String?,
    numHands: Int,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float,
    liveStreamDelegate: HandLandmarkerServiceLiveStreamDelegate?,
    delegate: HandLandmarkerDelegate) -> HandLandmarkerService? {
    let handLandmarkerService = HandLandmarkerService(
      modelPath: modelPath,
      runningMode: .liveStream,
      numHands: numHands,
      minHandDetectionConfidence: minHandDetectionConfidence,
      minHandPresenceConfidence: minHandPresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)
    handLandmarkerService?.liveStreamDelegate = liveStreamDelegate

    return handLandmarkerService
  }

  static func stillImageLandmarkerService(
    modelPath: String?,
    numHands: Int,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float,
    delegate: HandLandmarkerDelegate) -> HandLandmarkerService? {
    let handLandmarkerService = HandLandmarkerService(
      modelPath: modelPath,
      runningMode: .image,
      numHands: numHands,
      minHandDetectionConfidence: minHandDetectionConfidence,
      minHandPresenceConfidence: minHandPresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)

    return handLandmarkerService
  }

  // MARK: - Detection Methods for Different Modes
  /**
   This method return HandLandmarkerResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
    do {
      let startDate = Date()
      let result = try handLandmarker?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, handLandmarkerResults: [result])
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
      try handLandmarker?.detectAsync(image: image, timestampInMilliseconds: timeStamps)
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
      videoDelegate?.handLandmarkerService(self, willBeginDetection: frameCount)
    }

    let handLandmarkerResultTuple = detectHandLandmarksInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      handLandmarkerResults: handLandmarkerResultTuple.handLandmarkerResults,
      size: handLandmarkerResultTuple.videoSize)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func detectHandLandmarksInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (handLandmarkerResults: [HandLandmarkerResult?], videoSize: CGSize)  {
    var handLandmarkerResults: [HandLandmarkerResult?] = []
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
        return (handLandmarkerResults, videoSize)
      }

      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size

      do {
        let result = try handLandmarker?.detect(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          handLandmarkerResults.append(result)
        Task { @MainActor in
          videoDelegate?.handLandmarkerService(self, didFinishDetectionOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }

    return (handLandmarkerResults, videoSize)
  }
}

// MARK: - HandLandmarkerLiveStreamDelegate Methods
extension HandLandmarkerService: HandLandmarkerLiveStreamDelegate {
  func handLandmarker(
    _ handLandmarker: HandLandmarker,
    didFinishDetection result: HandLandmarkerResult?,
    timestampInMilliseconds: Int,
    error: Error?) {
      let resultBundle = ResultBundle(
        inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
        handLandmarkerResults: [result])
      liveStreamDelegate?.handLandmarkerService(
        self,
        didFinishDetection: resultBundle,
        error: error)
  }
}

/// A result from the `HandLandmarkerService`.
struct ResultBundle {
  let inferenceTime: Double
  let handLandmarkerResults: [HandLandmarkerResult?]
  var size: CGSize = .zero
}
