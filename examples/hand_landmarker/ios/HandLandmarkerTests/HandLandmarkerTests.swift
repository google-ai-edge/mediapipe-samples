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
@testable import HandLandmarker
import MediaPipeTasksVision

final class HandLandmarkerTests: XCTestCase {

  static let modelPath: String = Bundle.main.path(forResource: "hand_landmarker", ofType: "task")!

  static let minHandDetectionConfidence: Float = 0.3
  static let minHandPresenceConfidence: Float = 0.3
  static let minTrackingConfidence: Float = 0.3

  static let testImage = UIImage(
    named: "thumbs-up.png",
    in:Bundle(for: HandLandmarkerTests.self),
    compatibleWith: nil)!

  static let results: [[NormalizedLandmark]] = [[
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
  ]]

  func handLandmarkerWithModelPath(
    _ modelPath: String,
    minHandDetectionConfidence: Float,
    minHandPresenceConfidence: Float,
    minTrackingConfidence: Float) throws -> HandLandmarkerService {
      let handLandmarkerService = HandLandmarkerService.stillImageLandmarkerService(
        modelPath: modelPath,
        numHands: 1,
        minHandDetectionConfidence: HandLandmarkerTests.minHandDetectionConfidence,
        minHandPresenceConfidence: HandLandmarkerTests.minHandPresenceConfidence,
        minTrackingConfidence: HandLandmarkerTests.minTrackingConfidence,
        delegate: .CPU)!
    return handLandmarkerService
  }

  func assertHandLandmarkResultHasOneHead(
    _ handLandmarkerResult: HandLandmarkerResult
  ) {
    XCTAssertEqual(handLandmarkerResult.landmarks.count, 1)
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

  func assertResultsForHandLandmark(
    image: UIImage,
    using handLandmarkerService: HandLandmarkerService,
    equals expectedLandmarks: [[NormalizedLandmark]]
  ) throws {
    let handLandmarkerResult =
    try XCTUnwrap(handLandmarkerService.detect(image: image)!.handLandmarkerResults[0])
    for landmark in handLandmarkerResult.landmarks[0] {
      print("NormalizedLandmark(x: \(landmark.x), y: \(landmark.y), z: \(landmark.z), visibility: nil, presence: nil),")
    }
    assertHandLandmarkResultHasOneHead(handLandmarkerResult)
    assertEqualLandmarkArrays(landmarkArray: handLandmarkerResult.landmarks, expectedLandmarkArray: expectedLandmarks)
  }
  func testHandLandmarkSucceeds() throws {

    let handLandmarkerService = try handLandmarkerWithModelPath(
      HandLandmarkerTests.modelPath,
      minHandDetectionConfidence: HandLandmarkerTests.minHandDetectionConfidence,
      minHandPresenceConfidence: HandLandmarkerTests.minHandPresenceConfidence,
      minTrackingConfidence: HandLandmarkerTests.minTrackingConfidence)
    try assertResultsForHandLandmark(
      image: HandLandmarkerTests.testImage,
      using: handLandmarkerService,
      equals: HandLandmarkerTests.results)
  }

}
