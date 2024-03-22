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
 This protocol must be adopted by any class that wants to get the detection results of the object detector in live stream mode.
 */
protocol ObjectDetectorServiceLiveStreamDelegate: AnyObject {
  func objectDetectorService(_ objectDetectorService: ObjectDetectorService,
                             didFinishDetection result: ResultBundle?,
                             error: Error?)
}

/**
 This protocol must be adopted by any class that wants to take appropriate actions during  different stages of object detection on videos.
 */
protocol ObjectDetectorServiceVideoDelegate: AnyObject {
 func objectDetectorService(_ objectDetectorService: ObjectDetectorService,
                                  didFinishDetectionOnVideoFrame index: Int)
 func objectDetectorService(_ objectDetectorService: ObjectDetectorService,
                             willBeginDetection totalframeCount: Int)
}


// Initializes and calls the MediaPipe APIs for detection.
class ObjectDetectorService: NSObject {

  weak var liveStreamDelegate: ObjectDetectorServiceLiveStreamDelegate?
  weak var videoDelegate: ObjectDetectorServiceVideoDelegate?

  var objectDetector: ObjectDetector?
  private(set) var runningMode = RunningMode.image
  private var maxResults = 3
  private var scoreThreshold: Float = 0.5
  private var modelPath: String
  private var delegate: Delegate

  // MARK: - Custom Initializer
  private init?(model: Model, maxResults: Int, scoreThreshold: Float, runningMode:RunningMode, delegate: Delegate) {
    guard let modelPath = model.modelPath else {
      return nil
    }
    self.modelPath = modelPath
    self.maxResults = maxResults
    self.scoreThreshold = scoreThreshold
    self.runningMode = runningMode
    self.delegate = delegate
    super.init()
    
    createObjectDetector()
  }
  
  private func createObjectDetector() {
    let objectDetectorOptions = ObjectDetectorOptions()
    objectDetectorOptions.runningMode = runningMode
    objectDetectorOptions.maxResults = self.maxResults
    objectDetectorOptions.scoreThreshold = self.scoreThreshold
    objectDetectorOptions.baseOptions.modelAssetPath = modelPath
    objectDetectorOptions.baseOptions.delegate = delegate
    if runningMode == .liveStream {
      objectDetectorOptions.objectDetectorLiveStreamDelegate = self
    }
    do {
      objectDetector = try ObjectDetector(options: objectDetectorOptions)
    }
    catch {
      print(error)
    }
  }
  
  // MARK: - Static Initializers
  static func videoObjectDetectorService(
    model: Model, maxResults: Int,
    scoreThreshold: Float,
    videoDelegate: ObjectDetectorServiceVideoDelegate?,
    delegate: Delegate) -> ObjectDetectorService? {
    let objectDetectorService = ObjectDetectorService(
      model: model,
      maxResults: maxResults,
      scoreThreshold: scoreThreshold,
      runningMode: .video,
      delegate: delegate)
    objectDetectorService?.videoDelegate = videoDelegate
    
    return objectDetectorService
  }
  
  static func liveStreamDetectorService(
    model: Model, maxResults: Int,
    scoreThreshold: Float,
    liveStreamDelegate: ObjectDetectorServiceLiveStreamDelegate?,
    delegate: Delegate) -> ObjectDetectorService? {
    let objectDetectorService = ObjectDetectorService(
      model: model,
      maxResults: maxResults,
      scoreThreshold: scoreThreshold,
      runningMode: .liveStream,
      delegate: delegate)
    objectDetectorService?.liveStreamDelegate = liveStreamDelegate
    
    return objectDetectorService
  }
  
  static func stillImageDetectorService(
    model: Model, maxResults: Int,
    scoreThreshold: Float,
    delegate: Delegate) -> ObjectDetectorService? {
    let objectDetectorService = ObjectDetectorService(
      model: model,
      maxResults: maxResults,
      scoreThreshold: scoreThreshold,
      runningMode: .image,
      delegate: delegate)

    return objectDetectorService
  }
  
  // MARK: - Detection Methods for Different Modes
  /**
   This method return ObjectDetectorResult and infrenceTime when receive an image
   **/
  func detect(image: UIImage) -> ResultBundle? {
    guard let mpImage = try? MPImage(uiImage: image) else {
      return nil
    }
    print(image.imageOrientation.rawValue)
    do {
      let startDate = Date()
      let result = try objectDetector?.detect(image: mpImage)
      let inferenceTime = Date().timeIntervalSince(startDate) * 1000
      return ResultBundle(inferenceTime: inferenceTime, objectDetectorResults: [result])
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
      try objectDetector?.detectAsync(image: image, timestampInMilliseconds: timeStamps)
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
      videoDelegate?.objectDetectorService(self, willBeginDetection: frameCount)
    }
  
    let objectDetectorResultTuple = detectObjectsInFramesGenerated(
      by: assetGenerator,
      totalFrameCount: frameCount,
      atIntervalsOf: inferenceIntervalInMilliseconds)

    return ResultBundle(
      inferenceTime: Date().timeIntervalSince(startDate) / Double(frameCount) * 1000,
      objectDetectorResults: objectDetectorResultTuple.objectDetectorResults,
      size: objectDetectorResultTuple.videoSize)
  }
  
  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true
    
    return generator
  }
  
  private func detectObjectsInFramesGenerated(
    by assetGenerator: AVAssetImageGenerator,
    totalFrameCount frameCount: Int,
    atIntervalsOf inferenceIntervalMs: Double)
  -> (objectDetectorResults: [ObjectDetectorResult?], videoSize: CGSize)  {
    var objectDetectorResults: [ObjectDetectorResult?] = []
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
        return (objectDetectorResults, videoSize)
      }
        
      let uiImage = UIImage(cgImage:image)
      videoSize = uiImage.size
        
      do {
        let result = try objectDetector?.detect(
          videoFrame: MPImage(uiImage: uiImage),
          timestampInMilliseconds: timestampMs)
          objectDetectorResults.append(result)
        Task { @MainActor in
          videoDelegate?.objectDetectorService(self, didFinishDetectionOnVideoFrame: i)
        }
        } catch {
          print(error)
        }
      }
    
    return (objectDetectorResults, videoSize)
  }
}

// MARK: - ObjectDetectorLiveStreamDelegate
extension ObjectDetectorService: ObjectDetectorLiveStreamDelegate {
  func objectDetector(
    _ objectDetector: ObjectDetector,
    didFinishDetection result: ObjectDetectorResult?,
    timestampInMilliseconds: Int,
    error: Error?) {
    guard let result = result else {
      liveStreamDelegate?.objectDetectorService(self, didFinishDetection: nil, error: error)
      return
    }
    let resultBundle = ResultBundle(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      objectDetectorResults: [result])
    liveStreamDelegate?.objectDetectorService(self, didFinishDetection: resultBundle, error: nil)
  }
}

/// A result from the `ObjectDetectorHelper`.
struct ResultBundle {
  let inferenceTime: Double
  let objectDetectorResults: [ObjectDetectorResult?]
  var size: CGSize = .zero
}
