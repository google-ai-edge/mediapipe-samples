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

/// Errors thrown by ``EmbeddingMetrics`` when vectors are empty, mismatched, or degenerate.
public enum EmbeddingMetricsError: LocalizedError, Equatable, Sendable {
  case emptyVector
  case dimensionMismatch(Int, Int)
  case zeroNorm

  public var errorDescription: String? {
    switch self {
    case .emptyVector:
      return "Embedding vectors must not be empty."
    case .dimensionMismatch(let lhs, let rhs):
      return "Embedding dimensions must match (\(lhs) != \(rhs))."
    case .zeroNorm:
      return "Embedding vectors must have a non-zero L2 norm."
    }
  }
}

/// Shared quality metrics for embedding-based MediaPipe iOS samples and tests.
public enum EmbeddingMetrics {
  /// Computes cosine similarity in `[-1.0, 1.0]` between two non-empty vectors of equal length.
  public static func cosineSimilarity(_ a: [Float], _ b: [Float]) throws -> Double {
    guard !a.isEmpty else { throw EmbeddingMetricsError.emptyVector }
    guard a.count == b.count else {
      throw EmbeddingMetricsError.dimensionMismatch(a.count, b.count)
    }
    var dot = 0.0
    var normA = 0.0
    var normB = 0.0
    for index in a.indices {
      let va = Double(a[index])
      let vb = Double(b[index])
      dot += va * vb
      normA += va * va
      normB += vb * vb
    }
    let denom = sqrt(normA) * sqrt(normB)
    guard denom > 0.0 else { throw EmbeddingMetricsError.zeroNorm }
    return min(max(dot / denom, -1.0), 1.0)
  }

  /// Computes the Euclidean (L2) norm of `vector`.
  public static func l2Norm(_ vector: [Float]) -> Double {
    let sumOfSquares = vector.reduce(0.0) { acc, value in
      let d = Double(value)
      return acc + d * d
    }
    return sqrt(sumOfSquares)
  }

  /// Returns `true` when every element in `vector` is finite (not NaN or infinite).
  public static func allFinite(_ vector: [Float]) -> Bool {
    vector.allSatisfy(\.isFinite)
  }
}
