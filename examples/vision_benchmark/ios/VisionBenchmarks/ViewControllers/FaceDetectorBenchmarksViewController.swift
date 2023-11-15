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

class FaceDetectorBenchmarksViewController: UIViewController {

  var percentileValue: Double?

  // MARK: Storyboards Connections
  @IBOutlet weak var tableView: UITableView!
  @IBOutlet weak var percentileLabel: UILabel!
  @IBOutlet weak var activityView: UIView!
  // MARK: Constants
  private let testImages: [UIImage] = [
    UIImage(named: "face_test.jpg")!,
    UIImage(named: "face2.png")!]
  private let percentile: Double = 95

  // MARK: Private Instance Variables
  private var faceDetectorService: FaceDetectorService?
  private var displayDatas: [VisionBenchmarkDetailData] = []
  private var cellClassName = String(describing: VisionBenchmarkDetailTableViewCell.self)

  override func viewDidLoad() {
    super.viewDidLoad()
    title = "Face Detector Benchmarks"
    initFaceDetectorService()
    setupTableView()
  }

  private func initFaceDetectorService() {
    faceDetectorService = FaceDetectorService.stillImageDetectorService(
      modelPath: DefaultConstants.FaceDetectorConstants.modelPath,
      minDetectionConfidence: DefaultConstants.FaceDetectorConstants.minDetectionConfidence,
      minSuppressionThreshold: DefaultConstants.FaceDetectorConstants.minSuppressionThreshold)
  }

  private func setupTableView() {
    tableView.rowHeight = VisionBenchmarkDetailTableViewCell.cellHeight
    tableView.register(
      UINib(nibName: cellClassName, bundle: nil),
      forCellReuseIdentifier: cellClassName)
  }

  // StillImageDetectorService
  private func benchmarkStillImage() {
    guard let faceDetectorService = faceDetectorService else {
        print("can't create services")
        return
      }
    activityView.isHidden = false
    displayDatas = []
    var inferenceTimes: [Double] = []
    for image in testImages {
      guard let result = faceDetectorService.detect(image: image) else { fatalError("can not get result") }
      displayDatas.append(VisionBenchmarkDetailData(
        image: image,
        inferenceTime: result.inferenceTime))
      inferenceTimes.append(result.inferenceTime)
    }

    activityView.isHidden = true

    if let percentileValue = Calculator.calculatePercentile(data: inferenceTimes, percentile: percentile) {
      percentileLabel.text = "\(percentileValue)"
    }
    tableView.reloadData()
  }

  // MARK: Action
  @IBAction func caculatorButtonTouchUpInside(_ sender: Any) {
    benchmarkStillImage()
  }
}

// MARK: UITableViewDataSource
extension FaceDetectorBenchmarksViewController: UITableViewDataSource {

  func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    return displayDatas.count
  }

  func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    guard let cell = tableView.dequeueReusableCell(withIdentifier: cellClassName, for: indexPath) as? VisionBenchmarkDetailTableViewCell else { fatalError("can not load cell") }
    let data = displayDatas[indexPath.row]
    cell.updateVisionBenchmarkDetailData(data)
    return cell
  }
}
