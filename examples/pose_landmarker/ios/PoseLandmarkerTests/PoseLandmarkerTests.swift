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
@testable import PoseLandmarker
import MediaPipeTasksVision

final class PoseLandmarkerTests: XCTestCase {

  static let poseLandmarkerLite = Model.pose_landmarker_lite
  static let poseLandmarkerFull = Model.pose_landmarker_full
  static let poseLandmarkerHeavy = Model.pose_landmarker_heavy

  static let modelPath: String = Bundle.main.path(forResource: "pose_landmarker", ofType: "task")!

  static let minPoseDetectionConfidence: Float = 0.3
  static let minPosePresenceConfidence: Float = 0.3
  static let minTrackingConfidence: Float = 0.3
  static let delegate: PoseLandmarkerDelegate = .CPU

  static let testImage = UIImage(
    named: "test_image.jpg",
    in:Bundle(for: PoseLandmarkerTests.self),
    compatibleWith: nil)!

  static let poseLandmarkerLiteResults: [[NormalizedLandmark]] = [[
    NormalizedLandmark(x: 0.34601745, y: 0.19586587, z: -0.22529367, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.35211033, y: 0.16611224, z: -0.21235746, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.35881242, y: 0.16287759, z: -0.21248426, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.36616313, y: 0.15937835, z: -0.21261702, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.337192, y: 0.17275393, z: -0.19953679, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.33328032, y: 0.17422992, z: -0.1996071, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.32976007, y: 0.17530218, z: -0.1996238, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.38498148, y: 0.15925026, z: -0.120410584, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.33059993, y: 0.17779648, z: -0.05878691, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.36467868, y: 0.21389955, z: -0.1892728, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.34454274, y: 0.22190517, z: -0.17202774, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.47295794, y: 0.2886992, z: -0.059382666, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.30886742, y: 0.3090514, z: 0.0058827796, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.5628027, y: 0.40643016, z: -0.06312299, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.25543422, y: 0.4280594, z: 0.0510036, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.6051952, y: 0.50987256, z: -0.115379676, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.25393325, y: 0.5572498, z: 0.0070737577, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.62162346, y: 0.5578593, z: -0.1311044, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.24412906, y: 0.59409046, z: -0.009504357, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.610209, y: 0.56049275, z: -0.15917027, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.26281005, y: 0.5860975, z: -0.03484678, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.6038486, y: 0.5463792, z: -0.12717697, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.26774156, y: 0.57822365, z: -0.00643589, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.4534513, y: 0.6087232, z: 0.0049427804, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.36683425, y: 0.6126064, z: -0.005126736, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.584394, y: 0.46410632, z: -0.29808718, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.25934812, y: 0.4747805, z: -0.22688225, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.7365738, y: 0.6740164, z: -0.38003546, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.2387538, y: 0.76181906, z: -0.3117024, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.73830545, y: 0.73771536, z: -0.3881956, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.27491307, y: 0.8210956, z: -0.32148752, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.7899677, y: 0.68931204, z: -0.51872736, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.20070833, y: 0.8310362, z: -0.44352525, visibility: nil, presence: nil)
  ]]

  static let poseLandmarkerFullResults: [[NormalizedLandmark]] = [[
    NormalizedLandmark(x: 0.3416839, y: 0.200982, z: -0.22110254, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.34953192, y: 0.16807279, z: -0.21883053, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3571448, y: 0.16423804, z: -0.21892402, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.364446, y: 0.16023675, z: -0.21907547, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.33499268, y: 0.17314282, z: -0.19845515, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3322115, y: 0.17321482, z: -0.1984762, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.32940033, y: 0.17339447, z: -0.19849394, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.38362566, y: 0.15792915, z: -0.14539179, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3374006, y: 0.17238992, z: -0.05117146, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.36224046, y: 0.22139329, z: -0.19178002, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3408795, y: 0.22926658, z: -0.16419546, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.47425005, y: 0.28354418, z: -0.05173415, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.30952454, y: 0.30415714, z: -0.020035675, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.56144845, y: 0.3972501, z: -0.07157067, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.25561148, y: 0.43168718, z: -0.086737975, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.608471, y: 0.5055584, z: -0.18784633, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.2567498, y: 0.5518723, z: -0.3042748, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.626544, y: 0.56122243, z: -0.22566915, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.24912634, y: 0.60717106, z: -0.35346568, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.6068496, y: 0.55597067, z: -0.2616287, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.27358305, y: 0.5938352, z: -0.387526, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.59405744, y: 0.5407812, z: -0.20393702, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.28413308, y: 0.5834719, z: -0.3235872, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.4471499, y: 0.6187877, z: 0.020716906, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.35785753, y: 0.6307902, z: -0.020602377, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.5960785, y: 0.4751578, z: -0.109639525, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.26487112, y: 0.48350298, z: -0.2530248, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.7560797, y: 0.67796034, z: -0.19816472, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.23292743, y: 0.7680728, z: -0.24647515, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.75778246, y: 0.73447037, z: -0.20942158, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.25636697, y: 0.82228833, z: -0.24469998, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.8520549, y: 0.72119886, z: -0.30117324, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.21516101, y: 0.8172918, z: -0.3273017, visibility: nil, presence: nil)
  ]]

  static let poseLandmarkerHeavyResults: [[NormalizedLandmark]] = [[
    NormalizedLandmark(x: 0.33756483, y: 0.19285777, z: -0.14782482, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.34506625, y: 0.15905559, z: -0.13017417, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.35300633, y: 0.15507865, z: -0.13044815, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3612153, y: 0.15186775, z: -0.13033727, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.33051312, y: 0.16620281, z: -0.11429841, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3275816, y: 0.16680098, z: -0.11469543, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.32507113, y: 0.16691998, z: -0.11479899, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3856284, y: 0.150128, z: -0.030694468, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.33601594, y: 0.16750926, z: 0.040638406, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.35938036, y: 0.21029776, z: -0.11108218, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.34162658, y: 0.21974057, z: -0.093085155, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.475636, y: 0.2892521, z: 0.0300439, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.30737326, y: 0.30460426, z: 0.08224628, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.55425984, y: 0.39514655, z: -0.027681638, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.25695303, y: 0.42368722, z: 0.0030791494, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.6146809, y: 0.48486835, z: -0.21534929, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.25198436, y: 0.5260541, z: -0.22066888, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.6316862, y: 0.5475055, z: -0.2656277, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.2427506, y: 0.5814971, z: -0.27603522, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.6102867, y: 0.55080867, z: -0.3034234, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.26722288, y: 0.5791314, z: -0.31198892, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.5968287, y: 0.5356534, z: -0.23425798, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.27864194, y: 0.5644246, z: -0.2442666, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.4542159, y: 0.6166205, z: 0.010963887, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.3635073, y: 0.6301084, z: -0.011260088, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.5861436, y: 0.47963881, z: -0.2262676, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.2710452, y: 0.47827148, z: -0.3673478, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.7492332, y: 0.6698844, z: -0.35335556, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.22373599, y: 0.764271, z: -0.41562873, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.7453176, y: 0.7306385, z: -0.37556562, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.23849793, y: 0.82369584, z: -0.42231897, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.8494438, y: 0.7350029, z: -0.5076424, visibility: nil, presence: nil),
    NormalizedLandmark(x: 0.21515703, y: 0.87670517, z: -0.5157361, visibility: nil, presence: nil)
  ]]

  func poseLandmarkerWithModel(
    _ model: Model,
    minPoseDetectionConfidence: Float,
    minPosePresenceConfidence: Float,
    minTrackingConfidence: Float) throws -> PoseLandmarkerService {
      let poseLandmarkerService = PoseLandmarkerService.stillImageLandmarkerService(
        modelPath: model.modelPath,
        numPoses: 1,
        minPoseDetectionConfidence: PoseLandmarkerTests.minPoseDetectionConfidence,
        minPosePresenceConfidence: PoseLandmarkerTests.minPosePresenceConfidence,
        minTrackingConfidence: PoseLandmarkerTests.minTrackingConfidence,
        delegate: PoseLandmarkerTests.delegate)!
    return poseLandmarkerService
  }

  func assertPoseLandmarkResultHasOneHead(
    _ poseLandmarkerResult: PoseLandmarkerResult
  ) {
    XCTAssertEqual(poseLandmarkerResult.landmarks.count, 1)
  }

  func assertLandmarksAreEqual(
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
        assertLandmarksAreEqual(
          landmark: landmark, expectedLandmark: expectedLandmark, indexInLandmarkList: index)
      }
    }
  }

  func assertResultsForPoseLandmark(
    image: UIImage,
    using poseLandmarkerService: PoseLandmarkerService,
    equals expectedLandmarks: [[NormalizedLandmark]]
  ) throws {
    let poseLandmarkerResult =
    try XCTUnwrap(poseLandmarkerService.detect(image: image)!.poseLandmarkerResults[0])
    for landmark in poseLandmarkerResult.landmarks[0] {
      print("NormalizedLandmark(x: \(landmark.x), y: \(landmark.y), z: \(landmark.z), visibility: nil, presence: nil),")
    }
    assertPoseLandmarkResultHasOneHead(poseLandmarkerResult)
    assertEqualLandmarkArrays(landmarkArray: poseLandmarkerResult.landmarks, expectedLandmarkArray: expectedLandmarks)
  }
  func testPoseLandmarkSucceeds() throws {

    let poseLandmarkerServiceWithLiteModel = try poseLandmarkerWithModel(
      PoseLandmarkerTests.poseLandmarkerLite,
      minPoseDetectionConfidence: PoseLandmarkerTests.minPoseDetectionConfidence,
      minPosePresenceConfidence: PoseLandmarkerTests.minPosePresenceConfidence,
      minTrackingConfidence: PoseLandmarkerTests.minTrackingConfidence)
    try assertResultsForPoseLandmark(
      image: PoseLandmarkerTests.testImage,
      using: poseLandmarkerServiceWithLiteModel,
      equals: PoseLandmarkerTests.poseLandmarkerLiteResults)

    let poseLandmarkerServiceWithFullModel = try poseLandmarkerWithModel(
      PoseLandmarkerTests.poseLandmarkerFull,
      minPoseDetectionConfidence: PoseLandmarkerTests.minPoseDetectionConfidence,
      minPosePresenceConfidence: PoseLandmarkerTests.minPosePresenceConfidence,
      minTrackingConfidence: PoseLandmarkerTests.minTrackingConfidence)
    try assertResultsForPoseLandmark(
      image: PoseLandmarkerTests.testImage,
      using: poseLandmarkerServiceWithFullModel,
      equals: PoseLandmarkerTests.poseLandmarkerFullResults)

    let poseLandmarkerServiceWithHeavyModel = try poseLandmarkerWithModel(
      PoseLandmarkerTests.poseLandmarkerHeavy,
      minPoseDetectionConfidence: PoseLandmarkerTests.minPoseDetectionConfidence,
      minPosePresenceConfidence: PoseLandmarkerTests.minPosePresenceConfidence,
      minTrackingConfidence: PoseLandmarkerTests.minTrackingConfidence)
    try assertResultsForPoseLandmark(
      image: PoseLandmarkerTests.testImage,
      using: poseLandmarkerServiceWithHeavyModel,
      equals: PoseLandmarkerTests.poseLandmarkerHeavyResults)
  }

}
