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
 This protocol must be adopted by any class that wants to get the detection results of the pose landmarker in live stream mode.
 */
protocol PoseLandmarkerServiceLiveStreamDelegate: AnyObject {
  func poseLandmarkerService(_ poseLandmarkerService: PoseLandmarkerService,
                             didFinishDetection result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of pose landmark on videos.
 */
protocol PoseLandmarkerServiceVideoDelegate: AnyObject {
 func poseLandmarkerService(_ poseLandmarkerService: PoseLandmarkerService,
                                  didFinishDetectionOnVideoFrame index: Int)
 func poseLandmarkerService(_ poseLandmarkerService: PoseLandmarkerService,
                             willBeginDetection totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for detection.
class PoseLandmarkerService: NSObject {

  weak var liveStreamDelegate: PoseLandmarkerServiceLiveStreamDelegate?
  weak var videoDelegate: PoseLandmarkerServiceVideoDelegate?

  var poseLandmarker: PoseLandmarker?
  private(set) var runningMode = RunningMode.image
  private var numPoses: Int
  private var minPoseDetectionConfidence: Float
  private var minPosePresenceConfidence: Float
  private var minTrackingConfidence: Float
  private var modelPath: String
  private var delegate: PoseLandmarkerDelegate

  // MARK: - Custom Initializer
  private init?(modelPath: String?,
                runningMode:RunningMode,
                numPoses: Int,
                minPoseDetectionConfidence: Float,
                minPosePresenceConfidence: Float,
                minTrackingConfidence: Float,
                delegate: PoseLandmarkerDelegate) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    self.numPoses = numPoses
    self.minPoseDetectionConfidence = minPoseDetectionConfidence
    self.minPosePresenceConfidence = minPosePresenceConfidence
    self.minTrackingConfidence = minTrackingConfidence
    self.delegate = delegate
    super.init()

    createPoseLandmarker()
  }

  private func createPoseLandmarker() {
    let poseLandmarkerOptions = PoseLandmarkerOptions()
    poseLandmarkerOptions.runningMode = runningMode
    poseLandmarkerOptions.numPoses = numPoses
    poseLandmarkerOptions.minPoseDetectionConfidence = minPoseDetectionConfidence
    poseLandmarkerOptions.minPosePresenceConfidence = minPosePresenceConfidence
    poseLandmarkerOptions.minTrackingConfidence = minTrackingConfidence
    poseLandmarkerOptions.baseOptions.modelAssetPath = modelPath
    poseLandmarkerOptions.baseOptions.delegate = delegate.delegate
    if runningMode == .liveStream {
      poseLandmarkerOptions.poseLandmarkerLiveStreamDelegate = self
    }
    do {
      poseLandmarker = try PoseLandmarker(options: poseLandmarkerOptions)
    }
    catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoPoseLandmarkerService(
    modelPath: String?,
    numPoses: Int,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minTrackingConfidence: Float,
    videoDelegate: PoseLandmarkerServiceVideoDelegate?,
    delegate: PoseLandmarkerDelegate) -> PoseLandmarkerService? {
    let poseLandmarkerService = PoseLandmarkerService(
      modelPath: modelPath,
      runningMode: .video,
      numPoses: numPoses,
      minPoseDetectionConfidence: minPoseDetectionConfidence,
      minPosePresenceConfidence: minPosePresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)
    poseLandmarkerService?.videoDelegate = videoDelegate
    return poseLandmarkerService
  }

  static func liveStreamPoseLandmarkerService(
    modelPath: String?,
    numPoses: Int,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minTrackingConfidence: Float,
    liveStreamDelegate: PoseLandmarkerServiceLiveStreamDelegate?,
    delegate: PoseLandmarkerDelegate) -> PoseLandmarkerService? {
    let poseLandmarkerService = PoseLandmarkerService(
      modelPath: modelPath,
      runningMode: .liveStream,
      numPoses: numPoses,
      minPoseDetectionConfidence: minPoseDetectionConfidence,
      minPosePresenceConfidence: minPosePresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)
    poseLandmarkerService?.liveStreamDelegate = liveStreamDelegate

    return poseLandmarkerService
  }

  static func stillImageLandmarkerService(
    modelPath: String?,
    numPoses: Int,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minTrackingConfidence: Float,
    delegate: PoseLandmarkerDelegate) -> PoseLandmarkerService? {
    let poseLandmarkerService = PoseLandmarkerService(
      modelPath: modelPath,
      runningMode: .image,
      numPoses: numPoses,
      minPoseDetectionConfidence: minPoseDetectionConfidence,
      minPosePresenceConfidence: minPosePresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)

    return poseLandmarkerService
  }

  // MARK: - Detection Methods for Different Modes
  /**
   This method return PoseLandmarkerResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    do {
      let startDate = Date()
      let result = try poseLandmarker?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, poseLandmarkerResults: [result])
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
      try poseLandmarker?.detectAsync(image: image, timestampInMilliseconds: timeStamps)
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
      videoDelegate?.poseLandmarkerService(self, willBeginDetection: frameCount)
    }

    let poseLandmarkerResultTuple = detectPoseLandmarksInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      poseLandmarkerResults: poseLandmarkerResultTuple.poseLandmarkerResults,
      size: poseLandmarkerResultTuple.videoSize)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func detectPoseLandmarksInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (poseLandmarkerResults: [PoseLandmarkerResult?], videoSize: CGSize)  {
    var poseLandmarkerResults: [PoseLandmarkerResult?] = []
    var videoSize = CGSize.zero

    for i in 0..<frameCount {
      let timestampMs = Int(inferenceIntervalMs) * i // ms
      let image: CGImage
      do {
        let time = CMTime(value: Int64(timestampMs), timescale: 1000)
        image = try assetGenerator.copyCGImage(at: time, actualTime: nil)
      } catch {
        print(error)
        return (poseLandmarkerResults, videoSize)
      }

      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size

      do {
        let result = try poseLandmarker?.detect(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          poseLandmarkerResults.append(result)
        Task { @MainActor in
          videoDelegate?.poseLandmarkerService(self, didFinishDetectionOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }

    return (poseLandmarkerResults, videoSize)
  }
}

// MARK: - PoseLandmarkerLiveStreamDelegate Methods
extension PoseLandmarkerService: PoseLandmarkerLiveStreamDelegate {
    func poseLandmarker(_ poseLandmarker: PoseLandmarker, didFinishDetection result: PoseLandmarkerResult?, timestampInMilliseconds: Int, error: (any Error)?) {
        let resultBundle = ResultBundle(
          inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
          poseLandmarkerResults: [result])
        liveStreamDelegate?.poseLandmarkerService(
          self,
          didFinishDetection: resultBundle,
          error: error)
    }
}

/// A result from the `PoseLandmarkerService`.
struct ResultBundle {
  let inferenceTime: Double
  let poseLandmarkerResults: [PoseLandmarkerResult?]
  var size: CGSize = .zero
}
