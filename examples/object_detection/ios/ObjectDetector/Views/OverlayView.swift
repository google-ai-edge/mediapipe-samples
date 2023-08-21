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
 This structure holds the display parameters for the overlay to be drawon on a detected object.
 */
struct ObjectOverlay {
  let name: String
  let borderRect: CGRect
  let nameStringSize: CGSize
  let color: UIColor
  let font: UIFont
}

/**
 This UIView draws overlay on a detected object.
 */
class OverlayView: UIView {
  
  // MARK: Private properties
  private var objectOverlays: [ObjectOverlay] = []
  
  private var contentImageSize: CGSize = CGSizeZero
  private var imageContentMode: UIView.ContentMode = .scaleAspectFit
  private var orientation = UIDeviceOrientation.portrait
  
  private var edgeOffset: CGFloat = 0.0
  
  // MARK: Constants
  private struct Constants {
    static let stringVerticalSpacing: CGFloat = 7.0
    static let stringHorizontalSpacing: CGFloat = 13.0
    static let stringFontColor = UIColor.white
    static let lineWidth: CGFloat = 3
    static let stringBgAlpha: CGFloat = 0.7
    static let cornerRadius: CGFloat = 10.0
    static let unknownString = "Unknown"
  }
  
  // MARK: Public Functions
  func draw(
    objectOverlays: [ObjectOverlay],
    inBoundsOfContentImageOfSize imageSize: CGSize,
    edgeOffset: CGFloat = 0.0,
    imageContentMode: UIView.ContentMode) {
      
      self.clear()
      contentImageSize = imageSize
      self.edgeOffset = edgeOffset
      self.objectOverlays = objectOverlays
      self.imageContentMode = imageContentMode
      orientation = UIDevice.current.orientation
      self.setNeedsDisplay()
    }
  
  func redrawObjectOverlays(forNewDeviceOrientation deviceOrientation:UIDeviceOrientation) {
    
    orientation = deviceOrientation
    
    switch orientation {
      case .portrait:
        fallthrough
      case .landscapeLeft:
        fallthrough
      case .landscapeRight:
        self.setNeedsDisplay()
      default:
        return
    }
  }
  
  func clear() {
    objectOverlays = []
    contentImageSize = CGSize.zero
    imageContentMode = .scaleAspectFit
    orientation = UIDevice.current.orientation
    edgeOffset = 0.0
    setNeedsDisplay()
  }
  
  override func draw(_ rect: CGRect) {
    // Drawing code
    for objectOverlay in objectOverlays {
      
      let readjustedBorderRect = rectAfterApplyingBoundsAdjustment(
        onOverlayBorderRect: objectOverlay.borderRect)
      draw(
        borderWithRect: readjustedBorderRect,
        color: objectOverlay.color)
      draw(
        name: objectOverlay.name,
        withBorderRect: readjustedBorderRect,
        color: objectOverlay.color,
        nameStringSize: objectOverlay.nameStringSize,
        font: objectOverlay.font)
    }
  }
  
  // MARK: Private Functions
  private func rectAfterApplyingBoundsAdjustment(
    onOverlayBorderRect borderRect: CGRect) -> CGRect {
      
      var currentSize = self.bounds.size
      let minDimension = min(self.bounds.width, self.bounds.height)
      let maxDimension = max(self.bounds.width, self.bounds.height)
      
      switch orientation {
        case .portrait:
          currentSize = CGSizeMake(minDimension, maxDimension)
        case .landscapeLeft:
          fallthrough
        case .landscapeRight:
          currentSize = CGSizeMake(maxDimension, minDimension)
        default:
          break
      }
      
      let offsetsAndScaleFactor = OverlayView.offsetsAndScaleFactor(
        forImageOfSize: self.contentImageSize,
        tobeDrawnInViewOfSize: currentSize,
        withContentMode: imageContentMode)
      
      var newRect = borderRect
        .applying(
          CGAffineTransform(scaleX: offsetsAndScaleFactor.scaleFactor, y: offsetsAndScaleFactor.scaleFactor)
        )
        .applying(
          CGAffineTransform(translationX: offsetsAndScaleFactor.xOffset, y: offsetsAndScaleFactor.yOffset)
        )
      
      if newRect.origin.x < 0 &&
          newRect.origin.x + newRect.size.width > edgeOffset {
        newRect.size.width = newRect.maxX - edgeOffset
        newRect.origin.x = edgeOffset
      }
      
      if newRect.origin.y < 0 &&
          newRect.origin.y + newRect.size.height > edgeOffset {
        newRect.size.height += newRect.maxY - edgeOffset
        newRect.origin.y = edgeOffset
      }
      
      if newRect.maxY > currentSize.height {
        newRect.size.height = currentSize.height - newRect.origin.y  - edgeOffset
      }
      
      if newRect.maxX > currentSize.width {
        newRect.size.width = currentSize.width - newRect.origin.x - edgeOffset
      }
      
      return newRect
      
    }
  
  /**
   This method draws the borders of the detected objects.
   */
  private func draw(borderWithRect rect: CGRect, color: UIColor) {
    
    
    let path = UIBezierPath(rect: rect)
    path.lineWidth = Constants.lineWidth
    color.setStroke()
    
    path.stroke()
  }
  
  /**
   This method draws the name  and background with the specified params.
   */
  private func draw(
    name: String,
    withBorderRect borderRect: CGRect,
    color: UIColor,
    nameStringSize: CGSize,
    font: UIFont) {
      
      // Draws the background of the name.
      let stringBgRect = CGRect(
        x: borderRect.origin.x,
        y: borderRect.origin.y ,
        width: 2 * Constants.stringHorizontalSpacing + nameStringSize.width,
        height: 2 * Constants.stringVerticalSpacing + nameStringSize.height
      )
      
      let stringBgPath = UIBezierPath(rect: stringBgRect)
      color.withAlphaComponent(Constants.stringBgAlpha)
        .setFill()
      stringBgPath.fill()
      
      // Draws the name.
      let stringRect = CGRect(
        x: borderRect.origin.x + Constants.stringHorizontalSpacing,
        y: borderRect.origin.y + Constants.stringVerticalSpacing,
        width: nameStringSize.width,
        height: nameStringSize.height)
      
      let attributedString = NSAttributedString(
        string: name,
        attributes: [
          NSAttributedString.Key.foregroundColor : Constants.stringFontColor,
          NSAttributedString.Key.font : font
        ])
      
      attributedString.draw(in: stringRect)
    }
  
  // MARK: Helper Functions
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
        "\(category.categoryName ?? OverlayView.Constants.unknownString)  (\(confidenceValue)%)"
        
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
