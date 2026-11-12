# MediaPipe Tasks Object Detection Android Demo

## Overview

This sample demonstrates the MediaPipe [Object Detector](https://ai.google.dev/edge/mediapipe/solutions/vision/object_detector/android) task in a Jetpack Compose Material 3 app (consolidating the legacy Views and Jetpack Compose Android variants). It supports:

- **Camera:** Live front/back CameraX feed with real-time bounding boxes, category labels, and inference telemetry.
- **Gallery:** Bundled dining sample (`Try sample`) as well as user-selected images and videos from the device gallery.

In the **Settings** sheet, you can switch models (**EfficientDet Lite0** and **EfficientDet Lite2**), toggle hardware **Delegate** (`CPU` or `GPU`), change **Max results** (`1`, `3`, `5`), and tune the **Confidence threshold**.

![Object Detection Demo](objectdetection.png?raw=true "Object Detection Demo")

## Prerequisites

- **[Android Studio](https://developer.android.com/studio)** (with Android SDK 36 and JDK 17+)
- An Android device or emulator running Android 9.0 (API 28) or higher

## Build and Run

The Gradle script [app/download_models.gradle.kts](app/download_models.gradle.kts) automatically downloads and SHA-256 verifies `efficientdet-lite0.tflite` and `efficientdet-lite2.tflite` into `app/src/main/assets/` before building:

```bash
./gradlew :app:verifyModels :app:assembleDebug
```

To run the shared unit tests and instrumented real-model + UI tests:

```bash
./gradlew :android-common:testing:testDebugUnitTest :app:connectedDebugAndroidTest
```
