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
 This protocol must be adopted by any class that wants to get the embedding results of the image embedder in live stream mode.
 */
protocol ImageEmbedderServiceLiveStreamDelegate: AnyObject {
  func imageEmbedderService(_ imageEmbedderService: ImageEmbedderService,
                             didFinishEmbedding result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of image embedding on videos.
 */
protocol ImageEmbedderServiceVideoDelegate: AnyObject {
 func imageEmbedderService(_ imageEmbedderService: ImageEmbedderService,
                                  didFinishEmbeddingOnVideoFrame index: Int)
 func imageEmbedderService(_ imageEmbedderService: ImageEmbedderService,
                             willBeginEmbedding totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for embedding.
class ImageEmbedderService: NSObject {

  weak var liveStreamDelegate: ImageEmbedderServiceLiveStreamDelegate?
  weak var videoDelegate: ImageEmbedderServiceVideoDelegate?

  var imageEmbedder: ImageEmbedder?
  private(set) var runningMode: RunningMode
  private var modelPath: String
  private var delegate: ImageEmbedderDelegate

  // MARK: - Custom Initializer
  private init?(model: Model,
                runningMode:RunningMode,
                delegate: ImageEmbedderDelegate) {
    guard let modelPath = model.modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    self.delegate = delegate
    super.init()

    createImageEmbedder()
  }

  private func createImageEmbedder() {
    let imageEmbedderOptions = ImageEmbedderOptions()
    imageEmbedderOptions.runningMode = runningMode
    imageEmbedderOptions.baseOptions.modelAssetPath = modelPath
    imageEmbedderOptions.baseOptions.delegate = delegate.delegate
    if runningMode == .liveStream {
      imageEmbedderOptions.imageEmbedderLiveStreamDelegate = self
    }
    do {
      imageEmbedder = try ImageEmbedder(options: imageEmbedderOptions)
    }
    catch {
      print(error)
    }
  }

  // MARK: - Static Initializers
  static func videoImageEmbedderService(
    model: Model,
    videoDelegate: ImageEmbedderServiceVideoDelegate?,
    delegate: ImageEmbedderDelegate) -> ImageEmbedderService? {
    let imageEmbedderService = ImageEmbedderService(
      model: model,
      runningMode: .video,
      delegate: delegate)
    imageEmbedderService?.videoDelegate = videoDelegate

    return imageEmbedderService
  }

  static func liveStreamEmbedderService(
    model: Model,
    liveStreamDelegate: ImageEmbedderServiceLiveStreamDelegate?,
    delegate: ImageEmbedderDelegate) -> ImageEmbedderService? {
    let imageEmbedderService = ImageEmbedderService(
      model: model,
      runningMode: .liveStream,
      delegate: delegate)
    imageEmbedderService?.liveStreamDelegate = liveStreamDelegate

    return imageEmbedderService
  }

  static func stillImageEmbedderService(
    model: Model,
    delegate: ImageEmbedderDelegate) -> ImageEmbedderService? {
      let imageEmbedderService = ImageEmbedderService(
        model: model,
        runningMode: .image,
        delegate: delegate)

      return imageEmbedderService
  }

  // MARK: - Embedding Methods for Different Modes
  /**
   This method return ImageEmbedderResult and infrenceTime when receive an image
   **/
  func embed(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
    do {
      let startDate = Date()
      let result = try imageEmbedder?.embed(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, imageEmbedderResults: [result])
    } catch {
        print(error)
        return nil
    }
  }

  func embedAsync(
    sampleBuffer: CMSampleBuffer,
    orientation: UIImage.Orientation,
    timeStamps: Int) {
    guard let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
      return
    }
    do {
      try imageEmbedder?.embedAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }

  func embed(
    videoAsset: AVAsset,
    durationInMilliseconds: Double,
    inferenceIntervalInMilliseconds: Double) async -> ResultBundle? {
    let startDate = Date()
    let assetGenerator = imageGenerator(with: videoAsset)

    let frameCount = Int(durationInMilliseconds / inferenceIntervalInMilliseconds)
    Task { @MainActor in
      videoDelegate?.imageEmbedderService(self, willBeginEmbedding: frameCount)
    }

    let imageEmbedderResultTuple = embedObjectsInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      imageEmbedderResults: imageEmbedderResultTuple.imageEmbedderResults,
      size: imageEmbedderResultTuple.videoSize)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func embedObjectsInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (imageEmbedderResults: [ImageEmbedderResult?], videoSize: CGSize)  {
    var imageEmbedderResults: [ImageEmbedderResult?] = []
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
        return (imageEmbedderResults, videoSize)
      }

      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size

      do {
        let result = try imageEmbedder?.embed(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          imageEmbedderResults.append(result)
        Task { @MainActor in
          videoDelegate?.imageEmbedderService(self, didFinishEmbeddingOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }

    return (imageEmbedderResults, videoSize)
  }
}

// MARK: - ImageEmbedderLiveStreamDelegate
extension ImageEmbedderService: ImageEmbedderLiveStreamDelegate {
  func imageEmbedder(_ imageEmbedder: ImageEmbedder, didFinishEmbedding result: ImageEmbedderResult?, timestampInMilliseconds: Int, error: (any Error)?) {
    guard let result = result else {
      liveStreamDelegate?.imageEmbedderService(self, didFinishEmbedding: nil, error: error)
      return
    }
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      imageEmbedderResults: [result])
    liveStreamDelegate?.imageEmbedderService(self, didFinishEmbedding: resultBundle, error: nil)
  }
}

/// A result from inference, the time it takes for inference to be
/// performed.
struct ResultBundle {
  let inferenceTime: Double
  let imageEmbedderResults: [ImageEmbedderResult?]
  var size: CGSize = .zero
}
