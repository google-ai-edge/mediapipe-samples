// Copyright 2024 The MediaPipe Authors.
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

/** The view controller is responsible for presenting the controls to change the meta data for the holistic landmarker and updating the singleton InferenceConfigurationManager on user input.
 */
class BottomSheetViewController: UIViewController {

  // MARK: Delegates
  weak var delegate: BottomSheetViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!
  @IBOutlet weak var inferenceTimeLabel: UILabel!

  @IBOutlet weak var minPoseDetectionConfidenceStepper: UIStepper!
  @IBOutlet weak var minPoseDetectionConfidenceValueLabel: UILabel!
  @IBOutlet weak var minPosePresenceConfidenceStepper: UIStepper!
  @IBOutlet weak var minPosePresenceConfidenceValueLabel: UILabel!
  @IBOutlet weak var minFaceDetectionConfidenceStepper: UIStepper!
  @IBOutlet weak var minFaceDetectionConfidenceValueLabel: UILabel!
  @IBOutlet weak var minHandLandmarksConfidenceStepper: UIStepper!
  @IBOutlet weak var minHandLandmarksConfidenceValueLabel: UILabel!

  @IBOutlet weak var chooseModelButton: UIButton!
  @IBOutlet weak var chooseDelegateButton: UIButton!
  @IBOutlet weak var toggleBottomSheetButton: UIButton!

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
    minPoseDetectionConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minPoseDetectionConfidence)
    minPoseDetectionConfidenceValueLabel.text = String(format: "%.2f", InferenceConfigurationManager.sharedInstance.minPoseDetectionConfidence)

    minPosePresenceConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minPosePresenceConfidence)
    minPosePresenceConfidenceValueLabel.text = String(format: "%.2f", InferenceConfigurationManager.sharedInstance.minPosePresenceConfidence)

    minFaceDetectionConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minFaceDetectionConfidence)
    minFaceDetectionConfidenceValueLabel.text = String(format: "%.2f", InferenceConfigurationManager.sharedInstance.minFaceDetectionConfidence)

    minHandLandmarksConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minHandLandmarksConfidence)
    minHandLandmarksConfidenceValueLabel.text = String(format: "%.2f", InferenceConfigurationManager.sharedInstance.minHandLandmarksConfidence)

    // Choose model option
    let selectedModelAction = { (action: UIAction) in
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

    let selectedDelegateAction = { (action: UIAction) in
      self.updateDelegate(title: action.title)
    }
    let delegateActions: [UIAction] = HolisticLandmarkerDelegate.allCases.compactMap { delegate in
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
    minPoseDetectionConfidenceStepper.isEnabled = isUIEnabled
    minPosePresenceConfidenceStepper.isEnabled = isUIEnabled
    minFaceDetectionConfidenceStepper.isEnabled = isUIEnabled
    minHandLandmarksConfidenceStepper.isEnabled = isUIEnabled
  }

  private func updateModel(modelTitle: String) {
    guard let model = Model(name: modelTitle) else { return }
    InferenceConfigurationManager.sharedInstance.model = model
  }

  private func updateDelegate(title: String) {
    guard let delegate = HolisticLandmarkerDelegate(name: title) else { return }
    InferenceConfigurationManager.sharedInstance.delegate = delegate
  }

  // MARK: IBAction
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    inferenceTimeLabel.isHidden = !sender.isSelected
    inferenceTimeNameLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, didSwitchBottomSheetViewState: sender.isSelected)
  }

  @IBAction func minPoseDetectionConfidenceStepperValueChanged(_ sender: UIStepper) {
    let value = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minPoseDetectionConfidence = value
    minPoseDetectionConfidenceValueLabel.text = String(format: "%.2f", value)
  }

  @IBAction func minPosePresenceConfidenceStepperValueChanged(_ sender: UIStepper) {
    let value = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minPosePresenceConfidence = value
    minPosePresenceConfidenceValueLabel.text = String(format: "%.2f", value)
  }

  @IBAction func minFaceDetectionConfidenceStepperValueChanged(_ sender: UIStepper) {
    let value = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minFaceDetectionConfidence = value
    minFaceDetectionConfidenceValueLabel.text = String(format: "%.2f", value)
  }

  @IBAction func minHandLandmarksConfidenceStepperValueChanged(_ sender: UIStepper) {
    let value = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minHandLandmarksConfidence = value
    minHandLandmarksConfidenceValueLabel.text = String(format: "%.2f", value)
  }
}
