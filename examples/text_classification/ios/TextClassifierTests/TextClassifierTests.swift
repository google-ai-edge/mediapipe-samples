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
@testable import TextClassifier
import MediaPipeTasksText

final class TextClassifierTests: XCTestCase {
  
  static let bertModel = Model.mobileBert
  
  static let negativeText = "unflinchingly bleak and desperate"
  
  static let bertNegativeTextResults = [
    ResultCategory(
      index: 0,
      score: 0.956187,
      categoryName: "negative",
      displayName: nil),
    ResultCategory(
      index: 1,
      score: 0.043812,
      categoryName: "positive",
      displayName: nil),
  ]
  
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
  
  func assertTextClassifierResultHasOneHead(
    _ textClassifierResult: TextClassifierResult
  ) {
    XCTAssertEqual(textClassifierResult.classificationResult.classifications.count, 1)
    XCTAssertEqual(textClassifierResult.classificationResult.classifications[0].headIndex, 0)
  }
  
  func textClassifierWithModel(
    _ model: Model
  ) throws -> TextClassifierHelper {
    let textClassifierHelper = TextClassifierHelper(model: model)
    return textClassifierHelper
  }
  
  func assertResultsForClassify(
    text: String,
    using textClassifier: TextClassifierHelper,
    equals expectedCategories: [ResultCategory]
  ) throws {
    let textClassifierResult =
    try XCTUnwrap(
      textClassifier.classify(text: text))
    assertTextClassifierResultHasOneHead(textClassifierResult)
    assertEqualCategoryArrays(
      categoryArray:
        textClassifierResult.classificationResult.classifications[0].categories,
      expectedCategoryArray: expectedCategories)
  }
  func testClassifyWithBertSucceeds() throws {
    
    let textClassifier = try textClassifierWithModel(TextClassifierTests.bertModel)
    try assertResultsForClassify(
      text: TextClassifierTests.negativeText,
      using: textClassifier,
      equals: TextClassifierTests.bertNegativeTextResults)
  }
}
