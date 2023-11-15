
import UIKit

class VisionBenchmarkDetailTableViewCell: UITableViewCell {

  static let cellHeight: CGFloat = 78

  // MARK: Storyboards Connections
  @IBOutlet weak var inferenceTimeLabel: UILabel!
  @IBOutlet weak var thumbImageView: UIImageView!

  func updateVisionBenchmarkDetailData(_ data: VisionBenchmarkDetailData) {
    thumbImageView.image = data.image
    inferenceTimeLabel.text = "\(data.inferenceTime)"
  }
}

struct VisionBenchmarkDetailData {
  let image: UIImage
  let inferenceTime: Double
}
