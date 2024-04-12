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

/**
 This protocol must be adopted by any class that wants to get the embedding results of the image embedder in live stream mode.
 */
protocol ImageEmbedderServiceLiveStreamDelegate: AnyObject {
  func imageEmbedderService(_ imageEmbedderService: ImageEmbedderService,
                             didFinishEmbedding result: ResultBundle?,
                             error: Error?)
}

// Initializes and calls the MediaPipe APIs for embedding.
class ImageEmbedderService: NSObject {

  weak var liveStreamDelegate: ImageEmbedderServiceLiveStreamDelegate?

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
