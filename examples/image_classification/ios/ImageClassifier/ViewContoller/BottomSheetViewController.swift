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

/** The view controller is responsible for presenting the controls to change the meta data for the image classifier (model, max results,
 * score threshold) and updating the singleton`` ClassifierMetadata`` on user input.
 */
class BottomSheetViewController: UIViewController {

  // MARK: Delegates
  weak var delegate: BottomSheetViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var inferenceTimeLabel: UILabel!
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!

  @IBOutlet weak var thresholdStepper: UIStepper!
  @IBOutlet weak var thresholdValueLabel: UILabel!

  @IBOutlet weak var maxResultStepper: UIStepper!
  @IBOutlet weak var maxResultLabel: UILabel!

  @IBOutlet weak var toggleBottomSheetButton: UIButton!

  @IBOutlet weak var choseModelButton: UIButton!

  @IBOutlet weak var tableView: UITableView!

  // MARK: Constants
  private let normalCellHeight: CGFloat = 27.0
  private var imageClassifierResult: ImageClassifierResult?

  // MARK: Computed properties
  var collapsedHeight: CGFloat {
    return normalCellHeight * CGFloat(InferenceConfigManager.sharedInstance.maxResults)
  }
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
  func update(inferenceTimeString: String, result: ImageClassifierResult?) {
    inferenceTimeLabel.text = inferenceTimeString
    imageClassifierResult = result
    tableView.reloadData()
  }

  // MARK: - Private function
  private func setupUI() {

    maxResultStepper.value = Double(InferenceConfigManager.sharedInstance.maxResults)
    maxResultLabel.text = "\(InferenceConfigManager.sharedInstance.maxResults)"

    thresholdStepper.value = Double(InferenceConfigManager.sharedInstance.scoreThreshold)
    thresholdValueLabel.text = "\(InferenceConfigManager.sharedInstance.scoreThreshold)"

    // Chose model option
    let choseModel = {(action: UIAction) in
      self.updateModel(modelTitle: action.title)
    }
    let actions: [UIAction] = Model.allCases.compactMap { model in
      let action = UIAction(title: model.rawValue, handler: choseModel)
      if model == InferenceConfigManager.sharedInstance.model {
        action.state = .on
      }
      return action
    }
    choseModelButton.menu = UIMenu(children: actions)
    choseModelButton.showsMenuAsPrimaryAction = true
    choseModelButton.changesSelectionAsPrimaryAction = true

    // Setup table view cell height
    tableView.rowHeight = normalCellHeight
  }

  private func updateModel(modelTitle: String) {
    guard let model = Model(rawValue: modelTitle) else { return }
    InferenceConfigManager.sharedInstance.model = model
  }
  
  private func enableOrDisableClicks() {
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

extension BottomSheetViewController: UITableViewDataSource {
  func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    return InferenceConfigManager.sharedInstance.maxResults
  }

  func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    let cell = tableView.dequeueReusableCell(withIdentifier: "INFO_CELL") as! InfoCell
    guard let imageClassifierResult = imageClassifierResult,
          let classification = imageClassifierResult.classificationResult.classifications.first else {
      cell.fieldNameLabel.text = "--"
      cell.infoLabel.text = "--"
      return cell
    }
    if indexPath.row < classification.categories.count {
      let category = classification.categories[indexPath.row]
      cell.fieldNameLabel.text = category.categoryName
      cell.infoLabel.text = String(format: "%.2f", category.score)
    } else {
      cell.fieldNameLabel.text = "--"
      cell.infoLabel.text = "--"
    }
    return cell
  }
}

// MARK: Info cell
class InfoCell: UITableViewCell {
  @IBOutlet weak var fieldNameLabel: UILabel!
  @IBOutlet weak var infoLabel: UILabel!
}
