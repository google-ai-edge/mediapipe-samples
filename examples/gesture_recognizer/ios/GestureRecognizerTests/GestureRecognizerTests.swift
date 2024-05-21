
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
    gestures: [[ResultCategory(index: -1, score: 0.7283777, categoryName: "Thumb_Up", displayName: "")]],
    handedness: [],
    landmarks: [[
      NormalizedLandmark(x: 0.6129676, y: 0.70157504, z: -4.5833377e-07, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.6159242, y: 0.5730554, z: -0.04404007, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.58462656, y: 0.45141116, z: -0.066422015, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.54258853, y: 0.3550938, z: -0.08355088, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5299578, y: 0.27741316, z: -0.09152996, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.4884828, y: 0.48931584, z: -0.03891499, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.42707062, y: 0.5070781, z: -0.082204446, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.48659548, y: 0.52944756, z: -0.09566363, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5170652, y: 0.5180234, z: -0.097826585, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.47752064, y: 0.5746913, z: -0.030233975, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.42322388, y: 0.58384126, z: -0.06978146, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5008309, y: 0.6011655, z: -0.062682286, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5144273, y: 0.57651, z: -0.048970204, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.47189528, y: 0.65008116, z: -0.029931678, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.4212282, y: 0.6498341, z: -0.071003094, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.49262476, y: 0.65974927, z: -0.04700193, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5128528, y: 0.63937056, z: -0.020825379, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.47315174, y: 0.721069, z: -0.033766963, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.4348337, y: 0.7294104, z: -0.058631197, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.48701334, y: 0.7236482, z: -0.04348786, visibility: nil, presence: nil),
      NormalizedLandmark(x: 0.5102773, y: 0.7046261, z: -0.02522209, visibility: nil, presence: nil)
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
        minTrackingConfidence: GestureRecognizerTests.minTrackingConfidence,
        delegate: .CPU)
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
