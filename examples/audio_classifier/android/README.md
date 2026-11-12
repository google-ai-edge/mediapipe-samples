# MediaPipe Tasks Audio Classifier Android Demo

### Overview

This app continuously classifies sounds recorded from the microphone or loaded from audio clips on device using [YAMNet](https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite) and the MediaPipe Tasks Audio library (`com.google.mediapipe:tasks-audio`).

* **Audio Clip mode:** Classify the bundled 16 kHz speech clip, a synthesized 440 Hz sine tone, or any audio file picked from device storage, with window-by-window playback and probability bars.
* **Live Mic mode:** Stream 16 kHz microphone audio in `RunningMode.AUDIO_STREAM` with configurable overlap, max results, and confidence threshold.
* **Material 3 Compose UI:** Shared `:android-common:ui` theming and components with edge-to-edge layout and light/dark theme support.

![Audio Classifier Demo](audioclassifier.png?raw=true "Audio Classifier Demo")

## Building and Running

This project is self-contained using Gradle Kotlin DSL and a local version catalog (`gradle/libs.versions.toml`). Model and sample audio downloads are verified via SHA-256 in `app/download_model.gradle.kts`.

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17+).
* An Android device or emulator running API level 28 (Android 9.0) or higher.

### Build and Test via Gradle

```bash
cd examples/audio_classifier/android
./gradlew :app:verifyModels :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

### Models and Sample Audio Used

Model and sample audio assets are automatically downloaded and verified in `app/src/main/assets/` by `app/download_model.gradle.kts`:

* `yamnet.tflite`: YAMNet audio event classification model (16 kHz mono).
* `speech_16000_hz_mono.wav`: Bundled 16 kHz mono sample clip for immediate offline classification and instrumented testing.
