# MediaPipe Audio Transcriber — Android Sample

This sample app demonstrates real-time, on-device automatic speech recognition (ASR) using the MediaPipe Tasks Audio `AudioTranscriber` API with the [Parakeet TDT 0.6B](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3) stateful LiteRT-LM model (`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`).

## Features

- **On-device streaming ASR**: Low-latency speech-to-text recognition running entirely on-device via MediaPipe Tasks Audio and LiteRT Omni ASR runtime.
- **Real-time Live Audio Capture**: Captures 16 kHz mono audio via Android `AudioRecord` and streams chunks into `AudioTranscriber.transcribe()`.
- **Live Interim & Final Transcripts**: Displays streaming interim hypotheses with real-time audio input level visualization and final timestamped segments.
- **Dynamic Model Loading**: Ability to sideload alternative `.litertlm` models via Android's document picker or push them via ADB.
- **Jetpack Compose UI**: Modern, responsive Material 3 user interface.
- **Scripted testing**: Command-line automation via `scripts/run_android_demo.sh`.

## Model Setup

The demo uses the Parakeet TDT 0.6B v3 stateful 5-second quantized model:
`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`

Download URL:
https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/blob/main/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm

### Download Script
Run the download script to fetch the model into the app assets directory:
```bash
sh RunScripts/download_models.sh
```

### Pushing the Model via ADB (Recommended)
You can push the model directly to your device or emulator without rebuilding:

```bash
adb push parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm /sdcard/Android/data/com.google.mediapipe.tasks.examples.audiotranscriber/files/
```

Or push to `/data/local/tmp/audiotranscriber`:

```bash
adb shell mkdir -p /data/local/tmp/audiotranscriber
adb push parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm /data/local/tmp/audiotranscriber/
adb shell chmod 777 /data/local/tmp/audiotranscriber/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm
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
The sample includes a convenience script that checks ADB, pushes the model, builds/installs the app, grants microphone permissions, launches MainActivity, and verifies transcription:

```bash
./scripts/run_android_demo.sh --record-seconds 5
```

Options:
- `--model PATH`: Path to local `.litertlm` model file on host.
- `--serial SERIAL`: Target ADB device serial.
- `--record-seconds N`: Duration in seconds to capture live audio.
- `--no-build`: Skip Gradle build if the APK is already installed.
