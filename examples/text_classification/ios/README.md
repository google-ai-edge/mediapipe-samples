# MediaPipe Tasks Text Classifier iOS Demo

### Overview

This iOS app classifies sentiment (`Positive` / `Negative`) for input text on-device using
the MediaPipe `TextClassifier` task, with a choice of:

- [MobileBERT](https://huggingface.co/litert-community/MediaPipe-TextClassifier-MobileBERT/resolve/main/bert_classifier.tflite)
  (contextual transformer sentiment classifier — default)
- [Average WordVec](https://huggingface.co/litert-community/MediaPipe-TextClassifier-AverageWord/resolve/main/average_word_classifier.tflite)
  (compact bag-of-words sentiment classifier)

![Text Classifier iOS Demo](textclassifier.png?raw=true "Text Classifier iOS Demo")

Models are downloaded automatically during the Xcode build via
`RunScripts/download_models.sh` with SHA-256 verification. Shared SwiftUI
components and test helpers live in [`ios-common/`](../../../ios-common).

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `TextClassifier/TextClassifierApp.swift` — minimal `@main` SwiftUI entry point
- `TextClassifier/TextClassifierView.swift` — SwiftUI interface and Settings sheet
  accepting an injectable `TextClassifierViewModel`
- `TextClassifier/TextClassifierViewModel.swift` — `@Observable @MainActor` state
  container exposing awaitable operations (`loadClassifier()`, `configure(...)`,
  `classify()`)
- `TextClassifier/TextClassifierHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksText.TextClassifier` off the main thread

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ or an iOS 18+ Simulator.

### Building from Xcode

1. Open `examples/text_classification/ios/TextClassifier.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksText` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and
   verifies both model assets on the first build.

### Building and testing from the command line

```bash
cd examples/text_classification/ios

# Download and verify model SHA-256 checksums
./RunScripts/download_models.sh --verify

# Run real-model XCTests on an iOS Simulator
xcodebuild test \
  -project TextClassifier.xcodeproj \
  -scheme TextClassifier \
  -destination "platform=iOS Simulator,name=iPhone 17" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project TextClassifier.xcodeproj \
  -scheme TextClassifier \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```
