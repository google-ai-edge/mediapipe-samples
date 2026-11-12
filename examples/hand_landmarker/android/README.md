# MediaPipe Tasks Hand Landmark Detection Android Demo

## Overview

This sample demonstrates the MediaPipe [Hand Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/hand_landmarker/android) task in a Jetpack Compose Material 3 app. It detects 21 3D hand-knuckle coordinates and handedness (`Left` / `Right`) across:

- **Camera:** Live front/back CameraX feed with real-time landmark skeleton overlays and inference latency telemetry.
- **Gallery:** Bundled sample photo (`Try sample`) as well as user-selected images and videos from the device gallery.

In the **Settings** sheet, you can switch the hardware **Delegate** (`CPU` or `GPU`), change **Max hands** (`1` or `2`), and tune detection, tracking, and presence confidence thresholds.

![Hand Landmark Detection Demo](handlandmarker.png?raw=true "Hand Landmark Detection Demo")

## Prerequisites

- **[Android Studio](https://developer.android.com/studio)** (with Android SDK 36 and JDK 17+)
- An Android device or emulator running Android 9.0 (API 28) or higher

## Build and Run

The Gradle script [app/download_tasks.gradle.kts](app/download_tasks.gradle.kts) automatically downloads and SHA-256 verifies `hand_landmarker.task` into `app/src/main/assets/` before building:

```bash
./gradlew :app:verifyModels :app:assembleDebug
```

To run the shared unit tests and instrumented real-model + UI tests:

```bash
./gradlew :android-common:testing:testDebugUnitTest :app:connectedDebugAndroidTest
```
