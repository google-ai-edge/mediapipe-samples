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

protocol InferenceViewControllerDelegate: AnyObject {
  /**
   This method is called when the user opens or closes the bottom sheet.
  **/
  func viewController(
    _ viewController: BottomSheetViewController,
    didSwitchBottomSheetViewState isOpen: Bool)
}

/** The view controller is responsible for presenting the controls to change the meta data for the object detector (model, max results,
 * score threshold) and updating the singleton`` DetectorMetadata`` on user input.
 */
class BottomSheetViewController: UIViewController {

  enum Action {
    case changeScoreThreshold(Float)
    case changeMaxResults(Int)
    case changeModel(Model)
    case changeBottomSheetViewBottomSpace(Bool)
  }

  // MARK: Delegates
  weak var delegate: InferenceViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var choseModelButton: UIButton!
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!
  @IBOutlet weak var inferenceTimeLabel: UILabel!
  @IBOutlet weak var thresholdValueLabel: UILabel!
  @IBOutlet weak var thresholdStepper: UIStepper!
  @IBOutlet weak var maxResultStepper: UIStepper!
  @IBOutlet weak var maxResultLabel: UILabel!
  @IBOutlet weak var toggleBottomSheetButton: UIButton!
  @IBOutlet weak var toggleBottomSheetButtonTopSpace: NSLayoutConstraint!
  
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
    inferenceTimeNameLabel.isHidden = false
  }

  // MARK: - Private function
  private func setupUI() {

    maxResultStepper.value = Double(InferenceConfigManager.sharedInstance.maxResults)
    maxResultLabel.text = "\(InferenceConfigManager.sharedInstance.maxResults)"

    thresholdStepper.value = Double(InferenceConfigManager.sharedInstance.scoreThreshold)
    thresholdValueLabel.text = "\(InferenceConfigManager.sharedInstance.scoreThreshold)"

    // Choose model option
    let selectedModelAction = {(action: UIAction) in
      self.updateModel(modelTitle: action.title)
    }
    
    let actions: [UIAction] = Model.allCases.compactMap { model in
      return UIAction(
        title: model.name,
        state: (InferenceConfigManager.sharedInstance.model == model) ? .on : .off,
        handler: selectedModelAction
      )
    }
    
    choseModelButton.menu = UIMenu(children: actions)
    choseModelButton.showsMenuAsPrimaryAction = true
    choseModelButton.changesSelectionAsPrimaryAction = true
  }
  
  private func updateModel(modelTitle: String) {
    guard let model = Model(name: modelTitle) else {
      return
    }
    InferenceConfigManager.sharedInstance.model = model
  }
  
  private func enableOrDisableClicks() {
    choseModelButton.isEnabled = isUIEnabled
    maxResultStepper.isEnabled = isUIEnabled
    thresholdStepper.isEnabled = isUIEnabled
  }
  
  // MARK: IBAction
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    inferenceTimeLabel.isHidden = !sender.isSelected
    inferenceTimeNameLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, didSwitchBottomSheetViewState: sender.isSelected)
  }

  @IBAction func thresholdStepperValueChanged(_ sender: UIStepper) {
    let scoreThreshold = Float(sender.value)
    InferenceConfigManager.sharedInstance.scoreThreshold = scoreThreshold
    thresholdValueLabel.text = "\(scoreThreshold)"
  }

  @IBAction func maxResultStepperValueChanged(_ sender: UIStepper) {
    let maxResults = Int(sender.value)
    InferenceConfigManager.sharedInstance.maxResults = maxResults
    maxResultLabel.text = "\(maxResults)"
  }
}
