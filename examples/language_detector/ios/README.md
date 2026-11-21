# MediaPipe Tasks Language Detector iOS Demo

### Overview

This iOS app identifies the language of input text on-device using the MediaPipe
`LanguageDetector` task powered by
[`language_detector.tflite`](https://huggingface.co/litert-community/MediaPipe-LanguageDetector/resolve/main/language_detector.tflite).
It provides one-tap multilingual presets (French, Chinese, English, Spanish,
German, Japanese, and Russian), displays the top localized language name and ISO
code with a confidence gauge and ranked probability breakdown, and lets you
configure the maximum number of returned predictions (`Top 1`, `Top 3`, or
`Top 5`).

![Language Detector iOS Demo](languagedetector.png?raw=true "Language Detector iOS Demo")

The model is downloaded automatically during the Xcode build via
`RunScripts/download_models.sh` with SHA-256 verification. Shared SwiftUI
components and test helpers live in [`ios-common/`](../../../ios-common).

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `LanguageDetector/LanguageDetectorApp.swift` — minimal `@main` SwiftUI entry
  point
- `LanguageDetector/LanguageDetectorView.swift` — SwiftUI interface and Settings
  sheet accepting an injectable `LanguageDetectorViewModel`
- `LanguageDetector/LanguageDetectorViewModel.swift` — `@Observable @MainActor`
  state container exposing awaitable operations (`loadDetector()`,
  `configure(...)`, `detect()`)
- `LanguageDetector/LanguageDetectorHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksText.LanguageDetector` off the main thread

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ or an iOS 18+ Simulator.

### Building from Xcode

1. Open `examples/language_detector/ios/LanguageDetector.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksText` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and
   verifies `language_detector.tflite` on the first build.

### Building and testing from the command line

```bash
cd examples/language_detector/ios

# Download and verify model SHA-256 checksum
./RunScripts/download_models.sh --verify

# Run real-model XCTests on an iOS Simulator
xcodebuild test \
  -project LanguageDetector.xcodeproj \
  -scheme LanguageDetector \
  -destination "platform=iOS Simulator,name=iPhone 17" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project LanguageDetector.xcodeproj \
  -scheme LanguageDetector \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```
