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

/// A straight line.
struct Line {
  let from: CGPoint
  let to: CGPoint
}

/// Line connection
struct LineConnection {
  let color: UIColor
  let lines: [Line]
}

/**
 This structure holds the display parameters for the overlay to be drawon on a detected object.
 */
struct FaceOverlay {
  let dots: [CGPoint]
  let lineConnections: [LineConnection]
}

/// Custom view to visualize the face landmarker result on top of the input image.
class OverlayView: UIView {

  var faceOverlays: [FaceOverlay] = []

  private var contentImageSize: CGSize = CGSizeZero
  var imageContentMode: UIView.ContentMode = .scaleAspectFit
  private var orientation = UIDeviceOrientation.portrait

  private var edgeOffset: CGFloat = 0.0

  // MARK: Public Functions
  func draw(
    faceOverlays: [FaceOverlay],
    inBoundsOfContentImageOfSize imageSize: CGSize,
    edgeOffset: CGFloat = 0.0,
    imageContentMode: UIView.ContentMode) {

      self.clear()
      contentImageSize = imageSize
      self.edgeOffset = edgeOffset
      self.faceOverlays = faceOverlays
      self.imageContentMode = imageContentMode
      orientation = UIDevice.current.orientation
      self.setNeedsDisplay()
    }

  func redrawFaceOverlays(forNewDeviceOrientation deviceOrientation:UIDeviceOrientation) {

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
    faceOverlays = []
    contentImageSize = CGSize.zero
    imageContentMode = .scaleAspectFit
    orientation = UIDevice.current.orientation
    edgeOffset = 0.0
    setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    for faceOverlay in faceOverlays {
      drawDots(faceOverlay.dots)
      for lineConnection in faceOverlay.lineConnections {
        drawLines(lineConnection.lines, lineColor: lineConnection.color)
      }
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

  private func drawDots(_ dots: [CGPoint]) {
    for dot in dots {
      let dotRect = CGRect(
        x: CGFloat(dot.x) - DefaultConstants.pointRadius / 2,
        y: CGFloat(dot.y) - DefaultConstants.pointRadius / 2,
        width: DefaultConstants.pointRadius,
        height: DefaultConstants.pointRadius)
      let path = UIBezierPath(ovalIn: dotRect)
      DefaultConstants.pointFillColor.setFill()
      DefaultConstants.pointColor.setStroke()
      path.stroke()
      path.fill()
    }
  }

  private func drawLines(_ lines: [Line], lineColor: UIColor) {
    let path = UIBezierPath()
    for line in lines {
      path.move(to: line.from)
      path.addLine(to: line.to)
    }
    path.lineWidth = DefaultConstants.lineWidth
    lineColor.setStroke()
    path.stroke()
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
  static func faceOverlays(
    fromMultipleFaceLandmarks landmarks: [[NormalizedLandmark]],
    inferredOnImageOfSize originalImageSize: CGSize,
    ovelayViewSize: CGSize,
    imageContentMode: UIView.ContentMode,
    andOrientation orientation: UIImage.Orientation) -> [FaceOverlay] {

      var faceOverlays: [FaceOverlay] = []

      guard !landmarks.isEmpty else {
        return []
      }

      let offsetsAndScaleFactor = OverlayView.offsetsAndScaleFactor(
        forImageOfSize: originalImageSize,
        tobeDrawnInViewOfSize: ovelayViewSize,
        withContentMode: imageContentMode)

      for faceLandmarks in landmarks {
        var transformedFaceLandmarks: [CGPoint]!

        switch orientation {
        case .left:
          transformedFaceLandmarks = faceLandmarks.map({CGPoint(x: CGFloat($0.y), y: 1 - CGFloat($0.x))})
        case .right:
          transformedFaceLandmarks = faceLandmarks.map({CGPoint(x: 1 - CGFloat($0.y), y: CGFloat($0.x))})
        default:
          transformedFaceLandmarks = faceLandmarks.map({CGPoint(x: CGFloat($0.x), y: CGFloat($0.y))})
        }

        let dots: [CGPoint] = transformedFaceLandmarks.map({CGPoint(x: CGFloat($0.x) * originalImageSize.width * offsetsAndScaleFactor.scaleFactor + offsetsAndScaleFactor.xOffset, y: CGFloat($0.y) * originalImageSize.height * offsetsAndScaleFactor.scaleFactor + offsetsAndScaleFactor.yOffset)})

        var lineConnections: [LineConnection] = []
        lineConnections.append(LineConnection(
          color: DefaultConstants.faceOvalConnectionsColor,
          lines: FaceLandmarker.faceOvalConnections()
            .map({ connection in
            let start = dots[Int(connection.start)]
            let end = dots[Int(connection.end)]
            return Line(from: start, to: end)
            })))
        lineConnections.append(LineConnection(
          color: DefaultConstants.rightEyebrowConnectionsColor,
          lines: FaceLandmarker.rightEyebrowConnections()
            .map({ connection in
            let start = dots[Int(connection.start)]
            let end = dots[Int(connection.end)]
              return Line(from: start, to: end)
          })))
        lineConnections.append(LineConnection(
          color: DefaultConstants.leftEyebrowConnectionsColor,
          lines: FaceLandmarker.leftEyebrowConnections()
          .map({ connection in
          let start = dots[Int(connection.start)]
          let end = dots[Int(connection.end)]
            return Line(from: start, to: end)
        })))
        lineConnections.append(LineConnection(
          color: DefaultConstants.rightEyeConnectionsColor,
          lines: FaceLandmarker.rightEyeConnections()
          .map({ connection in
          let start = dots[Int(connection.start)]
          let end = dots[Int(connection.end)]
            return Line(from: start, to: end)
        })))
        lineConnections.append(LineConnection(
          color: DefaultConstants.leftEyeConnectionsColor,
          lines: FaceLandmarker.leftEyeConnections()
          .map({ connection in
          let start = dots[Int(connection.start)]
          let end = dots[Int(connection.end)]
            return Line(from: start, to: end)
        })))
        lineConnections.append(LineConnection(
          color: DefaultConstants.lipsConnectionsColor,
          lines: FaceLandmarker.lipsConnections()
          .map({ connection in
          let start = dots[Int(connection.start)]
          let end = dots[Int(connection.end)]
            return Line(from: start, to: end)
        })))

        faceOverlays.append(FaceOverlay(dots: dots, lineConnections: lineConnections))
      }

      return faceOverlays
    }
}
