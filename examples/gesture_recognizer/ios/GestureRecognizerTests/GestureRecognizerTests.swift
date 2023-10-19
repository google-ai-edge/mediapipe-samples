
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
@testable import GestureRecognizer
import MediaPipeTasksVision

final class GestureRecognizerTests: XCTestCase {

  static let modelPath: String = Bundle.main.path(forResource: "gesture_recognizer", ofType: "task")!

  static let minHandDetectionConfidence: Float = 0.3
  static let minHandPresenceConfidence: Float = 0.3
  static let minTrackingConfidence: Float = 0.3

  static let testImage = UIImage(
    named: "thumbs-up.png",
    in:Bundle(for: GestureRecognizerTests.self),
    compatibleWith: nil)!

  static let result = GestureRecognizerResult(
    gestures: [[ResultCategory(index: -1, score: 0.74601436, categoryName: "Thumb_Up", displayName: "")]],
    handedness: [],
    landmarks: [[
      NormalizedLandmark(x: 0.6146113, y: 0.71075666, z: -4.1557226e-07, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.6142792, y: 0.57649153, z: -0.040831544, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5836266, y: 0.4429407, z: -0.059525516, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5389037, y: 0.33637148, z: -0.07342299, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.536148, y: 0.25158498, z: -0.07771388, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.4898765, y: 0.4913109, z: -0.030454714, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.4276508, y: 0.50301707, z: -0.06859867, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.49330515, y: 0.52595127, z: -0.0773961, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.52693504, y: 0.5121813, z: -0.07744958, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.4763346, y: 0.5743718, z: -0.023844246, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.42159313, y: 0.58094376, z: -0.06347593, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.50296295, y: 0.60153985, z: -0.057907313, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.52059495, y: 0.57536906, z: -0.046426427, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.47042432, y: 0.6498483, z: -0.025004275, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.42147171, y: 0.65280235, z: -0.069050804, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.49437872, y: 0.66357565, z: -0.046906527, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5176527, y: 0.6408466, z: -0.022207312, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.4691668, y: 0.7234682, z: -0.029635455, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.43116334, y: 0.7330426, z: -0.056126874, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.48526073, y: 0.7278307, z: -0.041881826, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5117951, y: 0.70887417, z: -0.024859443, visibility: nil, presence: nil)
    ]],
    worldLandmarks: [],
    timestampInMilliseconds: 0)

  func gestureRecognizerWithModelPath(
    _ modelPath: String,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float) throws -> GestureRecognizerService {
      let gestureRecognizerService = GestureRecognizerService.stillImageGestureRecognizerService(
        modelPath: modelPath,
        minHandDetectionConfidence: GestureRecognizerTests.minHandDetectionConfidence,
        minHandPresenceConfidence: GestureRecognizerTests.minHandPresenceConfidence,
        minTrackingConfidence: GestureRecognizerTests.minTrackingConfidence)
    return gestureRecognizerService!
  }

  func assertGestureRecognizerResultHasOneHead(
    _ gestureRecognizerResult: GestureRecognizerResult
  ) {
    XCTAssertEqual(gestureRecognizerResult.landmarks.count, 1)
    XCTAssertEqual(gestureRecognizerResult.gestures.count, 1)
  }

  func assertGesturesAreEqual(
    gestures: ResultCategory,
    expectedGestures: ResultCategory
  ) {
    XCTAssertEqual(gestures.categoryName, expectedGestures.categoryName)
    XCTAssertEqual(gestures.index, expectedGestures.index)
    XCTAssertEqual(gestures.score, expectedGestures.score)
  }

  func assertLandmarkAreEqual(
    landmark: NormalizedLandmark,
    expectedLandmark: NormalizedLandmark,
    indexInLandmarkList: Int
  ) {
    XCTAssertEqual(
      landmark.x,
      expectedLandmark.x,
      String(
        format: """
                          landmark[%d].x and expectedLandmark[%d].x are not equal.
              """, indexInLandmarkList))
    XCTAssertEqual(
      landmark.y,
      expectedLandmark.y,
      String(
        format: """
                          landmark[%d].y and expectedLandmark[%d].y are not equal.
              """, indexInLandmarkList))
    XCTAssertEqual(
      landmark.z,
      expectedLandmark.z,
      String(
        format: """
                          landmark[%d].z and expectedLandmark[%d].z are not equal.
              """, indexInLandmarkList))
  }

  func assertEqualLandmarkArrays(
    landmarkArray: [[NormalizedLandmark]],
    expectedLandmarkArray: [[NormalizedLandmark]]
  ) {
    XCTAssertEqual(
      landmarkArray.count,
      expectedLandmarkArray.count)

    for (_, (landmarks, expectedLandmarks)) in zip(landmarkArray, expectedLandmarkArray)
      .enumerated()
    {
      XCTAssertEqual(
        landmarks.count,
        expectedLandmarks.count)

      for (index, (landmark, expectedLandmark)) in zip(landmarks, expectedLandmarks)
        .enumerated()
      {
        XCTAssertEqual(
          landmarks.count,
          expectedLandmarks.count)
        assertLandmarkAreEqual(
          landmark: landmark, expectedLandmark: expectedLandmark, indexInLandmarkList: index)
      }
    }
  }

  func assertResultsForGestureRecognizer(
    image: UIImage,
    using gestureRecognizerService: GestureRecognizerService,
    equals expectedResult: GestureRecognizerResult
  ) throws {
    let gestureRecognizerResult =
    try XCTUnwrap(gestureRecognizerService.recognize(image: image)!.gestureRecognizerResults[0])
    assertGestureRecognizerResultHasOneHead(gestureRecognizerResult)
    assertEqualLandmarkArrays(landmarkArray: gestureRecognizerResult.landmarks, expectedLandmarkArray: expectedResult.landmarks)
    assertGesturesAreEqual(gestures: gestureRecognizerResult.gestures[0][0], expectedGestures: expectedResult.gestures[0][0])
  }
  func testHandLandmarkSucceeds() throws {

    let gestureRecognizerService = try gestureRecognizerWithModelPath(
      GestureRecognizerTests.modelPath,
      minHandDetectionConfidence: GestureRecognizerTests.minHandDetectionConfidence,
      minHandPresenceConfidence: GestureRecognizerTests.minHandPresenceConfidence,
      minTrackingConfidence: GestureRecognizerTests.minTrackingConfidence)
    try assertResultsForGestureRecognizer(
      image: GestureRecognizerTests.testImage,
      using: gestureRecognizerService,
      equals: GestureRecognizerTests.result)
  }

}
