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
import AVFoundation

// MARK: CameraFeedManagerDelegate Declaration
protocol CameraFeedManagerDelegate: AnyObject {

  /**
   This method delivers the pixel buffer of the current frame seen by the device's camera.
   */
  func didOutput(sampleBuffer: CMSampleBuffer, orientation: UIImage.Orientation)

  /**
   This method initimates that the camera permissions have been denied.
   */
  func presentCameraPermissionsDeniedAlert()

  /**
   This method initimates that there was an error in video configurtion.
   */
  func presentVideoConfigurationErrorAlert()

  /**
   This method initimates that a session runtime error occured.
   */
  func sessionRunTimeErrorOccured()

  /**
   This method initimates that the session was interrupted.
   */
  func sessionWasInterrupted(canResumeManually resumeManually: Bool)

  /**
   This method initimates that the session interruption has ended.
   */
  func sessionInterruptionEnded()

}

/**
 This enum holds the state of the camera initialization.
 */
enum CameraConfiguration {

  case success
  case failed
  case permissionDenied
}

/**
 This class manages all camera related functionality
 */
class CameraFeedManager: NSObject {

  // MARK: Camera Related Instance Variables
  private let session: AVCaptureSession = AVCaptureSession()
  private let previewView: PreviewView
  private let sessionQueue = DispatchQueue(label: "sessionQueue")
  private var cameraConfiguration: CameraConfiguration = .failed
  private lazy var videoDataOutput = AVCaptureVideoDataOutput()
  private var isSessionRunning = false
  private var coreImageContext: CIContext
  private var needCalculationSize = true
  private let cameraPosition: AVCaptureDevice.Position = .back

  var orientation: UIImage.Orientation {
    get {
      switch UIDevice.current.orientation {
      case .landscapeLeft:
          return .left
      case .landscapeRight:
          return .right
      default:
          return .up
      }
    }
  }
  var videoFrameSize: CGSize = .zero

  // MARK: CameraFeedManagerDelegate
  weak var delegate: CameraFeedManagerDelegate?

  // MARK: Initializer
  init(previewView: PreviewView) {
    self.previewView = previewView
    if let metalDevice = MTLCreateSystemDefaultDevice() {
      coreImageContext = CIContext(mtlDevice: metalDevice)
    } else {
      coreImageContext = CIContext(options: nil)
    }
    super.init()

    // Initializes the session
    session.sessionPreset = .high
    self.previewView.session = session
    self.previewView.previewLayer.connection?.videoOrientation = .portrait
    self.previewView.previewLayer.videoGravity = .resizeAspectFill
    self.attemptToConfigureSession()
    NotificationCenter.default.addObserver(self, selector: #selector(orientationChanged), name: UIDevice.orientationDidChangeNotification, object: nil)
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
  }

  // MARK: notification methods
  @objc func orientationChanged(notification: Notification) {
    needCalculationSize = true
    switch orientation {
    case .up:
      previewView.previewLayer.connection?.videoOrientation = .portrait
    case .left:
      previewView.previewLayer.connection?.videoOrientation = .landscapeRight
    case .right:
      previewView.previewLayer.connection?.videoOrientation = .landscapeLeft
    default:
      break
    }
  }

  // MARK: Session Start and End methods

  /**
   This method starts an AVCaptureSession based on whether the camera configuration was successful.
   */

  func checkCameraConfigurationAndStartSession() {
    sessionQueue.async {
      switch self.cameraConfiguration {
      case .success:
        self.addObservers()
        self.startSession()
      case .failed:
        DispatchQueue.main.async {
          self.delegate?.presentVideoConfigurationErrorAlert()
        }
      case .permissionDenied:
        DispatchQueue.main.async {
          self.delegate?.presentCameraPermissionsDeniedAlert()
        }
      }
    }
  }

  /**
   This method stops a running an AVCaptureSession.
   */
  func stopSession() {
    self.removeObservers()
    sessionQueue.async {
      if self.session.isRunning {
        self.session.stopRunning()
        self.isSessionRunning = self.session.isRunning
      }
    }

  }

  /**
   This method resumes an interrupted AVCaptureSession.
   */
  func resumeInterruptedSession(withCompletion completion: @escaping (Bool) -> ()) {

    sessionQueue.async {
      self.startSession()

      DispatchQueue.main.async {
        completion(self.isSessionRunning)
      }
    }
  }

  /**
   This method starts the AVCaptureSession
   **/
  private func startSession() {
    self.session.startRunning()
    self.isSessionRunning = self.session.isRunning
  }

  // MARK: Session Configuration Methods.
  /**
   This method requests for camera permissions and handles the configuration of the session and stores the result of configuration.
   */
  private func attemptToConfigureSession() {
    switch AVCaptureDevice.authorizationStatus(for: .video) {
    case .authorized:
      self.cameraConfiguration = .success
    case .notDetermined:
      self.sessionQueue.suspend()
      self.requestCameraAccess(completion: { (granted) in
        self.sessionQueue.resume()
      })
    case .denied:
      self.cameraConfiguration = .permissionDenied
    default:
      break
    }

    self.sessionQueue.async {
      self.configureSession()
    }
  }

  /**
   This method requests for camera permissions.
   */
  private func requestCameraAccess(completion: @escaping (Bool) -> ()) {
    AVCaptureDevice.requestAccess(for: .video) { (granted) in
      if !granted {
        self.cameraConfiguration = .permissionDenied
      }
      else {
        self.cameraConfiguration = .success
      }
      completion(granted)
    }
  }


  /**
   This method handles all the steps to configure an AVCaptureSession.
   */
  private func configureSession() {

    guard cameraConfiguration == .success else {
      return
    }
    session.beginConfiguration()

    // Tries to add an AVCaptureDeviceInput.
    guard addVideoDeviceInput() == true else {
      self.session.commitConfiguration()
      self.cameraConfiguration = .failed
      return
    }

    // Tries to add an AVCaptureVideoDataOutput.
    guard addVideoDataOutput() else {
      self.session.commitConfiguration()
      self.cameraConfiguration = .failed
      return
    }

    session.commitConfiguration()
    self.cameraConfiguration = .success
  }

  /**
   This method tries to an AVCaptureDeviceInput to the current AVCaptureSession.
   */
  private func addVideoDeviceInput() -> Bool {

    /**Tries to get the default back camera.
     */
    guard let camera  = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: cameraPosition) else {
      return false
    }

    do {
      let videoDeviceInput = try AVCaptureDeviceInput(device: camera)
      if session.canAddInput(videoDeviceInput) {
        session.addInput(videoDeviceInput)
        return true
      }
      else {
        return false
      }
    }
    catch {
      fatalError("Cannot create video device input")
    }
  }

  /**
   This method tries to an AVCaptureVideoDataOutput to the current AVCaptureSession.
   */
  private func addVideoDataOutput() -> Bool {

    let sampleBufferQueue = DispatchQueue(label: "sampleBufferQueue")
    videoDataOutput.setSampleBufferDelegate(self, queue: sampleBufferQueue)
    videoDataOutput.alwaysDiscardsLateVideoFrames = true
    videoDataOutput.videoSettings = [ String(kCVPixelBufferPixelFormatTypeKey) : kCMPixelFormat_32BGRA]

    if session.canAddOutput(videoDataOutput) {
      session.addOutput(videoDataOutput)
      videoDataOutput.connection(with: .video)?.videoOrientation = .portrait
      if videoDataOutput.connection(with: .video)?.isVideoOrientationSupported == true
          && cameraPosition == .front {
        videoDataOutput.connection(with: .video)?.isVideoMirrored = true
      }
      return true
    }
    return false
  }

  // MARK: Notification Observer Handling
  private func addObservers() {
    NotificationCenter.default.addObserver(self, selector: #selector(CameraFeedManager.sessionRuntimeErrorOccured(notification:)), name: NSNotification.Name.AVCaptureSessionRuntimeError, object: session)
    NotificationCenter.default.addObserver(self, selector: #selector(CameraFeedManager.sessionWasInterrupted(notification:)), name: NSNotification.Name.AVCaptureSessionWasInterrupted, object: session)
    NotificationCenter.default.addObserver(self, selector: #selector(CameraFeedManager.sessionInterruptionEnded), name: NSNotification.Name.AVCaptureSessionInterruptionEnded, object: session)
  }

  private func removeObservers() {
    NotificationCenter.default.removeObserver(self, name: NSNotification.Name.AVCaptureSessionRuntimeError, object: session)
    NotificationCenter.default.removeObserver(self, name: NSNotification.Name.AVCaptureSessionWasInterrupted, object: session)
    NotificationCenter.default.removeObserver(self, name: NSNotification.Name.AVCaptureSessionInterruptionEnded, object: session)
  }

  // MARK: Notification Observers
  @objc func sessionWasInterrupted(notification: Notification) {

    if let userInfoValue = notification.userInfo?[AVCaptureSessionInterruptionReasonKey] as AnyObject?,
       let reasonIntegerValue = userInfoValue.integerValue,
       let reason = AVCaptureSession.InterruptionReason(rawValue: reasonIntegerValue) {
      print("Capture session was interrupted with reason \(reason)")

      var canResumeManually = false
      if reason == .videoDeviceInUseByAnotherClient {
        canResumeManually = true
      } else if reason == .videoDeviceNotAvailableWithMultipleForegroundApps {
        canResumeManually = false
      }

      self.delegate?.sessionWasInterrupted(canResumeManually: canResumeManually)

    }
  }

  @objc func sessionInterruptionEnded(notification: Notification) {

    self.delegate?.sessionInterruptionEnded()
  }

  @objc func sessionRuntimeErrorOccured(notification: Notification) {
    guard let error = notification.userInfo?[AVCaptureSessionErrorKey] as? AVError else {
      return
    }

    print("Capture session runtime error: \(error)")

    if error.code == .mediaServicesWereReset {
      sessionQueue.async {
        if self.isSessionRunning {
          self.startSession()
        } else {
          DispatchQueue.main.async {
            self.delegate?.sessionRunTimeErrorOccured()
          }
        }
      }
    } else {
      self.delegate?.sessionRunTimeErrorOccured()

    }
  }
}

/**
 AVCaptureVideoDataOutputSampleBufferDelegate
 */
extension CameraFeedManager: AVCaptureVideoDataOutputSampleBufferDelegate {

  /** This method delegates the CVPixelBuffer of the frame seen by the camera currently.
   */
  func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
    if needCalculationSize {
      let imageBuffer = CMSampleBufferGetImageBuffer(sampleBuffer)!
      switch orientation {
      case .left, .right:
        videoFrameSize = CGSize(width: CVPixelBufferGetHeight(imageBuffer), height: CVPixelBufferGetWidth(imageBuffer))
      default:
        videoFrameSize = CGSize(width: CVPixelBufferGetWidth(imageBuffer), height: CVPixelBufferGetHeight(imageBuffer))
      }
      needCalculationSize = false
    }
    delegate?.didOutput(sampleBuffer: sampleBuffer, orientation: orientation)
  }
}
