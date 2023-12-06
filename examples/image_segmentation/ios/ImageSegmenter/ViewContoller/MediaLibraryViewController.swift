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
 * presenting them with the landmarks of the face to the user.
 */
class MediaLibraryViewController: UIViewController {
  
  // MARK: Constants
  private struct Constants {
    static let edgeOffset: CGFloat = 2.0
    static let inferenceTimeIntervalInMilliseconds = 300.0
    static let milliSeconds = 1000.0
    static let savedPhotosNotAvailableText = "Saved photos album is not available."
    static let mediaEmptyText =
    "Click + to add an image or a video to begin running the face landmark."
    static let pickFromGalleryButtonInset: CGFloat = 10.0
  }
  // MARK: Face Segmenter Service
  weak var interfaceUpdatesDelegate: InterfaceUpdatesDelegate?
  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?
  
  // MARK: Controllers that manage functionality
  private lazy var pickerController = UIImagePickerController()
  private var playerViewController: AVPlayerViewController?
  
  // MARK: Face Segmenter Service
  private var imageSegmenterService: ImageSegmenterService?
  
  // MARK: Private properties
  private var playerTimeObserverToken : Any?
  
  // MARK: Storyboards Connections
  @IBOutlet weak var overlayView: OverlayView!
  @IBOutlet weak var pickFromGalleryButton: UIButton!
  @IBOutlet weak var progressView: UIProgressView!
  @IBOutlet weak var imageEmptyLabel: UILabel!
  @IBOutlet weak var pickedImageView: UIImageView!
  @IBOutlet weak var pickFromGalleryButtonBottomSpace: NSLayoutConstraint!
  
  override func viewDidLoad() {
    super.viewDidLoad()
    setupDrive()
  }

  func setupDrive() {
    device = MTLCreateSystemDefaultDevice()
    library = device.makeDefaultLibrary()!
    commandQueue = device.makeCommandQueue()!
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
    if imageSegmenterService?.runningMode == .video {
      overlayView.clear()
    }
    imageSegmenterService = nil
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
    self.view.bringSubviewToFront(self.overlayView)
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
    guard let imageSegmenterService = imageSegmenterService,
          imageSegmenterService.runningMode == .image ||
          self.playerViewController?.player?.timeControlStatus == .paused else {
      return
    }
    overlayView
      .redrawFaceOverlays(
        forNewDeviceOrientation: UIDevice.current.orientation)
  }
  
  deinit {
    playerViewController?.player?.removeTimeObserver(self)
  }

  // MARK: - MTL
  var device: MTLDevice!
  var library: MTLLibrary!
  var commandQueue: MTLCommandQueue!
#warning ("Demo shader")
  func demoRemoveBg(result: ImageSegmenterResult, image: UIImage) -> UIImage? {
//    let imageOrientation = image.imageOrientation.rawValue
    return image
    let function = (library.makeFunction(name: "drawWithInvertedColor"))!
    var computePipeline: MTLComputePipelineState?
    do {
      computePipeline = try device.makeComputePipelineState(function: function)
    } catch {
      print(error)
    }
    guard let computePipeline = computePipeline else { return nil }
    let textureLoader = MTKTextureLoader(device: device)
    let image2 = UIImage(named: "bg1.jpeg")!
    var inputTexture: MTLTexture!
    do {
      inputTexture = try textureLoader.newTexture(cgImage: image.cgImage!)
    } catch {
      print(error)
      return nil
    }
    let inputTexture2 = try? textureLoader.newTexture(cgImage: image2.cgImage!)
    let textureDescriptor = MTLTextureDescriptor.texture2DDescriptor(pixelFormat: .bgra8Unorm, width: inputTexture.width, height: inputTexture.height, mipmapped: false)
    textureDescriptor.usage = .unknown

    let outputTexture = device.makeTexture(descriptor: textureDescriptor)
    let inputScaleTexture = device.makeTexture(descriptor: textureDescriptor)
    resizeTexture(sourceTexture: inputTexture2!, desTexture: inputScaleTexture!, targetSize: MTLSize(width: inputTexture.width, height: inputTexture.height, depth: 1), resizeMode: .scaleToFill)

    let marks = result.confidenceMasks
//      for _mark in marks! {
    let _mark = marks![0]
    let float32Data = _mark.float32Data
    let legth = _mark.width * _mark.height * MemoryLayout<Float>.size
    let bs = device.makeBuffer(bytes: float32Data, length: legth)
    let date = Date()
    let commandBuffer = commandQueue!.makeCommandBuffer()
    let commandEncoder = commandBuffer!.makeComputeCommandEncoder()
    commandEncoder?.setComputePipelineState(computePipeline)
    commandEncoder?.setTexture(inputTexture, index: 0)
    commandEncoder?.setTexture(inputScaleTexture, index: 1)
    commandEncoder?.setTexture(outputTexture, index: 2)
    commandEncoder?.setBuffer(bs, offset: 0, index: 3)
    let threadsPerThreadGroup = MTLSize(width: 16, height: 16, depth: 1)
    let threadgroupsPerGrid = MTLSize(width: inputTexture.width / 16 + 1, height: inputTexture.height / 16 + 1, depth: 1)
    print(inputTexture.width)
    print(inputTexture.height)
    print(threadgroupsPerGrid)
    print(threadsPerThreadGroup)
    commandEncoder?.dispatchThreadgroups(threadgroupsPerGrid, threadsPerThreadgroup: threadsPerThreadGroup)
    commandEncoder?.endEncoding()
    commandBuffer?.commit()
    commandBuffer?.waitUntilCompleted()
    let timems = Date().timeIntervalSince(date) * 1000
    print("\(timems) ms")
    let ciimage = CIImage(mtlTexture: outputTexture!)?.oriented(.up)
    return UIImage(ciImage: ciimage!)
  }

  func resizeTexture(sourceTexture: MTLTexture, desTexture: MTLTexture, targetSize:MTLSize, resizeMode: UIView.ContentMode) {
    guard let queue = self.commandQueue,
          let commandBuffer = queue.makeCommandBuffer() else {
      print("FrameMixer resizeTexture command buffer create failed")
      return
    }

    let device = queue.device;

    // Scale texture
    let sourceWidth = sourceTexture.width
    let sourceHeight = sourceTexture.height
    let widthRatio: Double = Double(targetSize.width) / Double(sourceWidth)
    let heightRatio: Double = Double(targetSize.height) / Double(sourceHeight)
    var scaleX: Double = 0;
    var scaleY: Double  = 0;
    var translateX: Double = 0;
    var translateY: Double = 0;
    if resizeMode == .scaleToFill {
      //ScaleFill
      scaleX = Double(targetSize.width) / Double(sourceWidth)
      scaleY = Double(targetSize.height) / Double(sourceHeight)

    } else if resizeMode == .scaleAspectFit {
      //AspectFit
      if heightRatio > widthRatio {
        scaleX = Double(targetSize.width) / Double(sourceWidth)
        scaleY = scaleX
        let currentHeight = Double(sourceHeight) * scaleY
        translateY = (Double(targetSize.height) - currentHeight) * 0.5
      } else {
        scaleY = Double(targetSize.height) / Double(sourceHeight)
        scaleX = scaleY
        let currentWidth = Double(sourceWidth) * scaleX
        translateX = (Double(targetSize.width) - currentWidth) * 0.5
      }
    } else if resizeMode == .scaleAspectFill {
      //AspectFill
      if heightRatio > widthRatio {
        scaleY = Double(targetSize.height) / Double(sourceHeight)
        scaleX = scaleY
        let currentWidth = Double(sourceWidth) * scaleX
        translateX = (Double(targetSize.width) - currentWidth) * 0.5

      } else {
        scaleX = Double(targetSize.width) / Double(sourceWidth)
        scaleY = scaleX
        let currentHeight = Double(sourceHeight) * scaleY
        translateY = (Double(targetSize.height) - currentHeight) * 0.5
      }
    }
    var transform = MPSScaleTransform(scaleX: scaleX, scaleY: scaleY, translateX: translateX, translateY: translateY)
    if #available(iOS 11.0, *) {
      let scale = MPSImageBilinearScale.init(device: device)
//      let scale = MPSImageLanczosScale(device: device)
      withUnsafePointer(to: &transform) { (transformPtr: UnsafePointer<MPSScaleTransform>) -> () in
        scale.scaleTransform = transformPtr
        scale.encode(commandBuffer: commandBuffer, sourceTexture: sourceTexture, destinationTexture: desTexture)
      }
    } else {
      print("Frame mixer resizeTexture failed, only support iOS 11.0")
    }

    commandBuffer.commit()
    commandBuffer.waitUntilCompleted()
  }
}

extension MediaLibraryViewController: UIImagePickerControllerDelegate, UINavigationControllerDelegate {
  
  func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
    picker.dismiss(animated: true)
  }
  
  func imagePickerController(
    _ picker: UIImagePickerController,
    didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
    clearPlayerView()
    pickedImageView.image = nil
    overlayView.clear()
    
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
      clearAndInitializeImageSegmenterService(runningMode: .video)
      let asset = AVAsset(url: mediaURL)
      Task {
        interfaceUpdatesDelegate?.shouldClicksBeEnabled(false)
        showProgressView()
        
        guard let videoDuration = try? await asset.load(.duration).seconds else {
          hideProgressView()
          return
        }
        
        let resultBundle = await self.imageSegmenterService?.segment(
          videoAsset: asset,
          durationInMilliseconds: videoDuration * Constants.milliSeconds,
          inferenceIntervalInMilliseconds: Constants.inferenceTimeIntervalInMilliseconds)
        
        hideProgressView()

        DispatchQueue.main.async {
          self.inferenceResultDeliveryDelegate?.didPerformInference(result: resultBundle)
        }
        
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
      imageEmptyLabel.isHidden = true
      
      showProgressView()
      
      clearAndInitializeImageSegmenterService(runningMode: .image)
      
      DispatchQueue.global(qos: .userInteractive).async { [weak self] in
        let maxImageWidth = 700.0
        var newImage: UIImage!
        if image.size.width > maxImageWidth {
          newImage = image.scale(maxImageWidth/image.size.width)
        } else {
          newImage = image
        }
        guard let weakSelf = self,
              let resultBundle = weakSelf.imageSegmenterService?.segment(image: newImage),
              let imageSegmenterResult = resultBundle.imageSegmenterResults.first,
              let imageSegmenterResult = imageSegmenterResult else {
          DispatchQueue.main.async {
            self?.hideProgressView()
          }
          return
        }
          
        DispatchQueue.main.async {
          weakSelf.hideProgressView()
          weakSelf.inferenceResultDeliveryDelegate?.didPerformInference(result: resultBundle)
          let imageSize = newImage.size
          let newImage = weakSelf.demoRemoveBg(result: imageSegmenterResult, image: newImage)
          weakSelf.pickedImageView.image = newImage
//          let faceOverlays = OverlayView.faceOverlays(
//            fromMultipleFaceLandmarks: imageSegmenterResult.faceLandmarks,
//            inferredOnImageOfSize: imageSize,
//            ovelayViewSize: weakSelf.overlayView.bounds.size,
//            imageContentMode: weakSelf.overlayView.imageContentMode,
//            andOrientation: image.imageOrientation)
//          weakSelf.overlayView.draw(faceOverlays: faceOverlays,
//                           inBoundsOfContentImageOfSize: imageSize,
//                                    imageContentMode: .scaleAspectFit)
        }
      }
    default:
      break
    }
  }

  func clearAndInitializeImageSegmenterService(runningMode: RunningMode) {
    imageSegmenterService = nil
    switch runningMode {
      case .image:
      imageSegmenterService = ImageSegmenterService.stillImageSegmenterService(
        modelPath: InferenceConfigurationManager.sharedInstance.modelPath)
      case .video:
      imageSegmenterService = ImageSegmenterService.videoImageSegmenterService(
        modelPath: InferenceConfigurationManager.sharedInstance.modelPath,
        videoDelegate: self)
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
                let resultBundle = resultBundle,
                index < resultBundle.imageSegmenterResults.count,
                let imageSegmenterResult = resultBundle.imageSegmenterResults[index] else {
            return
          }
          let imageSize = resultBundle.size
//          let faceOverlays = OverlayView.faceOverlays(
//            fromMultipleFaceLandmarks: imageSegmenterResult.faceLandmarks,
//            inferredOnImageOfSize: imageSize,
//            ovelayViewSize: weakSelf.overlayView.bounds.size,
//            imageContentMode: weakSelf.overlayView.imageContentMode,
//            andOrientation: .up)
//          weakSelf.overlayView.draw(faceOverlays: faceOverlays,
//                           inBoundsOfContentImageOfSize: imageSize,
//                                    imageContentMode: .scaleAspectFit)
          
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

extension MediaLibraryViewController: ImageSegmenterServiceVideoDelegate {

  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService, willBeginSegmention totalframeCount: Int) {
    progressView.observedProgress = Progress(totalUnitCount: Int64(totalframeCount))
  }

  func imageSegmenterService(_ imageSegmenterService: ImageSegmenterService, didFinishSegmentionOnVideoFrame index: Int) {
    progressView.observedProgress?.completedUnitCount = Int64(index + 1)
  }
}


