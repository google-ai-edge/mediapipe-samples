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

class RootViewController: UITableViewController {


  // MARK: Private Instance Variables
  private let cellIdentifier = "Cell"
  private let datas: [VisionTask] = [.FaceDetector]

  override func viewDidLoad() {
    super.viewDidLoad()
    tableView.register(UITableViewCell.self, forCellReuseIdentifier: cellIdentifier)
  }
}

// MARK: UITableViewDatasources
extension RootViewController {
  override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
    datas.count
  }

  override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
    let cell = tableView.dequeueReusableCell(withIdentifier: cellIdentifier, for: indexPath)
    let data = datas[indexPath.row]
    cell.textLabel?.text = data.title
    return cell
  }

  override func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
    let visionTask = datas[indexPath.row]
    switch visionTask {
    case .FaceDetector:
      let identifier = "FaceDetectorBenchmarksViewController"
      guard let vc = UIStoryboard(name: "Main", bundle: nil)
        .instantiateViewController(identifier: identifier) as? FaceDetectorBenchmarksViewController else { return }
      navigationController?.pushViewController(vc, animated: true)
    }
  }
}
