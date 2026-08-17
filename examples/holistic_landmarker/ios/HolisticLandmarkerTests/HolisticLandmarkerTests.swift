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

import XCTest
@testable import HolisticLandmarker
import MediaPipeTasksVision

final class HolisticLandmarkerTests: XCTestCase {

  static let model = Model.holistic_landmarker
  static let minFaceDetectionConfidence: Float = 0.5
  static let minFacePresenceConfidence: Float = 0.5
  static let minPoseDetectionConfidence: Float = 0.5
  static let minPosePresenceConfidence: Float = 0.5
  static let minHandLandmarksConfidence: Float = 0.5

  static var testImage: UIImage? {
    return UIImage(
      named: "business-person.png",
      in: Bundle(for: HolisticLandmarkerTests.self),
      compatibleWith: nil)
  }

  func testHolisticLandmarkerCPUInitialization() throws {
    let service = HolisticLandmarkerService.stillImageLandmarkerService(
      modelPath: HolisticLandmarkerTests.model.modelPath,
      minFaceDetectionConfidence: HolisticLandmarkerTests.minFaceDetectionConfidence,
      minFacePresenceConfidence: HolisticLandmarkerTests.minFacePresenceConfidence,
      minPoseDetectionConfidence: HolisticLandmarkerTests.minPoseDetectionConfidence,
      minPosePresenceConfidence: HolisticLandmarkerTests.minPosePresenceConfidence,
      minHandLandmarksConfidence: HolisticLandmarkerTests.minHandLandmarksConfidence,
      delegate: .CPU)

    XCTAssertNotNil(service)
    XCTAssertNotNil(service?.holisticLandmarker)
  }

  func testHolisticLandmarkerGPUInitialization() throws {
    let service = HolisticLandmarkerService.stillImageLandmarkerService(
      modelPath: HolisticLandmarkerTests.model.modelPath,
      minFaceDetectionConfidence: HolisticLandmarkerTests.minFaceDetectionConfidence,
      minFacePresenceConfidence: HolisticLandmarkerTests.minFacePresenceConfidence,
      minPoseDetectionConfidence: HolisticLandmarkerTests.minPoseDetectionConfidence,
      minPosePresenceConfidence: HolisticLandmarkerTests.minPosePresenceConfidence,
      minHandLandmarksConfidence: HolisticLandmarkerTests.minHandLandmarksConfidence,
      delegate: .GPU)

    XCTAssertNotNil(service)
    XCTAssertNotNil(service?.holisticLandmarker)
  }

  func testHolisticLandmarkerDetectionOnImage() throws {
    let service = HolisticLandmarkerService.stillImageLandmarkerService(
      modelPath: HolisticLandmarkerTests.model.modelPath,
      minFaceDetectionConfidence: DefaultConstants.minFaceDetectionConfidence,
      minFacePresenceConfidence: DefaultConstants.minFacePresenceConfidence,
      minPoseDetectionConfidence: DefaultConstants.minPoseDetectionConfidence,
      minPosePresenceConfidence: DefaultConstants.minPosePresenceConfidence,
      minHandLandmarksConfidence: DefaultConstants.minHandLandmarksConfidence,
      delegate: .CPU)

    XCTAssertNotNil(service)
    guard let image = HolisticLandmarkerTests.testImage else {
      XCTFail("Failed to load test image")
      return
    }

    let resultBundle = service?.detect(image: image)
    XCTAssertNotNil(resultBundle, "ResultBundle is nil")
    let holisticResult = resultBundle?.holisticLandmarkerResults.first as? HolisticLandmarkerResult
    XCTAssertNotNil(holisticResult, "HolisticLandmarkerResult is nil")

    let poseCount = holisticResult?.poseLandmarks.count ?? 0
    let faceCount = holisticResult?.faceLandmarks.count ?? 0

    XCTAssertGreaterThan(poseCount, 0, "Pose landmarks should not be empty")
    XCTAssertGreaterThan(faceCount, 0, "Face landmarks should not be empty")

    let overlays = OverlayView.holisticOverlays(
      fromHolisticResult: holisticResult!,
      inferredOnImageOfSize: image.size,
      overlayViewSize: CGSize(width: 393, height: 759),
      imageContentMode: .scaleAspectFit,
      andOrientation: image.imageOrientation)

    XCTAssertGreaterThan(overlays.count, 0, "Overlays should not be empty")
    if let firstOverlay = overlays.first {
      XCTAssertGreaterThan(firstOverlay.dots.count, 0, "Overlay dots should not be empty")
    }
  }

  func testMediaLibraryViewControllerImageWorkflow() throws {
    let storyboard = UIStoryboard(name: "Main", bundle: Bundle(for: RootViewController.self))
    guard let mediaVC = storyboard.instantiateViewController(withIdentifier: "MEDIA_LIBRARY_VIEW_CONTROLLER") as? MediaLibraryViewController else {
      XCTFail("Failed to instantiate MediaLibraryViewController")
      return
    }
    let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 393, height: 852))
    window.rootViewController = mediaVC
    window.makeKeyAndVisible()
    mediaVC.loadViewIfNeeded()
    mediaVC.view.layoutIfNeeded()

    guard let image = HolisticLandmarkerTests.testImage else {
      XCTFail("Could not load test image")
      return
    }

    let picker = UIImagePickerController()
    mediaVC.imagePickerController(picker, didFinishPickingMediaWithInfo: [
      .mediaType: "public.image",
      .originalImage: image
    ])

    let exp = expectation(description: "Inference completed")
    var checkCount = 0
    Timer.scheduledTimer(withTimeInterval: 0.1, repeats: true) { timer in
      checkCount += 1
      if !mediaVC.overlayView.holisticOverlays.isEmpty || checkCount > 50 {
        timer.invalidate()
        exp.fulfill()
      }
    }
    wait(for: [exp], timeout: 10.0)

    XCTAssertFalse(mediaVC.overlayView.holisticOverlays.isEmpty, "OverlayView should have overlays")

    let overlayRenderer = UIGraphicsImageRenderer(bounds: mediaVC.overlayView.bounds)
    let overlaySnapshot = overlayRenderer.image { ctx in
      mediaVC.overlayView.layer.render(in: ctx.cgContext)
    }
    guard let cgImage = overlaySnapshot.cgImage else {
      XCTFail("Failed to get CGImage of overlay")
      return
    }
    let width = cgImage.width
    let height = cgImage.height
    var rawData = [UInt8](repeating: 0, count: width * height * 4)
    let colorSpace = CGColorSpaceCreateDeviceRGB()
    let bitmapInfo = CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue
    let context = CGContext(data: &rawData, width: width, height: height, bitsPerComponent: 8, bytesPerRow: width * 4, space: colorSpace, bitmapInfo: bitmapInfo)
    context?.draw(cgImage, in: CGRect(x: 0, y: 0, width: width, height: height))

    var nonTransparentPixels = 0
    for i in stride(from: 3, to: rawData.count, by: 4) {
      if rawData[i] > 0 {
        nonTransparentPixels += 1
      }
    }
    XCTAssertGreaterThan(nonTransparentPixels, 50, "Overlay should have drawn landmark dots and lines on screen")
  }

  func testHolisticLandmarkerDetectionOnOrientedImage() throws {
    let service = HolisticLandmarkerService.stillImageLandmarkerService(
      modelPath: HolisticLandmarkerTests.model.modelPath,
      minFaceDetectionConfidence: DefaultConstants.minFaceDetectionConfidence,
      minFacePresenceConfidence: DefaultConstants.minFacePresenceConfidence,
      minPoseDetectionConfidence: DefaultConstants.minPoseDetectionConfidence,
      minPosePresenceConfidence: DefaultConstants.minPosePresenceConfidence,
      minHandLandmarksConfidence: DefaultConstants.minHandLandmarksConfidence,
      delegate: .CPU)

    XCTAssertNotNil(service)
    guard let baseImage = HolisticLandmarkerTests.testImage,
          let cgImage = baseImage.cgImage else {
      XCTFail("Failed to load test image")
      return
    }

    let rightImage = UIImage(cgImage: cgImage, scale: 1.0, orientation: .right)
    let resultBundle = service?.detect(image: rightImage)
    XCTAssertNotNil(resultBundle, "ResultBundle should not be nil for oriented image")
    let holisticResult = resultBundle?.holisticLandmarkerResults.first as? HolisticLandmarkerResult
    XCTAssertNotNil(holisticResult, "HolisticLandmarkerResult should not be nil")

    let overlays = OverlayView.holisticOverlays(
      fromHolisticResult: holisticResult!,
      inferredOnImageOfSize: rightImage.size,
      overlayViewSize: CGSize(width: 393, height: 759),
      imageContentMode: .scaleAspectFit,
      andOrientation: rightImage.imageOrientation)

    XCTAssertGreaterThan(overlays.count, 0, "Overlays should not be empty")
  }
}
