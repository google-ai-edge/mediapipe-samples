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
 * The view controller is responsible for performing embedding on incoming frames from the live camera and presenting the frames with the
 * class of the embedded objects to the user.
 */
class CameraViewController: UIViewController {
  private struct Constants {
    static let edgeOffset: CGFloat = 2.0
  }
  
  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?
  
  @IBOutlet weak var compareImgeView: UIImageView!
  @IBOutlet weak var previewView: UIView!
  @IBOutlet weak var cameraUnavailableLabel: UILabel!
  @IBOutlet weak var resumeButton: UIButton!

  @IBOutlet weak var clickImageLabel: UILabel!
  private var compareImageEmbedderResult: ResultBundle?
  private var isSessionRunning = false
  private var isObserving = false
  private let backgroundQueue = DispatchQueue(label: "com.google.mediapipe.cameraController.backgroundQueue")
  
  // MARK: Controllers that manage functionality
  // Handles all the camera related functionality
  private lazy var cameraFeedService = CameraFeedService(previewView: previewView)
  private lazy var pickerController = UIImagePickerController()

  private let imageEmbedderServiceQueue = DispatchQueue(
    label: "com.google.mediapipe.cameraController.imageEmbedderServiceQueue",
    attributes: .concurrent)
  
  // Queuing reads and writes to imageEmbedderService using the Apple recommended way
  // as they can be read and written from multiple threads and can result in race conditions.
  private var _imageEmbedderService: ImageEmbedderService?
  private var imageEmbedderService: ImageEmbedderService? {
    get {
      imageEmbedderServiceQueue.sync {
        return self._imageEmbedderService
      }
    }
    set {
      imageEmbedderServiceQueue.async(flags: .barrier) {
        self._imageEmbedderService = newValue
      }
    }
  }

#if !targetEnvironment(simulator)
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    initializeImageEmbedderServiceOnSessionResumption()
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
    clearImageEmbedderServiceOnSessionInterruption()
  }
  
  override func viewDidLoad() {
    super.viewDidLoad()
    cameraFeedService.delegate = self
    compareImgeView.addGestureRecognizer(UITapGestureRecognizer(target: self, action: #selector(compareImageClick)))
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
        self?.initializeImageEmbedderServiceOnSessionResumption()
      }
    }
  }

  @objc private func compareImageClick() {
    configurePickerController()
    present(pickerController, animated: true)
  }

  private func configurePickerController() {
    pickerController.delegate = self
    pickerController.sourceType = .savedPhotosAlbum
    pickerController.mediaTypes = [UTType.image.identifier]
    pickerController.allowsEditing = false
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
  
  private func initializeImageEmbedderServiceOnSessionResumption() {
    clearAndInitializeImageEmbedderService()
    startObserveConfigChanges()
  }
  
  @objc private func clearAndInitializeImageEmbedderService() {
    imageEmbedderService = nil
    imageEmbedderService = ImageEmbedderService
        .liveStreamEmbedderService(
          model: InferenceConfigurationManager.sharedInstance.model,
          liveStreamDelegate: self,
          delegate: InferenceConfigurationManager.sharedInstance.delegate)
    if let compareImage =  compareImgeView.image {
      getImageEmbedding(image: compareImage)
    }
  }

  private func getImageEmbedding(image: UIImage) {
    let imageEmbedderService = ImageEmbedderService.stillImageEmbedderService(
      model: InferenceConfigurationManager.sharedInstance.model,
      delegate: InferenceConfigurationManager.sharedInstance.delegate)
    DispatchQueue.global(qos: .userInteractive).async { [weak self] in
      self?.compareImageEmbedderResult = imageEmbedderService?.embed(image: image)
    }
  }

  private func clearImageEmbedderServiceOnSessionInterruption() {
    stopObserveConfigChanges()
    imageEmbedderService = nil
  }
  
  private func startObserveConfigChanges() {
    NotificationCenter.default
      .addObserver(self,
                   selector: #selector(clearAndInitializeImageEmbedderService),
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
      self?.imageEmbedderService?.embedAsync(
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
    clearImageEmbedderServiceOnSessionInterruption()
  }
  
  func sessionInterruptionEnded() {
    // Updates UI once session interruption has ended.
    cameraUnavailableLabel.isHidden = true
    resumeButton.isHidden = true
    initializeImageEmbedderServiceOnSessionResumption()
  }
  
  func didEncounterSessionRuntimeError() {
    // Handles session run time error by updating the UI and providing a button if session can be
    // manually resumed.
    resumeButton.isHidden = false
    clearImageEmbedderServiceOnSessionInterruption()
  }
}

// MARK: ImageEmbedderServiceLiveStreamDelegate
extension CameraViewController: ImageEmbedderServiceLiveStreamDelegate {
  func imageEmbedderService(_ imageEmbedderService: ImageEmbedderService, didFinishEmbedding result: ResultBundle?, error: (any Error)?) {
    inferenceResultDeliveryDelegate?.didPerformInference(result1: result, result2: compareImageEmbedderResult)
  }
}

// MARK: UIImagePickerControllerDelegate
extension CameraViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {

  func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
    picker.dismiss(animated: true)
  }

  func imagePickerController(
    _ picker: UIImagePickerController,
    didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
      picker.dismiss(animated: true)
      guard let mediaType = info[.mediaType] as? String else {
        return
      }

      switch mediaType {
      case UTType.image.identifier:
        guard let image = info[.originalImage] as? UIImage else {
          break
        }
        compareImgeView.image = image
        clickImageLabel.text = "Click to change image"
        getImageEmbedding(image: image)
      default:
        break
      }
    }
}
