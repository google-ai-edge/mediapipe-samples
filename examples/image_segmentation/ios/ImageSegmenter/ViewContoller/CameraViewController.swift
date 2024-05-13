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
import Metal
import MetalKit
import MetalPerformanceShaders

/**
 * The view controller is responsible for performing segmention on incoming frames from the live camera and presenting the frames with the
 * new backgrourd to the user.
 */
class CameraViewController: UIViewController {
  private struct Constants {
    static let edgeOffset: CGFloat = 2.0
  }

  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?

  @IBOutlet weak var previewView: PreviewMetalView!
  @IBOutlet weak var cameraUnavailableLabel: UILabel!
  @IBOutlet weak var resumeButton: UIButton!

  private var videoPixelBuffer: CVImageBuffer!
  private var formatDescription: CMFormatDescription!
  private var isSessionRunning = false
  private var isObserving = false
  private let backgroundQueue = DispatchQueue(label: "com.google.mediapipe.cameraController.backgroundQueue")

  // MARK: Controllers that manage functionality
  // Handles all the camera related functionality
  private lazy var cameraFeedService = CameraFeedService()
  private let render = SegmentedImageRenderer()

  private let imageSegmenterServiceQueue = DispatchQueue(
    label: "com.google.mediapipe.cameraController.imageSegmenterServiceQueue",
    attributes: .concurrent)

  // Queuing reads and writes to imageSegmenterService using the Apple recommended way
  // as they can be read and written from multiple threads and can result in race conditions.
  private var _imageSegmenterService: ImageSegmenterService?
  private var imageSegmenterService: ImageSegmenterService? {
    get {
      imageSegmenterServiceQueue.sync {
        return self._imageSegmenterService
      }
    }
    set {
      imageSegmenterServiceQueue.async(flags: .barrier) {
        self._imageSegmenterService = newValue
      }
    }
  }

#if !targetEnvironment(simulator)
  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    initializeImageSegmenterServiceOnSessionResumption()
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
    clearImageSegmenterServiceOnSessionInterruption()
  }

  override func viewDidLoad() {
    super.viewDidLoad()
    cameraFeedService.delegate = self
    // Do any additional setup after loading the view.
  }

#endif

  // Resume camera session when click button resume
  @IBAction func onClickResume(_ sender: Any) {
    cameraFeedService.resumeInterruptedSession {[weak self] isSessionRunning in
      if isSessionRunning {
        self?.resumeButton.isHidden = true
        self?.cameraUnavailableLabel.isHidden = true
        self?.initializeImageSegmenterServiceOnSessionResumption()
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

  private func initializeImageSegmenterServiceOnSessionResumption() {
    clearAndInitializeImageSegmenterService()
    startObserveConfigChanges()
  }

  @objc private func clearAndInitializeImageSegmenterService() {
    imageSegmenterService = nil
    imageSegmenterService = ImageSegmenterService
      .liveStreamImageSegmenterService(
        modelPath: InferenceConfigurationManager.sharedInstance.model.modelPath,
        liveStreamDelegate: self,
        delegate: InferenceConfigurationManager.sharedInstance.delegate)
  }

  private func clearImageSegmenterServiceOnSessionInterruption() {
    stopObserveConfigChanges()
    imageSegmenterService = nil
  }

  private func startObserveConfigChanges() {
    NotificationCenter.default
      .addObserver(self,
                   selector: #selector(clearAndInitializeImageSegmenterService),
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
    guard let videoPixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer),
          let formatDescription = CMSampleBufferGetFormatDescription(sampleBuffer) else {
      return
    }

    self.videoPixelBuffer = videoPixelBuffer
    self.formatDescription = formatDescription

    backgroundQueue.async { [weak self] in
      self?.imageSegmenterService?.segmentAsync(
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
    clearImageSegmenterServiceOnSessionInterruption()
  }

  func sessionInterruptionEnded() {
    // Updates UI once session interruption has ended.
    cameraUnavailableLabel.isHidden = true
    resumeButton.isHidden = true
    initializeImageSegmenterServiceOnSessionResumption()
  }

  func didEncounterSessionRuntimeError() {
    // Handles session run time error by updating the UI and providing a button if session can be
    // manually resumed.
    resumeButton.isHidden = false
    clearImageSegmenterServiceOnSessionInterruption()
  }
}

// MARK: ImageSegmenterServiceLiveStreamDelegate
extension CameraViewController: ImageSegmenterServiceLiveStreamDelegate {

  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService, didFinishSegmention result: ResultBundle?, error: Error?) {
    guard let imageSegmenterResult = result?.imageSegmenterResults.first as? ImageSegmenterResult,
      let confidenceMasks = imageSegmenterResult.categoryMask else { return }
    let confidenceMask = confidenceMasks.uint8Data

    if !render.isPrepared {
      render.prepare(with: formatDescription, outputRetainedBufferCountHint: 3)
    }

    let outputPixelBuffer = render.render(pixelBuffer: videoPixelBuffer, segmentDatas: confidenceMask)
    previewView.pixelBuffer = outputPixelBuffer
    inferenceResultDeliveryDelegate?.didPerformInference(result: result)
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
