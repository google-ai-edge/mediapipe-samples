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
 This protocol must be adopted by any class that wants to get the segmention results of the face landmarker in live stream mode.
 */
protocol ImageSegmenterServiceLiveStreamDelegate: AnyObject {
  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService,
                             didFinishSegmention result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of face landmark on videos.
 */
protocol ImageSegmenterServiceVideoDelegate: AnyObject {
  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService,
                             didFinishSegmentionOnVideoFrame index: Int)
  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService,
                             willBeginSegmention totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for segmention.
class ImageSegmenterService: NSObject {
  
  weak var liveStreamDelegate: ImageSegmenterServiceLiveStreamDelegate?
  weak var videoDelegate: ImageSegmenterServiceVideoDelegate?
  
  var imageSegmenter: ImageSegmenter?
  private(set) var runningMode = RunningMode.image
  var modelPath: String
  
  // MARK: - Custom Initializer
  private init?(modelPath: String?,
                runningMode:RunningMode) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    super.init()
    
    createImageSegmenter()
  }
  
  private func createImageSegmenter() {
    let imageSegmenterOptions = ImageSegmenterOptions()
    imageSegmenterOptions.runningMode = runningMode
    imageSegmenterOptions.shouldOutputConfidenceMasks = true
    imageSegmenterOptions.baseOptions.modelAssetPath = modelPath
    if runningMode == .liveStream {
      imageSegmenterOptions.imageSegmenterLiveStreamDelegate = self
    }
    do {
      imageSegmenter = try ImageSegmenter(options: imageSegmenterOptions)
    }
    catch {
      print(error)
    }
  }
  
  // MARK: - Static Initializers
  static func videoImageSegmenterService(
    modelPath: String?,
    videoDelegate: ImageSegmenterServiceVideoDelegate?) -> ImageSegmenterService? {
      let imageSegmenterService = ImageSegmenterService(
        modelPath: modelPath,
        runningMode: .video)
      imageSegmenterService?.videoDelegate = videoDelegate
      return imageSegmenterService
    }
  
  static func liveStreamImageSegmenterService(
    modelPath: String?,
    liveStreamDelegate: ImageSegmenterServiceLiveStreamDelegate?) -> ImageSegmenterService? {
      let imageSegmenterService = ImageSegmenterService(
        modelPath: modelPath,
        runningMode: .liveStream)
      imageSegmenterService?.liveStreamDelegate = liveStreamDelegate
      
      return imageSegmenterService
    }
  
  static func stillImageSegmenterService(
    modelPath: String?) -> ImageSegmenterService? {
      let imageSegmenterService = ImageSegmenterService(
        modelPath: modelPath,
        runningMode: .image)
      
      return imageSegmenterService
    }
  
  // MARK: - Segmention Methods for Different Modes
  /**
   This method return ImageSegmenterResult and infrenceTime when receive an image
   **/
  func segment(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
    do {
      let startDate = Date()
      let result = try imageSegmenter?.segment(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, imageSegmenterResults: [result])
    } catch {
      print(error)
      return nil
    }
  }
  
  func segmentAsync(
    sampleBuffer: CMSampleBuffer,
    orientation: UIImage.Orientation,
    timeStamps: Int) {
      guard let image = try? MPImage(sampleBuffer: sampleBuffer, orientation: orientation) else {
        return
      }
      do {
        try imageSegmenter?.segmentAsync(image: image, timestampInMilliseconds: timeStamps)
      } catch {
        print(error)
      }
    }
  
  func segment(
    videoAsset: AVAsset,
    durationInMilliseconds: Double,
    inferenceIntervalInMilliseconds: Double) async -> ResultBundle? {
    let startDate = Date()
    let assetGenerator = imageGenerator(with: videoAsset)

    let frameCount = Int(durationInMilliseconds / inferenceIntervalInMilliseconds)
    Task { @MainActor in
      videoDelegate?.imageSegmenterService(self, willBeginSegmention: frameCount)
    }

    let imageSegmenterResults = segmentImageInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      imageSegmenterResults: imageSegmenterResults)
  }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  private func segmentImageInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> [ImageSegmenterResult?]  {
    var imageSegmenterResults: [ImageSegmenterResult?] = []

    for i in 0..<frameCount {
      let timestampMs = Int(inferenceIntervalMs) * i // ms
      let image: CGImage
      do {
        let time = CMTime(value: Int64(timestampMs), timescale: 1000)
        image = try assetGenerator.copyCGImage(at: time, actualTime: nil)
      } catch {
        print(error)
        return imageSegmenterResults
      }

      let uiImage = UIImage(cgImage:image)

      do {
        let result = try imageSegmenter?.segment(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          imageSegmenterResults.append(result)
        Task { @MainActor in
          videoDelegate?.imageSegmenterService(self, didFinishSegmentionOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }

    return imageSegmenterResults
  }
}

// MARK: - ImageSegmenterLiveStreamDelegate Methods
extension ImageSegmenterService: ImageSegmenterLiveStreamDelegate {
  func imageSegmenter(_ imageSegmenter: ImageSegmenter, didFinishSegmentation result: ImageSegmenterResult?, timestampInMilliseconds: Int, error: Error?) {
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      imageSegmenterResults: [result])
    liveStreamDelegate?.imageSegmenterService(
      self,
      didFinishSegmention: resultBundle,
      error: error)
  }
}

/// A result from the `ImageSegmenterService`.
struct ResultBundle {
  let inferenceTime: Double
  let imageSegmenterResults: [ImageSegmenterResult?]
  var size: CGSize = .zero
}

struct VideoFrame {
  let pixelBuffer: CVPixelBuffer
  let formatDescription: CMFormatDescription
}
