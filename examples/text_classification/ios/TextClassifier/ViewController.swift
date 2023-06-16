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
import MediaPipeTasksText

class ViewController: UIViewController {
  
  // IBOutlet
  @IBOutlet weak var tableView: UITableView!
  @IBOutlet weak var chooseModelButton: UIButton!
  @IBOutlet weak var classifyButton: UIButton!
  @IBOutlet weak var clearButton: UIButton!
  @IBOutlet weak var inputTextView: UITextView!
  @IBOutlet var titleView: UIView!
  @IBOutlet weak var inferenceTimeLabel: UILabel!
  @IBOutlet weak var settingViewHeightLayoutConstraint: NSLayoutConstraint!
  
  // variable
  var defaultModel = Model.mobileBert
  var textClassifier: TextClassifierHelper!
  var categories: [ResultCategory] = []
  
  override func viewDidLoad() {
    super.viewDidLoad()
    setupUI()
    setupTableView()
    textClassifier = TextClassifierHelper(model: defaultModel)
    hideKeyboardWhenTappedAround()
    inputTextView.delegate = self
  }
  
  // IBAction
  @IBAction func classifyButtonTouchUpInside(_ sender: Any) {
    let timeStart = Date()
    let result = textClassifier.classify(text: inputTextView.text)
    guard let result = result,
          let classification = result.classificationResult.classifications.first else { return }
    let inferenceTime = Date().timeIntervalSinceReferenceDate - timeStart.timeIntervalSinceReferenceDate
    categories = classification.categories
    inferenceTimeLabel.text = String(format: "%.2fms", inferenceTime * 1000)
    tableView.reloadData()
  }
  
  @IBAction func clearButtonTouchUpInside(_ sender: Any) {
    inputTextView.text = ""
    clearButton.isEnabled = false
    classifyButton.isEnabled = false
  }
  @IBAction func expandButtonTouchUpInside(_ sender: UIButton) {
    sender.isSelected.toggle()
    settingViewHeightLayoutConstraint.constant = sender.isSelected ? 160 : 80
    UIView.animate(withDuration: 0.3) {
      self.view.layoutSubviews()
    }
  }
  
  // Private function
  private func setupUI() {
    
    navigationItem.titleView = titleView
    
    // Chose model option
    let choseModel = {(action: UIAction) in
      self.update(modelTitle: action.title)
    }
    let actions: [UIAction] = Model.allCases.compactMap { model in
      let action = UIAction(title: model.rawValue, handler: choseModel)
      if model == defaultModel {
        action.state = .on
      }
      return action
    }
    chooseModelButton.menu = UIMenu(children: actions)
    chooseModelButton.showsMenuAsPrimaryAction = true
    chooseModelButton.changesSelectionAsPrimaryAction = true
    inputTextView.text = Texts.defaultText
  }
  
  private func setupTableView() {
    tableView.register(UITableViewCell.self, forCellReuseIdentifier: "DefaultCell")
    tableView.dataSource = self
    tableView.delegate = self
    tableView.rowHeight = 44
  }
  
  private func hideKeyboardWhenTappedAround() {
    let tap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
    tap.cancelsTouchesInView = false
    view.addGestureRecognizer(tap)
  }
  
  @objc private func dismissKeyboard() {
    view.endEditing(true)
  }
  
  private func update(modelTitle: String) {
    guard let model = Model(rawValue: modelTitle) else { return }
    textClassifier = TextClassifierHelper(model: model)
  }
}

// MARK: - UITableViewDataSource, UITableViewDelegate
extension ViewController: UITableViewDataSource, UITableViewDelegate {
  func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    categories.count
  }
  
  func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    let cell = tableView.dequeueReusableCell(withIdentifier: "DefaultCell")!
    let categorie = categories[indexPath.row]
    let text = (categorie.categoryName ?? "") + " (\(categorie.score))"
    cell.textLabel?.text = text
    return cell
  }
}

// MARK: UITextViewDelegate
extension ViewController: UITextViewDelegate {
  func textViewDidChange(_ textView: UITextView) {
    clearButton.isEnabled = !textView.text.isEmpty
    classifyButton.isEnabled = !textView.text.isEmpty
  }
}
