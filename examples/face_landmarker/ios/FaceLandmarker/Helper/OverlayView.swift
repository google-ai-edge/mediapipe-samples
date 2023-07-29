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

/**
 This structure holds the display parameters for the overlay to be drawon on a detected object.
 */
struct ObjectOverlay {
  let dots: [CGPoint]
  let lines: [Line]
}

/// Custom view to visualize the face landmarks result on top of the input image.
class OverlayView: UIView {

  var objectOverlays: [ObjectOverlay] = []
  private let lineWidth: CGFloat = 3
  private let pointRadius: CGFloat = 3
  private let lineColor = UIColor(red: 0, green: 127/255.0, blue: 139/255.0, alpha: 1)
  private let pointColor = UIColor.yellow

  override func draw(_ rect: CGRect) {
    for objectOverlay in objectOverlays {
      drawDots(objectOverlay.dots)
      drawLines(objectOverlay.lines)
    }
  }

  /**
   This method takes the landmarks, translates the points and lines to the current view, draws to the  of inferences.
   */
  func drawLandmarks(_ landmarks: [[NormalizedLandmark]], orientation: UIImage.Orientation, withImageSize imageSize: CGSize) {
    guard !landmarks.isEmpty else {
      objectOverlays = []
      setNeedsDisplay()
      return
    }

    var viewWidth = bounds.size.width
    var viewHeight = bounds.size.height
    var originX: CGFloat = 0
    var originY: CGFloat = 0

    if viewWidth / viewHeight > imageSize.width / imageSize.height {
      viewHeight = imageSize.height / imageSize.width  * bounds.size.width
      originY = (bounds.size.height - viewHeight) / 2
    } else {
      viewWidth = imageSize.width / imageSize.height * bounds.size.height
      originX = (bounds.size.width - viewWidth) / 2
    }

    var objectOverlays: [ObjectOverlay] = []

    for landmark in landmarks {
      var transformedLandmark: [CGPoint]!

      switch orientation {
      case .left:
        transformedLandmark = landmark.map({CGPoint(x: CGFloat($0.y), y: 1 - CGFloat($0.x))})
      case .right:
        transformedLandmark = landmark.map({CGPoint(x: 1 - CGFloat($0.y), y: CGFloat($0.x))})
      default:
        transformedLandmark = landmark.map({CGPoint(x: CGFloat($0.x), y: CGFloat($0.y))})
      }

      let dots: [CGPoint] = transformedLandmark.map({CGPoint(x: CGFloat($0.x) * viewWidth + originX, y: CGFloat($0.y) * viewHeight + originY)})
      var lines: [Line] = FaceLandmarker.faceOvalConnections()
        .map({ connection in
        let start = transformedLandmark[Int(connection.start)]
        let end = transformedLandmark[Int(connection.end)]
          return Line(from: CGPoint(x: CGFloat(start.x) * viewWidth + originX, y: CGFloat(start.y) * viewHeight + originY),
                      to: CGPoint(x: CGFloat(end.x) * viewWidth + originX, y: CGFloat(end.y) * viewHeight + originY))
        })
      lines.append(contentsOf: FaceLandmarker.rightEyeConnections()
        .map({ connection in
        let start = transformedLandmark[Int(connection.start)]
        let end = transformedLandmark[Int(connection.end)]
          return Line(from: CGPoint(x: CGFloat(start.x) * viewWidth + originX, y: CGFloat(start.y) * viewHeight + originY),
                      to: CGPoint(x: CGFloat(end.x) * viewWidth + originX, y: CGFloat(end.y) * viewHeight + originY))
      }))
      lines.append(contentsOf: FaceLandmarker.leftEyeConnections()
        .map({ connection in
        let start = transformedLandmark[Int(connection.start)]
        let end = transformedLandmark[Int(connection.end)]
          return Line(from: CGPoint(x: CGFloat(start.x) * viewWidth + originX, y: CGFloat(start.y) * viewHeight + originY),
                      to: CGPoint(x: CGFloat(end.x) * viewWidth + originX, y: CGFloat(end.y) * viewHeight + originY))
      }))
      lines.append(contentsOf: FaceLandmarker.lipsConnections()
        .map({ connection in
        let start = transformedLandmark[Int(connection.start)]
        let end = transformedLandmark[Int(connection.end)]
          return Line(from: CGPoint(x: CGFloat(start.x) * viewWidth + originX, y: CGFloat(start.y) * viewHeight + originY),
                      to: CGPoint(x: CGFloat(end.x) * viewWidth + originX, y: CGFloat(end.y) * viewHeight + originY))
      }))
      objectOverlays.append(ObjectOverlay(dots: dots, lines: lines))
    }

    self.objectOverlays = objectOverlays
    setNeedsDisplay()
  }

  private func drawDots(_ dots: [CGPoint]) {
    for dot in dots {
      let dotRect = CGRect(
        x: CGFloat(dot.x) - pointRadius / 2, y: CGFloat(dot.y) - pointRadius / 2,
        width: pointRadius, height: pointRadius)
      let path = UIBezierPath(ovalIn: dotRect)
      pointColor.setFill()
      path.fill()
    }
  }

  private func drawLines(_ lines: [Line]) {
    let path = UIBezierPath()
    for line in lines {
      path.move(to: line.from)
      path.addLine(to: line.to)
    }
    path.lineWidth = lineWidth
    lineColor.setStroke()
    path.stroke()
  }
}
