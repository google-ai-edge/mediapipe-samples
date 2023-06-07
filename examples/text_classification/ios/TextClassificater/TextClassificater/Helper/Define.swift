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

import UIKit

enum Model: String, CaseIterable {
    case mobileBert = "Mobile Bert"
    case avgWordClassifier = "Avg Word Classifier"

    var modelPath: String? {
        switch self {
        case .mobileBert:
            return Bundle.main.path(
                forResource: "bert_classifier", ofType: "tflite")
        case .avgWordClassifier:
            return Bundle.main.path(
                forResource: "average_word_classifier", ofType: "tflite")
        }
    }
}

struct Texts {
    static let defaultText = "This app consists of a single screen containing a large text field. Below the text field has a button titled classify that, when clicked, runs text classification on the text in the text field. Results as a list where every row item is the label and the confidence in that label."
}

struct Colors {
    static let mpColorPrimary = UIColor(hex: "#007F8B")
    static let mpColorPrimaryVariant = UIColor(hex: "#12B5CB")
    static let mpColorPrimaryDark = UIColor(hex: "#00676D")
    static let mpColorSecondary = UIColor(hex: "#FBBC04")
    static let mpColorSecondaryVariant = UIColor(hex: "#F9AB00")
    static let mpColorError = UIColor(hex: "#B00020")
    static let placeHolderColor = UIColor.lightGray
    static let textViewTextColor = UIColor.black
}

extension UIColor {
    public convenience init?(hex: String) {
        let r, g, b, a: CGFloat

        if hex.hasPrefix("#") {
            let start = hex.index(hex.startIndex, offsetBy: 1)
            let hexColor = String(hex[start...])
            if hexColor.count == 8 {
                let scanner = Scanner(string: hexColor)
                var hexNumber: UInt64 = 0

                if scanner.scanHexInt64(&hexNumber) {
                    r = CGFloat((hexNumber & 0xff000000) >> 24) / 255
                    g = CGFloat((hexNumber & 0x00ff0000) >> 16) / 255
                    b = CGFloat((hexNumber & 0x0000ff00) >> 8) / 255
                    a = CGFloat(hexNumber & 0x000000ff) / 255
                    self.init(red: r, green: g, blue: b, alpha: a)
                    return
                }
            }
        }
        return nil
    }
}
