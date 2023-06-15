//
//  ImageClassifierHelper.swift
//  ImageClassifier
//
//  Created by MBA0077 on 6/8/23.
//

import UIKit
import MediaPipeTasksVision

class ImageClassifierHelper {

  var imageClassifier: ImageClassifier?

  init(model: Model, maxResults: Int, scoreThreshold: Float) {
    guard let modelPath = model.modelPath else { return }
    let imageClassifierOptions = ImageClassifierOptions()
    imageClassifierOptions.runningMode = .video
    imageClassifierOptions.maxResults = maxResults
    imageClassifierOptions.scoreThreshold = scoreThreshold
    imageClassifierOptions.baseOptions.modelAssetPath = modelPath
    imageClassifier = try? ImageClassifier(options: imageClassifierOptions)
  }

  func classify(image: MPImage) -> ImageClassifierResult? {
    return try? imageClassifier?.classify(image: image)
  }

  func classify(videoFrame: CVPixelBuffer, timeStamps: Int) -> ImageClassifierResult? {
    guard let imageClassifier = imageClassifier,
          let image = try? MPImage(pixelBuffer: videoFrame) else { return nil }
    do {
      let result = try imageClassifier.classify(videoFrame: image, timestampInMilliseconds: timeStamps)
      return result
    } catch {
      print(error)
      return nil
    }
  }
}
