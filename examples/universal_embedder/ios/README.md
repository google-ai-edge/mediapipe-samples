# MediaPipe Tasks Universal Embedder iOS Demo

### Overview

This iOS app demonstrates on-device multimodal embedding extraction and cross-modal
similarity comparison using the MediaPipe `UniversalEmbedder` task
(`MediaPipeTasksRetrieval`) powered by
[EmbeddingGemma 2 (Text + Vision 440M)](https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm)
running locally via LiteRT-LM.

Because `UniversalEmbedder` projects both text and images into a single shared 768d
vector space, you can compare **Text ↔ Image**, **Text ↔ Text**, and **Image ↔ Image**
inputs directly on-device — choosing from 10 bundled sample photos, picking images from
your Photos library via `PhotosPicker`, or typing custom captions.

![Universal Embedder iOS Demo](universalembedder.png?raw=true "Universal Embedder iOS Demo")

The model (~370 MB) is downloaded automatically during the Xcode build via
`RunScripts/download_models.sh` with SHA-256 verification. Shared SwiftUI components
and test helpers live in [`ios-common/`](../../../ios-common).

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `UniversalEmbedder/UniversalEmbedderApp.swift` — minimal `@main` SwiftUI entry point
- `UniversalEmbedder/UniversalEmbedderView.swift` — SwiftUI interface with dual
  multimodal input cards (`Text` / `Image` + bundled sample strip + `PhotosPicker`),
  cosine similarity gauge, telemetry tiles, and Settings sheet (CPU / GPU Metal and
  L2 normalization)
- `UniversalEmbedder/UniversalEmbedderViewModel.swift` — `@Observable @MainActor`
  state container exposing awaitable operations (`loadEmbedder()`, `configure(...)`,
  `compare()`, `swapInputs()`, `applyPreset(...)`)
- `UniversalEmbedder/UniversalEmbedderHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksRetrieval.UniversalEmbedder` off the main thread, storing LiteRT-LM
  compiled caches in `Caches/MediaPipeUniversalEmbedderCache`

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ or an iOS 18+ Simulator.

### Building from Xcode

1. Open `examples/universal_embedder/ios/UniversalEmbedder.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksRetrieval` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and verifies
   `embeddinggemma-2-text-vision-440m.litertlm` on the first build.

### Building and testing from the command line

```bash
cd examples/universal_embedder/ios

# Download and verify model SHA-256 checksum
./RunScripts/download_models.sh --verify

# Run real-model XCTests on an iOS Simulator
xcodebuild test \
  -project UniversalEmbedder.xcodeproj \
  -scheme UniversalEmbedder \
  -destination "platform=iOS Simulator,name=iPhone Air" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project UniversalEmbedder.xcodeproj \
  -scheme UniversalEmbedder \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```

### Related samples

- [`examples/semantic_retriever`](../../semantic_retriever) builds on
  `UniversalEmbedder`, adding an on-device vector store for semantic image search.
