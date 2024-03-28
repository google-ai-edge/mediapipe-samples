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

/** The view controller is responsible for presenting the controls to change the meta data for the pose landmarker and updating the singleton`` DetectorMetadata`` on user input.
 */
class BottomSheetViewController: UIViewController {

  // MARK: Delegates
  weak var delegate: BottomSheetViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!
  @IBOutlet weak var inferenceTimeLabel: UILabel!

  @IBOutlet weak var numPosesStepper: UIStepper!
  @IBOutlet weak var numPosesValueLabel: UILabel!
  @IBOutlet weak var minPoseDetectionConfidenceStepper: UIStepper!
  @IBOutlet weak var minPoseDetectionConfidenceValueLabel: UILabel!
  @IBOutlet weak var minPosePresenceConfidenceStepper: UIStepper!
  @IBOutlet weak var minPosePresenceConfidenceValueLabel: UILabel!
  @IBOutlet weak var minTrackingConfidenceStepper: UIStepper!
  @IBOutlet weak var minTrackingConfidenceValueLabel: UILabel!
  @IBOutlet weak var chooseModelButton: UIButton!

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

    numPosesStepper.value = Double(InferenceConfigurationManager.sharedInstance.numPoses)
    numPosesValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.numPoses)"

    minPoseDetectionConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minPoseDetectionConfidence)
    minPoseDetectionConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minPoseDetectionConfidence)"

    minPosePresenceConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minPosePresenceConfidence)
    minPosePresenceConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minPosePresenceConfidence)"

    minTrackingConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minTrackingConfidence)
    minTrackingConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minTrackingConfidence)"

    // Choose model option
    let selectedModelAction = {(action: UIAction) in
      self.updateModel(modelTitle: action.title)
    }

    let actions: [UIAction] = Model.allCases.compactMap { model in
      return UIAction(
        title: model.name,
        state: (InferenceConfigurationManager.sharedInstance.model == model) ? .on : .off,
        handler: selectedModelAction
      )
    }
    chooseModelButton.menu = UIMenu(children: actions)
    chooseModelButton.showsMenuAsPrimaryAction = true
    chooseModelButton.changesSelectionAsPrimaryAction = true

    let selectedDelegateAction = {(action: UIAction) in
      self.updateDelegate(title: action.title)
    }
    let delegateActions: [UIAction] = PoseLandmarkerDelegate.allCases.compactMap { delegate in
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
  
  private func enableOrDisableClicks() {
    numPosesStepper.isEnabled = isUIEnabled
    minPoseDetectionConfidenceStepper.isEnabled = isUIEnabled
    minPosePresenceConfidenceStepper.isEnabled = isUIEnabled
    minTrackingConfidenceStepper.isEnabled = isUIEnabled
  }

  private func updateModel(modelTitle: String) {
    guard let model = Model(name: modelTitle) else { return }
    InferenceConfigurationManager.sharedInstance.model = model
  }

  private func updateDelegate(title: String) {
    guard let delegate = PoseLandmarkerDelegate(name: title) else { return }
    InferenceConfigurationManager.sharedInstance.delegate = delegate
  }

  // MARK: IBAction
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    inferenceTimeLabel.isHidden = !sender.isSelected
    inferenceTimeNameLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, didSwitchBottomSheetViewState: sender.isSelected)
  }

  @IBAction func numPosesStepperValueChanged(_ sender: UIStepper) {
    let numPoses = Int(sender.value)
    InferenceConfigurationManager.sharedInstance.numPoses = numPoses
    numPosesValueLabel.text = "\(numPoses)"
  }

  @IBAction func minPoseDetectionConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minPoseDetectionConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minPoseDetectionConfidence = minPoseDetectionConfidence
    minPoseDetectionConfidenceValueLabel.text = "\(minPoseDetectionConfidence)"
  }

  @IBAction func minPosePresenceConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minPosePresenceConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minPosePresenceConfidence = minPosePresenceConfidence
    minPosePresenceConfidenceValueLabel.text = "\(minPosePresenceConfidence)"
  }

  @IBAction func minTrackingConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minTrackingConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minTrackingConfidence = minTrackingConfidence
    minTrackingConfidenceValueLabel.text = "\(minTrackingConfidence)"
  }
}
