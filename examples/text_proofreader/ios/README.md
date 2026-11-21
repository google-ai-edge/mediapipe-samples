# MediaPipe Tasks Text Proofreader iOS Demo

### Overview

This iOS app performs on-device grammar, spelling, and punctuation correction
using the MediaPipe `TextProofreader` task with the quantized
[Gemma 200M LiteRT-LM model](https://huggingface.co/litert-community/MediaPipe-TextProofreader-200M/resolve/main/proofread_quant_200m.litertlm),
supporting both live streaming and unary proofreading with color-coded inline
diff highlighting.

![Text Proofreader iOS Demo](textproofreader.png?raw=true "Text Proofreader iOS Demo")

The model asset (`proofread_quant_200m.litertlm`) is downloaded automatically
during the Xcode build via `RunScripts/download_models.sh` with SHA-256
verification. Shared SwiftUI components and test helpers live in
[`ios-common/`](../../../ios-common).

## Features

- **Curated presets** covering grammar, spelling & tense, meeting notes, and
  already-clean text.
- **Color-coded inline diff & clean view** toggling seamlessly between
  highlighted deletions/insertions built from `Correction` segments and the
  final proofread text.
- **Live streaming & unary inference** with one-tap copy-to-clipboard and
  **Apply Fix** actions.
- **Configurable token budget** (`256`, `512`, `1024` max tokens via
  `TextProofreaderOptions.maxNumTokens`) and streaming toggle in Settings, with
  CPU execution diagnostics.

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `TextProofreader/TextProofreaderApp.swift` — minimal `@main` SwiftUI entry
  point
- `TextProofreader/TextProofreaderView.swift` — SwiftUI interface, inline diff
  renderer, and Settings sheet accepting an injectable
  `TextProofreaderViewModel`
- `TextProofreader/TextProofreaderViewModel.swift` — `@Observable @MainActor`
  state container exposing awaitable operations (`loadProofreader()`,
  `configure(...)`, `proofread(streaming:)`)
- `TextProofreader/TextProofreaderHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksText.TextProofreader` off the main thread

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ or an iOS 18+ Simulator.

### Building from Xcode

1. Open `examples/text_proofreader/ios/TextProofreader.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksText` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and
   verifies `proofread_quant_200m.litertlm` on the first build.

### Building and testing from the command line

```bash
cd examples/text_proofreader/ios

# Download and verify model SHA-256 checksum
./RunScripts/download_models.sh --verify

# Run real-model XCTests on an iOS Simulator
xcodebuild test \
  -project TextProofreader.xcodeproj \
  -scheme TextProofreader \
  -destination "platform=iOS Simulator,name=iPhone 17" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project TextProofreader.xcodeproj \
  -scheme TextProofreader \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```
