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
 This protocol must be adopted by any class that wants to get the segmention results of the image segmenter in live stream mode.
 */
protocol ImageSegmenterServiceLiveStreamDelegate: AnyObject {
  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService,
                             didFinishSegmention result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of image segmenter on videos.
 */
//protocol ImageSegmenterServiceVideoDelegate: AnyObject {
//  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService,
//                             didFinishSegmentionOnVideoFrame index: Int)
//  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService,
//                             willBeginSegmention totalframeCount: Int)
//}


// Initializes and calls the MediaPipe APIs for segmention.
class ImageSegmenterService: NSObject {

  weak var liveStreamDelegate: ImageSegmenterServiceLiveStreamDelegate?
  //  weak var videoDelegate: ImageSegmenterServiceVideoDelegate?

  var imageSegmenter: ImageSegmenter?
  private(set) var runningMode = VisionRunningMode.image
  var modelPath: String
  var delegate: Delegate

  // MARK: - Custom Initializer
  private init?(modelPath: String?,
                runningMode:VisionRunningMode,
                delegate: Delegate) {
    guard let modelPath = modelPath else { return nil }
    self.modelPath = modelPath
    self.runningMode = runningMode
    self.delegate = delegate
    super.init()

    createImageSegmenter()
  }

  private func createImageSegmenter() {
    var imageSegmenterOptions = ImageSegmenterOptions()
    imageSegmenterOptions.runningMode = runningMode
    imageSegmenterOptions.outputCategoryMask = true
    imageSegmenterOptions.baseOptions.modelAssetPath = modelPath
    imageSegmenterOptions.baseOptions.delegate = self.delegate
    if runningMode == .liveStream {
      imageSegmenterOptions.liveStreamResultHandler = { [weak self] result, timestampInMilliseconds in
        self?.handleLiveStreamResult(result, timestampInMilliseconds: timestampInMilliseconds)
      }
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
    delegate: Delegate) -> ImageSegmenterService? {
      let imageSegmenterService = ImageSegmenterService(
        modelPath: modelPath,
        runningMode: .video,
      delegate: delegate)
      return imageSegmenterService
    }

  static func liveStreamImageSegmenterService(
    modelPath: String?,
    liveStreamDelegate: ImageSegmenterServiceLiveStreamDelegate?,
    delegate: Delegate) -> ImageSegmenterService? {
      let imageSegmenterService = ImageSegmenterService(
        modelPath: modelPath,
        runningMode: .liveStream,
      delegate: delegate)
      imageSegmenterService?.liveStreamDelegate = liveStreamDelegate

      return imageSegmenterService
    }

  static func stillImageSegmenterService(
    modelPath: String?,
    delegate: Delegate) -> ImageSegmenterService? {
      let imageSegmenterService = ImageSegmenterService(
        modelPath: modelPath,
        runningMode: .image,
      delegate: delegate)

      return imageSegmenterService
    }

  // MARK: - Segmention Methods for Different Modes
  /**
   This method return ImageSegmenterResult and infrenceTime when receive an image
   **/
  func segment(image: UIImage) -> ResultBundle? {
    guard let cgImage = image.fixedOrientation() else { return nil }
    let fixImage = UIImage(cgImage: cgImage, scale: 1.0, orientation: .up)
    guard let mpImage = try? MPImage(uiImage: fixImage) else {
      return nil
    }
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
    videoFrame: CGImage,
    orientation: UIImage.Orientation,
    timeStamps: Int)
  -> ResultBundle?  {
    do {
      let mpImage = try MPImage(uiImage: UIImage(cgImage: videoFrame))
      let startDate = Date()
      let result = try imageSegmenter?.segment(videoFrame: mpImage, timestampInMilliseconds: timeStamps)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, imageSegmenterResults: [result])
    } catch {
      print(error)
      return nil
    }
  }

  private func handleLiveStreamResult(
    _ result: Result<ImageSegmenterResult, any Error>,
    timestampInMilliseconds: Int
  ) {
    let inferenceTime = Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds)
    switch result {
    case .success(let segmenterResult):
      let resultBundle = ResultBundle(
        inferenceTime: inferenceTime,
        imageSegmenterResults: [segmenterResult])
      liveStreamDelegate?.imageSegmenterService(self, didFinishSegmention: resultBundle, error: nil)
    case .failure(let error):
      let resultBundle = ResultBundle(
        inferenceTime: inferenceTime,
        imageSegmenterResults: [nil])
      liveStreamDelegate?.imageSegmenterService(self, didFinishSegmention: resultBundle, error: error)
    }
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
