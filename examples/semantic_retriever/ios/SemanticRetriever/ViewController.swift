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

/// Indexes ten bundled photos into an on-device vector store and searches them by meaning.
///
/// Everything the engine does — loading the model, indexing, searching — runs on
/// `retrieverQueue`. A serial queue guarantees that those steps can never overlap: the native
/// engine is not reentrant, and overlapping work is what produces "EmbeddingEngine is not
/// initialized".
class ViewController: UIViewController {

  // MARK: - Sample content

  private let sampleImages = [
    "red_apple", "yellow_banana", "cute_cat", "fast_car", "green_tree",
    "blue_sky", "coffee_mug", "open_book", "sunny_beach", "snowy_mountain",
  ]

  private let suggestions = [
    "a piece of fruit", "an animal", "something to drink", "a vacation spot", "a vehicle",
  ]

  // MARK: - Views

  private let headerView = GradientView()
  private let cardView = UIView()
  private let acceleratorLabel = UILabel()
  private let gpuLabel = UILabel()
  private let gpuSwitch = UISwitch()
  private let indexButton = UIButton(type: .system)
  private let indexProgressView = UIProgressView(progressViewStyle: .default)
  private let statusSpinner = UIActivityIndicatorView(style: .medium)
  private let statusLabel = UILabel()
  private let searchBar = UIView()
  private let queryField = UITextField()
  private let searchButton = UIButton(type: .system)
  private let suggestionScrollView = UIScrollView()
  private let suggestionStack = UIStackView()
  private let resultsLabel = UILabel()
  private let tableView = UITableView()
  private let emptyLabel = UILabel()

  // MARK: - State

  private let helper = SemanticRetrieverHelper()
  private let retrieverQueue = DispatchQueue(
    label: "com.google.mediapipe.examples.semanticretriever.engine")

  private var results: [RetrievalResult] = []

  /// True while the model is loading, indexing or searching.
  private var isBusy = false

  /// True once the sample images have been embedded into the vector store.
  private var isIndexed = false

  private var useGPU = false

  /// Assets never change at runtime, so a tiny in-memory cache is all we need.
  private var thumbnailCache: [String: UIImage] = [:]

  // MARK: - Lifecycle

  override func viewDidLoad() {
    super.viewDidLoad()
    overrideUserInterfaceStyle = .light
    view.backgroundColor = Palette.background
    setupUI()
    reload()
  }

  deinit {
    let helper = self.helper
    // Releasing the native engine can block, so keep it off the main thread.
    retrieverQueue.async { helper.close() }
  }

  // MARK: - UI

  private func setupUI() {
    headerView.colors = [Palette.primary, Palette.primaryVariant]
    headerView.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(headerView)

    let titleLabel = UILabel()
    titleLabel.text = "Semantic Image Search"
    titleLabel.font = .systemFont(ofSize: 22, weight: .bold)
    titleLabel.textColor = .white
    titleLabel.translatesAutoresizingMaskIntoConstraints = false
    headerView.addSubview(titleLabel)

    cardView.backgroundColor = Palette.surface
    cardView.layer.cornerRadius = 16
    cardView.layer.shadowColor = UIColor.black.cgColor
    cardView.layer.shadowOpacity = 0.08
    cardView.layer.shadowRadius = 6
    cardView.layer.shadowOffset = CGSize(width: 0, height: 2)
    cardView.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(cardView)

    acceleratorLabel.text = "Accelerator"
    acceleratorLabel.font = .systemFont(ofSize: 13, weight: .semibold)
    acceleratorLabel.textColor = Palette.onSurfaceMuted

    gpuLabel.text = "GPU"
    gpuLabel.font = .systemFont(ofSize: 12)
    gpuLabel.textColor = Palette.onSurfaceMuted

    gpuSwitch.onTintColor = Palette.primary
    gpuSwitch.addTarget(self, action: #selector(gpuSwitched), for: .valueChanged)

    let headerRow = UIStackView(arrangedSubviews: [acceleratorLabel, UIView(), gpuLabel, gpuSwitch])
    headerRow.axis = .horizontal
    headerRow.alignment = .center
    headerRow.spacing = 8

    indexButton.setTitle("Index 10 sample images", for: .normal)
    indexButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
    indexButton.backgroundColor = Palette.primary
    indexButton.setTitleColor(.white, for: .normal)
    indexButton.setTitleColor(UIColor.white.withAlphaComponent(0.6), for: .disabled)
    indexButton.layer.cornerRadius = 12
    indexButton.addTarget(self, action: #selector(indexTapped), for: .touchUpInside)
    indexButton.heightAnchor.constraint(equalToConstant: 46).isActive = true

    indexProgressView.progressTintColor = Palette.primary
    indexProgressView.trackTintColor = Palette.scoreTrack
    indexProgressView.isHidden = true

    // The spinner lives in a fixed-width box so the status text does not jump around when the
    // spinner stops and hides itself.
    statusSpinner.color = Palette.primary
    statusSpinner.hidesWhenStopped = true
    statusSpinner.translatesAutoresizingMaskIntoConstraints = false
    let spinnerBox = UIView()
    spinnerBox.addSubview(statusSpinner)
    spinnerBox.widthAnchor.constraint(equalToConstant: 16).isActive = true

    statusLabel.text = "Ready"
    statusLabel.font = .systemFont(ofSize: 13)
    statusLabel.textColor = Palette.onSurfaceMuted
    statusLabel.numberOfLines = 0

    let statusRow = UIStackView(arrangedSubviews: [spinnerBox, statusLabel])
    statusRow.axis = .horizontal
    statusRow.alignment = .center
    statusRow.spacing = 8

    let cardStack = UIStackView(arrangedSubviews: [
      headerRow, indexButton, indexProgressView, statusRow,
    ])
    cardStack.axis = .vertical
    cardStack.spacing = 12
    cardStack.translatesAutoresizingMaskIntoConstraints = false
    cardView.addSubview(cardStack)

    searchBar.backgroundColor = Palette.surface
    searchBar.layer.cornerRadius = 14
    searchBar.layer.borderWidth = 1
    searchBar.layer.borderColor = Palette.scoreTrack.cgColor
    searchBar.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(searchBar)

    queryField.attributedPlaceholder = NSAttributedString(
      string: "Search images by meaning…",
      attributes: [.foregroundColor: Palette.onSurfaceMuted])
    queryField.font = .systemFont(ofSize: 16)
    queryField.textColor = Palette.onSurface
    queryField.tintColor = Palette.primary
    queryField.backgroundColor = .clear
    queryField.borderStyle = .none
    queryField.returnKeyType = .search
    queryField.clearButtonMode = .whileEditing
    queryField.autocorrectionType = .no
    queryField.delegate = self
    queryField.translatesAutoresizingMaskIntoConstraints = false
    searchBar.addSubview(queryField)

    searchButton.setImage(UIImage(systemName: "magnifyingglass"), for: .normal)
    searchButton.tintColor = Palette.primary
    searchButton.addTarget(self, action: #selector(searchTapped), for: .touchUpInside)
    searchButton.translatesAutoresizingMaskIntoConstraints = false
    searchBar.addSubview(searchButton)

    suggestionScrollView.showsHorizontalScrollIndicator = false
    suggestionScrollView.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(suggestionScrollView)

    suggestionStack.axis = .horizontal
    suggestionStack.spacing = 8
    suggestionStack.translatesAutoresizingMaskIntoConstraints = false
    suggestionScrollView.addSubview(suggestionStack)

    for (index, suggestion) in suggestions.enumerated() {
      var configuration = UIButton.Configuration.plain()
      configuration.attributedTitle = AttributedString(
        suggestion, attributes: AttributeContainer([.font: UIFont.systemFont(ofSize: 13)]))
      configuration.baseForegroundColor = Palette.onSurface
      configuration.contentInsets = NSDirectionalEdgeInsets(
        top: 6, leading: 14, bottom: 6, trailing: 14)
      configuration.background.backgroundColor = Palette.surface
      configuration.background.cornerRadius = 15
      configuration.background.strokeColor = Palette.scoreTrack
      configuration.background.strokeWidth = 1

      let chip = UIButton(type: .system)
      chip.configuration = configuration
      chip.tag = index
      chip.addTarget(self, action: #selector(suggestionTapped(_:)), for: .touchUpInside)
      suggestionStack.addArrangedSubview(chip)
    }

    resultsLabel.text = "Results"
    resultsLabel.font = .systemFont(ofSize: 13, weight: .semibold)
    resultsLabel.textColor = Palette.onSurfaceMuted
    resultsLabel.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(resultsLabel)

    tableView.dataSource = self
    tableView.delegate = self
    tableView.register(SearchResultCell.self, forCellReuseIdentifier: SearchResultCell.identifier)
    tableView.separatorStyle = .none
    tableView.backgroundColor = .clear
    tableView.rowHeight = 96
    tableView.keyboardDismissMode = .onDrag
    tableView.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(tableView)

    emptyLabel.text = "Index the sample images to start searching."
    emptyLabel.font = .systemFont(ofSize: 14)
    emptyLabel.textColor = Palette.onSurfaceMuted
    emptyLabel.textAlignment = .center
    emptyLabel.numberOfLines = 0
    emptyLabel.translatesAutoresizingMaskIntoConstraints = false
    view.addSubview(emptyLabel)

    NSLayoutConstraint.activate([
      headerView.topAnchor.constraint(equalTo: view.topAnchor),
      headerView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      headerView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
      headerView.bottomAnchor.constraint(
        equalTo: view.safeAreaLayoutGuide.topAnchor, constant: 60),

      titleLabel.leadingAnchor.constraint(equalTo: headerView.leadingAnchor, constant: 20),
      titleLabel.trailingAnchor.constraint(equalTo: headerView.trailingAnchor, constant: -20),
      titleLabel.bottomAnchor.constraint(equalTo: headerView.bottomAnchor, constant: -22),

      cardView.topAnchor.constraint(equalTo: headerView.bottomAnchor, constant: -12),
      cardView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
      cardView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),

      cardStack.topAnchor.constraint(equalTo: cardView.topAnchor, constant: 16),
      cardStack.bottomAnchor.constraint(equalTo: cardView.bottomAnchor, constant: -16),
      cardStack.leadingAnchor.constraint(equalTo: cardView.leadingAnchor, constant: 16),
      cardStack.trailingAnchor.constraint(equalTo: cardView.trailingAnchor, constant: -16),

      statusSpinner.centerXAnchor.constraint(equalTo: spinnerBox.centerXAnchor),
      statusSpinner.centerYAnchor.constraint(equalTo: spinnerBox.centerYAnchor),

      searchBar.topAnchor.constraint(equalTo: cardView.bottomAnchor, constant: 16),
      searchBar.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
      searchBar.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
      searchBar.heightAnchor.constraint(equalToConstant: 48),

      queryField.leadingAnchor.constraint(equalTo: searchBar.leadingAnchor, constant: 16),
      queryField.trailingAnchor.constraint(equalTo: searchButton.leadingAnchor, constant: -4),
      queryField.topAnchor.constraint(equalTo: searchBar.topAnchor),
      queryField.bottomAnchor.constraint(equalTo: searchBar.bottomAnchor),

      searchButton.trailingAnchor.constraint(equalTo: searchBar.trailingAnchor, constant: -8),
      searchButton.centerYAnchor.constraint(equalTo: searchBar.centerYAnchor),
      searchButton.widthAnchor.constraint(equalToConstant: 36),
      searchButton.heightAnchor.constraint(equalToConstant: 36),

      suggestionScrollView.topAnchor.constraint(equalTo: searchBar.bottomAnchor, constant: 10),
      suggestionScrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 16),
      suggestionScrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16),
      suggestionScrollView.heightAnchor.constraint(equalToConstant: 34),

      suggestionStack.topAnchor.constraint(equalTo: suggestionScrollView.topAnchor),
      suggestionStack.bottomAnchor.constraint(equalTo: suggestionScrollView.bottomAnchor),
      suggestionStack.leadingAnchor.constraint(equalTo: suggestionScrollView.leadingAnchor),
      suggestionStack.trailingAnchor.constraint(equalTo: suggestionScrollView.trailingAnchor),
      suggestionStack.heightAnchor.constraint(equalTo: suggestionScrollView.heightAnchor),

      resultsLabel.topAnchor.constraint(equalTo: suggestionScrollView.bottomAnchor, constant: 16),
      resultsLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 20),
      resultsLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -20),

      tableView.topAnchor.constraint(equalTo: resultsLabel.bottomAnchor, constant: 8),
      tableView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 12),
      tableView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -12),
      tableView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor),

      emptyLabel.centerXAnchor.constraint(equalTo: tableView.centerXAnchor),
      emptyLabel.centerYAnchor.constraint(equalTo: tableView.centerYAnchor),
      emptyLabel.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 32),
      emptyLabel.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -32),
    ])
  }

  // MARK: - Actions

  @objc private func gpuSwitched() {
    useGPU = gpuSwitch.isOn
    reload()
  }

  @objc private func indexTapped() {
    embedSampleImages()
  }

  @objc private func searchTapped() {
    runSearch()
  }

  @objc private func suggestionTapped(_ sender: UIButton) {
    queryField.text = suggestions[sender.tag]
    runSearch()
  }

  private func runSearch() {
    if isBusy {
      show(toast: "Busy — wait for the current step to finish")
      return
    }
    queryField.resignFirstResponder()
    let query = queryField.text?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    guard !query.isEmpty else {
      show(toast: "Enter a search query first")
      return
    }
    searchImages(query: query)
  }

  // MARK: - Engine work

  /// Loads the model (first run only), opens the store and empties it. Switching accelerators is a
  /// full reset: no index, no query, no results.
  private func reload() {
    setIndexed(false)
    let accelerator = useGPU ? "GPU" : "CPU"
    setBusy(true, status: "Loading model on \(accelerator)…")
    queryField.text = ""
    indexProgressView.isHidden = true
    indexProgressView.progress = 0
    results = []
    tableView.reloadData()
    updateEmptyState()

    retrieverQueue.async { [weak self] in
      guard let self else { return }
      do {
        try self.helper.prepareEmbedder(useGPU: self.useGPU)
        try self.helper.openStore()
        // Start from a known-empty store without deleting files under a live store.
        try self.helper.clear()
        DispatchQueue.main.async {
          self.setBusy(false, status: "Ready on \(accelerator) · store is empty")
          self.setIndexed(false)
        }
      } catch {
        print("Initialization failed (\(accelerator)): \(error) — \(error.localizedDescription)")
        DispatchQueue.main.async {
          self.setBusy(false, status: "Initialization failed: \(error.localizedDescription)")
        }
      }
    }
  }

  private func embedSampleImages() {
    guard helper.isReady, !isBusy else {
      show(toast: "Still getting ready, please wait…")
      return
    }
    setBusy(true, status: "Indexing…")
    indexProgressView.isHidden = false
    indexProgressView.progress = 0
    let startedAt = CACurrentMediaTime()

    retrieverQueue.async { [weak self] in
      guard let self else { return }
      do {
        for (index, name) in self.sampleImages.enumerated() {
          guard let path = Self.imagePath(for: name) else { continue }
          DispatchQueue.main.async {
            self.statusLabel.text =
              "Embedding \(index + 1)/\(self.sampleImages.count): "
              + name.replacingOccurrences(of: "_", with: " ")
            self.indexProgressView.setProgress(
              Float(index) / Float(self.sampleImages.count), animated: true)
          }
          try self.helper.embedImage(id: name, path: path)
        }
        let seconds = CACurrentMediaTime() - startedAt
        DispatchQueue.main.async {
          self.indexProgressView.setProgress(1, animated: true)
          // Let the bar finish animating, then tuck it away again.
          DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
            self.indexProgressView.isHidden = true
          }
          self.setBusy(
            false,
            status: String(
              format: "Indexed %d images in %.1fs on %@ · ready to search",
              self.sampleImages.count, seconds, self.useGPU ? "GPU" : "CPU"))
          self.setIndexed(true)
        }
      } catch {
        print("Indexing failed: \(error) — \(error.localizedDescription)")
        DispatchQueue.main.async {
          self.indexProgressView.isHidden = true
          self.setBusy(false, status: "Indexing failed: \(error.localizedDescription)")
        }
      }
    }
  }

  private func searchImages(query: String) {
    guard helper.isReady else {
      show(toast: "Still getting ready, please wait…")
      return
    }
    setBusy(true, status: "Searching for \"\(query)\"…")
    let startedAt = CACurrentMediaTime()

    retrieverQueue.async { [weak self] in
      guard let self else { return }
      do {
        let results = try self.helper.searchImages(query: query, limit: 5)
        let millis = (CACurrentMediaTime() - startedAt) * 1000
        DispatchQueue.main.async {
          self.results = results
          self.setBusy(
            false,
            status: String(
              format: "%d results for \"%@\" in %.0fms", results.count, query, millis))
          self.tableView.reloadData()
          self.updateEmptyState()
        }
      } catch {
        print("Search failed: \(error) — \(error.localizedDescription)")
        DispatchQueue.main.async {
          self.setBusy(false, status: "Search failed: \(error.localizedDescription)")
        }
      }
    }
  }

  // MARK: - UI state

  /// Shows/hides the small spinner next to the status line and disables input while busy.
  private func setBusy(_ busy: Bool, status: String) {
    isBusy = busy
    if busy {
      statusSpinner.startAnimating()
    } else {
      statusSpinner.stopAnimating()
    }
    indexButton.isEnabled = !busy
    indexButton.alpha = busy ? 0.5 : 1
    gpuSwitch.isEnabled = !busy
    statusLabel.text = status
    refreshSearchControls()
  }

  /// Indexing is a one-shot action per store: once the samples are in, the index button goes away
  /// and the search UI takes over.
  private func setIndexed(_ indexed: Bool) {
    isIndexed = indexed
    indexButton.isHidden = indexed
    refreshSearchControls()
    updateEmptyState()
  }

  /// Searching only makes sense once something has been indexed and nothing else is running.
  private func refreshSearchControls() {
    let enabled = isIndexed && !isBusy
    searchBar.alpha = enabled ? 1 : 0.5
    queryField.isEnabled = enabled
    searchButton.isEnabled = enabled
    suggestionScrollView.alpha = enabled ? 1 : 0.5
    suggestionStack.arrangedSubviews.forEach { $0.isUserInteractionEnabled = enabled }
  }

  private func updateEmptyState() {
    emptyLabel.isHidden = !results.isEmpty
    emptyLabel.text =
      isIndexed
      ? "Search for something like “a piece of fruit”." : "Index the sample images to start searching."
  }

  private func show(toast message: String) {
    let alert = UIAlertController(title: nil, message: message, preferredStyle: .alert)
    present(alert, animated: true)
    DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) { alert.dismiss(animated: true) }
  }

  // MARK: - Images

  static func imagePath(for name: String) -> String? {
    return Bundle.main.path(forResource: name, ofType: "jpg", inDirectory: "images")
  }

  private func thumbnail(for name: String) -> UIImage? {
    if let cached = thumbnailCache[name] { return cached }
    guard let path = Self.imagePath(for: name), let image = UIImage(contentsOfFile: path) else {
      return nil
    }
    thumbnailCache[name] = image
    return image
  }
}

// MARK: - UITextFieldDelegate

extension ViewController: UITextFieldDelegate {
  func textFieldShouldReturn(_ textField: UITextField) -> Bool {
    runSearch()
    return true
  }
}

// MARK: - UITableViewDataSource

extension ViewController: UITableViewDataSource, UITableViewDelegate {
  func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    return results.count
  }

  func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    let cell =
      tableView.dequeueReusableCell(withIdentifier: SearchResultCell.identifier, for: indexPath)
      as! SearchResultCell
    let result = results[indexPath.row]
    cell.configure(
      rank: indexPath.row + 1,
      recordId: result.recordId,
      score: result.score,
      image: thumbnail(for: result.recordId))
    return cell
  }
}

/// A rank badge, the matching photo and the similarity the retriever reported for it.
class SearchResultCell: UITableViewCell {

  static let identifier = "SearchResultCell"

  private let container = UIView()
  private let thumbnailView = UIImageView()
  private let rankLabel = UILabel()
  private let titleLabel = UILabel()
  private let scoreLabel = UILabel()
  private let scoreBar = UIProgressView(progressViewStyle: .default)

  override init(style: UITableViewCell.CellStyle, reuseIdentifier: String?) {
    super.init(style: style, reuseIdentifier: reuseIdentifier)
    selectionStyle = .none
    backgroundColor = .clear
    contentView.backgroundColor = .clear

    container.backgroundColor = Palette.surface
    container.layer.cornerRadius = 14
    container.translatesAutoresizingMaskIntoConstraints = false
    contentView.addSubview(container)

    thumbnailView.contentMode = .scaleAspectFill
    thumbnailView.clipsToBounds = true
    thumbnailView.layer.cornerRadius = 10
    thumbnailView.backgroundColor = Palette.surfaceMuted
    thumbnailView.translatesAutoresizingMaskIntoConstraints = false
    container.addSubview(thumbnailView)

    rankLabel.font = .systemFont(ofSize: 12, weight: .bold)
    rankLabel.textColor = .white
    rankLabel.textAlignment = .center
    rankLabel.backgroundColor = Palette.primary
    rankLabel.layer.cornerRadius = 11
    rankLabel.layer.masksToBounds = true
    rankLabel.translatesAutoresizingMaskIntoConstraints = false
    container.addSubview(rankLabel)

    titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
    titleLabel.textColor = Palette.onSurface
    titleLabel.translatesAutoresizingMaskIntoConstraints = false
    container.addSubview(titleLabel)

    scoreLabel.font = .monospacedDigitSystemFont(ofSize: 13, weight: .regular)
    scoreLabel.textColor = Palette.onSurfaceMuted
    scoreLabel.translatesAutoresizingMaskIntoConstraints = false
    container.addSubview(scoreLabel)

    scoreBar.progressTintColor = Palette.primary
    scoreBar.trackTintColor = Palette.scoreTrack
    scoreBar.translatesAutoresizingMaskIntoConstraints = false
    container.addSubview(scoreBar)

    NSLayoutConstraint.activate([
      container.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 4),
      container.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -4),
      container.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 4),
      container.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -4),

      thumbnailView.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 10),
      thumbnailView.centerYAnchor.constraint(equalTo: container.centerYAnchor),
      thumbnailView.widthAnchor.constraint(equalToConstant: 68),
      thumbnailView.heightAnchor.constraint(equalToConstant: 68),

      rankLabel.topAnchor.constraint(equalTo: thumbnailView.topAnchor, constant: -6),
      rankLabel.leadingAnchor.constraint(equalTo: thumbnailView.leadingAnchor, constant: -6),
      rankLabel.widthAnchor.constraint(equalToConstant: 22),
      rankLabel.heightAnchor.constraint(equalToConstant: 22),

      titleLabel.topAnchor.constraint(equalTo: thumbnailView.topAnchor, constant: 4),
      titleLabel.leadingAnchor.constraint(equalTo: thumbnailView.trailingAnchor, constant: 14),
      titleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),

      scoreLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 6),
      scoreLabel.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
      scoreLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),

      scoreBar.topAnchor.constraint(equalTo: scoreLabel.bottomAnchor, constant: 8),
      scoreBar.leadingAnchor.constraint(equalTo: titleLabel.leadingAnchor),
      scoreBar.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -14),
    ])
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  func configure(rank: Int, recordId: String, score: Double, image: UIImage?) {
    rankLabel.text = "\(rank)"
    titleLabel.text = recordId.replacingOccurrences(of: "_", with: " ").capitalized
    scoreLabel.text = String(format: "Similarity %.4f", score)
    // Scores are cosine similarities in [-1, 1]; map to a 0-1 bar.
    scoreBar.progress = Float(max(0, min(1, (score + 1) / 2)))
    thumbnailView.image = image
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
