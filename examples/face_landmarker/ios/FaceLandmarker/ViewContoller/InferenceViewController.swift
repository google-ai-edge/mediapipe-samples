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
    case changeDetectionConfidence(Float)
    case changePresenceConfidence(Float)
    case changeTrackingConfidence(Float)
    case changeBottomSheetViewBottomSpace(Bool)
  }

  // MARK: Delegate
  var delegate: InferenceViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var infrenceTimeLabel: UILabel!
  @IBOutlet weak var infrenceTimeTitleLabel: UILabel!
  @IBOutlet weak var detectionConfidenceStepper: UIStepper!
  @IBOutlet weak var detectionConfidenceValueLabel: UILabel!

  @IBOutlet weak var presenceConfidenceStepper: UIStepper!
  @IBOutlet weak var presenceConfidenceValueLabel: UILabel!

  @IBOutlet weak var minTrackingConfidenceStepper: UIStepper!
  @IBOutlet weak var minTrackingConfidenceValueLabel: UILabel!

  @IBOutlet weak var numFacesStepper: UIStepper!
  @IBOutlet weak var numFacestLabel: UILabel!

  // MARK: Instance Variables
  var result: ResultBundle? = nil
  var numFaces = DefaultConstants.numFaces
  var minFaceDetectionConfidence = DefaultConstants.detectionConfidence
  var minFacePresenceConfidence = DefaultConstants.presenceConfidence
  var minTrackingConfidence = DefaultConstants.trackingConfidence

  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
  }

  // Private function
  private func setupUI() {

    detectionConfidenceStepper.value = Double(minFaceDetectionConfidence)
    detectionConfidenceValueLabel.text = "\(minFaceDetectionConfidence)"

    presenceConfidenceStepper.value = Double(minFacePresenceConfidence)
    presenceConfidenceValueLabel.text = "\(minFacePresenceConfidence)"

    minTrackingConfidenceStepper.value = Double(minTrackingConfidence)
    minTrackingConfidenceValueLabel.text = "\(minTrackingConfidence)"

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

  @IBAction func detectionConfidenceStepperValueChanged(_ sender: UIStepper) {
    minFaceDetectionConfidence = Float(sender.value)
    delegate?.viewController(self, needPerformActions: .changeDetectionConfidence(minFaceDetectionConfidence))
    detectionConfidenceValueLabel.text = "\(minFaceDetectionConfidence)"
  }

  @IBAction func presenceConfidenceStepperValueChanged(_ sender: UIStepper) {
    minFacePresenceConfidence = Float(sender.value)
    delegate?.viewController(self, needPerformActions: .changePresenceConfidence(minFacePresenceConfidence))
    presenceConfidenceValueLabel.text = "\(minFacePresenceConfidence)"
  }

  @IBAction func minTrackingConfidenceStepperValueChanged(_ sender: UIStepper) {
    minTrackingConfidence = Float(sender.value)
    delegate?.viewController(self, needPerformActions: .changeTrackingConfidence(minTrackingConfidence))
    minTrackingConfidenceValueLabel.text = "\(minTrackingConfidence)"
  }

  @IBAction func numFacesStepperValueChanged(_ sender: UIStepper) {
    numFaces = Int(sender.value)
    delegate?.viewController(self, needPerformActions: .changeNumFaces(numFaces))
    numFacestLabel.text = "\(numFaces)"
  }
}
