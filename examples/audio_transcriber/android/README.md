# MediaPipe Audio Transcriber — Android Sample

This sample app demonstrates real-time, on-device automatic speech recognition (ASR) using the MediaPipe Tasks Audio `AudioTranscriber` API (`com.google.mediapipe:tasks-audio`) and the [Parakeet TDT 0.6B v3](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3) stateful LiteRT-LM model (`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`), built with Compose Material 3 (`:android-common:ui`).

## Features

- **On-device Streaming ASR:** Low-latency speech-to-text recognition running on CPU or GPU via `AudioTranscriber`.
- **Bundled Sample Clip:** Tap **Transcribe Sample** to run Parakeet TDT on the verified `speech_16000_hz_mono.wav` sample speech clip.
- **Real-time Microphone Capture:** Captures 16 kHz mono audio via Android `AudioRecord` and streams chunks into `AudioTranscriber.transcribe()`.
- **Settings & Custom Model Loading:** Switch between CPU and GPU delegates or load a sideloaded `.litertlm` model from device storage.

![Audio Transcriber Demo](audiotranscriber.png?raw=true "Audio Transcriber Demo")

## Model & Assets

Models and sample audio are downloaded automatically during Gradle build and verified via SHA-256 in `app/download_models.gradle.kts`:

- **Parakeet TDT 0.6B v3** (`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`): https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3
- **Sample Speech WAV** (`speech_16000_hz_mono.wav`): 16 kHz mono sample speech clip

## Build and Test

```bash
cd examples/audio_transcriber/android

# Download and verify assets, run shared unit tests, and build debug APK
./gradlew :app:verifyModels :android-common:testing:testDebugUnitTest :app:assembleDebug

# Run real-model ASR quality & Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```

### Run via Convenience Script

```bash
./scripts/run_android_demo.sh --record-seconds 5
```
