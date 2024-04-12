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

import AVKit
import MediaPipeTasksVision
import UIKit

// MARK: Constants
fileprivate struct Constants {
  static let edgeOffset: CGFloat = 2.0
  static let inferenceTimeIntervalInMilliseconds = 300.0
  static let milliSeconds = 1000.0
  static let savedPhotosNotAvailableText = "Saved photos album is not available."
  static let mediaEmptyText =
  "Click + to add an image to begin running the embedding."
  static let pickFromGalleryButtonInset: CGFloat = 10.0
}

/**
 * The view controller is responsible for performing embedding on images selected by the user from the device media library and
 * presenting the frames with the class of the embedded objects to the user.
 */
class MediaLibraryViewController: UIViewController {

  weak var inferenceResultDeliveryDelegate: InferenceResultDeliveryDelegate?

  // MARK: Controllers that manage functionality
  private lazy var pickerController = UIImagePickerController()

  // MARK: Image Embedder Service
  private var imageEmbedderService: ImageEmbedderService?
  private var image1EmbedResult: ResultBundle?
  private var image2EmbedResult: ResultBundle?

  // MARK: Private properties
  private var playerTimeObserverToken : Any?
  private var viewChoosed: ImageEmbedderView?

  // MARK: Storyboards Connections
  @IBOutlet weak var pickFromGalleryButtonBottomSpace: NSLayoutConstraint!
  @IBOutlet weak var imageEmbedderView1: ImageEmbedderView!
  @IBOutlet weak var imageEmbedderView2: ImageEmbedderView!
  @IBOutlet weak var imageViewBottomSpace: NSLayoutConstraint!


  override func viewDidLoad() {
    super.viewDidLoad()
    imageEmbedderView1.delegate = self
    imageEmbedderView2.delegate = self
  }

  override func viewWillLayoutSubviews() {
    super.viewWillLayoutSubviews()
  }

  override func viewWillDisappear(_ animated: Bool) {
    super.viewWillDisappear(animated)
    imageEmbedderService = nil
  }

  private func configurePickerController() {
    pickerController.delegate = self
    pickerController.sourceType = .savedPhotosAlbum
    pickerController.mediaTypes = [UTType.image.identifier]
    pickerController.allowsEditing = false
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

  func layoutUIElements(withInferenceViewHeight height: CGFloat) {
    imageViewBottomSpace.constant =
    height + Constants.pickFromGalleryButtonInset
    view.layoutSubviews()
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

      picker.dismiss(animated: true)

      guard let mediaType = info[.mediaType] as? String else {
        return
      }

      switch mediaType {
      case UTType.image.identifier:
        guard let image = info[.originalImage] as? UIImage else {
          break
        }
        viewChoosed?.setImage(image: image)
        clearAndInitializeImageEmbedderService(runningMode: .image)

        DispatchQueue.global(qos: .userInteractive).async { [weak self] in
          guard let weakself = self else { return }
          guard let resultBundle = weakself.imageEmbedderService?
            .embed(image: image),
                let imageEmbedderResult = resultBundle.imageEmbedderResults.first,
                let embedding = imageEmbedderResult?.embeddingResult.embeddings.first else {
            weakself.viewChoosed?.setResult(floatEmbedding: nil)
            return
          }
          weakself.viewChoosed?.setResult(floatEmbedding: embedding.floatEmbedding)
          switch weakself.viewChoosed {
          case weakself.imageEmbedderView1:
            weakself.image1EmbedResult = resultBundle
          default:
            weakself.image2EmbedResult = resultBundle
          }
          if weakself.image1EmbedResult != nil && weakself.image2EmbedResult != nil {
            weakself.inferenceResultDeliveryDelegate?.didPerformInference(result1: weakself.image1EmbedResult, result2: weakself.image2EmbedResult)
          }
        }
      default:
        break
      }
    }

  func clearAndInitializeImageEmbedderService(runningMode: RunningMode) {
    imageEmbedderService = nil
    switch runningMode {
    case .image:
      imageEmbedderService = ImageEmbedderService
        .stillImageEmbedderService(
          model: InferenceConfigurationManager.sharedInstance.model,
          delegate: InferenceConfigurationManager.sharedInstance.delegate
        )
    default:
      break;
    }
  }
}

extension MediaLibraryViewController: ImageEmbedderViewDelegate {
  func clickPickImageFrom(_ imageEmbedderView: ImageEmbedderView) {
    viewChoosed = imageEmbedderView
    openMediaLibrary()
  }
}

protocol ImageEmbedderViewDelegate: AnyObject {
  func clickPickImageFrom(_ imageEmbedderView: ImageEmbedderView)
}

class ImageEmbedderView: UIView {

  @IBOutlet weak var pickFromGalleryButton: UIButton!
  @IBOutlet weak var imageEmptyLabel: UILabel!
  @IBOutlet private weak var pickedImageView: UIImageView!
  @IBOutlet private weak var resultLabel: UILabel!
  @IBOutlet weak var imageHeigth: NSLayoutConstraint!

  weak var delegate: ImageEmbedderViewDelegate?

  private var imageWidth = UIScreen.main.bounds.width - 16

  override init(frame: CGRect) {
    super.init(frame: frame)
    guard UIImagePickerController.isSourceTypeAvailable(.savedPhotosAlbum) else {
      pickFromGalleryButton.isEnabled = false
      self.imageEmptyLabel.text = Constants.savedPhotosNotAvailableText
      return
    }
    pickFromGalleryButton.isEnabled = true
    self.imageEmptyLabel.text = Constants.mediaEmptyText
  }

  func setImage(image: UIImage) {
    pickedImageView.image = image
    imageHeigth.constant = image.size.height / image.size.width * imageWidth
    layoutIfNeeded()
  }

  func setResult(floatEmbedding: [NSNumber]?) {
    DispatchQueue.main.async {
      guard let floatEmbedding = floatEmbedding else {
        self.resultLabel.text = ""
        return
      }
      self.resultLabel.text = "Float embed: \(floatEmbedding)"
    }
  }

  @IBAction func onClickPickFromGallery(_ sender: Any) {
    delegate?.clickPickImageFrom(self)
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
  }
}

