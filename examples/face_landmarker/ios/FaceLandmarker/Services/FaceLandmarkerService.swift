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
 This protocol must be adopted by any class that wants to get the detection results of the face landmarker in live stream mode.
 */
protocol FaceLandmarkerServiceLiveStreamDelegate: AnyObject {
  func faceLandmarkerService(_ faceLandmarkerService: FaceLandmarkerService,
                             didFinishDetection result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of face landmark on videos.
 */
protocol FaceLandmarkerServiceVideoDelegate: AnyObject {
 func faceLandmarkerService(_ faceLandmarkerService: FaceLandmarkerService,
                                  didFinishDetectionOnVideoFrame index: Int)
 func faceLandmarkerService(_ faceLandmarkerService: FaceLandmarkerService,
                             willBeginDetection totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for detection.
class FaceLandmarkerService: NSObject {

  weak var liveStreamDelegate: FaceLandmarkerServiceLiveStreamDelegate?
  weak var videoDelegate: FaceLandmarkerServiceVideoDelegate?

  var faceLandmarker: FaceLandmarker?
  private(set) var runningMode = RunningMode.image
  private var numFaces: Int
  private var minFaceDetectionConfidence: Float
  private var minFacePresenceConfidence: Float
  private var minTrackingConfidence: Float
  private var modelPath: String
  private var delegate: FaceLandmarkerDelegate


  // MARK: - Custom Initializer
  private init?(modelPath: String?,
                runningMode:RunningMode,
                numFaces: Int,
                minFaceDetectionConfidence: Float,
                minFacePresenceConfidence: Float,
                minTrackingConfidence: Float,
                delegate: FaceLandmarkerDelegate) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    self.numFaces = numFaces
    self.minFaceDetectionConfidence = minFaceDetectionConfidence
    self.minFacePresenceConfidence = minFacePresenceConfidence
    self.minTrackingConfidence = minTrackingConfidence
    self.delegate = delegate
    super.init()

    createFaceLandmarker()
  }

  private func createFaceLandmarker() {
    let faceLandmarkerOptions = FaceLandmarkerOptions()
    faceLandmarkerOptions.runningMode = runningMode
    faceLandmarkerOptions.numFaces = numFaces
    faceLandmarkerOptions.minFaceDetectionConfidence = minFaceDetectionConfidence
    faceLandmarkerOptions.minFacePresenceConfidence = minFacePresenceConfidence
    faceLandmarkerOptions.minTrackingConfidence = minTrackingConfidence
    faceLandmarkerOptions.baseOptions.modelAssetPath = modelPath
    faceLandmarkerOptions.baseOptions.delegate = delegate.delegate
    if runningMode == .liveStream {
      faceLandmarkerOptions.faceLandmarkerLiveStreamDelegate = self
    }
    do {
      faceLandmarker = try FaceLandmarker(options: faceLandmarkerOptions)
    }
    catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoFaceLandmarkerService(
    modelPath: String?,
    numFaces: Int,
    minFaceDetectionConfidence: Float,
    minFacePresenceConfidence: Float,
    minTrackingConfidence: Float,
    videoDelegate: FaceLandmarkerServiceVideoDelegate?,
    delegate: FaceLandmarkerDelegate) -> FaceLandmarkerService? {
    let faceLandmarkerService = FaceLandmarkerService(
      modelPath: modelPath,
      runningMode: .video,
      numFaces: numFaces,
      minFaceDetectionConfidence: minFaceDetectionConfidence,
      minFacePresenceConfidence: minFacePresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)
    faceLandmarkerService?.videoDelegate = videoDelegate
    return faceLandmarkerService
  }

  static func liveStreamFaceLandmarkerService(
    modelPath: String?,
    numFaces: Int,
    minFaceDetectionConfidence: Float,
    minFacePresenceConfidence: Float,
    minTrackingConfidence: Float,
    liveStreamDelegate: FaceLandmarkerServiceLiveStreamDelegate?,
    delegate: FaceLandmarkerDelegate) -> FaceLandmarkerService? {
    let faceLandmarkerService = FaceLandmarkerService(
      modelPath: modelPath,
      runningMode: .liveStream,
      numFaces: numFaces,
      minFaceDetectionConfidence: minFaceDetectionConfidence,
      minFacePresenceConfidence: minFacePresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)
    faceLandmarkerService?.liveStreamDelegate = liveStreamDelegate

    return faceLandmarkerService
  }

  static func stillImageLandmarkerService(
    modelPath: String?,
    numFaces: Int,
    minFaceDetectionConfidence: Float,
    minFacePresenceConfidence: Float,
    minTrackingConfidence: Float,
    delegate: FaceLandmarkerDelegate) -> FaceLandmarkerService? {
    let faceLandmarkerService = FaceLandmarkerService(
      modelPath: modelPath,
      runningMode: .image,
      numFaces: numFaces,
      minFaceDetectionConfidence: minFaceDetectionConfidence,
      minFacePresenceConfidence: minFacePresenceConfidence,
      minTrackingConfidence: minTrackingConfidence,
      delegate: delegate)

    return faceLandmarkerService
  }

  // MARK: - Detection Methods for Different Modes
  /**
   This method return FaceLandmarkerResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
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

  func detectAsync(
    sampleBuffer: CMSampleBuffer,
    orientation: UIImage.Orientation,
    timeStamps: Int) {
    guard let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
      return
    }
    do {
      try faceLandmarker?.detectAsync(image: image, timestampInMilliseconds: timeStamps)
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
      videoDelegate?.faceLandmarkerService(self, willBeginDetection: frameCount)
    }

    let faceLandmarkerResultTuple = detectFaceLandmarksInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      faceLandmarkerResults: faceLandmarkerResultTuple.faceLandmarkerResults,
      size: faceLandmarkerResultTuple.videoSize)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func detectFaceLandmarksInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (faceLandmarkerResults: [FaceLandmarkerResult?], videoSize: CGSize)  {
    var faceLandmarkerResults: [FaceLandmarkerResult?] = []
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
        return (faceLandmarkerResults, videoSize)
      }

      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size

      do {
        let result = try faceLandmarker?.detect(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          faceLandmarkerResults.append(result)
        Task { @MainActor in
          videoDelegate?.faceLandmarkerService(self, didFinishDetectionOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }

    return (faceLandmarkerResults, videoSize)
  }
}

// MARK: - FaceLandmarkerLiveStreamDelegate Methods
extension FaceLandmarkerService: FaceLandmarkerLiveStreamDelegate {
  func faceLandmarker(
    _ faceLandmarker: FaceLandmarker,
    didFinishDetection result: FaceLandmarkerResult?,
    timestampInMilliseconds: Int,
    error: Error?) {
      let resultBundle = ResultBundle(
        inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
        faceLandmarkerResults: [result])
      liveStreamDelegate?.faceLandmarkerService(
        self,
        didFinishDetection: resultBundle,
        error: error)
  }
}

/// A result from the `FaceLandmarkerService`.
struct ResultBundle {
  let inferenceTime: Double
  let faceLandmarkerResults: [FaceLandmarkerResult?]
  var size: CGSize = .zero
}
