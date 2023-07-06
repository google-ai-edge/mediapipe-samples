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

protocol ImageClassifierHelperDelegate: AnyObject {
  func imageClassifierHelper(_ imageClassifierHelper: ImageClassifierHelper,
                             didFinishClassification result: ResultBundle?,
                             error: Error?)
}

class ImageClassifierHelper: NSObject {

  weak var delegate: ImageClassifierHelperDelegate?
  var imageClassifier: ImageClassifier?

  // Create ImageClassifierHelper with params
  init(model: Model, maxResults: Int, scoreThreshold: Float, runningModel: RunningMode, delegate: ImageClassifierHelperDelegate?) {
    super.init()
    guard let modelPath = model.modelPath else { return }
    let imageClassifierOptions = ImageClassifierOptions()
    imageClassifierOptions.runningMode = runningModel
    imageClassifierOptions.maxResults = maxResults
    imageClassifierOptions.scoreThreshold = scoreThreshold
    imageClassifierOptions.baseOptions.modelAssetPath = modelPath
    imageClassifierOptions.imageClassifierLiveStreamDelegate = runningModel == .liveStream ? self : nil
    imageClassifier = try? ImageClassifier(options: imageClassifierOptions)
    self.delegate = delegate
  }

  func classify(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else { return nil }
    do {
      let startDate = Date()
      guard let result = try imageClassifier?.classify(image: mpImage) else { return nil }
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, imageClassifierResults: [result])
    } catch {
      print(error)
      return nil
    }
  }

  func classifyVideoFile(url: URL, inferenceIntervalMs: Double) async -> ResultBundle? {
    guard let imageClassifier = imageClassifier else { return nil }
    let startDate = Date()
    let asset:AVAsset = AVAsset(url: url)
    let generator = AVAssetImageGenerator(asset:asset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true
    guard let videoDutationMS = try? await asset.load(.duration).seconds * 1000 else { return nil }
    let frameCount = Int(videoDutationMS / inferenceIntervalMs)
    var imageClassifierResults: [ImageClassifierResult?] = []
    for i in 0..<frameCount {
      let timestampMs = Int(inferenceIntervalMs) * i // ms
      let image:CGImage?
      do {
        let time = CMTime(seconds: Double(timestampMs) / 1000, preferredTimescale: 600)
        try image = generator.copyCGImage(at: time, actualTime:nil)
      } catch {
        print(error)
         return nil
      }
      guard let image = image else { return nil }
      let uiImage = UIImage(cgImage:image)
      let result = try? imageClassifier.classify(videoFrame: MPImage(uiImage: uiImage), timestampInMilliseconds: timestampMs)
      imageClassifierResults.append(result)
    }
    let inferenceTime = Date().timeIntervalSince(startDate) / Double(frameCount) * 1000
    return ResultBundle(inferenceTime: inferenceTime, imageClassifierResults: imageClassifierResults)
  }

  func classifyAsync(videoFrame: CVPixelBuffer, timeStamps: Int) {
    guard let imageClassifier = imageClassifier,
          let image = try? MPImage(pixelBuffer: videoFrame) else { return }
    do {
      try imageClassifier.classifyAsync(image: image, timestampInMilliseconds: timeStamps)
    } catch {
      print(error)
    }
  }
}

extension ImageClassifierHelper: ImageClassifierLiveStreamDelegate {
  func imageClassifier(_ imageClassifier: ImageClassifier, didFinishClassification result: ImageClassifierResult?, timestampInMilliseconds: Int, error: Error?) {
    guard let result = result else {
      delegate?.imageClassifierHelper(self, didFinishClassification: nil, error: error)
      return
    }
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      imageClassifierResults: [result])
    delegate?.imageClassifierHelper(self, didFinishClassification: resultBundle, error: nil)
  }
}

/// A result from inference, the time it takes for inference to be
/// performed.
struct ResultBundle {
  let inferenceTime: Double
  let imageClassifierResults: [ImageClassifierResult?]
}
