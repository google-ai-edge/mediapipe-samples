
import UIKit

extension UIImage {

  func imageResized(to size: CGSize) -> UIImage {
    return UIGraphicsImageRenderer(size: size).image { _ in
      draw(in: CGRect(origin: .zero, size: size))
    }
  }

  func scale(_ ratio: CGFloat) -> UIImage? {
    let newSize = CGSize(width: size.width * ratio, height: size.height * ratio)
    return self.resizeWithCoreImage(to: newSize)
  }

  private func resizeWithCoreImage(to newSize: CGSize) -> UIImage? {
          guard let cgImage = cgImage, let filter = CIFilter(name: "CILanczosScaleTransform") else { return nil }

          let ciImage = CIImage(cgImage: cgImage)
          let scale = (Double)(newSize.width) / (Double)(ciImage.extent.size.width)

          filter.setValue(ciImage, forKey: kCIInputImageKey)
          filter.setValue(NSNumber(value:scale), forKey: kCIInputScaleKey)
          filter.setValue(1.0, forKey: kCIInputAspectRatioKey)
          guard let outputImage = filter.value(forKey: kCIOutputImageKey) as? CIImage else { return nil }
          let context = CIContext(options: [.useSoftwareRenderer: false])
          guard let resultCGImage = context.createCGImage(outputImage, from: outputImage.extent) else { return nil }
          return UIImage(cgImage: resultCGImage)
      }

  func resizeWithCoreGraphics(to newSize: CGSize) -> UIImage? {
          guard let cgImage = cgImage, let colorSpace = cgImage.colorSpace else { return nil }

          let width = Int(newSize.width)
          let height = Int(newSize.height)
          let bitsPerComponent = cgImage.bitsPerComponent
          let bytesPerRow = cgImage.bytesPerRow
          let bitmapInfo = cgImage.bitmapInfo

          guard let context = CGContext(data: nil, width: width, height: height,
                                        bitsPerComponent: bitsPerComponent,
                                        bytesPerRow: bytesPerRow, space: colorSpace,
                                        bitmapInfo: bitmapInfo.rawValue) else { return nil }
          context.interpolationQuality = .medium
          let rect = CGRect(origin: CGPoint.zero, size: newSize)
          context.draw(cgImage, in: rect)

          return context.makeImage().flatMap { UIImage(cgImage: $0) }
      }
}
