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

class ViewController: UIViewController {

  // IBOutlet
  @IBOutlet weak var clear1Button: UIButton!
  @IBOutlet weak var clear2Button: UIButton!
  @IBOutlet weak var compareButton: UIButton!
  @IBOutlet weak var input1TextView: UITextView!
  @IBOutlet weak var input2TextView: UITextView!

  @IBOutlet weak var bottomSpaceLayoutConstraint: NSLayoutConstraint!
  @IBOutlet weak var resultLabel: UILabel!

  let backgroundQueue = DispatchQueue(
    label: "com.google.mediapipe.backgroundQueue",
    qos: .userInteractive
  )

  private var isText2Editing: Bool = false
  private var textEmbedderService: TextEmbedderService?

  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
    hideKeyboardWhenTappedAround()
    input1TextView.delegate = self
    input2TextView.delegate = self

    // Keyboard notification
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(keyboardDidChangeFrame), name: UIResponder.keyboardWillChangeFrameNotification,
      object: nil)

    // Setup the TextEmbedder Service.
    backgroundQueue.async {
      self.textEmbedderService = TextEmbedderService(modelPath: Constants.modelPath)
    }
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  @IBAction func clearButton1TouchUpInside(_ sender: Any) {
    input1TextView.text = ""
    updateButtonState()
  }
  @IBAction func clearButton2TouchUpInside(_ sender: Any) {
    input2TextView.text = ""
    updateButtonState()
  }

  @IBAction func compareButtonTouchUpInside(_ sender: Any) {
    guard let textEmbedderService = textEmbedderService else { return }
    let text1 = input1TextView.text
    let text2 = input2TextView.text
    backgroundQueue.async { [weak self] in
      guard let weakSelf = self else { return }
      guard let similarity = textEmbedderService.compare(
        text1: text1,
        text2: text2) else {
        DispatchQueue.main.async {
          weakSelf.resultLabel.text = Constants.errorString
        }
        return
      }
      DispatchQueue.main.async {
        weakSelf.resultLabel.text = String(format: "Similarity: %.2f", similarity)
      }
    }
  }

  // Private function
  private func setupUI() {
    input1TextView.text = Constants.defaultText
    input2TextView.text = Constants.defaultText
  }

  private func hideKeyboardWhenTappedAround() {
    let tap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
    tap.cancelsTouchesInView = false
    view.addGestureRecognizer(tap)
  }

  @objc private func dismissKeyboard() {
    view.endEditing(true)
  }

  private func updateButtonState() {
    clear1Button.isEnabled = !input1TextView.text.isEmpty
    clear2Button.isEnabled = !input2TextView.text.isEmpty
    compareButton.isEnabled = !input1TextView.text.isEmpty && !input2TextView.text.isEmpty
  }

  @objc private func keyboardDidChangeFrame(notification: Notification) {
    guard isText2Editing == true,
      let frame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue else { return }
    bottomSpaceLayoutConstraint.constant = view.bounds.height - frame.cgRectValue.origin.y
    UIView.animate(withDuration: 0.3) {
      self.view.layoutIfNeeded()
    }
  }
}

// MARK: UITextViewDelegate
extension ViewController: UITextViewDelegate {
  func textViewDidChange(_ textView: UITextView) {
    updateButtonState()
  }

  func textViewShouldBeginEditing(_ textView: UITextView) -> Bool {
    if textView == input2TextView {
      isText2Editing = true
    }
    return true
  }

  func textViewDidEndEditing(_ textView: UITextView) {
    isText2Editing = false
  }
}

struct Constants {
  static let defaultText = "Google has released 24 versions of the Android operating system since 2008 and continues to make substantial investments to develop, grow, and improve the OS."
  static let errorString = "Cannot compare two texts"
  static let modelPath = Bundle.main.path(forResource: "universal_sentence_encoder", ofType: "tflite")
}
