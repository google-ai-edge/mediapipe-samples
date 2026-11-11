# MediaPipe Tasks Face Landmark Detection Android Demo

## Overview

This sample demonstrates the MediaPipe [Face Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/android) task in a Jetpack Compose Material 3 app. It detects 478 3D face landmarks and 52 ARKit-compatible facial expression blendshape scores across:

- **Camera:** Live front/back CameraX feed with real-time face mesh connector overlays and inference latency telemetry.
- **Gallery:** Bundled portrait photo (`Try sample`) as well as user-selected images and videos from the device gallery, including top expression blendshape scores.

In the **Settings** sheet, you can switch hardware **Delegate** (`CPU` or `GPU`), change **Max faces** (`1` or `2`), and tune detection, tracking, and presence confidence thresholds.

![Face Landmark Detection Demo](facelandmarker.png?raw=true "Face Landmark Detection Demo")

## Prerequisites

- **[Android Studio](https://developer.android.com/studio)** (with Android SDK 36 and JDK 17+)
- An Android device or emulator running Android 7.0 (API 24) or higher

## Build and Run

The Gradle script [app/download_tasks.gradle.kts](app/download_tasks.gradle.kts) automatically downloads and SHA-256 verifies `face_landmarker.task` into `app/src/main/assets/` before building:

```bash
./gradlew :app:verifyModels :app:assembleDebug
```

To run the shared unit tests and instrumented real-model + UI tests:

```bash
./gradlew :android-common:testing:testDebugUnitTest :app:connectedDebugAndroidTest
```
