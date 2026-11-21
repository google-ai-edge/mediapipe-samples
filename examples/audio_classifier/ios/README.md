# MediaPipe Tasks Audio Classifier iOS Demo

### Overview

This iOS app classifies audio clips and live microphone audio into 521 AudioSet
sound categories on-device using the MediaPipe `AudioClassifier` task and the
[YAMNet](https://huggingface.co/litert-community/MediaPipe-AudioClassifier-YAMNet/resolve/main/yamnet.tflite)
model (`0.975 s` windows at `16 kHz`).

Features:
- **Audio Clip mode** — classifies the bundled `speech_16000_hz_mono.wav` sample,
  a generated 440 Hz test tone, or any imported audio file (`.wav`, `.m4a`,
  `.mp3`, `.aiff`) with waveform preview, synced `AVAudioPlayer` playback, and
  an interactive window timeline scrubber (`0.00s`–`3.90s`).
- **Both `AUDIO_CLIPS` and `AUDIO_STREAM` clip inference** — switch between
  single-call `classify(audioClip:)` and chunked asynchronous
  `classifyAsync(audioBlock:timestampInMilliseconds:)` with
  `AudioClassifierStreamDelegate` in Settings.
- **Live Mic mode** — real-time microphone audio classification via
  `AudioRecord` with configurable window overlap (`0%`–`75%`), max results
  (`1`–`5`), and score threshold (`0.0`–`0.9`).

![Audio Classifier iOS Demo](audioclassifier.png?raw=true "Audio Classifier iOS Demo")

Model and sample audio assets are downloaded automatically during the Xcode
build via `RunScripts/download_models.sh` with SHA-256 verification. Shared
SwiftUI components and test helpers live in [`ios-common/`](../../../ios-common).

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `AudioClassifier/AudioClassifierApp.swift` — minimal `@main` SwiftUI entry point
- `AudioClassifier/AudioClassifierView.swift` — SwiftUI interface, waveform
  timeline scrubber, file importer, and Settings sheet accepting an injectable
  `AudioClassifierViewModel`
- `AudioClassifier/AudioClassifierViewModel.swift` — `@Observable @MainActor`
  state container exposing awaitable operations (`loadClassifier()`,
  `configure(...)`, `classifyCurrentClip()`, `classifyAudioFile(at:)`,
  `startMicrophone()`, `stopMicrophone()`)
- `AudioClassifier/AudioClassifierHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksAudio.AudioClassifier` and `AudioRecord` off the main thread

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ (required for live microphone streaming) or
  an iOS 18+ Simulator (for Audio Clip mode and tests).

### Building from Xcode

1. Open `examples/audio_classifier/ios/AudioClassifier.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksAudio` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and
   verifies `yamnet.tflite` and `speech_16000_hz_mono.wav` on the first build.

### Building and testing from the command line

```bash
cd examples/audio_classifier/ios

# Download and verify model and sample audio SHA-256 checksums
./RunScripts/download_models.sh --verify

# Run real-model XCTests on an iOS Simulator
xcodebuild test \
  -project AudioClassifier.xcodeproj \
  -scheme AudioClassifier \
  -destination "platform=iOS Simulator,name=iPhone 17" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project AudioClassifier.xcodeproj \
  -scheme AudioClassifier \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```
