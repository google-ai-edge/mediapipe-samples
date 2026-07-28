import MediaPipeTasksVision
import PhotosUI
import SwiftUI

struct InteractiveSegmenterView: View {
  @State var viewModel = InteractiveSegmenterViewModel()
  @State private var selectedItem: PhotosPickerItem?
  @State private var selectedBrushMode: BrushMode = .positive

  var body: some View {
    VStack {
      HStack {
        if viewModel.isBusy {
          ProgressView()
            .progressViewStyle(CircularProgressViewStyle())
            .padding(.trailing, 4)
        }
        Text(viewModel.statusMessage)
          .font(.headline)
          .foregroundColor(.secondary)
      }
      .padding(.top)
      .frame(height: 40)

      GeometryReader { geometry in
        DrawingCanvasView(
          viewModel: viewModel,
          viewSize: geometry.size,
          selectedBrushMode: selectedBrushMode
        )
      }
      .background(Color.white)

      VStack {
        Picker("Brush Mode", selection: $selectedBrushMode) {
          Text("Positive").tag(BrushMode.positive)
          Text("Negative").tag(BrushMode.negative)
          Text("Lasso").tag(BrushMode.lasso)
        }
        .pickerStyle(.segmented)
        .padding([.horizontal, .top])

        HStack {
          PhotosPicker("Pick Photo", selection: $selectedItem, matching: .images)
            .padding()

          Spacer()

          Button("Clear") {
            viewModel.clearStrokes()
          }
          .padding()
        }
      }
      .disabled(viewModel.isBusy)
    }
    .task(id: selectedItem) {
      guard let selectedItem = selectedItem else { return }
      do {
        if let data = try await selectedItem.loadTransferable(type: Data.self),
          let image = UIImage(data: data)
        {
          viewModel.update(with: image)
          selectedBrushMode = .positive
        }
      } catch {
        print("Error loading image: \(error)")
        viewModel.statusMessage = "Failed to load image"
      }
    }
    .task {
      await viewModel.start()
    }
  }

  internal static func normalizedCoordinate(
    for location: CGPoint, imageSize: CGSize, viewSize: CGSize
  ) -> CGPoint? {
    let rect = imageSize.aspectFit(in: viewSize)
    guard rect.contains(location) else { return nil }
    return CGPoint(
      x: (location.x - rect.origin.x) / rect.width,
      y: (location.y - rect.origin.y) / rect.height
    )
  }
}

struct DrawingCanvasView: View {
  var viewModel: InteractiveSegmenterViewModel
  let viewSize: CGSize
  let selectedBrushMode: BrushMode

  @State private var currentStrokePoints: [NormalizedKeypoint] = []

  var body: some View {
    ZStack {
      if let inputImage = viewModel.inputImage {
        Image(uiImage: inputImage)
          .resizable()
          .scaledToFit()
          .frame(width: viewSize.width, height: viewSize.height)
          .gesture(
            DragGesture(minimumDistance: 0)
              .onChanged { value in
                handleGesture(value.location, in: viewSize)
              }
              .onEnded { value in
                handleGesture(value.location, in: viewSize, isCompleted: true)
              }
          )
          .disabled(viewModel.isBusy)

        if let maskImage = viewModel.maskImage {
          Image(uiImage: maskImage)
            .resizable()
            .scaledToFit()
            .frame(width: viewSize.width, height: viewSize.height)
            .opacity(0.5)
            .allowsHitTesting(false)  // Let touches pass through
        }

        // Overlay drawn strokes
        StrokesOverlay(
          strokes: viewModel.strokes,
          currentPoints: currentStrokePoints,
          currentBrushMode: selectedBrushMode,
          imageSize: inputImage.size,
          viewSize: viewSize
        )
      } else {
        Text("Loading image...")
      }
    }
  }

  private func handleGesture(_ location: CGPoint, in viewSize: CGSize, isCompleted: Bool = false) {
    guard let inputImage = viewModel.inputImage else { return }

    if let normalizedPoint = InteractiveSegmenterView.normalizedCoordinate(
      for: location, imageSize: inputImage.size, viewSize: viewSize)
    {
      let keypoint = NormalizedKeypoint(location: normalizedPoint, label: nil, score: 0.0)

      if let lastPoint = currentStrokePoints.last {
        if lastPoint.location != normalizedPoint {
          currentStrokePoints.append(keypoint)
        }
      } else {
        currentStrokePoints.append(keypoint)
      }
    }

    if isCompleted {
      if !currentStrokePoints.isEmpty {
        let stroke = Stroke(
          points: currentStrokePoints, brushMode: selectedBrushMode, isCompleted: true)
        viewModel.strokes.append(stroke)
        viewModel.performSegmentation()
      }
      currentStrokePoints.removeAll()
    }
  }
}

struct StrokesOverlay: View {
  let strokes: [Stroke]
  let currentPoints: [NormalizedKeypoint]
  let currentBrushMode: BrushMode
  let imageSize: CGSize
  let viewSize: CGSize

  private enum Constants {
    static let dotSize: CGFloat = 4.0
    static let lineWidth: CGFloat = 2.0
  }

  var body: some View {
    let rect = imageSize.aspectFit(in: viewSize)
    ZStack {
      // Drawn strokes
      ForEach(strokes, id: \.self) { stroke in
        drawStroke(stroke.points, color: color(for: stroke.brushMode), in: rect)
      }

      // Current stroke
      if !currentPoints.isEmpty {
        drawStroke(currentPoints, color: color(for: currentBrushMode), in: rect)
      }
    }
    .allowsHitTesting(false)
  }

  @ViewBuilder
  private func drawStroke(_ points: [NormalizedKeypoint], color: Color, in rect: CGRect)
    -> some View
  {
    if points.count == 1 {
      let point = denormalize(points[0].location, in: rect)
      Circle()
        .fill(color)
        .frame(width: Constants.dotSize, height: Constants.dotSize)
        .position(point)
    } else {
      Path { path in
        guard let firstPoint = points.first else { return }
        path.move(to: denormalize(firstPoint.location, in: rect))
        for point in points.dropFirst() {
          path.addLine(to: denormalize(point.location, in: rect))
        }
      }
      .stroke(color, lineWidth: Constants.lineWidth)
    }
  }

  private func denormalize(_ point: CGPoint, in rect: CGRect) -> CGPoint {
    return CGPoint(
      x: rect.origin.x + point.x * rect.width,
      y: rect.origin.y + point.y * rect.height
    )
  }

  private func color(for brushMode: BrushMode) -> Color {
    switch brushMode {
    case .positive: return .green
    case .negative: return .red
    case .lasso: return .blue
    case .unspecified: return .gray
    @unknown default: return .gray
    }
  }
}

extension CGSize {
  fileprivate func aspectFit(in viewSize: CGSize) -> CGRect {
    let imageRatio = self.width / self.height
    let viewRatio = viewSize.width / viewSize.height

    var scale: CGFloat
    var offset = CGPoint.zero

    if imageRatio > viewRatio {
      // Image is wider than view
      scale = viewSize.width / self.width
      offset.y = (viewSize.height - self.height * scale) / 2.0
    } else {
      // Image is taller than view
      scale = viewSize.height / self.height
      offset.x = (viewSize.width - self.width * scale) / 2.0
    }

    return CGRect(
      x: offset.x, y: offset.y, width: self.width * scale, height: self.height * scale)
  }
}
