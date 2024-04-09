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

protocol BottomSheetViewControllerDelegate: AnyObject {
  /**
   This method is called when the user opens or closes the bottom sheet.
  **/
  func viewController(
    _ viewController: BottomSheetViewController,
    didSwitchBottomSheetViewState isOpen: Bool)
}

/** The view controller is responsible for presenting the controls to change the meta data for the Gesture Recognizer and updating the singleton`` DetectorMetadata`` on user input.
 */
class BottomSheetViewController: UIViewController {

  // MARK: Delegates
  weak var delegate: BottomSheetViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!
  @IBOutlet weak var inferenceTimeLabel: UILabel!

  @IBOutlet weak var minHandDetectionConfidenceStepper: UIStepper!
  @IBOutlet weak var minHandDetectionConfidenceValueLabel: UILabel!
  @IBOutlet weak var minHandPresenceConfidenceStepper: UIStepper!
  @IBOutlet weak var minHandPresenceConfidenceValueLabel: UILabel!
  @IBOutlet weak var minTrackingConfidenceStepper: UIStepper!
  @IBOutlet weak var minTrackingConfidenceValueLabel: UILabel!

  @IBOutlet weak var recognizationResultLabel: UILabel!
  @IBOutlet weak var scoreLabel: UILabel!

  @IBOutlet weak var toggleBottomSheetButton: UIButton!
  @IBOutlet weak var chooseDelegateButton: UIButton!

  // MARK: Instance Variables
  var isUIEnabled: Bool = false {
    didSet {
      enableOrDisableClicks()
    }
  }
  
  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
    enableOrDisableClicks()
  }
  
  // MARK: - Public Functions
  func update(inferenceTimeString: String, recognizationResultString: String?, score: Float?) {
    inferenceTimeLabel.text = inferenceTimeString
    if let recognizationResultString = recognizationResultString,
       let score = score {
      recognizationResultLabel.text = recognizationResultString
      scoreLabel.text = String(format: "%.2f", score)
    } else {
      recognizationResultLabel.text = "--"
      scoreLabel.text = ""
    }
  }

  // MARK: - Private function
  private func setupUI() {

    minHandDetectionConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minHandDetectionConfidence)
    minHandDetectionConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minHandDetectionConfidence)"

    minHandPresenceConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minHandPresenceConfidence)
    minHandPresenceConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minHandPresenceConfidence)"

    minTrackingConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minTrackingConfidence)
    minTrackingConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minTrackingConfidence)"

    // Chose delegate option
    let selectedDelegateAction = {(action: UIAction) in
      self.updateDelegate(title: action.title)
    }
    let delegateActions: [UIAction] = GestureRecognizerDelegate.allCases.compactMap { delegate in
      return UIAction(
        title: delegate.name,
        state: (InferenceConfigurationManager.sharedInstance.delegate == delegate) ? .on : .off,
        handler: selectedDelegateAction
      )
    }

    chooseDelegateButton.menu = UIMenu(children: delegateActions)
    chooseDelegateButton.showsMenuAsPrimaryAction = true
    chooseDelegateButton.changesSelectionAsPrimaryAction = true
  }

  private func updateDelegate(title: String) {
    guard let delegate = GestureRecognizerDelegate(name: title) else { return }
    InferenceConfigurationManager.sharedInstance.delegate = delegate
  }

  private func enableOrDisableClicks() {
    minHandDetectionConfidenceStepper.isEnabled = isUIEnabled
    minHandPresenceConfidenceStepper.isEnabled = isUIEnabled
    minTrackingConfidenceStepper.isEnabled = isUIEnabled
  }
  
  // MARK: IBAction
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    inferenceTimeLabel.isHidden = !sender.isSelected
    inferenceTimeNameLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, didSwitchBottomSheetViewState: sender.isSelected)
  }

  @IBAction func minHandDetectionConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minHandDetectionConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minHandDetectionConfidence = minHandDetectionConfidence
    minHandDetectionConfidenceValueLabel.text = "\(minHandDetectionConfidence)"
  }

  @IBAction func minHandPresenceConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minHandPresenceConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minHandPresenceConfidence = minHandPresenceConfidence
    minHandPresenceConfidenceValueLabel.text = "\(minHandPresenceConfidence)"
  }

  @IBAction func minTrackingConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minTrackingConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minTrackingConfidence = minTrackingConfidence
    minTrackingConfidenceValueLabel.text = "\(minTrackingConfidence)"
  }
}
