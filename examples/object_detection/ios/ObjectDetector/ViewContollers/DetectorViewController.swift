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

/**
 * Base class to view controllers that handle performing detection on images, videos or camera frames.
 */
class DetectorViewController: UIViewController {
  private struct Constants {
    static let edgeOffset: CGFloat = 2.0
  }
  
  @IBOutlet weak var overlayView: OverlayView!
  private var isObserver = false
  
  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?
  weak var interfaceUpdatesDelegate: InterfaceUpdatesDelegate?
  
  override func viewDidLoad() {
    super.viewDidLoad()
    // Do any additional setup after loading the view.
  }
  
  func addDetectorMetadataKeyValueObservers() {
    DetectorMetadata.sharedInstance.addObserver(
      self,
      forKeyPath: (#keyPath(DetectorMetadata.maxResults)),
      options: [.new],
      context: nil)
    DetectorMetadata.sharedInstance.addObserver(
      self,
      forKeyPath: (#keyPath(DetectorMetadata.scoreThreshold)),
      options: [.new],
      context: nil)
    DetectorMetadata.sharedInstance.addObserver(
      self,
      forKeyPath: (#keyPath(DetectorMetadata.model)),
      options: [.new],
      context: nil)
    isObserver = true
  }
  
  func removeDetectorMetadataKeyValueObservers() {
    if isObserver {
      DetectorMetadata.sharedInstance.removeObserver(self, forKeyPath: #keyPath(DetectorMetadata.maxResults))
      DetectorMetadata.sharedInstance.removeObserver(self, forKeyPath: #keyPath(DetectorMetadata.scoreThreshold))
      DetectorMetadata.sharedInstance.removeObserver(self, forKeyPath: #keyPath(DetectorMetadata.model))
    }
    isObserver = false
  }
  
  func draw(
    detections: [Detection],
    originalImageSize: CGSize,
    andOrientation orientation: UIImage.Orientation,
    imageContentMode: UIView.ContentMode = .scaleAspectFit) {
      // Hands off drawing to the OverlayView
      overlayView.draw(
        objectOverlays:ObjectOverlayHelper.objectOverlays(
          fromDetections: detections,
          inferredOnImageOfSize: originalImageSize,
          andOrientation: orientation),
        inBoundsOfContentImageOfSize: originalImageSize,
        edgeOffset: Constants.edgeOffset,
        imageContentMode: imageContentMode)
    }
}
