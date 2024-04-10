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

import UIKit
import MediaPipeTasksVision

protocol InferenceResultDeliveryDelegate: AnyObject {
  func didPerformInference(result1: ResultBundle?, result2: ResultBundle?)
}

/** The view controller is responsible for presenting and handling the tabbed controls for switching between the live camera feed and
  * media library view controllers. It also handles the presentation of the inferenceVC
  */
class RootViewController: UIViewController {

  // MARK: Storyboards Connections
  @IBOutlet weak var tabBarContainerView: UIView!
  @IBOutlet weak var runningModeTabbar: UITabBar!
  @IBOutlet weak var bottomSheetViewBottomSpace: NSLayoutConstraint!
  @IBOutlet weak var bottomViewHeightConstraint: NSLayoutConstraint!
  
  // MARK: Constants
  private struct Constants {
    static let inferenceBottomHeight = 208.0
    static let expandButtonHeight = 41.0
    static let expandButtonTopSpace = 40.0
    static let mediaLibraryViewControllerStoryBoardId = "MEDIA_LIBRARY_VIEW_CONTROLLER"
    static let cameraViewControllerStoryBoardId = "CAMERA_VIEW_CONTROLLER"
    static let storyBoardName = "Main"
    static let inferenceVCEmbedSegueName = "EMBED"
    static let tabBarItemsCount = 2
  }
  
  // MARK: Controllers that manage functionality
  private var bottomSheetViewController: BottomSheetViewController?
  private var cameraViewController: CameraViewController?
  private var mediaLibraryViewController: MediaLibraryViewController?
  
  // MARK: Private Instance Variables
  private var isObserving = false
  private var totalBottomSheetHeight: CGFloat {
    guard let bottomSheetViewController = bottomSheetViewController else {
      return 0.0
    }
    
    return bottomSheetViewController.toggleBottomSheetButton.isSelected ?
      Constants.inferenceBottomHeight - self.view.safeAreaInsets.bottom:
    Constants.expandButtonHeight + Constants.expandButtonTopSpace
  }

  // MARK: View Handling Methods
  override func viewDidLoad() {
    super.viewDidLoad()

    runningModeTabbar.selectedItem = runningModeTabbar.items?.first
    runningModeTabbar.delegate = self
    instantiateCameraViewController()
    switchTo(childViewController: cameraViewController, fromViewController: nil)
  }
  
  override func viewWillLayoutSubviews() {
    super.viewWillLayoutSubviews()
    guard let bottomSheetViewController = bottomSheetViewController else { return }
    bottomViewHeightConstraint.constant = Constants.inferenceBottomHeight
    if bottomSheetViewController.toggleBottomSheetButton.isSelected == false {
      bottomSheetViewBottomSpace.constant = -Constants.inferenceBottomHeight
      + Constants.expandButtonHeight
      + self.view.safeAreaInsets.bottom
      + Constants.expandButtonTopSpace
    } else {
      bottomSheetViewBottomSpace.constant = 0.0
    }
  }
  
  override var preferredStatusBarStyle: UIStatusBarStyle {
    return .lightContent
  }
  
  // MARK: Storyboard Segue Handlers
  override func prepare(for segue: UIStoryboardSegue, sender: Any?) {
    super.prepare(for: segue, sender: sender)
    if segue.identifier == Constants.inferenceVCEmbedSegueName {
      bottomSheetViewController = segue.destination as? BottomSheetViewController
      bottomSheetViewController?.delegate = self
      bottomViewHeightConstraint.constant = Constants.inferenceBottomHeight
      view.layoutSubviews()
    }
  }

  // MARK: Private Methods
  private func instantiateCameraViewController() {
    guard cameraViewController == nil else {
      return
    }
    
    guard let viewController = UIStoryboard(
      name: Constants.storyBoardName, bundle: .main)
      .instantiateViewController(
        withIdentifier: Constants.cameraViewControllerStoryBoardId) as? CameraViewController else {
      return
    }
    
    viewController.inferenceResultDeliveryDelegate = self
    
    cameraViewController = viewController
  }
  
  private func instantiateMediaLibraryViewController() {
    guard mediaLibraryViewController == nil else {
      return
    }
    guard let viewController = UIStoryboard(name: Constants.storyBoardName, bundle: .main)
      .instantiateViewController(
        withIdentifier: Constants.mediaLibraryViewControllerStoryBoardId)
            as? MediaLibraryViewController else {
      return
    }

    viewController.inferenceResultDeliveryDelegate = self
    mediaLibraryViewController = viewController
  }
  
  private func updateMediaLibraryControllerUI() {
    guard let mediaLibraryViewController = mediaLibraryViewController else {
      return
    }
    
    mediaLibraryViewController.layoutUIElements(
      withInferenceViewHeight: self.totalBottomSheetHeight)
  }
}

// MARK: UITabBarDelegate
extension RootViewController: UITabBarDelegate {
  func switchTo(
    childViewController: UIViewController?,
    fromViewController: UIViewController?) {
    fromViewController?.willMove(toParent: nil)
    fromViewController?.view.removeFromSuperview()
    fromViewController?.removeFromParent()
    
    guard let childViewController = childViewController else {
      return
    }
      
    addChild(childViewController)
    childViewController.view.translatesAutoresizingMaskIntoConstraints = false
    tabBarContainerView.addSubview(childViewController.view)
    NSLayoutConstraint.activate(
      [
        childViewController.view.leadingAnchor.constraint(
          equalTo: tabBarContainerView.leadingAnchor,
          constant: 0.0),
        childViewController.view.trailingAnchor.constraint(
          equalTo: tabBarContainerView.trailingAnchor,
          constant: 0.0),
        childViewController.view.topAnchor.constraint(
          equalTo: tabBarContainerView.topAnchor,
          constant: 0.0),
        childViewController.view.bottomAnchor.constraint(
          equalTo: tabBarContainerView.bottomAnchor,
          constant: 0.0)
      ]
    )
    childViewController.didMove(toParent: self)
  }
  
  func tabBar(_ tabBar: UITabBar, didSelect item: UITabBarItem) {
    guard let tabBarItems = tabBar.items, tabBarItems.count == Constants.tabBarItemsCount else {
      return
    }

    var fromViewController: UIViewController?
    var toViewController: UIViewController?
    
    switch item {
    case tabBarItems[0]:
        fromViewController = mediaLibraryViewController
        toViewController = cameraViewController
    case tabBarItems[1]:
        instantiateMediaLibraryViewController()
        fromViewController = cameraViewController
        toViewController = mediaLibraryViewController
    default:
      break
    }
    
    switchTo(
      childViewController: toViewController,
      fromViewController: fromViewController)
    self.updateMediaLibraryControllerUI()
  }
}

// MARK: InferenceResultDeliveryDelegate Methods
extension RootViewController: InferenceResultDeliveryDelegate {
  func didPerformInference(result1: ResultBundle?, result2: ResultBundle?) {
    var inferenceTimeString = ""
    
    if let inferenceTime = result1?.inferenceTime {
      inferenceTimeString = String(format: "%.2fms", inferenceTime)
    }
    var similarity: Float?
    if let result1 = result1,
       let result2 = result2,
       let imageEmbedderResults1 = result1.imageEmbedderResults.first,
       let imageEmbedderResults2 = result2.imageEmbedderResults.first,
       let embedding1 = imageEmbedderResults1?.embeddingResult.embeddings.first,
       let embedding2 = imageEmbedderResults2?.embeddingResult.embeddings.first {
      similarity = try? ImageEmbedder.cosineSimilarity(embedding1: embedding1, embedding2: embedding2).floatValue
    }
    DispatchQueue.main.async {
      self.bottomSheetViewController?.update(inferenceTimeString: inferenceTimeString, similarity: similarity)
    }
  }
}

// MARK: InferenceViewControllerDelegate Methods
extension RootViewController: BottomSheetViewControllerDelegate {
  func viewController(
    _ viewController: BottomSheetViewController,
    didSwitchBottomSheetViewState isOpen: Bool) {
      if isOpen == true {
        bottomSheetViewBottomSpace.constant = 0.0
      }
      else {
        bottomSheetViewBottomSpace.constant = -Constants.inferenceBottomHeight
        + Constants.expandButtonHeight
        + self.view.safeAreaInsets.bottom
        + Constants.expandButtonTopSpace
      }
      
      UIView.animate(withDuration: 0.3) {[weak self] in
        guard let weakSelf = self else {
          return
        }
        weakSelf.view.layoutSubviews()
        weakSelf.updateMediaLibraryControllerUI()
      }
    }
}
