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

import AVFoundation
import MediaPipeTasksVision
import UIKit

/**
 * The view controller is responsible for performing detection on incoming frames from the live camera and presenting the frames with the
 * bounding boxes and landmark of the detected faces to the user.
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
  
  private let faceDetectorServiceQueue = DispatchQueue(
    label: "com.google.mediapipe.cameraController.faceDetectorServiceQueue",
    attributes: .concurrent)
  
  // Queuing reads and writes to faceDetectorService using the Apple recommended way
  // as they can be read and written from multiple threads and can result in race conditions.
  private var _faceDetectorService: FaceDetectorService?
  private var faceDetectorService: FaceDetectorService? {
    get {
      faceDetectorServiceQueue.sync {
        return self._faceDetectorService
      }
    }
    set {
      faceDetectorServiceQueue.async(flags: .barrier) {
        self._faceDetectorService = newValue
      }
    }
  }

#if !targetEnvironment(simulator)
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    initializeFaceDetectorServiceOnSessionResumption()
    cameraFeedService.startLiveCameraSession {[weak self] cameraConfiguration in
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
    cameraFeedService.stopSession()
    clearFaceDetectorServiceOnSessionInterruption()
  }
  
  override func viewDidLoad() {
    super.viewDidLoad()
    cameraFeedService.delegate = self
    // Do any additional setup after loading the view.
  }
  
  override func viewDidAppear(_ animated: Bool) {
    super.viewDidAppear(animated)
    cameraFeedService.updateVideoPreviewLayer(toFrame: previewView.bounds)
  }
  
  override func viewWillLayoutSubviews() {
    super.viewWillLayoutSubviews()
    cameraFeedService.updateVideoPreviewLayer(toFrame: previewView.bounds)
  }
#endif
  
  // Resume camera session when click button resume
  @IBAction func onClickResume(_ sender: Any) {
    cameraFeedService.resumeInterruptedSession {[weak self] isSessionRunning in
      if isSessionRunning {
        self?.resumeButton.isHidden = true
        self?.cameraUnavailableLabel.isHidden = true
        self?.initializeFaceDetectorServiceOnSessionResumption()
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
  
  private func initializeFaceDetectorServiceOnSessionResumption() {
    clearAndInitializeFaceDetectorService()
    startObserveConfigChanges()
  }
  
  @objc private func clearAndInitializeFaceDetectorService() {
    faceDetectorService = nil
      faceDetectorService = FaceDetectorService
        .liveStreamDetectorService(
          modelPath: InferenceConfigurationManager.sharedInstance.modelPath,
          minDetectionConfidence: InferenceConfigurationManager.sharedInstance.minDetectionConfidence,
          minSuppressionThreshold: InferenceConfigurationManager.sharedInstance.minSuppressionThreshold,
          liveStreamDelegate: self,
          delegate: InferenceConfigurationManager.sharedInstance.delegate)
  }
  
  private func clearFaceDetectorServiceOnSessionInterruption() {
    stopObserveConfigChanges()
    faceDetectorService = nil
  }
  
  private func startObserveConfigChanges() {
    NotificationCenter.default
      .addObserver(self,
                   selector: #selector(clearAndInitializeFaceDetectorService),
                   name: InferenceConfigurationManager.notificationName,
                   object: nil)
    isObserving = true
  }
  
  private func stopObserveConfigChanges() {
    if isObserving {
      NotificationCenter.default
        .removeObserver(self,
                        name:InferenceConfigurationManager.notificationName,
                        object: nil)
    }
    isObserving = false
  }
}

extension CameraViewController: CameraFeedServiceDelegate {
  
  func didOutput(sampleBuffer: CMSampleBuffer, orientation: UIImage.Orientation) {
    let currentTimeMs = Date().timeIntervalSince1970 * 1000
    // Pass the pixel buffer to mediapipe
    backgroundQueue.async { [weak self] in
      self?.faceDetectorService?.detectAsync(
        sampleBuffer: sampleBuffer,
        orientation: orientation,
        timeStamps: Int(currentTimeMs))
    }
  }
  
  // MARK: Session Handling Alerts
  func sessionWasInterrupted(canResumeManually resumeManually: Bool) {
    // Updates the UI when session is interupted.
    if resumeManually {
      resumeButton.isHidden = false
    } else {
      cameraUnavailableLabel.isHidden = false
    }
    clearFaceDetectorServiceOnSessionInterruption()
  }
  
  func sessionInterruptionEnded() {
    // Updates UI once session interruption has ended.
    cameraUnavailableLabel.isHidden = true
    resumeButton.isHidden = true
    initializeFaceDetectorServiceOnSessionResumption()
  }
  
  func didEncounterSessionRuntimeError() {
    // Handles session run time error by updating the UI and providing a button if session can be
    // manually resumed.
    resumeButton.isHidden = false
    clearFaceDetectorServiceOnSessionInterruption()
  }
}

// MARK: FaceDetectorServiceLiveStreamDelegate
extension CameraViewController: FaceDetectorServiceLiveStreamDelegate {

  func faceDetectorService(
    _ faceDetectorService: FaceDetectorService,
    didFinishDetection result: ResultBundle?,
    error: Error?) {
    DispatchQueue.main.async { [weak self] in
      guard let weakSelf = self else {
        return
      }
      weakSelf.inferenceResultDeliveryDelegate?.didPerformInference(result: result)
      guard let faceDetectorResult =
              result?.faceDetectorResults.first as? FaceDetectorResult else {
        return
      }
      let imageSize = weakSelf.cameraFeedService.videoResolution
      weakSelf.overlayView.draw(
        objectOverlays: OverlayView.objectOverlays(
          fromDetections: faceDetectorResult.detections,
          inferredOnImageOfSize: imageSize,
          andOrientation:  UIImage.Orientation.from(
            deviceOrientation: UIDevice.current.orientation)),
        inBoundsOfContentImageOfSize: imageSize,
        edgeOffset: Constants.edgeOffset,
        imageContentMode: weakSelf.cameraFeedService.videoGravity.contentMode)
    }
  }
}

// MARK: - AVLayerVideoGravity Extension
extension AVLayerVideoGravity {
  var contentMode: UIView.ContentMode {
    switch self {
      case .resizeAspectFill:
        return .scaleAspectFill
      case .resizeAspect:
        return .scaleAspectFit
      case .resize:
        return .scaleToFill
      default:
        return .scaleAspectFill
    }
  }
}

