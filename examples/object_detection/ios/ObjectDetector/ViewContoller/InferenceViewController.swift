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

protocol InferenceViewControllerDelegate {

  /**
   This method is called when the user changes the value to update model used for inference.
   **/
  func viewController(
    _ viewController: InferenceViewController,
    needPerformActions action: InferenceViewController.Action)
}

class InferenceViewController: UIViewController {

  enum Action {
    case changeScoreThreshold(Float)
    case changeMaxResults(Int)
    case changeModel(Model)
    case changeBottomSheetViewBottomSpace(Bool)
  }

  // MARK: Delegate
  var delegate: InferenceViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var choseModelButton: UIButton!

  @IBOutlet weak var infrenceTimeLabel: UILabel!
  @IBOutlet weak var infrenceTimeTitleLabel: UILabel!
  @IBOutlet weak var thresholdStepper: UIStepper!
  @IBOutlet weak var thresholdValueLabel: UILabel!

  @IBOutlet weak var maxResultStepper: UIStepper!
  @IBOutlet weak var maxResultLabel: UILabel!

  // MARK: Instance Variables
  var objectDetectorHelperResult: ObjectDetectorHelperResult? = nil
  var maxResults = DefaultConstants.maxResults
  var scoreThreshold = DefaultConstants.scoreThreshold
  var modelChose = DefaultConstants.model

  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
  }

  // Private function
  private func setupUI() {

    maxResultStepper.value = Double(maxResults)
    maxResultLabel.text = "\(maxResults)"

    thresholdStepper.value = Double(scoreThreshold)
    thresholdValueLabel.text = "\(scoreThreshold)"

    // Chose model option
    let choseModel = {(action: UIAction) in
      self.updateModel(modelTitle: action.title)
    }
    let actions: [UIAction] = Model.allCases.compactMap { model in
      let action = UIAction(title: model.rawValue, handler: choseModel)
      if model == modelChose {
        action.state = .on
      }
      return action
    }
    choseModelButton.menu = UIMenu(children: actions)
    choseModelButton.showsMenuAsPrimaryAction = true
    choseModelButton.changesSelectionAsPrimaryAction = true
  }
  
  private func updateModel(modelTitle: String) {
    guard let model = Model(rawValue: modelTitle) else { return }
    delegate?.viewController(self, needPerformActions: .changeModel(model))
  }

  // Public function
  func updateData() {
    if let inferenceTime = objectDetectorHelperResult?.inferenceTime {
      infrenceTimeLabel.text = String(format: "%.2fms", inferenceTime)
    }
  }
  // MARK: IBAction

  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    infrenceTimeLabel.isHidden = !sender.isSelected
    infrenceTimeTitleLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, needPerformActions: .changeBottomSheetViewBottomSpace(sender.isSelected))
  }

  @IBAction func thresholdStepperValueChanged(_ sender: UIStepper) {
    scoreThreshold = Float(sender.value)
    delegate?.viewController(self, needPerformActions: .changeScoreThreshold(scoreThreshold))
    thresholdValueLabel.text = "\(scoreThreshold)"
  }

  @IBAction func maxResultStepperValueChanged(_ sender: UIStepper) {
    maxResults = Int(sender.value)
    delegate?.viewController(self, needPerformActions: .changeMaxResults(maxResults))
    maxResultLabel.text = "\(maxResults)"
  }
}
