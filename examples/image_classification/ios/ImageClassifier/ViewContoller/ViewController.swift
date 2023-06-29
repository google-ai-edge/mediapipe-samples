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
import UniformTypeIdentifiers
import AVKit

class ViewController: UIViewController {

  // MARK: Storyboards Connections
  @IBOutlet weak var previewView: PreviewView!
  @IBOutlet weak var addImageButton: UIButton!
  @IBOutlet weak var cameraUnavailableLabel: UILabel!
  @IBOutlet weak var imageEmptyLabel: UILabel!
  @IBOutlet weak var resumeButton: UIButton!
  @IBOutlet weak var runningModelTabbar: UITabBar!
  @IBOutlet weak var cameraTabbarItem: UITabBarItem!
  @IBOutlet weak var photoTabbarItem: UITabBarItem!
  @IBOutlet weak var bottomSheetViewBottomSpace: NSLayoutConstraint!
  @IBOutlet weak var bottomViewHeightConstraint: NSLayoutConstraint!

  // MARK: Constants
  private let delayBetweenInferencesMs = 1000.0
  private let inferenceBottomHeight = 220.0
  private let expandButtonHeight = 41.0
  private let playerViewController = AVPlayerViewController()
  private let videoOutput = AVPlayerItemVideoOutput(pixelBufferAttributes: [String(kCVPixelBufferPixelFormatTypeKey): NSNumber(value: kCVPixelFormatType_32BGRA)])

  // MARK: Instance Variables
  private var videoDetectTimer: Timer?
  private var previousInferenceTimeMs = Date.distantPast.timeIntervalSince1970 * 1000
  private var maxResults = DefaultConstants.maxResults {
    didSet {
      guard let inferenceVC = inferenceViewController else { return }
      bottomViewHeightConstraint.constant = inferenceVC.collapsedHeight + inferenceBottomHeight
      view.layoutSubviews()
    }
  }
  private var liveStreamDelegate: ImageClassifierLiveStreamDelegate?
  private var scoreThreshold = DefaultConstants.scoreThreshold
  private var model = DefaultConstants.model
  private var runingModel: RunningMode = .liveStream {
    didSet {
      if runingModel != .liveStream {
        liveStreamDelegate = nil
      } else {
        liveStreamDelegate = self
      }
      imageClassifierHelper = ImageClassifierHelper(
        model: model,
        maxResults: maxResults,
        scoreThreshold: scoreThreshold,
        runningModel: runingModel,
        delegate: liveStreamDelegate
      )
    }
  }

  // MARK: Controllers that manage functionality
  // Handles all the camera related functionality
  private lazy var cameraCapture = CameraFeedManager(previewView: previewView)

  // Handles all data preprocessing and makes calls to run inference through the
  // `ImageClassificationHelper`.
  private var imageClassifierHelper: ImageClassifierHelper?

  // Handles the presenting of results on the screen
  private var inferenceViewController: InferenceViewController?

  // MARK: View Handling Methods
  override func viewDidLoad() {
    super.viewDidLoad()
    // Create image classifier helper
    liveStreamDelegate = self
    imageClassifierHelper = ImageClassifierHelper(model: model, maxResults: maxResults, scoreThreshold: scoreThreshold, runningModel: runingModel, delegate: liveStreamDelegate)

    runningModelTabbar.selectedItem = cameraTabbarItem
    runningModelTabbar.delegate = self
    cameraCapture.delegate = self
  }
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
#if !targetEnvironment(simulator)
    if runingModel == .liveStream {
      cameraCapture.checkCameraConfigurationAndStartSession()
    }
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
      guard let inferenceVC = inferenceViewController else { return }
      bottomViewHeightConstraint.constant = inferenceVC.collapsedHeight + inferenceBottomHeight
      bottomSheetViewBottomSpace.constant = -inferenceBottomHeight + expandButtonHeight
      view.layoutSubviews()
    }
  }

  // MARK: IBAction

  @IBAction func addPhotoButtonTouchUpInside(_ sender: Any) {
    openImagePickerController()
  }
  // Resume camera session when click button resume
  @IBAction func resumeButtonTouchUpInside(_ sender: Any) {
    cameraCapture.resumeInterruptedSession { isSessionRunning in
      if isSessionRunning {
        self.resumeButton.isHidden = true
        self.cameraUnavailableLabel.isHidden = true
      }
    }
  }
  // MARK: Private function
  private func openImagePickerController() {
    if UIImagePickerController.isSourceTypeAvailable(.savedPhotosAlbum){
      let imagePicker = UIImagePickerController()
      imagePicker.delegate = self
      imagePicker.sourceType = .savedPhotosAlbum
      imagePicker.mediaTypes = [UTType.image.identifier, UTType.movie.identifier]
      imagePicker.allowsEditing = false
      DispatchQueue.main.async {
        self.present(imagePicker, animated: true, completion: nil)
      }
    }
  }

  private func removePlayerViewController() {
    playerViewController.view.removeFromSuperview()
    playerViewController.removeFromParent()
  }

  private func processVideo(url: URL) {
    let player = AVPlayer(url: url)
    playerViewController.player = player
    playerViewController.showsPlaybackControls = false
    playerViewController.view.frame = previewView.bounds
    previewView.addSubview(playerViewController.view)
    addChild(playerViewController)
    player.play()
    player.currentItem?.add(videoOutput)
    NotificationCenter.default.removeObserver(self)
    NotificationCenter.default
      .addObserver(self,
                   selector: #selector(playerDidFinishPlaying),
                   name: .AVPlayerItemDidPlayToEndTime,
                   object: player.currentItem
      )

    videoDetectTimer?.invalidate()
    videoDetectTimer = Timer.scheduledTimer(
      timeInterval: delayBetweenInferencesMs / 1000,
      target: self,
      selector: #selector(classificationVideoFrame),
      userInfo: nil,
      repeats: true)
  }

  @objc func classificationVideoFrame() {
    guard let player = playerViewController.player else { return }
    let currentTime: CMTime = player.currentTime()
    guard let buffer = self.pixelBufferFromCurrentPlayer(currentTime: currentTime) else { return }
    let currentTimeMs = Date().timeIntervalSince1970 * 1000
    let result = self.imageClassifierHelper?.classify(videoFrame: buffer, timeStamps: Int(currentTimeMs))
    // Display results by handing off to the InferenceViewController.
    inferenceViewController?.imageClassifierHelperResult = result
    DispatchQueue.main.async {
      self.inferenceViewController?.updateData()
    }
  }

  @objc func playerDidFinishPlaying(note: NSNotification) {
    videoDetectTimer?.invalidate()
    videoDetectTimer = nil
  }
  private func pixelBufferFromCurrentPlayer(currentTime: CMTime) -> CVPixelBuffer? {
    guard let buffer: CVPixelBuffer = videoOutput.copyPixelBuffer(forItemTime: currentTime, itemTimeForDisplay: nil) else { return nil }
    return buffer
  }
}

// MARK: UIImagePickerControllerDelegate, UINavigationControllerDelegate
extension ViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
  func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
    picker.dismiss(animated: true)
    if info[.mediaType] as? String == UTType.movie.identifier {
      guard let mediaURL = info[.mediaURL] as? URL else { return }
      imageEmptyLabel.isHidden = true
      if runingModel != .video {
        runingModel = .video
      }
      processVideo(url: mediaURL)
    } else {
      guard let image = info[.originalImage] as? UIImage else { return }
      if runingModel != .image {
        runingModel = .image
      }
      removePlayerViewController()
      previewView.image = image
      imageEmptyLabel.isHidden = true
      // Pass the uiimage to mediapipe
      let result = imageClassifierHelper?.classify(image: image)
      // Display results by handing off to the InferenceViewController.
      inferenceViewController?.imageClassifierHelperResult = result
      DispatchQueue.main.async {
        self.inferenceViewController?.updateData()
      }
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
    imageClassifierHelper?.classifyAsyn(videoFrame: pixelBuffer, timeStamps: Int(currentTimeMs))
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

// MARK: ImageClassifierLiveStreamDelegate
extension ViewController: ImageClassifierLiveStreamDelegate {
  func imageClassifier(_ imageClassifier: ImageClassifier, didFinishClassification result: ImageClassifierResult?, timestampInMilliseconds: Int, error: Error?) {
    guard let result = result else {
      print(error ?? "")
      return
    }
    let imageClassifierHelperResult = ImageClassifierHelperResult(
      inferenceTime: Date().timeIntervalSince1970 * 1000 - Double(timestampInMilliseconds),
      imageClassifierResult: result)
    DispatchQueue.main.async {
      self.inferenceViewController?.imageClassifierHelperResult = imageClassifierHelperResult
      self.inferenceViewController?.updateData()
    }
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
      bottomSheetViewBottomSpace.constant = isExpand ? 0 : -inferenceBottomHeight + expandButtonHeight
      UIView.animate(withDuration: 0.3) {
        self.view.layoutSubviews()
      }
    }
    if isModelNeedsRefresh {
      imageClassifierHelper = ImageClassifierHelper(
        model: self.model,
        maxResults: self.maxResults,
        scoreThreshold: self.scoreThreshold,
        runningModel: self.runingModel,
        delegate: liveStreamDelegate
      )
    }
  }
}

// MARK: UITabBarDelegate
extension ViewController: UITabBarDelegate {
  func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
    switch item {
    case cameraTabbarItem:
      if runingModel != .liveStream {
        runingModel = .liveStream
      }
      removePlayerViewController()
    #if !targetEnvironment(simulator)
      cameraCapture.checkCameraConfigurationAndStartSession()
    #endif
      previewView.shouldUseClipboardImage = false
      addImageButton.isHidden = true
      imageEmptyLabel.isHidden = true
    case photoTabbarItem:
#if !targetEnvironment(simulator)
      cameraCapture.stopSession()
#endif
      previewView.shouldUseClipboardImage = true
      addImageButton.isHidden = false
      if previewView.image == nil || playerViewController.parent != self {
        imageEmptyLabel.isHidden = false
      }
    default:
      break
    }
  }
}
