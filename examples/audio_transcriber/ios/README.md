# MediaPipe Tasks Audio Transcriber iOS Demo

### Overview

This iOS app performs on-device automatic speech recognition (ASR) using the
MediaPipe `AudioTranscriber` task (`MediaPipeTasksAudio`) with the stateful
[Parakeet TDT 0.6B v3](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3)
LiteRT-LM model (`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`):

- **Audio Clip Transcription**: Transcribe the bundled 16 kHz mono speech clip
  (`speech_16000_hz_mono.wav`) or import custom `.wav` / audio files from Files
  (automatically resampled to 16 kHz mono float32 PCM), in either **Full Clip**
  (single-call) or **Streaming** (chunked 500 ms with live progress) mode.
- **Live Microphone Transcription**: Real-time `AVAudioEngine` microphone
  capture with animated level metering, interim partial transcripts, and
  finalized timestamped segments.
- **Hardware Acceleration**: Switch between **CPU** and **GPU (Metal)** in the
  Settings sheet, with live latency, Real-Time Factor (RTF), and duration
  telemetry.

![Audio Transcriber iOS Demo](audiotranscriber.png?raw=true "Audio Transcriber iOS Demo")

The model and sample audio clip are downloaded automatically during the Xcode
build via `RunScripts/download_models.sh` with SHA-256 verification. Shared
SwiftUI components and test helpers live in [`ios-common/`](../../../ios-common).

## Architecture

The sample follows a uniform 4-file SwiftUI + Swift 6 architecture designed for
both standalone execution and shared validation harnesses:

- `AudioTranscriber/AudioTranscriberApp.swift` — minimal `@main` SwiftUI entry
  point launching with automatic sample clip transcription
- `AudioTranscriber/AudioTranscriberView.swift` — SwiftUI interface (Audio Clip
  & Microphone modes, animated level meter, Hero Transcript card, Settings
  sheet) accepting an injectable `AudioTranscriberViewModel`
- `AudioTranscriber/AudioTranscriberViewModel.swift` — `@Observable @MainActor`
  state container owning `AVAudioEngine` microphone capture and exposing
  awaitable operations (`loadTranscriber()`, `configure(delegate:clipMode:)`,
  `transcribeFile(url:stream:)`, `transcribeBundledSample()`,
  `startRecording()`, `stopRecording()`)
- `AudioTranscriber/AudioTranscriberHelper.swift` — Swift `actor` wrapping
  `MediaPipeTasksAudio.AudioTranscriber` off the main thread, plus
  `AudioClipDecoder` for CoreAudio decoding and 16 kHz resampling

## Build and run

### Prerequisites

- **Xcode 16 or newer** (iOS 18.0+ deployment target).
- An iPhone/iPad running iOS 18.0+ or an iOS 18+ Simulator.

### Building from Xcode

1. Open `examples/audio_transcriber/ios/AudioTranscriber.xcodeproj` in Xcode.
2. Wait for Swift Package Manager to resolve `MediaPipeTasksAudio` and the local
   `ios-common` package.
3. Press **Run** (`⌘R`). The **Download Models** build phase fetches and
   verifies `parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm` (~615 MB) and
   `speech_16000_hz_mono.wav` on the first build.

### Building and testing from the command line

```bash
cd examples/audio_transcriber/ios

# Download and verify model and sample audio SHA-256 checksums
./RunScripts/download_models.sh --verify

# Run real-model XCTests (CPU + GPU, Full Clip + Streaming, WER <= 0.2)
xcodebuild test \
  -project AudioTranscriber.xcodeproj \
  -scheme AudioTranscriber \
  -destination "platform=iOS Simulator,name=iPhone 17" \
  CODE_SIGNING_ALLOWED=NO

# Build for generic iOS device (CI check)
xcodebuild build \
  -project AudioTranscriber.xcodeproj \
  -scheme AudioTranscriber \
  -destination "generic/platform=iOS" \
  CODE_SIGNING_ALLOWED=NO
```
