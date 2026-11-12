# MediaPipe Tasks Image Classification Android Demo

### Overview

This Android app classifies images and live camera frames on-device using the MediaPipe
`ImageClassifier` task, with a choice of
[EfficientNet Lite0](https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite)
or
[EfficientNet Lite2](https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite2/float32/1/efficientnet_lite2.tflite)
models.

![Image Classification Demo](imageclassifier.png?raw=true "Image Classification Demo")

The models are downloaded automatically during Gradle build via
`app/download_models.gradle.kts` with SHA-256 checksum verification (`./gradlew verifyModels`).
Shared Material 3 UI components and test utilities live in
[`android-common/`](../../../android-common).

## Build and run

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17 or newer).
* An Android device or emulator running API level 28 (Android 9.0) or higher. The
  Gallery tab includes a bundled sample photo for testing on emulators without a camera.

### Building from Android Studio

1. Open `examples/image_classification/android` in Android Studio.
2. Wait for Gradle sync to finish (dependencies are resolved from
   `gradle/libs.versions.toml`).
3. Click **Run** to deploy to your device or emulator.

### Building and testing from the command line

```bash
cd examples/image_classification/android

# Download and verify model checksums, build the debug APK, and run unit tests
./gradlew :app:verifyModels :app:assembleDebug :android-common:testing:testDebugUnitTest

# Run real-model quality and end-to-end Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```
