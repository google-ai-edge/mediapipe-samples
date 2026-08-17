// Copyright 2024 The MediaPipe Authors.
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
 This protocol must be adopted by any class that wants to get the detection results of the holistic landmarker in live stream mode.
 */
protocol HolisticLandmarkerServiceLiveStreamDelegate: AnyObject {
  func holisticLandmarkerService(
    _ holisticLandmarkerService: HolisticLandmarkerService,
    didFinishDetection result: ResultBundle?,
    error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during different stages of holistic landmark on videos.
 */
protocol HolisticLandmarkerServiceVideoDelegate: AnyObject {
  func holisticLandmarkerService(
    _ holisticLandmarkerService: HolisticLandmarkerService,
    didFinishDetectionOnVideoFrame index: Int)
  func holisticLandmarkerService(
    _ holisticLandmarkerService: HolisticLandmarkerService,
    willBeginDetection totalframeCount: Int)
}

// Initializes and calls the MediaPipe APIs for detection.
class HolisticLandmarkerService: NSObject {

  weak var liveStreamDelegate: HolisticLandmarkerServiceLiveStreamDelegate?
  weak var videoDelegate: HolisticLandmarkerServiceVideoDelegate?

  var holisticLandmarker: HolisticLandmarker?
  private(set) var runningMode = RunningMode.image
  private var minFaceDetectionConfidence: Float
  private var minFacePresenceConfidence: Float
  private var minPoseDetectionConfidence: Float
  private var minPosePresenceConfidence: Float
  private var minHandLandmarksConfidence: Float
  private var modelPath: String
  private var delegate: HolisticLandmarkerDelegate

  // MARK: - Custom Initializer
  private init?(
    modelPath: String?,
    runningMode: RunningMode,
    minFaceDetectionConfidence: Float,
    minFacePresenceConfidence: Float,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minHandLandmarksConfidence: Float,
    delegate: HolisticLandmarkerDelegate
  ) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    self.minFaceDetectionConfidence = minFaceDetectionConfidence
    self.minFacePresenceConfidence = minFacePresenceConfidence
    self.minPoseDetectionConfidence = minPoseDetectionConfidence
    self.minPosePresenceConfidence = minPosePresenceConfidence
    self.minHandLandmarksConfidence = minHandLandmarksConfidence
    self.delegate = delegate
    super.init()

    createHolisticLandmarker()
    if holisticLandmarker == nil {
      return nil
    }
  }

  private func createHolisticLandmarker() {
    let holisticLandmarkerOptions = HolisticLandmarkerOptions()
    holisticLandmarkerOptions.runningMode = runningMode
    holisticLandmarkerOptions.minFaceDetectionConfidence = minFaceDetectionConfidence
    holisticLandmarkerOptions.minFacePresenceConfidence = minFacePresenceConfidence
    holisticLandmarkerOptions.minPoseDetectionConfidence = minPoseDetectionConfidence
    holisticLandmarkerOptions.minPosePresenceConfidence = minPosePresenceConfidence
    holisticLandmarkerOptions.minHandLandmarksConfidence = minHandLandmarksConfidence
    holisticLandmarkerOptions.baseOptions.modelAssetPath = modelPath
    holisticLandmarkerOptions.baseOptions.delegate = delegate.delegate
    if runningMode == .liveStream {
      holisticLandmarkerOptions.holisticLandmarkerLiveStreamDelegate = self
    }
    do {
      holisticLandmarker = try HolisticLandmarker(options: holisticLandmarkerOptions)
    } catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoHolisticLandmarkerService(
    modelPath: String?,
    minFaceDetectionConfidence: Float,
    minFacePresenceConfidence: Float,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minHandLandmarksConfidence: Float,
    videoDelegate: HolisticLandmarkerServiceVideoDelegate?,
    delegate: HolisticLandmarkerDelegate
  ) -> HolisticLandmarkerService? {
    let service = HolisticLandmarkerService(
      modelPath: modelPath,
      runningMode: .video,
      minFaceDetectionConfidence: minFaceDetectionConfidence,
      minFacePresenceConfidence: minFacePresenceConfidence,
      minPoseDetectionConfidence: minPoseDetectionConfidence,
      minPosePresenceConfidence: minPosePresenceConfidence,
      minHandLandmarksConfidence: minHandLandmarksConfidence,
      delegate: delegate)
    service?.videoDelegate = videoDelegate
    return service
  }

  static func liveStreamHolisticLandmarkerService(
    modelPath: String?,
    minFaceDetectionConfidence: Float,
    minFacePresenceConfidence: Float,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minHandLandmarksConfidence: Float,
    liveStreamDelegate: HolisticLandmarkerServiceLiveStreamDelegate?,
    delegate: HolisticLandmarkerDelegate
  ) -> HolisticLandmarkerService? {
    let service = HolisticLandmarkerService(
      modelPath: modelPath,
      runningMode: .liveStream,
      minFaceDetectionConfidence: minFaceDetectionConfidence,
      minFacePresenceConfidence: minFacePresenceConfidence,
      minPoseDetectionConfidence: minPoseDetectionConfidence,
      minPosePresenceConfidence: minPosePresenceConfidence,
      minHandLandmarksConfidence: minHandLandmarksConfidence,
      delegate: delegate)
    service?.liveStreamDelegate = liveStreamDelegate
    return service
  }

  static func stillImageLandmarkerService(
    modelPath: String?,
    minFaceDetectionConfidence: Float,
    minFacePresenceConfidence: Float,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minHandLandmarksConfidence: Float,
    delegate: HolisticLandmarkerDelegate
  ) -> HolisticLandmarkerService? {
    let service = HolisticLandmarkerService(
      modelPath: modelPath,
      runningMode: .image,
      minFaceDetectionConfidence: minFaceDetectionConfidence,
      minFacePresenceConfidence: minFacePresenceConfidence,
      minPoseDetectionConfidence: minPoseDetectionConfidence,
      minPosePresenceConfidence: minPosePresenceConfidence,
      minHandLandmarksConfidence: minHandLandmarksConfidence,
      delegate: delegate)
    return service
  }

  // MARK: - Detection Methods for Different Modes
  /**
   This method returns HolisticLandmarkerResult and inferenceTime when receiving an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    let normalized = image.imageOrientation == .up ? image : image.normalizedImage()
    guard let mpImage = try? MPImage(uiImage: normalized) else {
      return nil
    }
    do {
      let startDate = Date()
      let result = try holisticLandmarker?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, holisticLandmarkerResults: [result])
    } catch {
      print(error)
      return nil
    }
  }

  func detectAsync(
    sampleBuffer: CMSampleBuffer,
    orientation: UIImage.Orientation,
    timeStamps: Int
  ) {
    guard let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
      return
    }
    do {
      try holisticLandmarker?.detectAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }

  func detect(
    videoAsset: AVAsset,
    durationInMilliseconds: Double,
    inferenceIntervalInMilliseconds: Double
  ) async -> ResultBundle? {
    let startDate = Date()
    let assetGenerator = imageGenerator(with: videoAsset)

    let frameCount = Int(durationInMilliseconds / inferenceIntervalInMilliseconds)
    Task { @MainActor in
      videoDelegate?.holisticLandmarkerService(self, willBeginDetection: frameCount)
    }

    let resultTuple = detectHolisticLandmarksInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      holisticLandmarkerResults: resultTuple.results,
      size: resultTuple.videoSize)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func detectHolisticLandmarksInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double
  ) -> (results: [HolisticLandmarkerResult?], videoSize: CGSize) {
    var results: [HolisticLandmarkerResult?] = []
    var videoSize = CGSize.zero

    for i in 0..<frameCount {
      let timestampMs = Int(inferenceIntervalMs) * i
      let image: CGImage
      do {
        let time = CMTime(value: Int64(timestampMs), timescale: 1000)
        image = try assetGenerator.copyCGImage(at: time, actualTime: nil)
      } catch {
        print(error)
        return (results, videoSize)
      }

      let uiImage = UIImage(cgImage: image)
      videoSize = uiImage.size

      do {
        let result = try holisticLandmarker?.detect(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
        results.append(result)
        Task { @MainActor in
          videoDelegate?.holisticLandmarkerService(self, didFinishDetectionOnVideoFrame: i)
        }
      } catch {
        print(error)
      }
    }

    return (results, videoSize)
  }
}

// MARK: - HolisticLandmarkerLiveStreamDelegate Methods
extension HolisticLandmarkerService: HolisticLandmarkerLiveStreamDelegate {
  func holisticLandmarker(
    _ holisticLandmarker: HolisticLandmarker,
    didFinishDetection result: HolisticLandmarkerResult?,
    timestampInMilliseconds: Int,
    error: Error?
  ) {
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      holisticLandmarkerResults: [result])
    liveStreamDelegate?.holisticLandmarkerService(
      self,
      didFinishDetection: resultBundle,
      error: error)
  }
}

/// A result from the `HolisticLandmarkerService`.
struct ResultBundle {
  let inferenceTime: Double
  let holisticLandmarkerResults: [HolisticLandmarkerResult?]
  var size: CGSize = .zero
}

// MARK: - UIImage Extension
extension UIImage {
  func normalizedImage() -> UIImage {
    if imageOrientation == .up {
      return self
    }
    let format = UIGraphicsImageRendererFormat()
    format.scale = self.scale
    let renderer = UIGraphicsImageRenderer(size: self.size, format: format)
    return renderer.image { _ in
      self.draw(in: CGRect(origin: .zero, size: self.size))
    }
  }
}
