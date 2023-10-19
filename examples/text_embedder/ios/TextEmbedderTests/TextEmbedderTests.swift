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
    floatEmbedding: [0.36532423, 0.047590725, -0.58034915, 0.81803715, -0.83936816, 1.0803267, -1.683998, -1.2703534, 1.8736261, -0.45948586, -0.4926827, -0.78908664, -0.3505274, -1.6993542, 0.769318, 1.9567271, -1.7904824, -1.1316108, 0.059136376, 0.00289971, -1.953178, 1.9991299, -0.3950468, -0.92938966, 0.9549097, 0.8119625, -0.09827121, -1.6820917, 2.2537918, 1.6061562, -1.6614496, -0.6898748, -0.7298267, 0.306313, -0.65373176, 0.2993963, -0.32034644, -0.12563556, 1.4751105, -0.4300541, -1.4357476, -0.5477365, 1.1301636, 1.9408872, -0.5358113, 0.7074716, 0.5665407, 2.2299273, 0.7578367, -0.8634927, -1.0908612, 0.4853919, 0.88917464, 0.17473765, 0.9157595, -0.028933238, 0.2870373, -0.33703837, -0.47779506, 2.1007392, -1.8750576, 0.13741638, 0.94581753, 0.7843519, -0.21389213, 0.7008632, -0.033169623, -2.598468, -0.97367656, 0.28298423, -0.3299811, -0.6989682, -1.3105547, 0.0032470266, 1.0610827, -0.7787282, 1.5894828, 0.105639495, -1.6225517, -0.9812658, 1.7289429, -0.016152928, 0.73713666, -0.008867563, 1.3180345, -0.30255127, 3.3796136, 0.29108533, -2.0276186, 0.34291005, -0.379848, 0.24279688, 0.54965883, -1.8938673, 0.34205624, -0.6351377, 0.68269515, 0.4076469, 0.9852723, 0.35037997],
    quantizedEmbedding: nil,
    head: 0,
    headName: nil)
  static let embeddingResult2: Embedding = Embedding(
    floatEmbedding: [0.64565486, 0.116448246, -1.0540192, 0.6861718, -0.31857502, 1.348019, -1.847239, -0.9350123, 2.2461917, -0.43754864, -0.9569025, -0.8150869, -0.19902262, -1.821287, 1.067085, 1.7314306, -1.1303993, -0.89637387, -0.972647, -0.2532016, -2.0174396, 1.5301282, -0.5990305, -0.95686877, 1.0816203, 0.31295553, 0.40090463, -1.3691411, 2.229438, 1.2033331, -1.4095374, -0.8667203, -0.8358657, 0.6628247, -1.0826141, 0.43714273, -0.42172623, 0.28555048, 1.6349199, -0.21735463, -1.4692173, 0.24371623, 1.5557445, 2.0530715, -0.38548565, 1.1879013, -0.23002982, 1.9709659, 1.2193843, -1.1444125, -1.0646181, 0.75855684, 0.7563348, 0.8307041, 0.4176243, -0.08782976, 0.11722673, -0.47372058, -0.7211732, 1.4049535, -2.0380833, -0.12806825, 0.66476125, 0.8927604, 0.4958357, 1.4040443, -0.282444, -2.6350355, -1.2326286, 0.5868405, -0.33381227, -1.2678164, -1.251449, -0.392318, 0.38106433, -0.5975351, 1.7139304, -0.076468684, -0.48719692, -1.3204789, 1.2524556, 0.19933356, 1.2739782, 0.006039286, 0.9298355, 0.010017813, 3.3296566, 0.4261271, -1.724599, 0.25884697, -0.78916174, 0.42680606, 0.3259297, -2.1638749, -0.46910936, -0.43734843, 0.7963945, -0.067255, 0.78012973, 0.35482663],
    quantizedEmbedding: nil,
    head: 0,
    headName: nil)

  static let similarity: Float = 0.9434198

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
