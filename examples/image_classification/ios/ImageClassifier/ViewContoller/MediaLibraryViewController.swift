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

import AVKit
import MediaPipeTasksVision
import UIKit

/**
 * The view controller is responsible for performing classification on videos or images selected by the user from the device media library and
 * presenting the frames with the class of the classified objects to the user.
 */
class MediaLibraryViewController: UIViewController {

  // MARK: Constants
  private struct Constants {
    static let edgeOffset: CGFloat = 2.0
    static let inferenceTimeIntervalInMilliseconds = 300.0
    static let milliSeconds = 1000.0
    static let savedPhotosNotAvailableText = "Saved photos album is not available."
    static let mediaEmptyText =
    "Click + to add an image or a video to begin running the classification."
    static let pickFromGalleryButtonInset: CGFloat = 10.0
  }

  weak var interfaceUpdatesDelegate: InterfaceUpdatesDelegate?
  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?

  // MARK: Controllers that manage functionality
  private lazy var pickerController = UIImagePickerController()
  private var playerViewController: AVPlayerViewController?

  // MARK: Image Classifier Service
  private var imageClassifierService: ImageClassifierService?

  // MARK: Private properties
  private var playerTimeObserverToken : Any?

  // MARK: Storyboards Connections
  @IBOutlet weak var pickFromGalleryButton: UIButton!
  @IBOutlet weak var progressView: UIProgressView!
  @IBOutlet weak var imageEmptyLabel: UILabel!
  @IBOutlet weak var pickedImageView: UIImageView!
  @IBOutlet weak var pickFromGalleryButtonBottomSpace: NSLayoutConstraint!

  override func viewDidLoad() {
    super.viewDidLoad()
  }

  override func viewWillLayoutSubviews() {
    super.viewWillLayoutSubviews()
    redrawBoundingBoxesForCurrentDeviceOrientation()
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)
    interfaceUpdatesDelegate?.shouldClicksBeEnabled(true)

    guard UIImagePickerController.isSourceTypeAvailable(.savedPhotosAlbum) else {
      pickFromGalleryButton.isEnabled = false
      self.imageEmptyLabel.text = Constants.savedPhotosNotAvailableText
      return
    }
    pickFromGalleryButton.isEnabled = true
    self.imageEmptyLabel.text = Constants.mediaEmptyText
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    clearPlayerView()
    imageClassifierService = nil
  }

  @IBAction func onClickPickFromGallery(_ sender: Any) {
    interfaceUpdatesDelegate?.shouldClicksBeEnabled(true)
    configurePickerController()
    present(pickerController, animated: true)
  }

  private func configurePickerController() {
    pickerController.delegate = self
    pickerController.sourceType = .savedPhotosAlbum
    pickerController.mediaTypes = [UTType.image.identifier, UTType.movie.identifier]
    pickerController.allowsEditing = false
  }

  private func addPlayerViewControllerAsChild() {
    guard let playerViewController = playerViewController else {
      return
    }
    playerViewController.view.translatesAutoresizingMaskIntoConstraints = false

    self.addChild(playerViewController)
    self.view.addSubview(playerViewController.view)
    self.view.bringSubviewToFront(self.pickFromGalleryButton)
    NSLayoutConstraint.activate([
      playerViewController.view.leadingAnchor.constraint(
        equalTo: view.leadingAnchor, constant: 0.0),
      playerViewController.view.trailingAnchor.constraint(
        equalTo: view.trailingAnchor, constant: 0.0),
      playerViewController.view.topAnchor.constraint(
        equalTo: view.topAnchor, constant: 0.0),
      playerViewController.view.bottomAnchor.constraint(
        equalTo: view.bottomAnchor, constant: 0.0)
    ])
    playerViewController.didMove(toParent: self)
  }

  private func removePlayerViewController() {
    defer {
      playerViewController?.view.removeFromSuperview()
      playerViewController?.willMove(toParent: nil)
      playerViewController?.removeFromParent()
    }
    removeObservers(player: playerViewController?.player)
    playerViewController?.player?.pause()
    playerViewController?.player = nil
  }

  private func removeObservers(player: AVPlayer?) {
    guard let player = player else {
      return
    }

    if let timeObserverToken = playerTimeObserverToken {
      player.removeTimeObserver(timeObserverToken)
      playerTimeObserverToken = nil
    }

  }

  private func openMediaLibrary() {
    configurePickerController()
    present(pickerController, animated: true)
  }

  private func clearPlayerView() {
    imageEmptyLabel.isHidden = false
    removePlayerViewController()
  }

  private func showProgressView() {
    guard let progressSuperview = progressView.superview?.superview else {
      return
    }
    progressSuperview.isHidden = false
    progressView.progress = 0.0
    progressView.observedProgress = nil
    self.view.bringSubviewToFront(progressSuperview)
  }

  private func hideProgressView() {
    guard let progressSuperview = progressView.superview?.superview else {
      return
    }
    self.view.sendSubviewToBack(progressSuperview)
    self.progressView.superview?.superview?.isHidden = true
  }

  func layoutUIElements(withInferenceViewHeight height: CGFloat) {
    pickFromGalleryButtonBottomSpace.constant =
    height + Constants.pickFromGalleryButtonInset
    view.layoutSubviews()
  }

  func redrawBoundingBoxesForCurrentDeviceOrientation() {
    guard let imageClassifierService = imageClassifierService,
          imageClassifierService.runningMode == .image ||
            self.playerViewController?.player?.timeControlStatus == .paused else {
      return
    }
  }

  deinit {
    playerViewController?.player?.removeTimeObserver(self)
  }
}

// MARK: UIImagePickerControllerDelegate, UINavigationControllerDelegate
extension MediaLibraryViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {

  func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
    picker.dismiss(animated: true)
  }

  func imagePickerController(
    _ picker: UIImagePickerController,
    didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
      clearPlayerView()
      pickedImageView.image = nil

      picker.dismiss(animated: true)

      guard let mediaType = info[.mediaType] as? String else {
        return
      }

      switch mediaType {
      case UTType.movie.identifier:
        guard let mediaURL = info[.mediaURL] as? URL else {
          imageEmptyLabel.isHidden = false
          return
        }
        clearAndInitializeImageClassifierService(runningMode: .video)
        let asset = AVAsset(url: mediaURL)
        Task {
          interfaceUpdatesDelegate?.shouldClicksBeEnabled(false)
          showProgressView()

          guard let videoDuration = try? await asset.load(.duration).seconds else {
            hideProgressView()
            return
          }

          let resultBundle = await self.imageClassifierService?.classify(
            videoAsset: asset,
            durationInMilliseconds: videoDuration * Constants.milliSeconds,
            inferenceIntervalInMilliseconds: Constants.inferenceTimeIntervalInMilliseconds)

          hideProgressView()

          playVideo(
            mediaURL: mediaURL,
            videoDuration: videoDuration,
            resultBundle: resultBundle)
        }

        imageEmptyLabel.isHidden = true
      case UTType.image.identifier:
        guard let image = info[.originalImage] as? UIImage else {
          imageEmptyLabel.isHidden = false
          break
        }
        pickedImageView.image = image
        imageEmptyLabel.isHidden = true

        showProgressView()

        clearAndInitializeImageClassifierService(runningMode: .image)

        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
          guard let weakSelf = self,
                let imageClassifierResult = weakSelf
            .imageClassifierService?
            .classify(image: image) else {
            DispatchQueue.main.async {
              self?.hideProgressView()
            }
            return
          }

          DispatchQueue.main.async {
            weakSelf.hideProgressView()
            weakSelf.inferenceResultDeliveryDelegate?.didPerformInference(result: imageClassifierResult)
          }
        }
      default:
        break
      }
    }

  func clearAndInitializeImageClassifierService(runningMode: RunningMode) {
    imageClassifierService = nil
    switch runningMode {
    case .image:
      imageClassifierService = ImageClassifierService
        .stillImageClassifierService(
          model: InferenceConfigurationManager.sharedInstance.model,
          scoreThreshold: InferenceConfigurationManager.sharedInstance.scoreThreshold,
          maxResult: InferenceConfigurationManager.sharedInstance.maxResults,
          delegate: InferenceConfigurationManager.sharedInstance.delegate
        )
    case .video:
      imageClassifierService = ImageClassifierService
        .videoImageClassifierService(
          model: InferenceConfigurationManager.sharedInstance.model,
          scoreThreshold: InferenceConfigurationManager.sharedInstance.scoreThreshold,
          maxResult: InferenceConfigurationManager.sharedInstance.maxResults,
          videoDelegate: self,
          delegate: InferenceConfigurationManager.sharedInstance.delegate)
    default:
      break;
    }
  }

  private func playVideo(mediaURL: URL, videoDuration: Double, resultBundle: ResultBundle?) {
    playVideo(asset: AVAsset(url: mediaURL))
    playerTimeObserverToken = playerViewController?.player?.addPeriodicTimeObserver(
      forInterval: CMTime(value: Int64(Constants.inferenceTimeIntervalInMilliseconds),
                          timescale: Int32(Constants.milliSeconds)),
      queue: DispatchQueue(label: "com.google.mediapipe.MediaLibraryViewController.timeObserverQueue", qos: .userInteractive),
      using: { [weak self] (time: CMTime) in
        DispatchQueue.main.async {
          let index =
          Int(CMTimeGetSeconds(time) * Constants.milliSeconds / Constants.inferenceTimeIntervalInMilliseconds)
          guard
            let weakSelf = self,
            let resultBundle = resultBundle else {
            return
          }
          self?.inferenceResultDeliveryDelegate?.didPerformInference(result: resultBundle, index: index)



          // Enable clicks on inferenceVC if playback has ended.
          if (floor(CMTimeGetSeconds(time) +
                    Constants.inferenceTimeIntervalInMilliseconds / Constants.milliSeconds)
              >= floor(videoDuration)) {
            weakSelf.interfaceUpdatesDelegate?.shouldClicksBeEnabled(true)
          }
        }
      })
  }

  private func playVideo(asset: AVAsset) {
    if playerViewController == nil {
      let playerViewController = AVPlayerViewController()
      self.playerViewController = playerViewController
    }

    let playerItem = AVPlayerItem(asset: asset)
    if let player = playerViewController?.player {
      player.replaceCurrentItem(with: playerItem)
    }
    else {
      playerViewController?.player = AVPlayer(playerItem: playerItem)
    }

    playerViewController?.showsPlaybackControls = false
    addPlayerViewControllerAsChild()
    playerViewController?.player?.play()
  }
}

// MARK: ImageClassifierServiceVideoDelegate
extension MediaLibraryViewController: ImageClassifierServiceVideoDelegate {

  func imageClassifierService(
    _ imageClassifierService: ImageClassifierService,
    didFinishClassificationOnVideoFrame index: Int) {
      progressView.observedProgress?.completedUnitCount = Int64(index + 1)
    }

  func imageClassifierService(
    _ imageClassifierService: ImageClassifierService,
    willBeginClassification totalframeCount: Int) {
      progressView.observedProgress = Progress(totalUnitCount: Int64(totalframeCount))
    }
}


