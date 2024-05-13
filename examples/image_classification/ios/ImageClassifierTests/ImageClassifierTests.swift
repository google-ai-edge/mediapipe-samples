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
@testable import ImageClassifier
import MediaPipeTasksVision

final class ImageClassifierTests: XCTestCase {

  static let efficientnetLite0 = Model.efficientnetLite0
  static let efficientnetLite2 = Model.efficientnetLite2

  static let scoreThreshold: Float = 0.01
  static let maxResult: Int = 3
  
  static let testImage = UIImage(
    named: "cup.png",
    in:Bundle(for: ImageClassifierTests.self),
    compatibleWith: nil)!

  static let efficientnetLite0Results = [
    ResultCategory(
      index: 504,
      score: 0.603319644,
      categoryName: "coffee mug",
      displayName: nil),
    ResultCategory(
      index: 968,
      score: 0.136507213,
      categoryName: "cup",
      displayName: nil),
    ResultCategory(
      index: 899,
      score: 0.0619561076,
      categoryName: "water jug",
      displayName: nil),
  ]

  static let efficientnetLite2Results = [
    ResultCategory(
      index: 504,
      score: 0.42104575,
      categoryName: "coffee mug",
      displayName: nil),
    ResultCategory(
      index: 968,
      score: 0.152147159,
      categoryName: "cup",
      displayName: nil),
    ResultCategory(
      index: 725,
      score: 0.0212709885,
      categoryName: "pitcher",
      displayName: nil),
  ]

  func imageClassifierWithModel(
    _ model: Model,
    scoreThreshold: Float,
    maxResult: Int
  ) throws -> ImageClassifierService {
    let imageClassifierService = ImageClassifierService.stillImageClassifierService(
      model: model,
      scoreThreshold: scoreThreshold,
      maxResult: maxResult,
      delegate: .CPU)
    return imageClassifierService!
  }

  func assertImageClassifierResultHasOneHead(
    _ imageClassifierResult: ImageClassifierResult
  ) {
    XCTAssertEqual(imageClassifierResult.classificationResult.classifications.count, 1)
    XCTAssertEqual(imageClassifierResult.classificationResult.classifications[0].headIndex, 0)
  }

  func assertCategoriesAreEqual(
    category: ResultCategory,
    expectedCategory: ResultCategory,
    indexInCategoryList: Int
  ) {
    XCTAssertEqual(
      category.index,
      expectedCategory.index,
      String(
        format: """
              category[%d].index and expectedCategory[%d].index are not equal.
              """, indexInCategoryList))
    XCTAssertEqual(
      category.score,
      expectedCategory.score,
      accuracy: 1e-3,
      String(
        format: """
              category[%d].score and expectedCategory[%d].score are not equal.
              """, indexInCategoryList))
    XCTAssertEqual(
      category.categoryName,
      expectedCategory.categoryName,
      String(
        format: """
              category[%d].categoryName and expectedCategory[%d].categoryName are \
              not equal.
              """, indexInCategoryList))
    XCTAssertEqual(
      category.displayName,
      expectedCategory.displayName,
      String(
        format: """
              category[%d].displayName and expectedCategory[%d].displayName are \
              not equal.
              """, indexInCategoryList))
  }

  func assertEqualCategoryArrays(
    categoryArray: [ResultCategory],
    expectedCategoryArray: [ResultCategory]
  ) {
    XCTAssertEqual(
      categoryArray.count,
      expectedCategoryArray.count)

    for (index, (category, expectedCategory)) in zip(categoryArray, expectedCategoryArray)
      .enumerated()
    {
      assertCategoriesAreEqual(
        category: category,
        expectedCategory: expectedCategory,
        indexInCategoryList: index)
    }
  }

  func assertResultsForClassify(
    image: UIImage,
    using imageClassifier: ImageClassifierService,
    equals expectedCategories: [ResultCategory]
  ) throws {
    let imageClassifierResult =
    try XCTUnwrap(
      imageClassifier.classify(image: image)!.imageClassifierResults[0])
    print(imageClassifierResult)
    assertImageClassifierResultHasOneHead(imageClassifierResult)
    assertEqualCategoryArrays(
      categoryArray:
        imageClassifierResult.classificationResult.classifications[0].categories,
      expectedCategoryArray: expectedCategories)
  }
  func testClassifyWithEfficientnetLite0Succeeds() throws {

    let imageClassifier = try imageClassifierWithModel(
      ImageClassifierTests.efficientnetLite0,
      scoreThreshold: ImageClassifierTests.scoreThreshold,
      maxResult: ImageClassifierTests.maxResult)
    try assertResultsForClassify(
      image: ImageClassifierTests.testImage,
      using: imageClassifier,
      equals: ImageClassifierTests.efficientnetLite0Results)
  }

  func testClassifyWithEfficientnetLite2Succeeds() throws {

    let imageClassifier = try imageClassifierWithModel(
      ImageClassifierTests.efficientnetLite2,
      scoreThreshold: ImageClassifierTests.scoreThreshold,
      maxResult: ImageClassifierTests.maxResult)
    try assertResultsForClassify(
      image: ImageClassifierTests.testImage,
      using: imageClassifier,
      equals: ImageClassifierTests.efficientnetLite2Results)
  }
}
