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
 This protocol must be adopted by any class that wants to get the classification results of the image classifier in live stream mode.
 */
protocol ImageClassifierServiceLiveStreamDelegate: AnyObject {
  func imageClassifierService(_ imageClassifierService: ImageClassifierService,
                             didFinishClassification result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of image classification on videos.
 */
protocol ImageClassifierServiceVideoDelegate: AnyObject {
 func imageClassifierService(_ imageClassifierService: ImageClassifierService,
                                  didFinishClassificationOnVideoFrame index: Int)
 func imageClassifierService(_ imageClassifierService: ImageClassifierService,
                             willBeginClassification totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for classification.
class ImageClassifierService: NSObject {

  weak var liveStreamDelegate: ImageClassifierServiceLiveStreamDelegate?
  weak var videoDelegate: ImageClassifierServiceVideoDelegate?

  var imageClassifier: ImageClassifier?
  private(set) var runningMode: RunningMode
  private var scoreThreshold: Float
  private var maxResult: Int
  private var modelPath: String
  private var delegate: ImageClassifierDelegate

  // MARK: - Custom Initializer
  private init?(model: Model,
                scoreThreshold: Float,
                maxResult: Int,
                runningMode:RunningMode,
                delegate: ImageClassifierDelegate) {
    guard let modelPath = model.modelPath else { return nil }
    self.modelPath = modelPath
    self.scoreThreshold = scoreThreshold
    self.runningMode = runningMode
    self.maxResult = maxResult
    self.delegate = delegate
    super.init()

    createImageClassifier()
  }

  private func createImageClassifier() {
    let imageClassifierOptions = ImageClassifierOptions()
    imageClassifierOptions.runningMode = runningMode
    imageClassifierOptions.scoreThreshold = scoreThreshold
    imageClassifierOptions.maxResults = maxResult
    imageClassifierOptions.baseOptions.modelAssetPath = modelPath
    imageClassifierOptions.baseOptions.delegate = delegate.delegate
    if runningMode == .liveStream {
      imageClassifierOptions.imageClassifierLiveStreamDelegate = self
    }
    do {
      imageClassifier = try ImageClassifier(options: imageClassifierOptions)
    }
    catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoImageClassifierService(
    model: Model,
    scoreThreshold: Float,
    maxResult: Int,
    videoDelegate: ImageClassifierServiceVideoDelegate?,
    delegate: ImageClassifierDelegate) -> ImageClassifierService? {
    let imageClassifierService = ImageClassifierService(
      model: model,
      scoreThreshold: scoreThreshold,
      maxResult: maxResult,
      runningMode: .video,
      delegate: delegate)
    imageClassifierService?.videoDelegate = videoDelegate

    return imageClassifierService
  }

  static func liveStreamClassifierService(
    model: Model,
    scoreThreshold: Float,
    maxResult: Int,
    liveStreamDelegate: ImageClassifierServiceLiveStreamDelegate?,
    delegate: ImageClassifierDelegate) -> ImageClassifierService? {
    let imageClassifierService = ImageClassifierService(
      model: model,
      scoreThreshold: scoreThreshold,
      maxResult: maxResult,
      runningMode: .liveStream,
      delegate: delegate)
    imageClassifierService?.liveStreamDelegate = liveStreamDelegate

    return imageClassifierService
  }

  static func stillImageClassifierService(
    model: Model,
    scoreThreshold: Float,
    maxResult: Int,
    delegate: ImageClassifierDelegate) -> ImageClassifierService? {
      let imageClassifierService = ImageClassifierService(
        model: model,
        scoreThreshold: scoreThreshold,
        maxResult: maxResult,
        runningMode: .image,
        delegate: delegate)

      return imageClassifierService
  }

  // MARK: - Classification Methods for Different Modes
  /**
   This method return ImageClassifierResult and infrenceTime when receive an image
   **/
  func classify(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
    do {
      let startDate = Date()
      let result = try imageClassifier?.classify(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, imageClassifierResults: [result])
    } catch {
        print(error)
        return nil
    }
  }

  func classifyAsync(
    sampleBuffer: CMSampleBuffer,
    orientation: UIImage.Orientation,
    timeStamps: Int) {
    guard let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
      return
    }
    do {
      try imageClassifier?.classifyAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }

  func classify(
    videoAsset: AVAsset,
    durationInMilliseconds: Double,
    inferenceIntervalInMilliseconds: Double) async -> ResultBundle? {
    let startDate = Date()
    let assetGenerator = imageGenerator(with: videoAsset)

    let frameCount = Int(durationInMilliseconds / inferenceIntervalInMilliseconds)
    Task { @MainActor in
      videoDelegate?.imageClassifierService(self, willBeginClassification: frameCount)
    }

    let imageClassifierResultTuple = classifyObjectsInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      imageClassifierResults: imageClassifierResultTuple.imageClassifierResults,
      size: imageClassifierResultTuple.videoSize)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func classifyObjectsInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (imageClassifierResults: [ImageClassifierResult?], videoSize: CGSize)  {
    var imageClassifierResults: [ImageClassifierResult?] = []
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
        return (imageClassifierResults, videoSize)
      }

      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size

      do {
        let result = try imageClassifier?.classify(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          imageClassifierResults.append(result)
        Task { @MainActor in
          videoDelegate?.imageClassifierService(self, didFinishClassificationOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }

    return (imageClassifierResults, videoSize)
  }
}

// MARK: - ImageClassifierLiveStreamDelegate
extension ImageClassifierService: ImageClassifierLiveStreamDelegate {

  func imageClassifier(
    _ imageClassifier: ImageClassifier,
    didFinishClassification result: ImageClassifierResult?,
    timestampInMilliseconds: Int,
    error: Error?) {
      guard let result = result else {
        liveStreamDelegate?.imageClassifierService(self, didFinishClassification: nil, error: error)
        return
      }
      let resultBundle = ResultBundle(
        inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
        imageClassifierResults: [result])
      liveStreamDelegate?.imageClassifierService(self, didFinishClassification: resultBundle, error: nil)
  }
}

/// A result from inference, the time it takes for inference to be
/// performed.
struct ResultBundle {
  let inferenceTime: Double
  let imageClassifierResults: [ImageClassifierResult?]
  var size: CGSize = .zero
}
