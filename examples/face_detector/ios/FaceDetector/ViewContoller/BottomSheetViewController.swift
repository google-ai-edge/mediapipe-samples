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
import MediaPipeTasksVision

protocol BottomSheetViewControllerDelegate: AnyObject {
  /**
   This method is called when the user opens or closes the bottom sheet.
  **/
  func viewController(
    _ viewController: BottomSheetViewController,
    didSwitchBottomSheetViewState isOpen: Bool)
}

/** The view controller is responsible for presenting the controls to change the meta data for the face detector (model, max results,
 * score threshold) and updating the singleton`` DetectorMetadata`` on user input.
 */
class BottomSheetViewController: UIViewController {

  enum Action {
    case changeScoreThreshold(Float)
    case changeBottomSheetViewBottomSpace(Bool)
    case changeMinSuppressionThreshold(Float)
  }

  // MARK: Delegates
  weak var delegate: BottomSheetViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!
  @IBOutlet weak var inferenceTimeLabel: UILabel!
  @IBOutlet weak var minDetectionConfidenceValueLabel: UILabel!
  @IBOutlet weak var minDetectionConfidenceStepper: UIStepper!
  @IBOutlet weak var minSuppressionThresholdStepper: UIStepper!
  @IBOutlet weak var minSuppressionThresholdValueLabel: UILabel!
  @IBOutlet weak var toggleBottomSheetButton: UIButton!
  @IBOutlet weak var delegateButton: UIButton!

  
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

    minDetectionConfidenceStepper.value = Double(InferenceConfigurationManager.sharedInstance.minDetectionConfidence)
    minDetectionConfidenceValueLabel.text = "\(InferenceConfigurationManager.sharedInstance.minDetectionConfidence)"

    let selectedDelegateAction = {(action: UIAction) in
      self.updateDelegate(title: action.title)
    }

    let delegates: [Delegate] = [.CPU, .GPU]
    let delegateActions: [UIAction] = delegates.compactMap { delegate in
      return UIAction(
        title: delegate == .CPU ? "CPU" : "GPU",
        state: (InferenceConfigurationManager.sharedInstance.delegate == delegate) ? .on : .off,
        handler: selectedDelegateAction
      )
    }

    delegateButton.menu = UIMenu(children: delegateActions)
    delegateButton.showsMenuAsPrimaryAction = true
    delegateButton.changesSelectionAsPrimaryAction = true
  }

  private func updateDelegate(title: String) {
    InferenceConfigurationManager.sharedInstance.delegate = title == "GPU" ? .GPU : .CPU
  }

  private func enableOrDisableClicks() {
    minDetectionConfidenceStepper.isEnabled = isUIEnabled
  }
  
  // MARK: IBAction
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    inferenceTimeLabel.isHidden = !sender.isSelected
    inferenceTimeNameLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, didSwitchBottomSheetViewState: sender.isSelected)
  }

  @IBAction func minDetectionConfidenceStepperValueChanged(_ sender: UIStepper) {
    let minDetectionConfidence = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minDetectionConfidence = minDetectionConfidence
    minDetectionConfidenceValueLabel.text = "\(minDetectionConfidence)"
  }

  @IBAction func minSuppressionThresholdStepperValueChanged(_ sender: UIStepper) {
    let minSuppressionThreshold = Float(sender.value)
    InferenceConfigurationManager.sharedInstance.minSuppressionThreshold = minSuppressionThreshold
    minSuppressionThresholdValueLabel.text = "\(minSuppressionThreshold)"
  }
}
