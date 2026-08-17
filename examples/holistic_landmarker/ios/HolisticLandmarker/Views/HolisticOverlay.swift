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
 This structure holds the display parameters for the overlay to be drawn on detected holistic landmarks.
 */
struct HolisticOverlay {
  let dots: [CGPoint]
  let lineConnections: [LineConnection]
}

/// Custom view to visualize the holistic landmarker result on top of the input image.
class OverlayView: UIView {

  var holisticOverlays: [HolisticOverlay] = []

  private var contentImageSize: CGSize = CGSize.zero
  var imageContentMode: UIView.ContentMode = .scaleAspectFit
  private var orientation = UIDeviceOrientation.portrait
  private var edgeOffset: CGFloat = 0.0

  // MARK: Public Functions
  func draw(
    holisticOverlays: [HolisticOverlay],
    inBoundsOfContentImageOfSize imageSize: CGSize,
    edgeOffset: CGFloat = 0.0,
    imageContentMode: UIView.ContentMode
  ) {
    contentImageSize = imageSize
    self.edgeOffset = edgeOffset
    self.holisticOverlays = holisticOverlays
    self.imageContentMode = imageContentMode
    orientation = UIDevice.current.orientation
    self.setNeedsDisplay()
  }

  func redrawHolisticOverlays(forNewDeviceOrientation deviceOrientation: UIDeviceOrientation) {
    orientation = deviceOrientation
    self.setNeedsDisplay()
  }

  func clear() {
    holisticOverlays = []
    contentImageSize = CGSize.zero
    imageContentMode = .scaleAspectFit
    orientation = UIDevice.current.orientation
    edgeOffset = 0.0
    setNeedsDisplay()
  }

  override func draw(_ rect: CGRect) {
    for holisticOverlay in holisticOverlays {
      for lineConnection in holisticOverlay.lineConnections {
        drawLines(lineConnection.lines, lineColor: lineConnection.color)
      }
      drawDots(holisticOverlay.dots)
    }
  }

  // MARK: Private Functions
  private func drawDots(_ dots: [CGPoint]) {
    for dot in dots {
      let dotRect = CGRect(
        x: CGFloat(dot.x) - DefaultConstants.pointRadius / 2,
        y: CGFloat(dot.y) - DefaultConstants.pointRadius / 2,
        width: DefaultConstants.pointRadius,
        height: DefaultConstants.pointRadius
      )
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
    withContentMode contentMode: UIView.ContentMode
  ) -> (xOffset: CGFloat, yOffset: CGFloat, scaleFactor: Double) {
    guard imageSize.width > 0 && imageSize.height > 0 else {
      return (0, 0, 1.0)
    }

    let widthScale = viewSize.width / imageSize.width
    let heightScale = viewSize.height / imageSize.height

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
      height: imageSize.height * scaleFactor
    )
    let xOffset = (viewSize.width - scaledSize.width) / 2
    let yOffset = (viewSize.height - scaledSize.height) / 2

    return (xOffset, yOffset, scaleFactor)
  }

  // Helper to get holistic overlays from a single HolisticLandmarkerResult
  static func holisticOverlays(
    fromHolisticResult result: HolisticLandmarkerResult,
    inferredOnImageOfSize originalImageSize: CGSize,
    overlayViewSize: CGSize,
    imageContentMode: UIView.ContentMode,
    andOrientation orientation: UIImage.Orientation
  ) -> [HolisticOverlay] {

    let offsetsAndScaleFactor = OverlayView.offsetsAndScaleFactor(
      forImageOfSize: originalImageSize,
      tobeDrawnInViewOfSize: overlayViewSize,
      withContentMode: imageContentMode)

    func transformLandmarks(_ landmarks: [NormalizedLandmark]) -> [CGPoint] {
      var transformed: [CGPoint]
      switch orientation {
      case .left:
        transformed = landmarks.map { CGPoint(x: CGFloat($0.y), y: 1 - CGFloat($0.x)) }
      case .right:
        transformed = landmarks.map { CGPoint(x: 1 - CGFloat($0.y), y: CGFloat($0.x)) }
      default:
        transformed = landmarks.map { CGPoint(x: CGFloat($0.x), y: CGFloat($0.y)) }
      }
      return transformed.map {
        CGPoint(
          x: CGFloat($0.x) * originalImageSize.width * offsetsAndScaleFactor.scaleFactor + offsetsAndScaleFactor.xOffset,
          y: CGFloat($0.y) * originalImageSize.height * offsetsAndScaleFactor.scaleFactor + offsetsAndScaleFactor.yOffset
        )
      }
    }

    var allDots: [CGPoint] = []
    var allLineConnections: [LineConnection] = []

    // 1. Pose landmarks
    if !result.poseLandmarks.isEmpty {
      let poseDots = transformLandmarks(result.poseLandmarks)
      allDots.append(contentsOf: poseDots)
      let poseLines = PoseLandmarker.poseLandmarks.compactMap { connection -> Line? in
        let startIdx = Int(connection.start)
        let endIdx = Int(connection.end)
        guard startIdx < poseDots.count && endIdx < poseDots.count else { return nil }
        return Line(from: poseDots[startIdx], to: poseDots[endIdx])
      }
      allLineConnections.append(LineConnection(color: DefaultConstants.poseLineColor, lines: poseLines))
    }

    // 2. Face landmarks
    if !result.faceLandmarks.isEmpty {
      let faceDots = transformLandmarks(result.faceLandmarks)
      allDots.append(contentsOf: faceDots)
      let faceConnections = FaceLandmarker.faceOvalConnections() +
        FaceLandmarker.leftEyeConnections() +
        FaceLandmarker.rightEyeConnections() +
        FaceLandmarker.leftEyebrowConnections() +
        FaceLandmarker.rightEyebrowConnections() +
        FaceLandmarker.lipsConnections()

      let faceLines = faceConnections.compactMap { connection -> Line? in
        let startIdx = Int(connection.start)
        let endIdx = Int(connection.end)
        guard startIdx < faceDots.count && endIdx < faceDots.count else { return nil }
        return Line(from: faceDots[startIdx], to: faceDots[endIdx])
      }
      allLineConnections.append(LineConnection(color: DefaultConstants.faceLineColor, lines: faceLines))
    }

    // 3. Left Hand landmarks
    if !result.leftHandLandmarks.isEmpty {
      let leftHandDots = transformLandmarks(result.leftHandLandmarks)
      allDots.append(contentsOf: leftHandDots)
      let leftHandLines = HandLandmarker.handConnections.compactMap { connection -> Line? in
        let startIdx = Int(connection.start)
        let endIdx = Int(connection.end)
        guard startIdx < leftHandDots.count && endIdx < leftHandDots.count else { return nil }
        return Line(from: leftHandDots[startIdx], to: leftHandDots[endIdx])
      }
      allLineConnections.append(LineConnection(color: DefaultConstants.leftHandLineColor, lines: leftHandLines))
    }

    // 4. Right Hand landmarks
    if !result.rightHandLandmarks.isEmpty {
      let rightHandDots = transformLandmarks(result.rightHandLandmarks)
      allDots.append(contentsOf: rightHandDots)
      let rightHandLines = HandLandmarker.handConnections.compactMap { connection -> Line? in
        let startIdx = Int(connection.start)
        let endIdx = Int(connection.end)
        guard startIdx < rightHandDots.count && endIdx < rightHandDots.count else { return nil }
        return Line(from: rightHandDots[startIdx], to: rightHandDots[endIdx])
      }
      allLineConnections.append(LineConnection(color: DefaultConstants.rightHandLineColor, lines: rightHandLines))
    }

    guard !allDots.isEmpty else { return [] }
    return [HolisticOverlay(dots: allDots, lineConnections: allLineConnections)]
  }
}
