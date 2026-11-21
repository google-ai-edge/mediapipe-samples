# MediaPipe Tasks Text Embedder iOS Demo

### Overview

This iOS app compares the semantic similarity of two text inputs on-device using
the MediaPipe `TextEmbedder` task, with a choice of:

- [EmbeddingGemma 300M](https://huggingface.co/litert-community/MediaPipe-TextEmbedder-EmbeddingGemma-300M/resolve/main/embedding_gemma.task)
  (768d, with optional `TextFormatContext` task prompts for Semantic Similarity,
  Query → Document retrieval, Q&A, and Clustering)
- [MobileBERT](https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite)
  (512d)
- [Universal Sentence Encoder](https://huggingface.co/litert-community/MediaPipe-TextEmbedder-Universal-Sentence-Encoder/resolve/main/universal_sentence_encoder.tflite)
  (100d)

![Text Embedder iOS Demo](textembedder.png?raw=true "Text Embedder iOS Demo")

Models are downloaded automatically during the Xcode build via
`RunScripts/download_models.sh` with SHA-256 verification. Shared SwiftUI
components and test helpers live in [`ios-common/`](../../../ios-common).

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `TextEmbedder/TextEmbedderApp.swift` — minimal `@main` SwiftUI entry point
- `TextEmbedder/TextEmbedderView.swift` — SwiftUI interface and Settings sheet
  accepting an injectable `TextEmbedderViewModel`
- `TextEmbedder/TextEmbedderViewModel.swift` — `@Observable @MainActor` state
  container exposing awaitable operations (`loadEmbedder()`, `configure(...)`,
  `compare()`)
- `TextEmbedder/TextEmbedderHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksText.TextEmbedder` off the main thread

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ or an iOS 18+ Simulator.

### Building from Xcode

1. Open `examples/text_embedder/ios/TextEmbedder.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksText` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and
   verifies all three model assets on the first build.

### Building and testing from the command line

```bash
cd examples/text_embedder/ios

# Download and verify model SHA-256 checksums
./RunScripts/download_models.sh --verify

# Run real-model XCTests on an iOS Simulator
xcodebuild test \
  -project TextEmbedder.xcodeproj \
  -scheme TextEmbedder \
  -destination "platform=iOS Simulator,name=iPhone 17" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project TextEmbedder.xcodeproj \
  -scheme TextEmbedder \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```
