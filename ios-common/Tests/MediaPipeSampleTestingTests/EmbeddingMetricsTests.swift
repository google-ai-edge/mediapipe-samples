// Copyright 2026 The MediaPipe Authors.
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

import Foundation
import MediaPipeSampleTesting
import MediaPipeSampleUI
import Testing

@Suite("EmbeddingMetrics & SampleModelLocator")
struct EmbeddingMetricsTests {
  @Test func identicalVectorsScoreOne() throws {
    let similarity = try EmbeddingMetrics.cosineSimilarity([1, 2, 3], [1, 2, 3])
    #expect(abs(similarity - 1.0) < 1e-6)
  }

  @Test func orthogonalVectorsScoreZero() throws {
    let similarity = try EmbeddingMetrics.cosineSimilarity([1, 0, 0], [0, 1, 0])
    #expect(abs(similarity) < 1e-6)
  }

  @Test func oppositeVectorsScoreMinusOne() throws {
    let similarity = try EmbeddingMetrics.cosineSimilarity([2, -3], [-2, 3])
    #expect(abs(similarity + 1.0) < 1e-6)
  }

  @Test func dimensionMismatchThrows() {
    #expect(throws: EmbeddingMetricsError.dimensionMismatch(2, 3)) {
      _ = try EmbeddingMetrics.cosineSimilarity([1, 2], [1, 2, 3])
    }
  }

  @Test func l2NormComputesEuclideanLength() {
    #expect(abs(EmbeddingMetrics.l2Norm([3, 4]) - 5.0) < 1e-6)
    #expect(EmbeddingMetrics.allFinite([1.0, -2.5, 0.0]))
    #expect(!EmbeddingMetrics.allFinite([1.0, .nan]))
  }

  @Test func modelLocatorFindsNonEmptyFileAndRejectsZeroByteStub() throws {
    let tempDir = FileManager.default.temporaryDirectory
      .appendingPathComponent("mediapipe-locator-\(UUID().uuidString)", isDirectory: true)
    try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)
    defer { try? FileManager.default.removeItem(at: tempDir) }

    let stubURL = tempDir.appendingPathComponent("stub.tflite")
    FileManager.default.createFile(atPath: stubURL.path, contents: Data())

    let realURL = tempDir.appendingPathComponent("real.tflite")
    try Data([0x01, 0x02, 0x03]).write(to: realURL)

    let locator = SampleModelLocator(overrideDirectories: [tempDir])

    #expect(throws: SampleModelError.emptyStub(filename: "stub.tflite")) {
      _ = try locator.locate(resourceName: "stub", fileExtension: "tflite")
    }
    #expect(throws: SampleModelError.missing(filename: "absent.tflite")) {
      _ = try locator.locate(resourceName: "absent", fileExtension: "tflite")
    }
    let found = try locator.locate(resourceName: "real", fileExtension: "tflite")
    #expect(found.standardizedFileURL == realURL.standardizedFileURL)
  }
}
