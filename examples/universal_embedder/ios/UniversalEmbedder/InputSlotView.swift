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

/// What a slot currently holds: a typed sentence or one of the bundled photos.
enum SlotInput {
  case text(String)
  case image(name: String, image: UIImage)

  /// Short description used in the per-slot info line.
  var label: String {
    switch self {
    case .text: return "text"
    case .image(let name, _): return name
    }
  }
}

/// One of the two comparison inputs: a text field or a strip of sample photos.
class InputSlotView: UIView {

  /// Called whenever the input changes, so a stale similarity score can be dropped.
  var onChange: (() -> Void)?

  private let imageNames: [String]
  private let titleLabel = UILabel()
  private let modeControl = UISegmentedControl(items: ["Text", "Image"])
  private let textField = UITextField()
  private let imageScrollView = UIScrollView()
  private let imageStack = UIStackView()
  private let infoLabel = UILabel()
  private let contentStack = UIStackView()

  private var thumbnailButtons: [UIButton] = []
  private var selectedImageIndex = 0

  init(title: String, imageNames: [String]) {
    self.imageNames = imageNames
    super.init(frame: .zero)

    backgroundColor = Palette.surface
    layer.cornerRadius = 16
    layer.shadowColor = UIColor.black.cgColor
    layer.shadowOpacity = 0.06
    layer.shadowRadius = 5
    layer.shadowOffset = CGSize(width: 0, height: 2)

    titleLabel.text = title
    titleLabel.font = .systemFont(ofSize: 13, weight: .semibold)
    titleLabel.textColor = Palette.onSurfaceMuted

    modeControl.selectedSegmentIndex = 0
    modeControl.selectedSegmentTintColor = Palette.primary
    modeControl.setTitleTextAttributes([.foregroundColor: UIColor.white], for: .selected)
    modeControl.addTarget(self, action: #selector(modeChanged), for: .valueChanged)

    textField.placeholder = "Type something to embed…"
    textField.font = .systemFont(ofSize: 16)
    textField.borderStyle = .roundedRect
    textField.returnKeyType = .done
    textField.delegate = self
    textField.addTarget(self, action: #selector(textChanged), for: .editingChanged)
    textField.heightAnchor.constraint(equalToConstant: 42).isActive = true

    imageStack.axis = .horizontal
    imageStack.spacing = 8
    imageStack.translatesAutoresizingMaskIntoConstraints = false

    imageScrollView.showsHorizontalScrollIndicator = false
    imageScrollView.addSubview(imageStack)
    imageScrollView.heightAnchor.constraint(equalToConstant: 72).isActive = true
    imageScrollView.isHidden = true

    infoLabel.font = .monospacedDigitSystemFont(ofSize: 12, weight: .regular)
    infoLabel.textColor = Palette.primary
    infoLabel.isHidden = true

    contentStack.axis = .vertical
    contentStack.spacing = 10
    contentStack.translatesAutoresizingMaskIntoConstraints = false
    [titleLabel, modeControl, textField, imageScrollView, infoLabel].forEach {
      contentStack.addArrangedSubview($0)
    }
    addSubview(contentStack)

    NSLayoutConstraint.activate([
      contentStack.topAnchor.constraint(equalTo: topAnchor, constant: 14),
      contentStack.bottomAnchor.constraint(equalTo: bottomAnchor, constant: -14),
      contentStack.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 14),
      contentStack.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -14),

      imageStack.topAnchor.constraint(equalTo: imageScrollView.topAnchor),
      imageStack.bottomAnchor.constraint(equalTo: imageScrollView.bottomAnchor),
      imageStack.leadingAnchor.constraint(equalTo: imageScrollView.leadingAnchor),
      imageStack.trailingAnchor.constraint(equalTo: imageScrollView.trailingAnchor),
      imageStack.heightAnchor.constraint(equalTo: imageScrollView.heightAnchor),
    ])

    buildImageStrip()
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  // MARK: - Public API

  func setText(_ text: String) {
    textField.text = text
  }

  /// Switches the slot to the image strip and picks `index`.
  func selectImageMode(index: Int) {
    modeControl.selectedSegmentIndex = 1
    selectImage(at: index)
    applyMode()
  }

  func showInfo(_ text: String) {
    infoLabel.text = text
    infoLabel.isHidden = false
  }

  func clearInfo() {
    infoLabel.isHidden = true
  }

  func endEditing() {
    textField.resignFirstResponder()
  }

  var currentInput: SlotInput? {
    if modeControl.selectedSegmentIndex == 0 {
      let text = textField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
      return text.isEmpty ? nil : .text(text)
    }
    let name = imageNames[selectedImageIndex]
    guard let image = Self.loadImage(named: name) else { return nil }
    return .image(name: name, image: image)
  }

  // MARK: - Internals

  private func buildImageStrip() {
    for (index, name) in imageNames.enumerated() {
      let button = UIButton(type: .custom)
      button.setImage(Self.loadImage(named: name), for: .normal)
      button.imageView?.contentMode = .scaleAspectFill
      button.contentHorizontalAlignment = .fill
      button.contentVerticalAlignment = .fill
      button.clipsToBounds = true
      button.layer.cornerRadius = 10
      button.layer.borderWidth = 2
      button.tag = index
      button.addTarget(self, action: #selector(thumbnailTapped(_:)), for: .touchUpInside)
      button.translatesAutoresizingMaskIntoConstraints = false
      button.widthAnchor.constraint(equalToConstant: 72).isActive = true
      imageStack.addArrangedSubview(button)
      thumbnailButtons.append(button)
    }
    highlightSelection()
  }

  private func selectImage(at index: Int) {
    selectedImageIndex = index
    highlightSelection()
  }

  private func highlightSelection() {
    for (index, button) in thumbnailButtons.enumerated() {
      button.layer.borderColor =
        (index == selectedImageIndex ? Palette.primary : Palette.scoreTrack).cgColor
    }
  }

  private func applyMode() {
    let isText = modeControl.selectedSegmentIndex == 0
    textField.isHidden = !isText
    imageScrollView.isHidden = isText
    infoLabel.isHidden = true
  }

  @objc private func modeChanged() {
    applyMode()
    onChange?()
  }

  @objc private func textChanged() {
    onChange?()
  }

  @objc private func thumbnailTapped(_ sender: UIButton) {
    selectImage(at: sender.tag)
    onChange?()
  }

  static func loadImage(named name: String) -> UIImage? {
    guard let path = Bundle.main.path(forResource: name, ofType: "jpg", inDirectory: "images")
    else { return nil }
    return UIImage(contentsOfFile: path)
  }
}

// MARK: - UITextFieldDelegate

extension InputSlotView: UITextFieldDelegate {
  func textFieldShouldReturn(_ textField: UITextField) -> Bool {
    textField.resignFirstResponder()
    return true
  }
}
