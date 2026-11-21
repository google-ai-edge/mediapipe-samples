# MediaPipe Tasks Text Summarizer iOS Demo

### Overview

This iOS app summarizes articles, meeting notes, and passages completely
on-device using the MediaPipe `TextSummarizer` task with the quantized
[Gemma 200M (2-Mode)](https://huggingface.co/litert-community/MediaPipe-TextSummarizer-200M/resolve/main/summarization_quant_200m_2modes.litertlm)
LiteRT-LM model:

- **Key Points** mode — extracts concise bulleted takeaways from the source passage
- **TL;DR** mode — distills the source passage into a brief, readable paragraph
- **Unary & Streaming** generation — supports both complete responses and
  incremental token streaming
- **Configurable token budget** — switch between `512`, `1024`, and `2048` max
  tokens in Settings

![Text Summarizer iOS Demo](textsummarizer.png?raw=true "Text Summarizer iOS Demo")

The model asset (`summarization_quant_200m_2modes.litertlm`) is downloaded
automatically during the Xcode build via `RunScripts/download_models.sh` with
pinned SHA-256 verification. Shared SwiftUI components and test helpers live in
[`ios-common/`](../../../ios-common).

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `TextSummarizer/TextSummarizerApp.swift` — minimal `@main` SwiftUI entry point
- `TextSummarizer/TextSummarizerView.swift` — SwiftUI interface and Settings
  sheet accepting an injectable `TextSummarizerViewModel`
- `TextSummarizer/TextSummarizerViewModel.swift` — `@Observable @MainActor`
  state container exposing awaitable operations (`loadSummarizer()`,
  `configure(...)`, `summarize(streaming:)`)
- `TextSummarizer/TextSummarizerHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksText.TextSummarizer` off the main thread

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ or an iOS 18+ Simulator.

### Building from Xcode

1. Open `examples/text_summarizer/ios/TextSummarizer.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksText` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and
   verifies `summarization_quant_200m_2modes.litertlm` on the first build.

### Building and testing from the command line

```bash
cd examples/text_summarizer/ios

# Download and verify model SHA-256 checksum
./RunScripts/download_models.sh --verify

# Run real-model XCTests on an iOS Simulator
xcodebuild test \
  -project TextSummarizer.xcodeproj \
  -scheme TextSummarizer \
  -destination "platform=iOS Simulator,name=iPhone 17" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project TextSummarizer.xcodeproj \
  -scheme TextSummarizer \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```
