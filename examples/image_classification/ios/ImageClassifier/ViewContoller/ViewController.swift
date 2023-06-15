//
//  ViewController.swift
//  ImageClassifier
//
//  Created by MBA0077 on 6/8/23.
//

import UIKit

class ViewController: UIViewController {

  // MARK: Storyboards Connections
  @IBOutlet weak var previewView: PreviewView!
  @IBOutlet weak var cameraUnavailableLabel: UILabel!
  @IBOutlet weak var resumeButton: UIButton!
  @IBOutlet weak var bottomSheetView: UIView!

  @IBOutlet weak var bottomSheetViewBottomSpace: NSLayoutConstraint!
  @IBOutlet weak var bottomSheetStateImageView: UIImageView!
  @IBOutlet weak var bottomViewHeightConstraint: NSLayoutConstraint!

  // MARK: Constants
  private let animationDuration = 0.5
  private let collapseTransitionThreshold: CGFloat = -40.0
  private let expandTransitionThreshold: CGFloat = 40.0
  private let delayBetweenInferencesMs = 1000.0

  // MARK: Instance Variables
  private var previousInferenceTimeMs = Date.distantPast.timeIntervalSince1970 * 1000
  private var initialBottomSpace: CGFloat = 0.0
  private var maxResults = DefaultConstants.maxResults {
    didSet {
      guard let inferenceVC = inferenceViewController else { return }
      bottomViewHeightConstraint.constant = inferenceVC.collapsedHeight + 165
      view.layoutSubviews()
    }
  }
  private var scoreThreshold = DefaultConstants.scoreThreshold
  private var model = DefaultConstants.model

  // MARK: Controllers that manage functionality
  // Handles all the camera related functionality
  private lazy var cameraCapture = CameraFeedManager(previewView: previewView)

  // Handles all data preprocessing and makes calls to run inference through the
  // `ImageClassificationHelper`.
  private var imageClassificationHelper: ImageClassifierHelper? = ImageClassifierHelper(model: .efficientnetLite0, maxResults: 3, scoreThreshold: 0.5)

  // Handles the presenting of results on the screen
  private var inferenceViewController: InferenceViewController?

  // MARK: View Handling Methods
  override func viewDidLoad() {
    super.viewDidLoad()

    cameraCapture.delegate = self
  }
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
#if !targetEnvironment(simulator)
    cameraCapture.checkCameraConfigurationAndStartSession()
#endif
  }

#if !targetEnvironment(simulator)
  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    cameraCapture.stopSession()
  }
#endif

  override var preferredStatusBarStyle: UIStatusBarStyle {
    return .lightContent
  }

  // MARK: Storyboard Segue Handlers
  override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
    super.prepare(for: segue, sender: sender)
    if segue.identifier == "EMBED" {
      inferenceViewController = segue.destination as? InferenceViewController
      inferenceViewController?.maxResults = maxResults
      inferenceViewController?.modelChose = model
      inferenceViewController?.delegate = self
    }
  }
}

// MARK: CameraFeedManagerDelegate Methods
extension ViewController: CameraFeedManagerDelegate {

  func didOutput(pixelBuffer: CVPixelBuffer) {
    // Make sure the model will not run too often, making the results changing quickly and hard to
    // read.
    let currentTimeMs = Date().timeIntervalSince1970 * 1000
    guard (currentTimeMs - previousInferenceTimeMs) >= delayBetweenInferencesMs else { return }
    previousInferenceTimeMs = currentTimeMs

    // Pass the pixel buffer to mediapipe
    let result = self.imageClassificationHelper?.classify(videoFrame: pixelBuffer, timeStamps: Int(currentTimeMs))

    // Display results by handing off to the InferenceViewController.
    inferenceViewController?.imageClassifierResult = result
    DispatchQueue.main.async {
      self.inferenceViewController?.tableView.reloadData()
    }
  }

// MARK: Session Handling Alerts
func sessionWasInterrupted(canResumeManually resumeManually: Bool) {

  // Updates the UI when session is interupted.
  if resumeManually {
    self.resumeButton.isHidden = false
  } else {
    self.cameraUnavailableLabel.isHidden = false
  }
}

func sessionInterruptionEnded() {
  // Updates UI once session interruption has ended.
  if !self.cameraUnavailableLabel.isHidden {
    self.cameraUnavailableLabel.isHidden = true
  }

  if !self.resumeButton.isHidden {
    self.resumeButton.isHidden = true
  }
}

func sessionRunTimeErrorOccured() {
  // Handles session run time error by updating the UI and providing a button if session can be
  // manually resumed.
  self.resumeButton.isHidden = false
  previewView.shouldUseClipboardImage = true
}

func presentCameraPermissionsDeniedAlert() {
  let alertController = UIAlertController(
    title: "Camera Permissions Denied",
    message:
      "Camera permissions have been denied for this app. You can change this by going to Settings",
    preferredStyle: .alert)

  let cancelAction = UIAlertAction(title: "Cancel", style: .cancel, handler: nil)
  let settingsAction = UIAlertAction(title: "Settings", style: .default) { (action) in
    UIApplication.shared.open(
      URL(string: UIApplication.openSettingsURLString)!, options: [:], completionHandler: nil)
  }
  alertController.addAction(cancelAction)
  alertController.addAction(settingsAction)

  present(alertController, animated: true, completion: nil)

  previewView.shouldUseClipboardImage = true
}

func presentVideoConfigurationErrorAlert() {
  let alert = UIAlertController(
    title: "Camera Configuration Failed", message: "There was an error while configuring camera.",
    preferredStyle: .alert)
  alert.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))

  self.present(alert, animated: true)
  previewView.shouldUseClipboardImage = true
}
}

// MARK: InferenceViewControllerDelegate Methods
extension ViewController: InferenceViewControllerDelegate {
  func viewController(
    _ viewController: InferenceViewController,
    needPerformActions action: InferenceViewController.Action
  ) {
    var isModelNeedsRefresh = false
    switch action {
    case .changeScoreThreshold(let scoreThreshold):
      if self.scoreThreshold != scoreThreshold {
        isModelNeedsRefresh = true
      }
      self.scoreThreshold = scoreThreshold
    case .changeMaxResults(let maxResults):
      if self.maxResults != maxResults {
        isModelNeedsRefresh = true
      }
      self.maxResults = maxResults
    case .changeModel(let model):
      if self.model != model {
        isModelNeedsRefresh = true
      }
      self.model = model
    case .changeBottomSheetViewBottomSpace(let isExpand):
      bottomSheetViewBottomSpace.constant = isExpand ? 0 : -165
      UIView.animate(withDuration: 0.3) {
        self.view.layoutSubviews()
      }
    }
    if isModelNeedsRefresh {
      imageClassificationHelper = ImageClassifierHelper(
        model: self.model,
        maxResults: self.maxResults,
        scoreThreshold: self.scoreThreshold
      )
    }
  }
}


