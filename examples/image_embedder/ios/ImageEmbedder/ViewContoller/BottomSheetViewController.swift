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
import MediaPipeTasksVision

protocol BottomSheetViewControllerDelegate: AnyObject {
  /**
   This method is called when the user opens or closes the bottom sheet.
  **/
  func viewController(
    _ viewController: BottomSheetViewController,
    didSwitchBottomSheetViewState isOpen: Bool)
}

/** The view controller is responsible for presenting the controls to change the meta data for the image embedder (model, max results,
 * score threshold) and updating the singleton`` EmbedderMetadata`` on user input.
 */
class BottomSheetViewController: UIViewController {

  // MARK: Delegates
  weak var delegate: BottomSheetViewControllerDelegate?

  // MARK: Storyboards Connections
  @IBOutlet weak var similarityLabel: UILabel!
  @IBOutlet weak var inferenceTimeLabel: UILabel!
  @IBOutlet weak var inferenceTimeNameLabel: UILabel!
  @IBOutlet weak var toggleBottomSheetButton: UIButton!
  @IBOutlet weak var choseModelButton: UIButton!
  @IBOutlet weak var chooseDelegateButton: UIButton!

  // MARK: Constants
  private var imageEmbedderResult: ImageEmbedderResult?

  
  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
  }
  
  // MARK: - Public Functions
  func update(inferenceTimeString: String, similarity: Float?) {
    inferenceTimeLabel.text = inferenceTimeString
    if let similarity = similarity {
      similarityLabel.text = "Similarity: \(similarity)"
    } else {
      similarityLabel.text = "Similarity: --------"
    }
  }

  // MARK: - Private function
  private func setupUI() {

    // Chose model option
    let choseModel = {(action: UIAction) in
      self.updateModel(modelTitle: action.title)
    }
    let actions: [UIAction] = Model.allCases.compactMap { model in
      let action = UIAction(title: model.rawValue, handler: choseModel)
      if model == InferenceConfigurationManager.sharedInstance.model {
        action.state = .on
      }
      return action
    }
    choseModelButton.menu = UIMenu(children: actions)
    choseModelButton.showsMenuAsPrimaryAction = true
    choseModelButton.changesSelectionAsPrimaryAction = true

    // Chose delegate option
    let selectedDelegateAction = {(action: UIAction) in
      self.updateDelegate(title: action.title)
    }
    let delegateActions: [UIAction] = ImageEmbedderDelegate.allCases.compactMap { delegate in
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

  private func updateModel(modelTitle: String) {
    guard let model = Model(rawValue: modelTitle) else { return }
    InferenceConfigurationManager.sharedInstance.model = model
  }

  private func updateDelegate(title: String) {
    guard let delegate = ImageEmbedderDelegate(name: title) else { return }
    InferenceConfigurationManager.sharedInstance.delegate = delegate
  }
  
  // MARK: IBAction
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    inferenceTimeLabel.isHidden = !sender.isSelected
    inferenceTimeNameLabel.isHidden = !sender.isSelected
    delegate?.viewController(self, didSwitchBottomSheetViewState: sender.isSelected)
  }
}
