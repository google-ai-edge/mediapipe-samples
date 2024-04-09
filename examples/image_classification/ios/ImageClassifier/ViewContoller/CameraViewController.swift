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
 * The view controller is responsible for performing classification on incoming frames from the live camera and presenting the frames with the
 * class of the classified objects to the user.
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
  
  private var isSessionRunning = false
  private var isObserving = false
  private let backgroundQueue = DispatchQueue(label: "com.google.mediapipe.cameraController.backgroundQueue")
  
  // MARK: Controllers that manage functionality
  // Handles all the camera related functionality
  private lazy var cameraFeedService = CameraFeedService(previewView: previewView)
  
  private let imageClassifierServiceQueue = DispatchQueue(
    label: "com.google.mediapipe.cameraController.imageClassifierServiceQueue",
    attributes: .concurrent)
  
  // Queuing reads and writes to imageClassifierService using the Apple recommended way
  // as they can be read and written from multiple threads and can result in race conditions.
  private var _imageClassifierService: ImageClassifierService?
  private var imageClassifierService: ImageClassifierService? {
    get {
      imageClassifierServiceQueue.sync {
        return self._imageClassifierService
      }
    }
    set {
      imageClassifierServiceQueue.async(flags: .barrier) {
        self._imageClassifierService = newValue
      }
    }
  }

#if !targetEnvironment(simulator)
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    initializeImageClassifierServiceOnSessionResumption()
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
    clearImageClassifierServiceOnSessionInterruption()
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
        self?.initializeImageClassifierServiceOnSessionResumption()
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
  
  private func initializeImageClassifierServiceOnSessionResumption() {
    clearAndInitializeImageClassifierService()
    startObserveConfigChanges()
  }
  
  @objc private func clearAndInitializeImageClassifierService() {
    imageClassifierService = nil
    imageClassifierService = ImageClassifierService
        .liveStreamClassifierService(
          model: InferenceConfigurationManager.sharedInstance.model,
          scoreThreshold: InferenceConfigurationManager.sharedInstance.scoreThreshold,
          maxResult: InferenceConfigurationManager.sharedInstance.maxResults,
          liveStreamDelegate: self,
          delegate: InferenceConfigurationManager.sharedInstance.delegate)
  }
  
  private func clearImageClassifierServiceOnSessionInterruption() {
    stopObserveConfigChanges()
    imageClassifierService = nil
  }
  
  private func startObserveConfigChanges() {
    NotificationCenter.default
      .addObserver(self,
                   selector: #selector(clearAndInitializeImageClassifierService),
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

extension CameraViewController: CameraFeedServiceDelegate {
  
  func didOutput(sampleBuffer: CMSampleBuffer, orientation: UIImage.Orientation) {
    let currentTimeMs = Date().timeIntervalSince1970 * 1000
    // Pass the pixel buffer to mediapipe
    backgroundQueue.async { [weak self] in
      self?.imageClassifierService?.classifyAsync(
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
    clearImageClassifierServiceOnSessionInterruption()
  }
  
  func sessionInterruptionEnded() {
    // Updates UI once session interruption has ended.
    cameraUnavailableLabel.isHidden = true
    resumeButton.isHidden = true
    initializeImageClassifierServiceOnSessionResumption()
  }
  
  func didEncounterSessionRuntimeError() {
    // Handles session run time error by updating the UI and providing a button if session can be
    // manually resumed.
    resumeButton.isHidden = false
    clearImageClassifierServiceOnSessionInterruption()
  }
}

// MARK: ImageClassifierServiceLiveStreamDelegate
extension CameraViewController: ImageClassifierServiceLiveStreamDelegate {

  func imageClassifierService(_ imageClassifierService: ImageClassifierService, didFinishClassification result: ResultBundle?, error: Error?) {
    DispatchQueue.main.async { [weak self] in
      self?.inferenceResultDeliveryDelegate?.didPerformInference(result: result)
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

