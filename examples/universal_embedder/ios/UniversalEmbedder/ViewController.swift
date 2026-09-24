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
import MediaPipeTasksRetrieval

/// Embeds two inputs — each either text or an image — and reports their cosine similarity.
///
/// Because `UniversalEmbedder` projects every modality into the same vector space, "a yellow piece
/// of fruit" can be compared directly against a photo of a banana.
///
/// The engine is not reentrant, so every call into it is serialized on `embedderQueue`.
class ViewController: UIViewController {

  private let sampleImages = [
    "red_apple", "yellow_banana", "cute_cat", "fast_car", "green_tree",
    "blue_sky", "coffee_mug", "open_book", "sunny_beach", "snowy_mountain",
  ]

  // MARK: - Views

  private let headerView = GradientView()
  private let scrollView = UIScrollView()
  private let contentStack = UIStackView()
  private let controlCard = UIView()
  private let acceleratorLabel = UILabel()
  private let gpuLabel = UILabel()
  private let gpuSwitch = UISwitch()
  private let statusSpinner = UIActivityIndicatorView(style: .medium)
  private let statusLabel = UILabel()
  private lazy var slotA = InputSlotView(title: "Input A", imageNames: sampleImages)
  private lazy var slotB = InputSlotView(title: "Input B", imageNames: sampleImages)
  private let vsLabel = UILabel()
  private let compareButton = UIButton(type: .system)
  private let resultCard = UIView()
  private let similarityCaptionLabel = UILabel()
  private let similarityLabel = UILabel()
  private let similarityBar = UIProgressView(progressViewStyle: .default)
  private let vectorInfoLabel = UILabel()

  // MARK: - State

  private let helper = UniversalEmbedderHelper()
  private let embedderQueue = DispatchQueue(
    label: "com.google.mediapipe.examples.universalembedder.engine")

  private var isBusy = false
  private var useGPU = false

  // MARK: - Lifecycle

  override func viewDidLoad() {
    super.viewDidLoad()
    view.backgroundColor = Palette.background
    setupUI()

    // A text/image pairing out of the box, so the cross-modal point lands immediately.
    slotA.setText("a yellow piece of fruit")
    slotB.selectImageMode(index: 1)  // yellow_banana
    slotA.onChange = { [weak self] in self?.invalidateResult() }
    slotB.onChange = { [weak self] in self?.invalidateResult() }

    prepare()
  }

  deinit {
    let helper = self.helper
    // Releasing the native engine can block, so keep it off the main thread.
    embedderQueue.async { helper.close() }
  }

  // MARK: - UI

  private func setupUI() {
    headerView.colors = [Palette.primary, Palette.primaryVariant]
    headerView.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(headerView)

    let titleLabel = UILabel()
    titleLabel.text = "Universal Embedder"
    titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
    titleLabel.textColor = .white
    titleLabel.translatesAutoresizingMaskIntoConstraints = false
    headerView.addSubview(titleLabel)

    scrollView.alwaysBounceVertical = true
    scrollView.keyboardDismissMode = .onDrag
    scrollView.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(scrollView)

    contentStack.axis = .vertical
    contentStack.spacing = 14
    contentStack.translatesAutoresizingMaskIntoConstraints = false
    scrollView.addSubview(contentStack)

    setupControlCard()

    vsLabel.text = "VS"
    vsLabel.font = .systemFont(ofSize: 13, weight: .bold)
    vsLabel.textColor = Palette.onSurfaceMuted
    vsLabel.textAlignment = .center

    compareButton.setTitle("Compare embeddings", for: .normal)
    compareButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
    compareButton.backgroundColor = Palette.primary
    compareButton.setTitleColor(.white, for: .normal)
    compareButton.layer.cornerRadius = 12
    compareButton.addTarget(self, action: #selector(compareTapped), for: .touchUpInside)
    compareButton.heightAnchor.constraint(equalToConstant: 48).isActive = true

    setupResultCard()

    [controlCard, slotA, vsLabel, slotB, compareButton, resultCard].forEach {
      contentStack.addArrangedSubview($0)
    }

    NSLayoutConstraint.activate([
      headerView.topAnchor.constraint(equalTo: view.topAnchor),
      headerView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      headerView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      headerView.bottomAnchor.constraint(
        equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 60),

      titleLabel.leadingAnchor.constraint(equalTo: headerView.leadingAnchor, constant: 20),
      titleLabel.trailingAnchor.constraint(equalTo: headerView.trailingAnchor, constant: -20),
      titleLabel.bottomAnchor.constraint(equalTo: headerView.bottomAnchor, constant: -22),

      scrollView.topAnchor.constraint(equalTo: headerView.bottomAnchor),
      scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),

      contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: 4),
      contentStack.bottomAnchor.constraint(
        equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -24),
      contentStack.leadingAnchor.constraint(
        equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: 16),
      contentStack.trailingAnchor.constraint(
        equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -16),
    ])
  }

  private func setupControlCard() {
    controlCard.backgroundColor = Palette.surface
    controlCard.layer.cornerRadius = 16
    controlCard.layer.shadowColor = UIColor.black.cgColor
    controlCard.layer.shadowOpacity = 0.08
    controlCard.layer.shadowRadius = 6
    controlCard.layer.shadowOffset = CGSize(width: 0, height: 2)

    acceleratorLabel.text = "Accelerator"
    acceleratorLabel.font = .systemFont(ofSize: 13, weight: .semibold)
    acceleratorLabel.textColor = Palette.onSurfaceMuted
    acceleratorLabel.translatesAutoresizingMaskIntoConstraints = false
    controlCard.addSubview(acceleratorLabel)

    gpuLabel.text = "GPU"
    gpuLabel.font = .systemFont(ofSize: 12)
    gpuLabel.textColor = Palette.onSurfaceMuted
    gpuLabel.translatesAutoresizingMaskIntoConstraints = false
    controlCard.addSubview(gpuLabel)

    gpuSwitch.onTintColor = Palette.primary
    gpuSwitch.addTarget(self, action: #selector(gpuSwitched), for: .valueChanged)
    gpuSwitch.translatesAutoresizingMaskIntoConstraints = false
    controlCard.addSubview(gpuSwitch)

    statusSpinner.color = Palette.primary
    statusSpinner.hidesWhenStopped = true
    statusSpinner.translatesAutoresizingMaskIntoConstraints = false
    controlCard.addSubview(statusSpinner)

    statusLabel.text = "Loading model…"
    statusLabel.font = .systemFont(ofSize: 13)
    statusLabel.textColor = Palette.onSurfaceMuted
    statusLabel.numberOfLines = 0
    statusLabel.translatesAutoresizingMaskIntoConstraints = false
    controlCard.addSubview(statusLabel)

    NSLayoutConstraint.activate([
      acceleratorLabel.topAnchor.constraint(equalTo: controlCard.topAnchor, constant: 16),
      acceleratorLabel.leadingAnchor.constraint(equalTo: controlCard.leadingAnchor, constant: 16),

      gpuSwitch.centerYAnchor.constraint(equalTo: acceleratorLabel.centerYAnchor),
      gpuSwitch.trailingAnchor.constraint(equalTo: controlCard.trailingAnchor, constant: -16),

      gpuLabel.centerYAnchor.constraint(equalTo: gpuSwitch.centerYAnchor),
      gpuLabel.trailingAnchor.constraint(equalTo: gpuSwitch.leadingAnchor, constant: -8),

      statusSpinner.centerYAnchor.constraint(equalTo: statusLabel.centerYAnchor),
      statusSpinner.leadingAnchor.constraint(equalTo: controlCard.leadingAnchor, constant: 16),
      statusSpinner.widthAnchor.constraint(equalToConstant: 16),

      statusLabel.topAnchor.constraint(equalTo: acceleratorLabel.bottomAnchor, constant: 12),
      statusLabel.leadingAnchor.constraint(equalTo: statusSpinner.trailingAnchor, constant: 8),
      statusLabel.trailingAnchor.constraint(equalTo: controlCard.trailingAnchor, constant: -16),
      statusLabel.bottomAnchor.constraint(equalTo: controlCard.bottomAnchor, constant: -16),
    ])
  }

  private func setupResultCard() {
    resultCard.backgroundColor = Palette.surface
    resultCard.layer.cornerRadius = 16
    resultCard.layer.shadowColor = UIColor.black.cgColor
    resultCard.layer.shadowOpacity = 0.08
    resultCard.layer.shadowRadius = 6
    resultCard.layer.shadowOffset = CGSize(width: 0, height: 2)
    resultCard.isHidden = true

    similarityCaptionLabel.text = "Cosine similarity"
    similarityCaptionLabel.font = .systemFont(ofSize: 13, weight: .semibold)
    similarityCaptionLabel.textColor = Palette.onSurfaceMuted
    similarityCaptionLabel.translatesAutoresizingMaskIntoConstraints = false
    resultCard.addSubview(similarityCaptionLabel)

    similarityLabel.font = .monospacedDigitSystemFont(ofSize: 34, weight: .bold)
    similarityLabel.textColor = Palette.primary
    similarityLabel.translatesAutoresizingMaskIntoConstraints = false
    resultCard.addSubview(similarityLabel)

    similarityBar.progressTintColor = Palette.primary
    similarityBar.trackTintColor = Palette.scoreTrack
    similarityBar.translatesAutoresizingMaskIntoConstraints = false
    resultCard.addSubview(similarityBar)

    vectorInfoLabel.font = .monospacedSystemFont(ofSize: 11, weight: .regular)
    vectorInfoLabel.textColor = Palette.onSurfaceMuted
    vectorInfoLabel.numberOfLines = 0
    vectorInfoLabel.translatesAutoresizingMaskIntoConstraints = false
    resultCard.addSubview(vectorInfoLabel)

    NSLayoutConstraint.activate([
      similarityCaptionLabel.topAnchor.constraint(equalTo: resultCard.topAnchor, constant: 16),
      similarityCaptionLabel.leadingAnchor.constraint(
        equalTo: resultCard.leadingAnchor, constant: 16),

      similarityLabel.topAnchor.constraint(
        equalTo: similarityCaptionLabel.bottomAnchor, constant: 2),
      similarityLabel.leadingAnchor.constraint(equalTo: resultCard.leadingAnchor, constant: 16),

      similarityBar.topAnchor.constraint(equalTo: similarityLabel.bottomAnchor, constant: 10),
      similarityBar.leadingAnchor.constraint(equalTo: resultCard.leadingAnchor, constant: 16),
      similarityBar.trailingAnchor.constraint(equalTo: resultCard.trailingAnchor, constant: -16),

      vectorInfoLabel.topAnchor.constraint(equalTo: similarityBar.bottomAnchor, constant: 12),
      vectorInfoLabel.leadingAnchor.constraint(equalTo: resultCard.leadingAnchor, constant: 16),
      vectorInfoLabel.trailingAnchor.constraint(equalTo: resultCard.trailingAnchor, constant: -16),
      vectorInfoLabel.bottomAnchor.constraint(equalTo: resultCard.bottomAnchor, constant: -16),
    ])
  }

  // MARK: - Actions

  @objc private func gpuSwitched() {
    useGPU = gpuSwitch.isOn
    prepare()
  }

  @objc private func compareTapped() {
    compare()
  }

  /// A shown score belongs to the inputs that produced it, so drop it when they change.
  private func invalidateResult() {
    resultCard.isHidden = true
    slotA.clearInfo()
    slotB.clearInfo()
  }

  // MARK: - Engine work

  private func prepare() {
    setBusy(true, status: "Loading model on \(useGPU ? "GPU" : "CPU")…")
    resultCard.isHidden = true

    embedderQueue.async { [weak self] in
      guard let self else { return }
      do {
        try self.helper.prepare(useGPU: self.useGPU)
        DispatchQueue.main.async {
          self.setBusy(false, status: "Ready · running on \(self.useGPU ? "GPU" : "CPU")")
        }
      } catch {
        print("Initialization failed (\(self.useGPU ? "GPU" : "CPU")): \(error) — \(error.localizedDescription)")
        DispatchQueue.main.async {
          self.setBusy(false, status: "Initialization failed: \(error.localizedDescription)")
        }
      }
    }
  }

  private func compare() {
    guard helper.isReady, !isBusy else {
      show(toast: "Still getting ready, please wait…")
      return
    }
    guard let inputA = slotA.currentInput, let inputB = slotB.currentInput else {
      show(toast: "Fill in both inputs first")
      return
    }
    slotA.endEditing()
    slotB.endEditing()
    setBusy(true, status: "Embedding both inputs…")

    embedderQueue.async { [weak self] in
      guard let self else { return }
      do {
        let runA = try self.embed(inputA)
        DispatchQueue.main.async {
          self.slotA.showInfo(self.info(for: inputA, run: runA))
        }

        let runB = try self.embed(inputB)
        DispatchQueue.main.async {
          self.slotB.showInfo(self.info(for: inputB, run: runB))
        }

        let similarity = try UniversalEmbedderHelper.similarity(runA.embedding, runB.embedding)
        DispatchQueue.main.async {
          self.showResult(
            similarity: similarity,
            embeddingA: runA.embedding,
            embeddingB: runB.embedding,
            totalMilliseconds: runA.milliseconds + runB.milliseconds)
        }
      } catch {
        print("Embedding failed: \(error) — \(error.localizedDescription)")
        DispatchQueue.main.async {
          self.setBusy(false, status: "Embedding failed: \(error.localizedDescription)")
        }
      }
    }
  }

  private func embed(_ input: SlotInput) throws -> EmbeddingRun {
    switch input {
    case .text(let text): return try helper.embed(text: text)
    case .image(_, let image): return try helper.embed(image: image)
    }
  }

  private func info(for input: SlotInput, run: EmbeddingRun) -> String {
    let dimensions = run.embedding.floatEmbedding?.count ?? 0
    return "\(input.label) · \(dimensions)d · \(Int(run.milliseconds.rounded()))ms"
  }

  private func showResult(
    similarity: Double, embeddingA: Embedding, embeddingB: Embedding, totalMilliseconds: Double
  ) {
    resultCard.isHidden = false
    similarityLabel.text = String(format: "%.4f", similarity)
    // Cosine similarity is in [-1, 1]; map onto the 0-1 bar.
    similarityBar.setProgress(Float(max(0, min(1, (similarity + 1) / 2))), animated: true)
    // Show both vectors: the score above is the angle between exactly these two.
    vectorInfoLabel.text = "A \(preview(embeddingA))\nB \(preview(embeddingB))"
    setBusy(
      false,
      status: String(
        format: "Embedded both inputs in %.0fms on %@", totalMilliseconds,
        useGPU ? "GPU" : "CPU"))
  }

  /// First few dimensions of an embedding, e.g. `[-0.010, +0.026, …] 768d`.
  private func preview(_ embedding: Embedding) -> String {
    let vector = embedding.floatEmbedding ?? []
    let head = vector.prefix(4).map { String(format: "%+.3f", $0.floatValue) }.joined(
      separator: ", ")
    return "[\(head), …] \(vector.count)d"
  }

  // MARK: - UI state

  private func setBusy(_ busy: Bool, status: String) {
    isBusy = busy
    if busy {
      statusSpinner.startAnimating()
    } else {
      statusSpinner.stopAnimating()
    }
    compareButton.isEnabled = !busy
    compareButton.alpha = busy ? 0.5 : 1
    gpuSwitch.isEnabled = !busy
    statusLabel.text = status
  }

  private func show(toast message: String) {
    let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
    present(alert, animated: true)
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { alert.dismiss(animated: true) }
  }
}

/// Reproduces the horizontal gradient the Android sample uses in its header.
class GradientView: UIView {

  var colors: [UIColor] = [] {
    didSet { gradientLayer.colors = colors.map { $0.cgColor } }
  }

  override class var layerClass: AnyClass { CAGradientLayer.self }

  private var gradientLayer: CAGradientLayer { layer as! CAGradientLayer }

  override init(frame: CGRect) {
    super.init(frame: frame)
    gradientLayer.startPoint = CGPoint(x: 0, y: 0.5)
    gradientLayer.endPoint = CGPoint(x: 1, y: 0.5)
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }
}

/// The MediaPipe sample palette, matching `colors.xml` in the Android app.
enum Palette {
  static let primary = UIColor(red: 0x00 / 255, green: 0x7F / 255, blue: 0x8B / 255, alpha: 1)
  static let primaryVariant = UIColor(red: 0x12 / 255, green: 0xB5 / 255, blue: 0xCB / 255, alpha: 1)
  static let background = UIColor(red: 0xF2 / 255, green: 0xF4 / 255, blue: 0xF5 / 255, alpha: 1)
  static let surface = UIColor.white
  static let surfaceMuted = UIColor(red: 0xED / 255, green: 0xF3 / 255, blue: 0xF4 / 255, alpha: 1)
  static let onSurface = UIColor(red: 0x1B / 255, green: 0x24 / 255, blue: 0x26 / 255, alpha: 1)
  static let onSurfaceMuted = UIColor(red: 0x5F / 255, green: 0x6E / 255, blue: 0x71 / 255, alpha: 1)
  static let scoreTrack = UIColor(red: 0xDC / 255, green: 0xE6 / 255, blue: 0xE8 / 255, alpha: 1)
}
