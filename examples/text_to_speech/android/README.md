# MediaPipe Text-to-Speech — Android Sample

This sample app demonstrates on-device neural speech synthesis using the MediaPipe Tasks Audio `TextToSpeech` API (`com.google.mediapipe:tasks-audio`) and the [Kokoro-82M](https://huggingface.co/litert-community/Kokoro-82M) LiteRT-LM model (`kokoro_int8_fp16.litertlm`), built with Compose Material 3 (`:android-common:ui`).

## Features

- **On-device Neural TTS:** High-quality 24 kHz speech synthesis running locally on GPU (with automatic CPU fallback) or CPU.
- **Unary & Streaming Synthesis:**
  - **Speak:** Full-utterance synthesis (`synthesize`) with WAV export and hardware `AudioTrack` playback.
  - **Stream:** Low-latency clause-by-clause streaming (`synthesizeStream`) with time-to-first-audio (TTFA) reporting and incremental playback.
  - **Replay & Stop:** Replay the most recent synthesized utterance or stop synthesis/playback immediately.
- **Multilingual Voices & Settings Sheet:** Select sample presets, pick languages/voices (`af_heart`, `ef_dora`, `zf_xiaobei`, etc.), adjust speaking rate (`0.75x–1.40x`), and switch hardware delegates (`Auto`, `CPU`, `GPU`).

![Text to Speech Demo](texttospeech.png?raw=true "Text to Speech Demo")

## Model

The default `kokoro_int8_fp16.litertlm` model is downloaded automatically during Gradle build and verified via SHA-256 in `app/download_models.gradle.kts` (supports custom URLs via `KOKORO_MODEL_URL`):

* **Kokoro-82M** (`kokoro_int8_fp16.litertlm`): https://huggingface.co/litert-community/Kokoro-82M

For sideloading a custom model variant (such as `kokoro_int8_gpu_fp16.litertlm` renamed to `kokoro.litertlm`), `TextToSpeechHelper` checks external files, `/data/local/tmp`, and internal storage before falling back to bundled assets.

## Build and Test

```bash
cd examples/text_to_speech/android

# Download and verify model checksum, run unit tests, and build debug APK
./gradlew :app:verifyModels :android-common:testing:testDebugUnitTest :app:assembleDebug

# Run real-model TTS quality & Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```

### Run via Command-line Script

```bash
./scripts/run_android_demo.sh --text "Hello from MediaPipe Text to Speech!" --voice af_heart
```
