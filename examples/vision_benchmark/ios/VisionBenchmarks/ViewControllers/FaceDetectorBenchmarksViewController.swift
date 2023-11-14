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

  @IBOutlet weak var tableView: UITableView!

  private let images: [UIImage] = [UIImage(named: "face_test.jpg")!,
                                   UIImage(named: "face2.png")!,
                                   UIImage(named: "face_test.jpg")!,
                                   UIImage(named: "face2.png")!,
                                   UIImage(named: "face_test.jpg")!,
                                   UIImage(named: "face_test.jpg")!,
                                   UIImage(named: "face2.png")!
                                   ]

  override func viewDidLoad() {
    super.viewDidLoad()
    title = "Face Detector Benchmarks"
    benchmarksStillImage()
  }

  private func setupTableView() {
    tableView.rowHeight = 44
    tableView.register(UITableViewCell.self, forCellReuseIdentifier: "Cell")
  }

  //  stillImageDetectorService
  private func benchmarksStillImage() {
    guard let faceDetector = FaceDetectorService.stillImageDetectorService(
      modelPath: DefaultConstants.FaceDetectorConstants.modelPath,
      minDetectionConfidence: DefaultConstants.FaceDetectorConstants.minDetectionConfidence,
      minSuppressionThreshold: DefaultConstants.FaceDetectorConstants.minSuppressionThreshold) else {
        print("can't create services")
        return
      }
    var times: [Double] = []
    for image in images {
      guard let result = faceDetector.detect(image: image) else { fatalError("can not get result") }
      times.append(result.inferenceTime)
      print(result.inferenceTime)
    }

    if let percentileValue = Calculator.calculatePercentile(data: times, percentile: 95.0) {
      print("95th percentile: \(percentileValue)")
      self.percentileValue = percentileValue
      tableView.reloadData()
    }
  }
}

extension FaceDetectorBenchmarksViewController: UITableViewDataSource {

  func numberOfSections(in tableView: UITableView) -> Int {
    return 1
  }

  func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    return 1
  }

  func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    let cell = tableView.dequeueReusableCell(withIdentifier: "Cell")!
    if let percentileValue = percentileValue {
      cell.textLabel?.text = "95th percentile: \(percentileValue)"
    }
    return cell
  }
}
