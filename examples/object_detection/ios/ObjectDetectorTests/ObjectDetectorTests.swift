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

import XCTest
@testable import ObjectDetector
import MediaPipeTasksVision

final class ObjectDetectorTests: XCTestCase {
  static let efficientdetLite0 = Model.efficientdetLite0
  static let efficientdetLite2 = Model.efficientdetLite2

  static let testImage = UIImage(named: "coupleanddog.jpeg",
                                 in: Bundle(for: ObjectDetectorTests.self),
                                 compatibleWith: nil)!

  static let efficientnetLite0Results: [Detection] = [
    Detection(
      categories: [ResultCategory(index: -1, score: 0.941339, categoryName: "person", displayName: nil)],
      boundingBox: CGRect(x: 213.0, y: 11.0, width: 120.0, height: 261.0),
      keypoints: nil),
    Detection(
      categories: [ResultCategory(index: -1, score: 0.77810854, categoryName: "dog", displayName: nil)],
      boundingBox: CGRect(x: 67.0, y: 254.0, width: 56.0, height: 76.0),
      keypoints: nil),
    Detection(
      categories: [ResultCategory(index: -1, score: 0.6601211, categoryName: "person", displayName: nil)],
      boundingBox: CGRect(x: 144.0, y: 18.0, width: 82.0, height: 242.0),
      keypoints: nil)
  ]

  static let efficientnetLite2Results: [Detection] = [
    Detection(
      categories: [ResultCategory(index: -1, score: 0.92731416, categoryName: "dog", displayName: nil)],
      boundingBox: CGRect(x: 71.0, y: 254.0, width: 52.0, height: 74.0),
      keypoints: nil),
    Detection(
      categories: [ResultCategory(index: -1, score: 0.9009373, categoryName: "person", displayName: nil)],
      boundingBox: CGRect(x: 207.0, y: 7.0, width: 126.0, height: 264.0),
      keypoints: nil),
    Detection(
      categories: [ResultCategory(index: -1, score: 0.8202129, categoryName: "person", displayName: nil)],
      boundingBox: CGRect(x: 147.0, y: 13.0, width: 81.0, height: 255.0),
      keypoints: nil)
  ]

  func objectDetectorWithModel(
    _ model: Model
  ) throws -> ObjectDetectorService {
    let objectDetectorHelper = ObjectDetectorService.stillImageDetectorService(
      model: model,
      maxResults: 3,
      scoreThreshold: 0,
      delegate: .CPU
    )
    return try XCTUnwrap(objectDetectorHelper)
  }

  func assertObjecDetectionResultHasOneHead(
    _ objectDetectorResult: ObjectDetectorResult
  ) {
    XCTAssertEqual(objectDetectorResult.detections.count, 3)
  }

  func assertDetectionAreEqual(
    detection: Detection,
    expectedDetection: Detection,
    indexInDetectionList: Int
  ) {
    XCTAssertEqual(
      detection.keypoints,
      expectedDetection.keypoints,
      String(
        format: """
                    detection[%d].keypoints and expectedDetection[%d].keypoints are not equal.
              """, indexInDetectionList))
    XCTAssertEqual(
      detection.boundingBox,
      expectedDetection.boundingBox,
      String(
        format: """
                    detection[%d].boundingBox and expectedDetection[%d].boundingBox are not equal.
              """, indexInDetectionList))
    for (category, expectedCategory) in zip(detection.categories, expectedDetection.categories) {
      XCTAssertEqual(
        category.index,
        expectedCategory.index,
        String(
          format: """
                category[%d].index and expectedCategory[%d].index are not equal.
                """, indexInDetectionList))
      XCTAssertEqual(
        category.score,
        expectedCategory.score,
        accuracy: 1e-3,
        String(
          format: """
                category[%d].score and expectedCategory[%d].score are not equal.
                """, indexInDetectionList))
      XCTAssertEqual(
        category.categoryName,
        expectedCategory.categoryName,
        String(
          format: """
                category[%d].categoryName and expectedCategory[%d].categoryName are \
                not equal.
                """, indexInDetectionList))
      XCTAssertEqual(
        category.displayName,
        expectedCategory.displayName,
        String(
          format: """
                category[%d].displayName and expectedCategory[%d].displayName are \
                not equal.
                """, indexInDetectionList))
    }
  }

  func assertEqualDetectionArrays(
    detectionArray: [Detection],
    expecteddetectionArray: [Detection]
  ) {
    XCTAssertEqual(
      detectionArray.count,
      expecteddetectionArray.count)

    for (index, (detection, expectedDetection)) in zip(detectionArray, expecteddetectionArray)
      .enumerated()
    {
      assertDetectionAreEqual(
        detection: detection,
        expectedDetection: expectedDetection,
        indexInDetectionList: index)
    }
  }

  func assertResultsForDetection(
    image: UIImage,
    using objectDetector: ObjectDetectorService,
    equals expectedDetections: [Detection]
  ) throws {
    let objectDetectorResult =
    try XCTUnwrap(
      objectDetector.detect(image: image)!.objectDetectorResults[0])
    assertObjecDetectionResultHasOneHead(objectDetectorResult)
    assertEqualDetectionArrays(
      detectionArray: objectDetectorResult.detections,
      expecteddetectionArray: expectedDetections)
  }
  func testDetectionWithEfficientnetLite0Succeeds() throws {

    let objectDetector = try objectDetectorWithModel(ObjectDetectorTests.efficientdetLite0)
    try assertResultsForDetection(
      image: ObjectDetectorTests.testImage,
      using: objectDetector,
      equals: ObjectDetectorTests.efficientnetLite0Results)
  }

  func testDetectionWithEfficientnetLite2Succeeds() throws {

    let objectDetector = try objectDetectorWithModel(ObjectDetectorTests.efficientdetLite2)
    try assertResultsForDetection(
      image: ObjectDetectorTests.testImage,
      using: objectDetector,
      equals: ObjectDetectorTests.efficientnetLite2Results)
  }
}
