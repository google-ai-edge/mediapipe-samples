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

/** The view controller is responsible for presenting the controls to change the meta data for the face landmarker and updating the singleton`` DetectorMetadata`` on user input.
 */
class BottomSheetViewController: UIViewController {

  // MARK: Delegates
  weak var delegate: BottomSheetViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!
  @IBOutlet weak var inferenceTimeLabel: UILabel!

  @IBOutlet weak var numFacesStepper: UIStepper!
  @IBOutlet weak var numFacesValueLabel: UILabel!
  @IBOutlet weak var minFaceDetectionConfidenceStepper: UIStepper!
  @IBOutlet weak var minFaceDetectionConfidenceValueLabel: UILabel!
  @IBOutlet weak var minFacePresenceConfidenceStepper: UIStepper!
  @IBOutlet weak var minFacePresenceConfidenceValueLabel: UILabel!
  @IBOutlet weak var minTrackingConfidenceStepper: UIStepper!
  @IBOutlet weak var minTrackingConfidenceValueLabel: UILabel!

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
  func update(inferenceTimeString: String) {
    inferenceTimeLabel.text = inferenceTimeString
  }

  // MARK: - Private function
  private func setupUI() {

    numFacesStepper.value = Double(InferenceConfigurationManager.sharedInstance.numFaces)
    numFacesValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.numFaces)"

    minFaceDetectionConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minFaceDetectionConfidence)
    minFaceDetectionConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minFaceDetectionConfidence)"

    minFacePresenceConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minFacePresenceConfidence)
    minFacePresenceConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minFacePresenceConfidence)"

    minTrackingConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minTrackingConfidence)
    minTrackingConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minTrackingConfidence)"

    // Chose delegate option
    let selectedDelegateAction = {(action: UIAction) in
      self.updateDelegate(title: action.title)
    }
    let delegateActions: [UIAction] = FaceLandmarkerDelegate.allCases.compactMap { delegate in
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
    guard let delegate = FaceLandmarkerDelegate(name: title) else { return }
    InferenceConfigurationManager.sharedInstance.delegate = delegate
  }

  private func enableOrDisableClicks() {
    numFacesStepper.isEnabled = isUIEnabled
    minFaceDetectionConfidenceStepper.isEnabled = isUIEnabled
    minFacePresenceConfidenceStepper.isEnabled = isUIEnabled
    minTrackingConfidenceStepper.isEnabled = isUIEnabled
  }
  
  // MARK: IBAction
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    inferenceTimeLabel.isHidden = !sender.isSelected
    inferenceTimeNameLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, didSwitchBottomSheetViewState: sender.isSelected)
  }

  @IBAction func numFacesStepperValueChanged(_ sender: UIStepper) {
    let numFaces = Int(sender.value)
    InferenceConfigurationManager.sharedInstance.numFaces = numFaces
    numFacesValueLabel.text = "\(numFaces)"
  }

  @IBAction func minFaceDetectionConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minFaceDetectionConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minFaceDetectionConfidence = minFaceDetectionConfidence
    minFaceDetectionConfidenceValueLabel.text = "\(minFaceDetectionConfidence)"
  }

  @IBAction func minFacePresenceConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minFacePresenceConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minFacePresenceConfidence = minFacePresenceConfidence
    minFacePresenceConfidenceValueLabel.text = "\(minFacePresenceConfidence)"
  }

  @IBAction func minTrackingConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minTrackingConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minTrackingConfidence = minTrackingConfidence
    minTrackingConfidenceValueLabel.text = "\(minTrackingConfidence)"
  }
}
