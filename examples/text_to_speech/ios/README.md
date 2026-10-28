# MediaPipe Tasks Text-to-Speech iOS Demo

### Overview

This sample app demonstrates on-device neural speech synthesis using the MediaPipe Tasks Audio TextToSpeech API with the [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) LiteRT-LM model.

The Text-to-Speech API enables low-latency, on-device audio generation from text inputs:

- **Unary Synthesis**: Synthesizes the entire utterance into an `AudioResult` float32 PCM buffer and plays it gaplessly via `AVAudioEngine`.
- **Streaming Synthesis**: Synthesizes clause-by-clause via `synthesizeStream` with real-time playback as `AudioChunk` buffers arrive, tracking Time-To-First-Audio (TTFA).
- **Multilingual Voices**: Dynamically lists all voices packaged in the model and allows interactive voice selection (e.g. `af_heart`, `ef_dora`, `zf_xiaobei`).
- **Interactive UI**: Built with modern SwiftUI, offering preset phrase shortcuts, custom text input, status indicators, and playback controls.

### API in a Nutshell

```swift
import MediaPipeTasksAudio

// 1. Initialize TextToSpeech engine
let options = TextToSpeechOptions(
  baseOptions: TTSBaseOptions(
    modelAssetPath: modelPath,
    delegate: .gpu
  ),
  defaultVoiceId: "af_heart",
  speakingRate: 1.0
)
let tts = try createTextToSpeech(options: options)

// 2. Query available voices
let voices = try tts.listVoices()

// 3. Synthesize speech (unary)
let result = try await tts.synthesizeAsync("Hello world!", options: SynthesisOptions(voiceId: "af_heart"))

// 4. Or synthesize with streaming
for try await chunk in tts.synthesizeStream("Hello world!", options: SynthesisOptions(voiceId: "af_heart")) {
  player.enqueue(chunk)
}
```

## Build the Demo Using Xcode

### Prerequisites

*   Xcode 15.0 or later.
*   iOS 16.0 or later (physical device recommended for GPU acceleration, or simulator).

The MediaPipe dependency is resolved automatically via Swift Package Manager (SPM) using `MediaPipeTasksAudio`.

### Model Setup

The app requires a Kokoro-82M LiteRT-LM model bundle from the [LiteRT Kokoro-82M Hugging Face repository](https://huggingface.co/litert-community/Kokoro-82M).

#### Choosing the Right Model Variant

| Model Bundle | Recommended Target | Delegate | Description |
|---|---|---|---|
| **`kokoro_int8_gpu_fp16.litertlm`** ⭐ | **Physical iOS Device** | **GPU** (`.gpu`) | **Recommended for devices**. Vocoder convolutions run natively on Metal in FP16. Fast (RTF ~0.12) with minimal peak RAM (~350 MB). *Note: running this model on CPU causes audible high-pitched quantization overtones.* |
| **`kokoro_int8_fp16.litertlm`** | **iOS Simulator & CPU** | **CPU** (`.cpu`) | **Recommended for simulator / CPU**. Contains explicit dequantization ops for XNNPACK, producing clean audio on CPU without quantization harmonics (~700–850 MB peak RAM). |

> **Note on Simulator vs. Device:**
> Apple's iOS Simulator does not support the Metal compute pipeline features (argument buffer residency) required by LiteRT's GPU delegate, so the simulator always runs on the CPU delegate. When testing on a simulator, use `kokoro_int8_fp16.litertlm` to avoid vocoder quantization noise.

The app automatically searches for the model in:
1. The app bundle (`TextToSpeech/kokoro_int8_gpu_fp16.litertlm` or `kokoro.litertlm`)
2. The app's `Documents` directory (on iOS device)
3. `~/models/kokoro_int8_gpu_fp16.litertlm` or `~/models/kokoro.litertlm` (on macOS / simulator)

To bundle the model with the app:
1. Place the chosen `.litertlm` file into the `TextToSpeech/` directory.
2. Ensure it is added to the Xcode target under **Build Phases > Copy Bundle Resources**.

### Building

1. Open `TextToSpeech.xcodeproj` in Xcode.
2. Select your development team under **Signing & Capabilities**.
3. Select your device or simulator and click **Run**.
