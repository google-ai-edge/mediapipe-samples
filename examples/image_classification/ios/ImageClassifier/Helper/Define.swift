//
//  Define.swift
//  ImageClassifier
//
//  Created by MBA0077 on 6/8/23.
//

import Foundation

enum Model: String, CaseIterable {
    case efficientnetLite0 = "Efficientnet lite 0"
    case efficientnetLite2 = "Efficientnet lite 2"

    var modelPath: String? {
        switch self {
        case .efficientnetLite0:
            return Bundle.main.path(
                forResource: "efficientnet_lite0", ofType: "tflite")
        case .efficientnetLite2:
            return Bundle.main.path(
                forResource: "efficientnet_lite2", ofType: "tflite")
        }
    }
}

// MARK: Define default constants
enum DefaultConstants {
  static let maxResults = 3
  static let scoreThreshold: Float = 0.2
  static let model: Model = .efficientnetLite0
}
