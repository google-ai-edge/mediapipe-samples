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
 This protocol must be adopted by any class that wants to get the recognition results of the hand landmarker and gesture in live stream mode.
 */
protocol GestureRecognizerServiceLiveStreamDelegate: AnyObject {
  func gestureRecognizerService(_ gestureRecognizerService: GestureRecognizerService,
                                didFinishRecognition result: ResultBundle?,
                                error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of hand landmark and gesture on videos.
 */
protocol GestureRecognizerServiceVideoDelegate: AnyObject {
  func gestureRecognizerService(_ gestureRecognizerService: GestureRecognizerService,
                                didFinishRecognitionOnVideoFrame index: Int)
  func gestureRecognizerService(_ gestureRecognizerService: GestureRecognizerService,
                                willBeginRecognition totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for recognition.
class GestureRecognizerService: NSObject {

  weak var liveStreamDelegate: GestureRecognizerServiceLiveStreamDelegate?
  weak var videoDelegate: GestureRecognizerServiceVideoDelegate?

  var gestureRecognizer: GestureRecognizer?
  private(set) var runningMode = RunningMode.image
  private var minHandDetectionConfidence: Float
  private var minHandPresenceConfidence: Float
  private var minTrackingConfidence: Float
  private var modelPath: String
  private var delegate: GestureRecognizerDelegate


  // MARK: - Custom Initializer
  private init?(modelPath: String?,
                runningMode:RunningMode,
                minHandDetectionConfidence: Float,
                minHandPresenceConfidence: Float,
                minTrackingConfidence: Float,
                delegate: GestureRecognizerDelegate) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    self.minHandDetectionConfidence = minHandDetectionConfidence
    self.minHandPresenceConfidence = minHandPresenceConfidence
    self.minTrackingConfidence = minTrackingConfidence
    self.delegate = delegate
    super.init()

    createGestureRecognizer()
  }

  private func createGestureRecognizer() {
    let gestureRecognizerOptions = GestureRecognizerOptions()
    gestureRecognizerOptions.runningMode = runningMode
    gestureRecognizerOptions.minHandDetectionConfidence = minHandDetectionConfidence
    gestureRecognizerOptions.minHandPresenceConfidence = minHandPresenceConfidence
    gestureRecognizerOptions.minTrackingConfidence = minTrackingConfidence
    gestureRecognizerOptions.baseOptions.modelAssetPath = modelPath
    gestureRecognizerOptions.baseOptions.delegate = delegate.delegate
    if runningMode == .liveStream {
      gestureRecognizerOptions.gestureRecognizerLiveStreamDelegate = self
    }
    do {
      gestureRecognizer = try GestureRecognizer(options: gestureRecognizerOptions)
    }
    catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoGestureRecognizerService(
    modelPath: String?,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float,
    videoDelegate: GestureRecognizerServiceVideoDelegate?,
    delegate: GestureRecognizerDelegate) -> GestureRecognizerService? {
      let gestureRecognizerService = GestureRecognizerService(
        modelPath: modelPath,
        runningMode: .video,
        minHandDetectionConfidence: minHandDetectionConfidence,
        minHandPresenceConfidence: minHandPresenceConfidence,
        minTrackingConfidence: minTrackingConfidence,
        delegate: delegate)
      gestureRecognizerService?.videoDelegate = videoDelegate
      return gestureRecognizerService
    }

  static func liveStreamGestureRecognizerService(
    modelPath: String?,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float,
    liveStreamDelegate: GestureRecognizerServiceLiveStreamDelegate?,
    delegate: GestureRecognizerDelegate) -> GestureRecognizerService? {
      let gestureRecognizerService = GestureRecognizerService(
        modelPath: modelPath,
        runningMode: .liveStream,
        minHandDetectionConfidence: minHandDetectionConfidence,
        minHandPresenceConfidence: minHandPresenceConfidence,
        minTrackingConfidence: minTrackingConfidence,
        delegate: delegate)
      gestureRecognizerService?.liveStreamDelegate = liveStreamDelegate

      return gestureRecognizerService
    }

  static func stillImageGestureRecognizerService(
    modelPath: String?,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float,
    delegate: GestureRecognizerDelegate) -> GestureRecognizerService? {
      let gestureRecognizerService = GestureRecognizerService(
        modelPath: modelPath,
        runningMode: .image,
        minHandDetectionConfidence: minHandDetectionConfidence,
        minHandPresenceConfidence: minHandPresenceConfidence,
        minTrackingConfidence: minTrackingConfidence,
        delegate: delegate)

      return gestureRecognizerService
    }

  // MARK: - Recognition Methods for Different Modes
  /**
   This method return GestureRecognizerResult and infrenceTime when receive an image
   **/
  func recognize(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
    do {
      let startDate = Date()
      let result = try gestureRecognizer?.recognize(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, gestureRecognizerResults: [result])
    } catch {
      print(error)
      return nil
    }
  }

  func recognizeAsync(
    sampleBuffer: CMSampleBuffer,
    orientation: UIImage.Orientation,
    timeStamps: Int) {
      guard let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
        return
      }
      do {
        try gestureRecognizer?.recognizeAsync(image: image, timestampInMilliseconds: timeStamps)
      } catch {
        print(error)
      }
    }

  func recognize(
    videoAsset: AVAsset,
    durationInMilliseconds: Double,
    inferenceIntervalInMilliseconds: Double) async -> ResultBundle? {
      let startDate = Date()
      let assetGenerator = imageGenerator(with: videoAsset)

      let frameCount = Int(durationInMilliseconds / inferenceIntervalInMilliseconds)
      Task { @MainActor in
        videoDelegate?.gestureRecognizerService(self, willBeginRecognition: frameCount)
      }

      let gestureRecognizerResultTuple = recognizeObjectsInFramesGenerated(
        by: assetGenerator,
        totalFrameCount: frameCount,
        atIntervalsOf: inferenceIntervalInMilliseconds)

      return ResultBundle(
        inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
        gestureRecognizerResults: gestureRecognizerResultTuple.gestureRecognizerResults,
        size: gestureRecognizerResultTuple.videoSize)
    }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func recognizeObjectsInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (gestureRecognizerResults: [GestureRecognizerResult?], videoSize: CGSize)  {
    var gestureRecognizerResults: [GestureRecognizerResult?] = []
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
        return (gestureRecognizerResults, videoSize)
      }

      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size

      do {
        let result = try gestureRecognizer?.recognize(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
        gestureRecognizerResults.append(result)
        Task { @MainActor in
          videoDelegate?.gestureRecognizerService(self, didFinishRecognitionOnVideoFrame: i)
        }
      } catch {
        print(error)
      }
    }

    return (gestureRecognizerResults, videoSize)
  }
}

// MARK: - GestureRecognizerLiveStreamDelegate Methods
extension GestureRecognizerService: GestureRecognizerLiveStreamDelegate {

  func gestureRecognizer(_ gestureRecognizer: GestureRecognizer, didFinishGestureRecognition result: GestureRecognizerResult?, timestampInMilliseconds: Int, error: Error?) {
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      gestureRecognizerResults: [result])
    liveStreamDelegate?.gestureRecognizerService(
      self,
      didFinishRecognition: resultBundle,
      error: error)
  }
}

/// A result from the `GestureRecognizerService`.
struct ResultBundle {
  let inferenceTime: Double
  let gestureRecognizerResults: [GestureRecognizerResult?]
  var size: CGSize = .zero
}
