# MediaPipe Tasks Audio Transcriber iOS Demo

### Overview

This sample app demonstrates on-device real-time speech transcription using the MediaPipe Tasks Audio `AudioTranscriber` (`MPPAudioTranscriber`) API with the Parakeet TDT 0.6B v3 LiteRT-LM model (`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`).

The Audio Transcriber API enables streaming on-device speech-to-text:

- **Live Microphone Capture**: Captures 48kHz mono float32 PCM audio via `AVAudioEngine` with real-time level metering.
- **Streaming Transcription**: Streams audio buffers into `AudioTranscriber` and displays both interim live transcriptions and finalized segments with timestamps and durations.
- **Interactive UI**: Built with modern SwiftUI, featuring real-time audio level meters, status indicators, transcript list view, and record/stop controls.

### API in a Nutshell

```swift
import MediaPipeTasksAudio

// 1. Initialize AudioTranscriber
let options = AudioTranscriberOptions()
options.baseOptions.modelAssetPath = modelPath
options.baseOptions.delegate = .CPU
options.sampleRateHz = 48_000.0
options.enableInterimResults = true
options.modelArchitecture = .auto

let transcriber = try AudioTranscriber(options: options)
try transcriber.prewarm()

// 2. Stream audio buffers
let result = try transcriber.transcribe(
  samples: samplesBuffer,
  count: UInt(samplesBuffer.count),
  endOfStream: false
)

// 3. Finalize stream
let finalResult = try transcriber.finishStream()
```

## Build the Demo Using Xcode

### Prerequisites

* Xcode 15.0 or later.
* iOS 17.0 or later (physical device or simulator).

The MediaPipe dependency is resolved via Swift Package Manager (SPM) using `MediaPipeTasksAudio`.

### Model Setup

The app requires the Parakeet TDT 0.6B v3 LiteRT-LM model bundle (`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`).

The app automatically searches for the model in:
1. The app bundle (`AudioTranscriber/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`)
2. The app's `Documents` directory (on iOS device)
3. `~/models/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm` (on macOS / simulator)

To bundle the model with the app:
1. Place `parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm` into the `AudioTranscriber/` directory.
2. Ensure it is added to the Xcode target under **Build Phases > Copy Bundle Resources**.

### Building

1. Open `AudioTranscriber.xcodeproj` in Xcode.
2. Select your development team under **Signing & Capabilities**.
3. Select your device or simulator and click **Run**.
