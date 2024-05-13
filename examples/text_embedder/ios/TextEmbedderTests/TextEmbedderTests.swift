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
@testable import TextEmbedder
import MediaPipeTasksText

final class TextEmbedderTests: XCTestCase {

  static let modelPath: String = Bundle.main.path(forResource: "universal_sentence_encoder", ofType: "tflite")!
  static let text1: String = "Google has released 24 versions of the Android operating system since 2008 and continues to make substantial investments to develop, grow, and improve the OS."
  static let text2: String = "Google has released 24 versions of the Android operating system since 2008"

  static let embeddingResult1: Embedding = Embedding(
    floatEmbedding: [0.3653241, 0.04759156, -0.5803496, 0.8180379, -0.8393675, 1.080326, -1.683998, -1.270353, 1.873625, -0.4594853, -0.492683, -0.7890862, -0.3505277, -1.699354, 0.7693181, 1.956728, -1.790482, -1.13161, 0.05913593, 0.002898678, -1.953177, 1.999129, -0.3950457, -0.9293886, 0.9549099, 0.8119621, -0.09827112, -1.682092, 2.253792, 1.606155, -1.661449, -0.6898757, -0.7298279, 0.3063133, -0.6537318, 0.2993962, -0.3203471, -0.1256338, 1.47511, -0.4300548, -1.435748, -0.5477355, 1.130164, 1.940887, -0.5358125, 0.7074715, 0.5665413, 2.229927, 0.7578374, -0.8634944, -1.090861, 0.4853912, 0.8891751, 0.1747377, 0.9157588, -0.02893238, 0.2870376, -0.3370381, -0.4777956, 2.10074, -1.875058, 0.1374154, 0.9458161, 0.7843524, -0.2138919, 0.7008632, -0.03317095, -2.598468, -0.9736772, 0.2829854, -0.3299808, -0.6989679, -1.310554, 0.003248722, 1.061082, -0.7787276, 1.589482, 0.1056395, -1.622551, -0.9812658, 1.728942, -0.01615245, 0.7371362, -0.008867697, 1.318034, -0.3025512, 3.379612, 0.2910861, -2.027618, 0.3429114, -0.3798485, 0.2427975, 0.5496578, -1.893867, 0.3420545, -0.6351373, 0.6826952, 0.4076473, 0.9852723, 0.3503794],
    quantizedEmbedding: nil,
    head: 0,
    headName: nil)
  static let embeddingResult2: Embedding = Embedding(
    floatEmbedding: [0.6456553, 0.1164482, -1.054019, 0.6861709, -0.3185744, 1.348018, -1.847239, -0.935012, 2.246192, -0.4375512, -0.956903, -0.8150874, -0.1990242, -1.821287, 1.067085, 1.73143, -1.130399, -0.8963736, -0.9726468, -0.2532014, -2.017439, 1.53013, -0.5990306, -0.9568684, 1.081622, 0.3129545, 0.4009041, -1.369142, 2.229438, 1.203333, -1.409538, -0.8667203, -0.8358657, 0.6628245, -1.082615, 0.4371429, -0.4217262, 0.2855506, 1.634919, -0.2173558, -1.469216, 0.2437164, 1.555744, 2.053072, -0.3854842, 1.187901, -0.2300293, 1.970965, 1.219385, -1.144413, -1.064619, 0.7585568, 0.7563339, 0.8307036, 0.4176229, -0.08783004, 0.1172272, -0.4737191, -0.7211733, 1.404953, -2.038084, -0.1280682, 0.6647619, 0.8927605, 0.4958345, 1.404044, -0.2824433, -2.635035, -1.232629, 0.5868394, -0.3338131, -1.267816, -1.25145, -0.3923185, 0.3810659, -0.597534, 1.71393, -0.07646833, -0.4871983, -1.320478, 1.252456, 0.1993347, 1.273979, 0.006039389, 0.9298355, 0.01001734, 3.329656, 0.4261275, -1.724598, 0.2588451, -0.7891623, 0.4268072, 0.3259311, -2.163875, -0.4691091, -0.4373473, 0.7963935, -0.0672554, 0.7801297, 0.3548266],
    quantizedEmbedding: nil,
    head: 0,
    headName: nil)

  static let similarity: Float = 0.94341993

  func textEmbedderWithModelPath(
    _ modelPath: String) throws -> TextEmbedderService {
      let textEmbedderService = TextEmbedderService(modelPath: modelPath)
    return textEmbedderService
  }

  func assertEmbeddingResultHasOneEmbedding(
    _ embeddingResult: EmbeddingResult
  ) {
    XCTAssertEqual(embeddingResult.embeddings.count, 1)
  }

  func assertEmbeddingAreEqual(
    _ embeddingResult: Embedding,
    expectedEmbeddingResult: Embedding
  ) {
    XCTAssertEqual(
      embeddingResult.floatEmbedding!.count,
      expectedEmbeddingResult.floatEmbedding!.count)
    for (index, (floatEmbedding, expectedFloatEmbedding)) in zip(embeddingResult.floatEmbedding!, expectedEmbeddingResult.floatEmbedding!)
      .enumerated() {
      XCTAssertEqual(
        floatEmbedding.floatValue,
        expectedFloatEmbedding.floatValue,
        accuracy: 1e-3,
        String(
          format: """
                            embedding[%d] and expectedEmbedding[%d] are not equal.
                """, index, index))
    }
  }

  func assertTwoSimilaritiesAreEqual(
    _ similarity: Float,
    expectedSimilarity: Float
  ) {
    XCTAssertEqual(similarity, expectedSimilarity, "similarity is wrong")
  }

  func assertEmbeddingResultForTextEmbedder(
    text: String,
    using textEmbedderService: TextEmbedderService,
    equals expectedEmbedding: Embedding
  ) throws {
    let textEmbedderServiceResult =
    try XCTUnwrap(textEmbedderService.embed(text: text))
    assertEmbeddingResultHasOneEmbedding(textEmbedderServiceResult)
    assertEmbeddingAreEqual(textEmbedderServiceResult.embeddings.first!, expectedEmbeddingResult: expectedEmbedding)
  }

  func assertSimilarityResultForTextEmbedder(
    text1: String,
    text2: String,
    using textEmbedderService: TextEmbedderService,
    equals expectedSimilarity: Float
  ) throws {
    let similarity =
    try XCTUnwrap(textEmbedderService.compare(text1: text1, text2: text2))
    assertTwoSimilaritiesAreEqual(similarity, expectedSimilarity: expectedSimilarity)
  }

  func testTextEmbedderSucceeds() throws {
    let textEmbedderService = try textEmbedderWithModelPath(TextEmbedderTests.modelPath)

    try assertEmbeddingResultForTextEmbedder(
      text: TextEmbedderTests.text1,
      using: textEmbedderService,
      equals: TextEmbedderTests.embeddingResult1)

    try assertEmbeddingResultForTextEmbedder(
      text: TextEmbedderTests.text2,
      using: textEmbedderService,
      equals: TextEmbedderTests.embeddingResult2)

    try assertSimilarityResultForTextEmbedder(
      text1: TextEmbedderTests.text1,
      text2: TextEmbedderTests.text2,
      using: textEmbedderService,
      equals: TextEmbedderTests.similarity)
  }
}
