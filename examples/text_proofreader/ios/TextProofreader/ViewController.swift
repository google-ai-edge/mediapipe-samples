// Copyright 2026 The MediaPipe Authors.
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

  private let titleLabel = UILabel()
  private let inputTextView = UITextView()
  private let proofreadButton = UIButton(type: .system)
  private let streamButton = UIButton(type: .system)
  private let outputTextView = UITextView()
  private let activityIndicator = UIActivityIndicatorView(style: .large)

  private var helper: TextProofreaderHelper?

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = .white
    setupUI()
    initializeHelper()
  }

  private func setupUI() {
    let stackView = UIStackView()
    stackView.axis = .vertical
    stackView.spacing = 10
    stackView.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(stackView)

    NSLayoutConstraint.activate([
      stackView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 20),
      stackView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
      stackView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),
      stackView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -20)
    ])

    titleLabel.text = "Text Proofreader"
    titleLabel.font = .boldSystemFont(ofSize: 24)
    titleLabel.textAlignment = .center
    stackView.addArrangedSubview(titleLabel)

    let inputLabel = UILabel()
    inputLabel.text = "Input Text (with errors):"
    inputLabel.font = .systemFont(ofSize: 14)
    stackView.addArrangedSubview(inputLabel)

    inputTextView.layer.borderColor = UIColor.lightGray.cgColor
    inputTextView.layer.borderWidth = 1
    inputTextView.font = .systemFont(ofSize: 16)
    inputTextView.text = "I has a apple. It are very good and i likes it. MediaPipe help me to build cool apps."
    stackView.addArrangedSubview(inputTextView)
    inputTextView.heightAnchor.constraint(equalToConstant: 150).isActive = true

    let buttonStack = UIStackView()
    buttonStack.axis = .horizontal
    buttonStack.distribution = .fillEqually
    buttonStack.spacing = 10
    
    proofreadButton.setTitle("Proofread", for: .normal)
    proofreadButton.addTarget(self, action: #selector(proofreadClicked), for: .touchUpInside)
    buttonStack.addArrangedSubview(proofreadButton)

    streamButton.setTitle("Stream", for: .normal)
    streamButton.addTarget(self, action: #selector(streamClicked), for: .touchUpInside)
    buttonStack.addArrangedSubview(streamButton)
    
    stackView.addArrangedSubview(buttonStack)

    let outputLabel = UILabel()
    outputLabel.text = "Proofread Text:"
    outputLabel.font = .systemFont(ofSize: 14)
    stackView.addArrangedSubview(outputLabel)

    outputTextView.layer.borderColor = UIColor.lightGray.cgColor
    outputTextView.layer.borderWidth = 1
    outputTextView.font = .systemFont(ofSize: 16)
    outputTextView.isEditable = false
    stackView.addArrangedSubview(outputTextView)

    view.addSubview(activityIndicator)
    activityIndicator.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      activityIndicator.centerXAnchor.constraint(equalTo: view.centerXAnchor),
      activityIndicator.centerYAnchor.constraint(equalTo: view.centerYAnchor)
    ])
  }

  private func initializeHelper() {
    guard let modelPath = Bundle.main.path(forResource: "proofread_quant_200m", ofType: "litertlm") else {
      print("Model not found")
      return
    }
    helper = TextProofreaderHelper(modelPath: modelPath)
  }

  @objc private func proofreadClicked() {
    guard let text = inputTextView.text, !text.isEmpty else { return }
    outputTextView.text = ""
    activityIndicator.startAnimating()
    
    DispatchQueue.global(qos: .userInitiated).async {
      let result = self.helper?.proofread(text: text)
      DispatchQueue.main.async {
        self.activityIndicator.stopAnimating()
        self.outputTextView.text = result?.proofreadText ?? "No result"
        // In a more advanced app, we could highlight corrections here.
      }
    }
  }

  @objc private func streamClicked() {
    guard let text = inputTextView.text, !text.isEmpty else { return }
    outputTextView.text = ""
    activityIndicator.startAnimating()

    helper?.proofreadStreaming(text: text) { [weak self] chunk, done, error in
      DispatchQueue.main.async {
        if let error = error {
          self?.outputTextView.text = "Error: \(error.localizedDescription)"
          self?.activityIndicator.stopAnimating()
          return
        }
        if let chunk = chunk {
          self?.outputTextView.text += chunk
        }
        if done {
          self?.activityIndicator.stopAnimating()
        }
      }
    }
  }
}
