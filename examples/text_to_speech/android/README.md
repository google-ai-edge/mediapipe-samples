# MediaPipe Text-to-Speech — Android Sample

This sample app demonstrates on-device speech synthesis using the MediaPipe Tasks Audio TextToSpeech API with the [Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) LiteRT-LM model (`kokoro.litertlm`).

## Features

- **On-device neural TTS**: High-quality speech synthesis running entirely on-device via MediaPipe and LiteRT.
- **Unary & Streaming Synthesis**:
  - **Speak**: Full-utterance synthesis with immediate gapless playback.
  - **Stream**: Clause-by-clause low-latency streaming (`synthesizeStream`) with real-time audio playback as chunks arrive.
- **Multilingual & Multivoice**: Supports multiple voices and locales provided by the model (e.g., `af_heart`, `ef_dora`, `zf_xiaobei`).
- **PCM Audio Player & WAV Export**: Audio playback via Android `AudioTrack` and optional WAV recording.
- **Scripted testing**: Command-line automation via `scripts/run_android_demo.sh`.

## Model Setup

The demo requires a Kokoro-82M LiteRT-LM model bundle from the [LiteRT Kokoro-82M Hugging Face repository](https://huggingface.co/litert-community/Kokoro-82M).

### Choosing the Right Model Variant

| Model Bundle | Recommended Target | Delegate | Description |
|---|---|---|---|
| **`kokoro_int8_gpu_fp16.litertlm`** ⭐ | **Physical Android Device** | **GPU** | **Recommended for devices**. Vocoder convolutions run natively on OpenCL in FP16. Fast (RTF ~0.12) with minimal peak RAM (~350 MB). *Note: running this model on CPU causes audible high-pitched quantization overtones.* |
| **`kokoro_int8_fp16.litertlm`** | **Android Emulator & CPU** | **CPU** | **Recommended for emulator / CPU**. Contains explicit dequantization ops for XNNPACK, producing clean audio on CPU without quantization harmonics (~700–850 MB peak RAM). |

> **Note on Emulator vs. Device:**
> The Android Emulator does not provide guest OpenCL compute drivers, so LiteRT's GPU delegate automatically falls back to CPU (XNNPACK). When testing on an Android emulator, use `kokoro_int8_fp16.litertlm` to avoid vocoder quantization noise.

The app automatically searches for the model in the following locations:
1. App's external files directory: `/sdcard/Android/data/com.google.mediapipe.tasks.audio.texttospeech.examples/files/kokoro.litertlm`
2. Shared device temporary storage: `/data/local/tmp/kokoro.litertlm`
3. Internal files directory: `context.filesDir`
4. Bundled APK assets: `app/src/main/assets/kokoro.litertlm`

### Pushing the Model via ADB (Recommended)

To push the model directly to your device without rebuilding:

```bash
adb push kokoro.litertlm /sdcard/Android/data/com.google.mediapipe.tasks.audio.texttospeech.examples/files/kokoro.litertlm
```

Or push to `/data/local/tmp`:

```bash
adb push kokoro.litertlm /data/local/tmp/kokoro.litertlm
```

## Building the Sample

### Prerequisites
- Android Studio Iguana or newer, or JDK 17+ with Android SDK (API 34+).
- A physical Android device running Android 8.0 (API 26) or higher, or an Android emulator.

### Build via Gradle
```bash
./gradlew installDebug
```

### Run via Command-line Script
The sample includes a convenience script that builds, installs, pushes the model, runs the demo, streams logcat output, and pulls the synthesized WAV file:

```bash
./scripts/run_android_demo.sh --text "Hello from MediaPipe Text to Speech!" --voice af_heart
```

Options:
- `--text "..."`: Text to synthesize.
- `--voice ID`: Voice identifier (default: `af_heart`).
- `--stream`: Run in clause-by-clause streaming mode.
- `--no-play`: Disable device speaker playback.
- `--model PATH`: Path to local model file on host.
