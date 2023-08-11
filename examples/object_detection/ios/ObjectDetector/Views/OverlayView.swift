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
  
  private var objectOverlays: [ObjectOverlay] = []
  
  private let cornerRadius: CGFloat = 10.0
  private let stringBgAlpha: CGFloat = 0.7
  private let lineWidth: CGFloat = 3
  private let stringFontColor = UIColor.white
  private let stringHorizontalSpacing: CGFloat = 13.0
  private let stringVerticalSpacing: CGFloat = 7.0
  
  private var contentImageSize: CGSize = CGSizeZero
  private var imageContentMode: UIView.ContentMode = .scaleAspectFit
  private var orientation = UIDeviceOrientation.portrait
  
  private var edgeOffset: CGFloat = 0.0
  
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
      
      let offsetsAndScaleFactor = ObjectOverlayHelper.offsetsAndScaleFactor(
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
    path.lineWidth = lineWidth
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
        width: 2 * stringHorizontalSpacing + nameStringSize.width,
        height: 2 * stringVerticalSpacing + nameStringSize.height
      )
      
      let stringBgPath = UIBezierPath(rect: stringBgRect)
      color.withAlphaComponent(stringBgAlpha)
        .setFill()
      stringBgPath.fill()
      
      // Draws the name.
      let stringRect = CGRect(
        x: borderRect.origin.x + stringHorizontalSpacing,
        y: borderRect.origin.y + stringVerticalSpacing,
        width: nameStringSize.width,
        height: nameStringSize.height)
      
      let attributedString = NSAttributedString(
        string: name,
        attributes: [
          NSAttributedString.Key.foregroundColor : stringFontColor,
          NSAttributedString.Key.font : font
        ])
      
      attributedString.draw(in: stringRect)
    }
}
