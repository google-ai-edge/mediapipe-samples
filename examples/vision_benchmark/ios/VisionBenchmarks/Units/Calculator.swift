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

import Foundation

class Calculator {
  static func calculatePercentile(data: [Double], percentile: Double) -> Double? {
    guard data.count > 0 else { return nil }

    let sortedData = data.sorted()

    let index = Double(sortedData.count - 1) * percentile / 100.0
    let lowerIndex = floor(index)
    let upperIndex = ceil(index)

    if lowerIndex == upperIndex {
      return sortedData[Int(index)]
    } else {
      let lowerValue = sortedData[Int(lowerIndex)]
      let upperValue = sortedData[Int(upperIndex)]
      return lowerValue + (upperValue - lowerValue) * (index - lowerIndex)
    }
  }
}
