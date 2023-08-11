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

import Foundation
import UIKit

import MediaPipeTasksVision

// Helper Methods for calculating bounding boxes that are to be drawn.
class ObjectOverlayHelper {
  
  private struct Constants {
    static let unknownString = "Unknown"
  }
  
  // Calculates by how much bounding boxes should and shift in the display view compared
  // to the original image size based on whether the image/camera frame is displayed using scale
  // aspect fit or fill.
  static func offsetsAndScaleFactor(
    forImageOfSize imageSize: CGSize,
    tobeDrawnInViewOfSize viewSize: CGSize,
    withContentMode contentMode: UIView.ContentMode)
  -> (xOffset: CGFloat, yOffset: CGFloat, scaleFactor: Double) {
    
    let widthScale = viewSize.width / imageSize.width;
    let heightScale = viewSize.height / imageSize.height;
    
    var scaleFactor = 0.0
    
    switch contentMode {
    case .scaleAspectFill:
      scaleFactor = max(widthScale, heightScale)
    case .scaleAspectFit:
      scaleFactor = min(widthScale, heightScale)
    default:
      scaleFactor = 1.0
    }
    
    let scaledSize = CGSize(
      width: imageSize.width * scaleFactor,
      height: imageSize.height * scaleFactor)
    let xOffset = (viewSize.width - scaledSize.width) / 2
    let yOffset = (viewSize.height - scaledSize.height) / 2
    
    return (xOffset, yOffset, scaleFactor)
  }
 
  // Helper to get object overlays from detections.
  static func objectOverlays(
    fromDetections detections: [Detection],
    inferredOnImageOfSize originalImageSize: CGSize,
    andOrientation orientation: UIImage.Orientation) -> [ObjectOverlay] {
      
      var objectOverlays: [ObjectOverlay] = []
      
      for (index, detection) in detections.enumerated() {
        guard let category = detection.categories.first else {
          continue
          
        }
        var newRect = detection.boundingBox

        // Based on orientation of the image, rotate the output bounding boxes.
        //
        // We pass in images with an orientation to MediaPipeVision inference methods. MediaPipe
        // performs inference on the the pixel buffers rotated according to the passed in
        // orientation.
        // The bounding boxes returned are not pre rotated according to the orientation of the
        // passed in image. It matches the bounds of the actual pixel buffer passed in.
        // Hence these boxes need to be rotated for display, like how iOS handles UIImage display.
        switch orientation {
        case .left:
          newRect = CGRect(
            x: detection.boundingBox.origin.y,
            y: originalImageSize.height - detection.boundingBox.origin.x - detection.boundingBox.width,
            width: detection.boundingBox.height,
            height: detection.boundingBox.width)
        case .right:
          newRect = CGRect(
            x: originalImageSize.width - detection.boundingBox.origin.y - detection.boundingBox.height,
            y: detection.boundingBox.origin.x, width: detection.boundingBox.height,
            height: detection.boundingBox.width)
          case .down:
            newRect.origin.x = originalImageSize.width - detection.boundingBox.maxX
            newRect.origin.y = originalImageSize.height - detection.boundingBox.maxY
        default:
          break
        }

        let confidenceValue = Int(category.score * 100.0)
        let string =
         "\(category.categoryName ?? ObjectOverlayHelper.Constants.unknownString)  (\(confidenceValue)%)"
        
        let displayColor = DefaultConstants.labelColors[index %  DefaultConstants.labelColors.count]
        
        let size = string.size(withAttributes: [.font: DefaultConstants.displayFont])
        
        let objectOverlay = ObjectOverlay(
          name: string,
          borderRect: newRect,
          nameStringSize: size,
          color: displayColor,
          font: DefaultConstants.displayFont)
        
        objectOverlays.append(objectOverlay)
      }
      
     return objectOverlays
  }
}


