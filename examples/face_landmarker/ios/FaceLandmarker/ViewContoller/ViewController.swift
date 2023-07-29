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
  @IBOutlet weak var overlayView: OverlayView!
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
  private var videoDetectTimer: Timer?
  private let inferenceIntervalMs: Double = 100
  private let inferenceBottomHeight = 220.0
  private let expandButtonHeight = 41.0
  private let edgeOffset: CGFloat = 2.0
  private let labelOffset: CGFloat = 10.0
  private let displayFont = UIFont.systemFont(ofSize: 14.0, weight: .medium)
  private let colors = [
    UIColor.red,
    UIColor(displayP3Red: 90.0/255.0, green: 200.0/255.0, blue: 250.0/255.0, alpha: 1.0),
    UIColor.green,
    UIColor.orange,
    UIColor.blue,
    UIColor.purple,
    UIColor.magenta,
    UIColor.yellow,
    UIColor.cyan,
    UIColor.brown
  ]
  private let playerViewController = AVPlayerViewController()

  // MARK: Instance Variables
  private var numFaces = DefaultConstants.numFaces
  private var detectionConfidence = DefaultConstants.detectionConfidence
  private var presenceConfidence = DefaultConstants.presenceConfidence
  private var trackingConfidence = DefaultConstants.trackingConfidence
  private let modelPath = DefaultConstants.modelPath
  private var runingModel: RunningMode = .liveStream {
    didSet {
      faceLandmarkerHelper = FaceLandmarkerHelper(
        modelPath: modelPath,
        numFaces: numFaces,
        minFaceDetectionConfidence: detectionConfidence,
        minFacePresenceConfidence: presenceConfidence,
        minTrackingConfidence: trackingConfidence,
        runningModel: runingModel,
        delegate: self)
    }
  }
  let backgroundQueue = DispatchQueue(
      label: "com.google.mediapipe.examples.facelandmarker",
      qos: .userInteractive
    )
  private var isProcess = false

  // MARK: Controllers that manage functionality
  // Handles all the camera related functionality
  private lazy var cameraCapture = CameraFeedManager(previewView: previewView)

  // Handles all data preprocessing and makes calls to run inference through the
  // `FaceLandmarkerHelper`.
  private var faceLandmarkerHelper: FaceLandmarkerHelper?

  // Handles the presenting of results on the screen
  private var inferenceViewController: InferenceViewController?

  // MARK: View Handling Methods
  override func viewDidLoad() {
    super.viewDidLoad()
    // Create object detector helper
    faceLandmarkerHelper = FaceLandmarkerHelper(
      modelPath: modelPath,
      numFaces: numFaces,
      minFaceDetectionConfidence: detectionConfidence,
      minFacePresenceConfidence: presenceConfidence,
      minTrackingConfidence: trackingConfidence,
      runningModel: runingModel,
      delegate: self)

    runningModelTabbar.selectedItem = cameraTabbarItem
    runningModelTabbar.delegate = self
    cameraCapture.delegate = self
    overlayView.clearsContextBeforeDrawing = true
  }
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
#if !targetEnvironment(simulator)
    if runingModel == .liveStream && runningModelTabbar.selectedItem == cameraTabbarItem {
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
      inferenceViewController?.numFaces = numFaces
      inferenceViewController?.delegate = self
      bottomViewHeightConstraint.constant = inferenceBottomHeight
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
    if UIImagePickerController.isSourceTypeAvailable(.savedPhotosAlbum) {
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
    backgroundQueue.async { [weak self] in
      guard let weakSelf = self else { return }
      let faceLandmarkerHelper = FaceLandmarkerHelper(
        modelPath: weakSelf.modelPath,
        numFaces: weakSelf.numFaces,
        minFaceDetectionConfidence: weakSelf.detectionConfidence,
        minFacePresenceConfidence: weakSelf.presenceConfidence,
        minTrackingConfidence: weakSelf.trackingConfidence,
        runningModel: .video,
        delegate: nil)
      Task {
        let result = await faceLandmarkerHelper.detectVideoFile(url: url, inferenceIntervalMs: weakSelf.inferenceIntervalMs)
        DispatchQueue.main.async {
          weakSelf.inferenceViewController?.result = result
          weakSelf.inferenceViewController?.updateData()
          let player = AVPlayer(url: url)
          weakSelf.playerViewController.player = player
          weakSelf.playerViewController.videoGravity = .resizeAspectFill
          weakSelf.playerViewController.showsPlaybackControls = false
          weakSelf.playerViewController.view.frame = weakSelf.previewView.bounds
          weakSelf.previewView.addSubview(weakSelf.playerViewController.view)
          weakSelf.addChild(weakSelf.playerViewController)
          player.play()
          NotificationCenter.default.removeObserver(weakSelf)
          NotificationCenter.default
            .addObserver(weakSelf,
                         selector: #selector(weakSelf.playerDidFinishPlaying),
                         name: .AVPlayerItemDidPlayToEndTime,
                         object: player.currentItem
            )

          weakSelf.videoDetectTimer?.invalidate()
          weakSelf.videoDetectTimer = Timer.scheduledTimer(
            withTimeInterval: weakSelf.inferenceIntervalMs / 1000,
            repeats: true, block: { _ in
              let currentTime: CMTime = player.currentTime()
              let index = Int(currentTime.seconds * 1000 / weakSelf.inferenceIntervalMs)
              guard let result = result,
                    index < result.faceLandmarkerResults.count,
                    let faceLandmarkerResult = result.faceLandmarkerResults[index] else { return }
              DispatchQueue.main.async {
                weakSelf.overlayView.drawLandmarks(faceLandmarkerResult.faceLandmarks,
                                                   orientation: .up,
                                                   withImageSize: result.imageSize)
              }
          })
        }
      }
    }
  }

  @objc func playerDidFinishPlaying(note: NSNotification) {
    videoDetectTimer?.invalidate()
    videoDetectTimer = nil
  }
}

// MARK: UIImagePickerControllerDelegate, UINavigationControllerDelegate
extension ViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
  func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
    picker.dismiss(animated: true)
    if info[.mediaType] as? String == UTType.movie.identifier {
      guard let mediaURL = info[.mediaURL] as? URL else { return }
      imageEmptyLabel.isHidden = true
      processVideo(url: mediaURL)
    } else {
      guard let image = info[.originalImage] as? UIImage else { return }
      imageEmptyLabel.isHidden = true
      if runingModel != .image {
        runingModel = .image
      }
      removePlayerViewController()
      previewView.image = image
      // Pass the uiimage to mediapipe
      let result = faceLandmarkerHelper?.detect(image: image)
      // Display results by handing off to the InferenceViewController.
      inferenceViewController?.result = result
      DispatchQueue.main.async {
        self.inferenceViewController?.updateData()
        guard let result = result,
              let faceLandmarkerResult = result.faceLandmarkerResults.first,
              let faceLandmarkerResult = faceLandmarkerResult else { return }
        self.overlayView.drawLandmarks(faceLandmarkerResult.faceLandmarks,
                                       orientation: image.imageOrientation,
                                       withImageSize: image.size)
      }
    }
  }
}

// MARK: CameraFeedManagerDelegate Methods
extension ViewController: CameraFeedManagerDelegate {

  func didOutput(sampleBuffer: CMSampleBuffer, orientation: UIImage.Orientation) {
    let currentTimeMs = Date().timeIntervalSince1970 * 1000
    backgroundQueue.async {
      self.faceLandmarkerHelper?.detectAsync(videoFrame: sampleBuffer, orientation: orientation, timeStamps: Int(currentTimeMs))
    }
  }

  // Convert CIImage to UIImage
  func convert(cmage: CIImage) -> UIImage {
       let context = CIContext(options: nil)
       let cgImage = context.createCGImage(cmage, from: cmage.extent)!
       let image = UIImage(cgImage: cgImage)
       return image
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
    case .changeNumFaces(let numFaces):
      if self.numFaces != numFaces {
        isModelNeedsRefresh = true
      }
      self.numFaces = numFaces
    case .changeDetectionConfidence(let detectionConfidence):
      if self.detectionConfidence != detectionConfidence {
        isModelNeedsRefresh = true
      }
      self.detectionConfidence = detectionConfidence
    case .changePresenceConfidence(let presenceConfidence):
      if self.presenceConfidence != presenceConfidence {
        isModelNeedsRefresh = true
      }
      self.presenceConfidence = presenceConfidence
    case .changeTrackingConfidence(let trackingConfidence):
      if self.trackingConfidence != trackingConfidence {
        isModelNeedsRefresh = true
      }
      self.trackingConfidence = trackingConfidence
    case .changeBottomSheetViewBottomSpace(let isExpand):
      bottomSheetViewBottomSpace.constant = isExpand ? 0 : -inferenceBottomHeight + expandButtonHeight
      UIView.animate(withDuration: 0.3) {
        self.view.layoutSubviews()
      }
    }
    if isModelNeedsRefresh {
      faceLandmarkerHelper = FaceLandmarkerHelper(
        modelPath: modelPath,
        numFaces: numFaces,
        minFaceDetectionConfidence: detectionConfidence,
        minFacePresenceConfidence: presenceConfidence,
        minTrackingConfidence: trackingConfidence,
        runningModel: runingModel,
        delegate: self)
    }
  }
}

extension ViewController: FaceLandmarkerHelperDelegate {
  func faceLandmarkerHelper(_ faceLandmarkerHelper: FaceLandmarkerHelper, didFinishDetection result: ResultBundle?, error: Error?) {
    guard let result = result,
          let faceLandmarkerResult = result.faceLandmarkerResults.first,
          let faceLandmarkerResult = faceLandmarkerResult else { return }
    DispatchQueue.main.async {
      if self.runningModelTabbar.selectedItem != self.cameraTabbarItem { return }
      self.overlayView.drawLandmarks(faceLandmarkerResult.faceLandmarks,
                                     orientation: self.cameraCapture.orientation,
                                     withImageSize: self.cameraCapture.videoFrameSize)
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
    overlayView.objectOverlays = []
    overlayView.setNeedsDisplay()
  }
}

// MARK: Define default constants
enum DefaultConstants {
  static let numFaces = 3
  static let detectionConfidence: Float = 0.5
  static let presenceConfidence: Float = 0.5
  static let trackingConfidence: Float = 0.5
  static let outputFaceBlendshapes: Bool = false
  static let modelPath: String? = Bundle.main.path(forResource: "face_landmarker", ofType: "task")
}
