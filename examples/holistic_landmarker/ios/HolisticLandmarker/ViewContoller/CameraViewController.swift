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

import AVFoundation
import MediaPipeTasksVision
import UIKit

/**
 * The view controller is responsible for performing detection on incoming frames from the live camera and presenting the frames with the
 * landmarks of holistic (pose, face, hands) to the user.
 */
class CameraViewController: UIViewController {
  private struct Constants {
    static let edgeOffset: CGFloat = 2.0
  }

  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?
  weak var interfaceUpdatesDelegate: InterfaceUpdatesDelegate?

  @IBOutlet weak var previewView: UIView!
  @IBOutlet weak var cameraUnavailableLabel: UILabel!
  @IBOutlet weak var resumeButton: UIButton!
  @IBOutlet weak var overlayView: OverlayView!

  private var isSessionRunning = false
  private var isObserving = false
  private let backgroundQueue = DispatchQueue(label: "com.google.mediapipe.cameraController.backgroundQueue")

  // MARK: Controllers that manage functionality
  // Handles all the camera related functionality
  private lazy var cameraFeedService = CameraFeedService(previewView: previewView)

  private let holisticLandmarkerServiceQueue = DispatchQueue(
    label: "com.google.mediapipe.cameraController.holisticLandmarkerServiceQueue",
    attributes: .concurrent)

  // Queuing reads and writes to holisticLandmarkerService using the Apple recommended way
  // as they can be read and written from multiple threads and can result in race conditions.
  private var _holisticLandmarkerService: HolisticLandmarkerService?
  private var holisticLandmarkerService: HolisticLandmarkerService? {
    get {
      holisticLandmarkerServiceQueue.sync {
        return self._holisticLandmarkerService
      }
    }
    set {
      holisticLandmarkerServiceQueue.async(flags: .barrier) {
        self._holisticLandmarkerService = newValue
      }
    }
  }

#if !targetEnvironment(simulator)
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    isSessionRunning = true
    cameraFeedService.delegate = self
    if holisticLandmarkerService == nil {
      initializeHolisticLandmarkerServiceOnSessionResumption()
    } else {
      startObserveConfigChanges()
    }
    cameraFeedService.startLiveCameraSession { [weak self] cameraConfiguration in
      DispatchQueue.main.async {
        switch cameraConfiguration {
        case .failed:
          self?.presentVideoConfigurationErrorAlert()
        case .permissionDenied:
          self?.presentCameraPermissionsDeniedAlert()
        default:
          break
        }
      }
    }
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    isSessionRunning = false
    cameraFeedService.delegate = nil
    cameraFeedService.stopSession()
    backgroundQueue.sync {
      // Drain backgroundQueue so no pending detectAsync blocks are executed
    }
    stopObserveConfigChanges()
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    cameraFeedService.delegate = self
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(appDidEnterBackground),
      name: UIApplication.didEnterBackgroundNotification,
      object: nil)
    NotificationCenter.default.addObserver(
      self,
      selector: #selector(appWillEnterForeground),
      name: UIApplication.willEnterForegroundNotification,
      object: nil)
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  @objc private func appDidEnterBackground() {
    isSessionRunning = false
    overlayView.clear()
  }

  @objc private func appWillEnterForeground() {
    if viewIfLoaded?.window != nil {
      isSessionRunning = true
      cameraFeedService.updateVideoPreviewLayer(toFrame: previewView.bounds)
      initializeHolisticLandmarkerServiceOnSessionResumption()
    }
  }

  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    cameraFeedService.updateVideoPreviewLayer(toFrame: previewView.bounds)
  }

  override func viewWillLayoutSubviews() {
    super.viewWillLayoutSubviews()
    cameraFeedService.updateVideoPreviewLayer(toFrame: previewView.bounds)
    overlayView.redrawHolisticOverlays(forNewDeviceOrientation: UIDevice.current.orientation)
  }
#endif

  // Resume camera session when click button resume
  @IBAction func onClickResume(_ sender: Any) {
    cameraFeedService.resumeInterruptedSession { [weak self] isSessionRunning in
      if isSessionRunning {
        self?.resumeButton.isHidden = true
        self?.cameraUnavailableLabel.isHidden = true
        self?.isSessionRunning = true
        self?.initializeHolisticLandmarkerServiceOnSessionResumption()
      }
    }
  }

  private func presentCameraPermissionsDeniedAlert() {
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
  }

  private func presentVideoConfigurationErrorAlert() {
    let alert = UIAlertController(
      title: "Camera Configuration Failed",
      message: "There was an error while configuring camera.",
      preferredStyle: .alert)
    alert.addAction(UIAlertAction(title: "OK", style: .default, handler: nil))

    self.present(alert, animated: true)
  }

  private func initializeHolisticLandmarkerServiceOnSessionResumption() {
    clearAndInitializeHolisticLandmarkerService()
    startObserveConfigChanges()
  }

  @objc private func clearAndInitializeHolisticLandmarkerService() {
    isSessionRunning = false
    backgroundQueue.sync { }
    holisticLandmarkerService = nil
    holisticLandmarkerService = HolisticLandmarkerService
      .liveStreamHolisticLandmarkerService(
        modelPath: InferenceConfigurationManager.sharedInstance.model.modelPath,
        minFaceDetectionConfidence: InferenceConfigurationManager.sharedInstance.minFaceDetectionConfidence,
        minFacePresenceConfidence: InferenceConfigurationManager.sharedInstance.minFacePresenceConfidence,
        minPoseDetectionConfidence: InferenceConfigurationManager.sharedInstance.minPoseDetectionConfidence,
        minPosePresenceConfidence: InferenceConfigurationManager.sharedInstance.minPosePresenceConfidence,
        minHandLandmarksConfidence: InferenceConfigurationManager.sharedInstance.minHandLandmarksConfidence,
        liveStreamDelegate: self,
        delegate: InferenceConfigurationManager.sharedInstance.delegate)
    isSessionRunning = true
  }

  private func clearHolisticLandmarkerServiceOnSessionInterruption() {
    stopObserveConfigChanges()
    isSessionRunning = false
    backgroundQueue.sync { }
    holisticLandmarkerService = nil
    DispatchQueue.main.async { [weak self] in
      self?.overlayView.clear()
    }
  }

  private func startObserveConfigChanges() {
    stopObserveConfigChanges()
    NotificationCenter.default
      .addObserver(self,
                   selector: #selector(clearAndInitializeHolisticLandmarkerService),
                   name: InferenceConfigurationManager.notificationName,
                   object: nil)
    isObserving = true
  }

  private func stopObserveConfigChanges() {
    if isObserving {
      NotificationCenter.default
        .removeObserver(self,
                        name: InferenceConfigurationManager.notificationName,
                        object: nil)
    }
    isObserving = false
  }
}

// MARK: CameraFeedServiceDelegate Methods
extension CameraViewController: CameraFeedServiceDelegate {

  func didOutput(sampleBuffer: CMSampleBuffer, orientation: UIImage.Orientation) {
    guard isSessionRunning else { return }
    let currentTimeMs = Date().timeIntervalSince1970 * 1000
    // Pass the pixel buffer to MediaPipe Tasks HolisticLandmarker
    backgroundQueue.async { [weak self] in
      guard let weakSelf = self, weakSelf.isSessionRunning else { return }
      weakSelf.holisticLandmarkerService?.detectAsync(
        sampleBuffer: sampleBuffer,
        orientation: orientation,
        timeStamps: Int(currentTimeMs))
    }
  }

  func didEncounterSessionRuntimeError() {
    // Show recovery button
    self.resumeButton.isHidden = false
    self.cameraUnavailableLabel.isHidden = false
    self.clearHolisticLandmarkerServiceOnSessionInterruption()
  }

  func sessionWasInterrupted(canResumeManually resumeManually: Bool) {
    // Updates the UI when session is interrupted.
    if resumeManually {
      self.resumeButton.isHidden = false
    } else {
      self.cameraUnavailableLabel.isHidden = false
    }
    self.clearHolisticLandmarkerServiceOnSessionInterruption()
  }

  func sessionInterruptionEnded() {
    // Reset UI once session interruption has ended.
    self.cameraUnavailableLabel.isHidden = true
    self.resumeButton.isHidden = true
    self.isSessionRunning = true
    self.initializeHolisticLandmarkerServiceOnSessionResumption()
  }
}

// MARK: HolisticLandmarkerServiceLiveStreamDelegate Methods
extension CameraViewController: HolisticLandmarkerServiceLiveStreamDelegate {

  func holisticLandmarkerService(
    _ holisticLandmarkerService: HolisticLandmarkerService,
    didFinishDetection result: ResultBundle?,
    error: Error?
  ) {
    DispatchQueue.main.async { [weak self] in
      guard let weakSelf = self, weakSelf.isSessionRunning else { return }
      weakSelf.inferenceResultDeliveryDelegate?.didPerformInference(result: result)
      guard let holisticResult = result?.holisticLandmarkerResults.first as? HolisticLandmarkerResult else {
        weakSelf.overlayView.clear()
        return
      }

      let imageSize = weakSelf.cameraFeedService.videoResolution
      guard imageSize.width > 0 && imageSize.height > 0,
            weakSelf.overlayView.bounds.width > 0 && weakSelf.overlayView.bounds.height > 0 else {
        return
      }

      let overlays = OverlayView.holisticOverlays(
        fromHolisticResult: holisticResult,
        inferredOnImageOfSize: imageSize,
        overlayViewSize: weakSelf.overlayView.bounds.size,
        imageContentMode: .scaleAspectFill,
        andOrientation: UIImage.Orientation.from(deviceOrientation: UIDevice.current.orientation))

      weakSelf.overlayView.draw(
        holisticOverlays: overlays,
        inBoundsOfContentImageOfSize: imageSize,
        edgeOffset: Constants.edgeOffset,
        imageContentMode: .scaleAspectFill)
    }
  }
}
