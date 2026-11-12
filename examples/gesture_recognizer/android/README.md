# MediaPipe Tasks Gesture Recognizer Android Demo

## Overview

This sample demonstrates the MediaPipe [Gesture Recognizer](https://ai.google.dev/edge/mediapipe/solutions/vision/gesture_recognizer/android) task in a Jetpack Compose Material 3 app. It classifies hand gestures and renders 21-point hand landmark skeletons across:

- **Camera:** Live front/back CameraX feed with real-time hand landmark overlays, recognized gesture name, and inference latency telemetry.
- **Gallery:** Bundled thumbs-up photo (`Try sample`) as well as user-selected images and videos from the device gallery.

In the **Settings** sheet, you can switch hardware **Delegate** (`CPU` or `GPU`) and tune detection, tracking, and presence confidence thresholds.

![Gesture Recognizer Demo](gesturerec.png?raw=true "Gesture Recognizer Demo")

## Prerequisites

- **[Android Studio](https://developer.android.com/studio)** (with Android SDK 36 and JDK 17+)
- An Android device or emulator running Android 9.0 (API 28) or higher

## Build and Run

The Gradle script [app/download_tasks.gradle.kts](app/download_tasks.gradle.kts) automatically downloads and SHA-256 verifies `gesture_recognizer.task` into `app/src/main/assets/` before building:

```bash
./gradlew :app:verifyModels :app:assembleDebug
```

To run the shared unit tests and instrumented real-model + UI tests:

```bash
./gradlew :android-common:testing:testDebugUnitTest :app:connectedDebugAndroidTest
```
