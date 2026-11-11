# MediaPipe Tasks Pose Landmark Detection Android Demo

## Overview

This sample demonstrates the MediaPipe [Pose Landmarker](https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android) task in a Jetpack Compose Material 3 app. It detects 33 3D human body pose landmarks across:

- **Camera:** Live front/back CameraX feed with real-time pose skeleton overlays and inference latency telemetry.
- **Gallery:** Bundled outdoor pose sample (`Try sample`) as well as user-selected images and videos from the device gallery.

In the **Settings** sheet, you can switch models (**Pose Lite**, **Pose Full**, and **Pose Heavy**), toggle hardware **Delegate** (`CPU` or `GPU`), and tune detection, tracking, and presence confidence thresholds.

![Pose Landmark Detection Demo](pose_landmarker.png?raw=true "Pose Landmark Detection Demo")

## Prerequisites

- **[Android Studio](https://developer.android.com/studio)** (with Android SDK 36 and JDK 17+)
- An Android device or emulator running Android 7.0 (API 24) or higher

## Build and Run

The Gradle script [app/download_tasks.gradle.kts](app/download_tasks.gradle.kts) automatically downloads and SHA-256 verifies `pose_landmarker_lite.task`, `pose_landmarker_full.task`, and `pose_landmarker_heavy.task` into `app/src/main/assets/` before building:

```bash
./gradlew :app:verifyModels :app:assembleDebug
```

To run the shared unit tests and instrumented real-model + UI tests:

```bash
./gradlew :android-common:testing:testDebugUnitTest :app:connectedDebugAndroidTest
```
