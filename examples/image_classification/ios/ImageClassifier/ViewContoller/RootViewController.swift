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

protocol InferenceResultDeliveryDelegate: AnyObject {
  func didPerformInference(result: ResultBundle?)
  func didPerformInference(result: ResultBundle?, index: Int)
}

protocol InterfaceUpdatesDelegate: AnyObject {
  func shouldClicksBeEnabled(_ isEnabled: Bool)
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
    static let inferenceBottomHeight = 284.0
    static let expandButtonHeight = 41.0
    static let expandButtonTopSpace = 20.0
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
      Constants.inferenceBottomHeight - self.view.safeAreaInsets.bottom + bottomSheetViewController.collapsedHeight :
    Constants.expandButtonHeight + Constants.expandButtonTopSpace + bottomSheetViewController.collapsedHeight
  }

  // MARK: View Handling Methods
  override func viewDidLoad() {
    super.viewDidLoad()
    
    bottomSheetViewController?.isUIEnabled = true
    runningModeTabbar.selectedItem = runningModeTabbar.items?.first
    runningModeTabbar.delegate = self
    instantiateCameraViewController()
    switchTo(childViewController: cameraViewController, fromViewController: nil)
    startObserveMaxResultsChanges()
  }
  
  override func viewWillLayoutSubviews() {
    super.viewWillLayoutSubviews()
    guard let bottomSheetViewController = bottomSheetViewController else { return }
    bottomViewHeightConstraint.constant = bottomSheetViewController.collapsedHeight + Constants.inferenceBottomHeight
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

  deinit {
    stopObserveMaxResultsChanges()
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
    viewController.interfaceUpdatesDelegate = self
    
    cameraViewController = viewController
  }

  private func startObserveMaxResultsChanges() {
    NotificationCenter.default
      .addObserver(self,
                   selector: #selector(changebottomViewHeightConstraint),
                   name: InferenceConfigurationManager.maxResultChangeNotificationName,
                   object: nil)
    isObserving = true
  }

  private func stopObserveMaxResultsChanges() {
    if isObserving {
      NotificationCenter.default
        .removeObserver(self,
                        name: InferenceConfigurationManager.maxResultChangeNotificationName,
                        object: nil)
    }
    isObserving = false
  }

  @objc private func changebottomViewHeightConstraint() {
    guard let bottomSheetViewController = bottomSheetViewController else { return }
    bottomViewHeightConstraint.constant = bottomSheetViewController.collapsedHeight + Constants.inferenceBottomHeight
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
    
    viewController.interfaceUpdatesDelegate = self
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
    self.shouldClicksBeEnabled(true)
    self.updateMediaLibraryControllerUI()
  }
}

// MARK: InferenceResultDeliveryDelegate Methods
extension RootViewController: InferenceResultDeliveryDelegate {
  func didPerformInference(result: ResultBundle?) {
    var inferenceTimeString = ""
    
    if let inferenceTime = result?.inferenceTime {
      inferenceTimeString = String(format: "%.2fms", inferenceTime)
    }
    bottomSheetViewController?.update(inferenceTimeString: inferenceTimeString,
                                    result: result?.imageClassifierResults.first ?? nil)
  }

  func didPerformInference(result: ResultBundle?, index: Int) {
    var inferenceTimeString = ""

    if let inferenceTime = result?.inferenceTime {
      inferenceTimeString = String(format: "%.2fms", inferenceTime)
    }
    if let imageClassifierResults = result?.imageClassifierResults,
       index < imageClassifierResults.count {
      bottomSheetViewController?.update(inferenceTimeString: inferenceTimeString,
                                      result: imageClassifierResults[index])
    }
  }
}

// MARK: InterfaceUpdatesDelegate Methods
extension RootViewController: InterfaceUpdatesDelegate {
  func shouldClicksBeEnabled(_ isEnabled: Bool) {
    bottomSheetViewController?.isUIEnabled = isEnabled
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
