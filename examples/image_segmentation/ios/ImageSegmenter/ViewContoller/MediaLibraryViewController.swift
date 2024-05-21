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
import Metal
import MetalKit
import MetalPerformanceShaders

/**
 * The view controller is responsible for performing segmention on videos or images selected by the user from the device media library and
 * presenting them with the new backgrourd of the image to the user.
 */
class MediaLibraryViewController: UIViewController {

  // MARK: Constants
  private struct Constants {
    static let edgeOffset: CGFloat = 2.0
    static let inferenceTimeIntervalInMilliseconds = 200.0
    static let milliSeconds = 1000.0
    static let savedPhotosNotAvailableText = "Saved photos album is not available."
    static let mediaEmptyText =
    "Click + to add an image or a video to begin running the image sengmentation."
    static let pickFromGalleryButtonInset: CGFloat = 10.0
  }
  // MARK: Face Segmenter Service
  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?

  // MARK: Controllers that manage functionality
  private lazy var pickerController = UIImagePickerController()
  private var playerViewController: AVPlayerViewController?

  private let backgroundQueue = DispatchQueue(label: "com.google.mediapipe.cameraController.backgroundQueue")
  private let imageSegmenterServiceQueue = DispatchQueue(
    label: "com.google.mediapipe.cameraController.imageSegmenterServiceQueue",
    attributes: .concurrent)

  // MARK: Face Segmenter Service
  private var imageSegmenterService: ImageSegmenterService?
  private let render = SegmentedImageRenderer()

  // MARK: Storyboards Connections
  @IBOutlet weak var pickFromGalleryButton: UIButton!
  @IBOutlet weak var progressView: UIProgressView!
  @IBOutlet weak var imageEmptyLabel: UILabel!
  @IBOutlet weak var pickedImageView: UIImageView!
  @IBOutlet weak var pickFromGalleryButtonBottomSpace: NSLayoutConstraint!
  @IBOutlet weak var previewView: PreviewMetalView!

  override func viewDidLoad() {
    super.viewDidLoad()
  }

  override func viewWillLayoutSubviews() {
    super.viewWillLayoutSubviews()
    redrawBoundingBoxesForCurrentDeviceOrientation()
  }

  override func viewWillAppear(_ animated: Bool) {
    super.viewWillAppear(animated)

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
    imageSegmenterService = nil
  }

  @IBAction func onClickPickFromGallery(_ sender: Any) {
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

    playerViewController?.player?.pause()
    playerViewController?.player = nil
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
    guard let imageSegmenterService = imageSegmenterService,
          imageSegmenterService.runningMode == .image ||
            self.playerViewController?.player?.timeControlStatus == .paused else {
      return
    }
  }

  deinit {
    playerViewController?.player?.removeTimeObserver(self)
  }
}

extension MediaLibraryViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {

  func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
    picker.dismiss(animated: true)
  }

  private func playVideoAnDetect(asset: AVAsset) {
    let videoDescription = getVideoFormatDescription(from: asset)
    guard let formatDescription = videoDescription.description else { return }
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
    guard let player = playerViewController?.player, let playerItem = player.currentItem else { return }
    let timeRange = CMTimeRange(start: .zero, duration: asset.duration)
//    var datas: [UnsafePointer<Float32>] = []
    let videoComposition = AVMutableVideoComposition(asset: asset) { [weak self] request in
      guard let self = self else { return }
      backgroundQueue.async {
        if !self.render.isPrepared {
          self.render.prepare(with: formatDescription, outputRetainedBufferCountHint: 3, needChangeWidthHeight: videoDescription.needChangeWidthHeight)
        }
        let sourceImage = request.sourceImage
        let time = Float((request.compositionTime - timeRange.start).seconds)
        let cgimage = self.render.getCGImmage(ciImage: sourceImage)
        guard let resultBundle = self.imageSegmenterService?.segment(videoFrame: cgimage, orientation: .up, timeStamps: Int(time * 1000)) else {
          request.finish(with: sourceImage, context: nil)
          return
        }
        self.inferenceResultDeliveryDelegate?.didPerformInference(result: resultBundle)
        guard let result = resultBundle.imageSegmenterResults.first, let result = result else { return }
        let mark = result.categoryMask
        let uint8Data = mark?.uint8Data
        guard let outputPixelBuffer = self.render.render(ciImage: sourceImage, categoryMasks: uint8Data) else {
          request.finish(with: sourceImage, context: nil)
          return
        }
        let outputImage = CIImage(cvImageBuffer: outputPixelBuffer)
        request.finish(with: outputImage, context: nil)
      }
    }

    playerItem.videoComposition = videoComposition
    playerViewController?.player?.play()
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
      render.reset()

      switch mediaType {
      case UTType.movie.identifier:
        guard let mediaURL = info[.mediaURL] as? URL else {
          imageEmptyLabel.isHidden = false
          return
        }
        clearAndInitializeImageSegmenterService(runningMode: .video)
        let asset = AVAsset(url: mediaURL)
        playVideoAnDetect(asset: asset)

      case UTType.image.identifier:
        guard let image = info[.originalImage] as? UIImage else {
          imageEmptyLabel.isHidden = false
          break
        }
        imageEmptyLabel.isHidden = true

        showProgressView()

        clearAndInitializeImageSegmenterService(runningMode: .image)
        print(image.size)

        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
          guard let self = self,
                let resultBundle = self.imageSegmenterService?.segment(image: image),
                let imageSegmenterResult = resultBundle.imageSegmenterResults.first,
                let imageSegmenterResult = imageSegmenterResult else {
            DispatchQueue.main.async {
              self?.hideProgressView()
            }
            return
          }

          DispatchQueue.main.async {
            self.hideProgressView()
            self.render.prepare(with: image.size, outputRetainedBufferCountHint: 3)
            self.inferenceResultDeliveryDelegate?.didPerformInference(result: resultBundle)
            let mark = imageSegmenterResult.categoryMask
            let uint8Data = mark?.uint8Data
            let newImage = self.render.render(image: image, categoryMasks: uint8Data)
            self.pickedImageView.image = newImage
          }
        }
      default:
        break
      }
    }

  private func imageGenerator(with videoAsset: AVAsset) -> AVAssetImageGenerator {
    let generator = AVAssetImageGenerator(asset: videoAsset)
    generator.requestedTimeToleranceBefore = CMTimeMake(value: 1, timescale: 25)
    generator.requestedTimeToleranceAfter = CMTimeMake(value: 1, timescale: 25)
    generator.appliesPreferredTrackTransform = true

    return generator
  }

  func clearAndInitializeImageSegmenterService(runningMode: RunningMode) {
    imageSegmenterService = nil
    switch runningMode {
    case .image:
      imageSegmenterService = ImageSegmenterService.stillImageSegmenterService(
        modelPath: InferenceConfigurationManager.sharedInstance.model.modelPath,
        delegate: InferenceConfigurationManager.sharedInstance.delegate)
    case .video:
      imageSegmenterService = ImageSegmenterService.videoImageSegmenterService(
        modelPath: InferenceConfigurationManager.sharedInstance.model.modelPath,
        delegate: InferenceConfigurationManager.sharedInstance.delegate)
    default:
      break;
    }
  }

  func getVideoFormatDescription(from asset: AVAsset) -> (description: CMFormatDescription?, needChangeWidthHeight: Bool)  {
    // Get the video track from the asset
    guard let videoTrack = asset.tracks(withMediaType: AVMediaType.video).first else {
      print("No video track found in the asset.")
      return (nil, false)
    }
    let naturalSize = videoTrack.naturalSize
    let size = videoTrack.naturalSize.applying(videoTrack.preferredTransform)
    let newSize = CGSize(width: abs(size.width), height: abs(size.height))

    // Create an asset reader
    do {
      let assetReader = try AVAssetReader(asset: asset)

      // Create an asset reader track output
      let outputSettings: [String: Any] = [kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA]
      let trackOutput = AVAssetReaderTrackOutput(track: videoTrack, outputSettings: outputSettings)
      assetReader.add(trackOutput)

      // Start the asset reader
      guard assetReader.startReading() else {
        print("Failed to start asset reader.")
        return (nil, false)
      }
      defer {
        assetReader.cancelReading()
      }
      // Read a sample to get the format description
      if let sampleBuffer = trackOutput.copyNextSampleBuffer() {
        if let formatDescription = CMSampleBufferGetFormatDescription(sampleBuffer) {
          return (formatDescription, naturalSize.width == newSize.height)
        } else {
          print("Failed to get format description from sample buffer.")
        }
      } else {
        print("Failed to read a sample buffer.")
      }
    } catch {
      print("Error creating asset reader: \(error)")
    }
    return (nil, false)
  }
}
