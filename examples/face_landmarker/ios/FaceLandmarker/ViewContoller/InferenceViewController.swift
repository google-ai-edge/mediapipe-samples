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
    case changeNumFaces(Int)
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

  @IBOutlet weak var numFacesStepper: UIStepper!
  @IBOutlet weak var numFacestLabel: UILabel!

  // MARK: Instance Variables
  var result: ResultBundle? = nil
  var numFaces = DefaultConstants.numFaces

  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
  }

  // Private function
  private func setupUI() {

    numFacesStepper.value = Double(numFaces)
    numFacestLabel.text = "\(numFaces)"
  }

  // Public function
  func updateData() {
    if let inferenceTime = result?.inferenceTime {
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
  }

  @IBAction func numFacesStepperValueChanged(_ sender: UIStepper) {
    numFaces = Int(sender.value)
    delegate?.viewController(self, needPerformActions: .changeNumFaces(numFaces))
    numFacestLabel.text = "\(numFaces)"
  }
}
