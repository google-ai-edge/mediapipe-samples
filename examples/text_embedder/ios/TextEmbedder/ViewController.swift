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
  @IBOutlet weak var contentScrollView: UIScrollView!
  @IBOutlet weak var resultLabel: UILabel!

  let backgroundQueue = DispatchQueue(
    label: "com.google.mediapipe.backgroundQueue",
    qos: .userInteractive
  )

  private var textEmbedderService: TextEmbedderService?
  private let textViewBotomSpace: CGFloat = 100

  //InputAccessoryView constants
  private let inputAccessoryViewHeight: CGFloat = 44
  private let doneButtonSpaceRight: CGFloat = 20
  private let doneButtonWidth: CGFloat = 60
  private let doneButtonHeight: CGFloat = 30
  private let doneButtonSpaceTop: CGFloat = 5
  private let doneButtonTitle: String = "Done"
  private let doneButtonBackgroudColor: UIColor = UIColor(displayP3Red: 0, green: 127/255.0, blue: 139/255.0, alpha: 1)

  // Keyboard show and hiden animation time
  private let keyboardAnimationTime = 0.3

  // The time waiting for scroll view set frame
  private let delayTimeScrollToVisible = 0.2

  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
    hideKeyboardOnBackgroundTap()
    input1TextView.delegate = self
    input2TextView.delegate = self

    // Keyboard notification
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(keyboardWillChangeFrame), name: UIResponder.keyboardWillChangeFrameNotification,
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
    input1TextView.inputAccessoryView = createInputAccessoryView()
    input2TextView.inputAccessoryView = createInputAccessoryView()
  }

  private func hideKeyboardOnBackgroundTap() {
    let tap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
    tap.cancelsTouchesInView = false
    view.addGestureRecognizer(tap)
  }

  private func createInputAccessoryView() -> UIView {
    let inputAccessoryView = UIView(
      frame: CGRect(x: 0, y: 0, width: view.bounds.width, height: inputAccessoryViewHeight))
    inputAccessoryView.backgroundColor = .lightGray

    let doneButton = UIButton(frame: CGRect(
      x: view.bounds.width - doneButtonWidth - doneButtonSpaceRight,
      y: doneButtonSpaceTop,
      width: doneButtonWidth,
      height: doneButtonHeight))
    doneButton.setTitle(doneButtonTitle, for: .normal)
    doneButton.setTitleColor(.white, for: .normal)
    doneButton.backgroundColor = doneButtonBackgroudColor
    doneButton.addTarget(self, action: #selector(dismissKeyboard), for: .touchUpInside)
    inputAccessoryView.addSubview(doneButton)
    return inputAccessoryView
  }

  @objc private func dismissKeyboard() {
    view.endEditing(true)
  }

  private func updateButtonState() {
    clear1Button.isEnabled = !input1TextView.text.isEmpty
    clear2Button.isEnabled = !input2TextView.text.isEmpty
    compareButton.isEnabled = !input1TextView.text.isEmpty && !input2TextView.text.isEmpty
  }

  @objc private func keyboardWillChangeFrame(notification: Notification) {
    guard let frame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue else { return }
    if view.bounds.size.height - frame.cgRectValue.origin.y == 0 {
      contentScrollView.scrollRectToVisible(CGRect(x: 0, y: 0, width: contentScrollView.bounds.width, height: contentScrollView.bounds.height), animated: true)
      DispatchQueue.main.asyncAfter(deadline: .now() + keyboardAnimationTime) {
        self.bottomSpaceLayoutConstraint.constant = 0
      }
    } else {
      bottomSpaceLayoutConstraint.constant = view.bounds.size.height - view.safeAreaInsets.bottom - frame.cgRectValue.origin.y
    }
  }
}

// MARK: UITextViewDelegate
extension ViewController: UITextViewDelegate {
  func textViewDidChange(_ textView: UITextView) {
    updateButtonState()
  }

  func textViewDidBeginEditing(_ textView: UITextView) {
    if textView == input2TextView {
      DispatchQueue.main.asyncAfter(deadline: .now() + delayTimeScrollToVisible) {
        self.contentScrollView.scrollRectToVisible(CGRect(x: 0, y: self.bottomSpaceLayoutConstraint.constant - self.textViewBotomSpace, width: self.contentScrollView.bounds.width, height: self.contentScrollView.bounds.height), animated: true)
      }
    } else {
      contentScrollView.scrollRectToVisible(CGRect(x: 0, y: 0, width: contentScrollView.bounds.width, height: contentScrollView.bounds.height), animated: true)
    }
  }
}

struct Constants {
  static let defaultText = "Google has released 24 versions of the Android operating system since 2008 and continues to make substantial investments to develop, grow, and improve the OS."
  static let errorString = "Cannot compare two texts"
  static let modelPath = Bundle.main.path(forResource: "universal_sentence_encoder", ofType: "tflite")
}
