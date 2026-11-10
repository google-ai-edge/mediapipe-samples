# MediaPipe Tasks Image Embedder Android Demo

### Overview

This Android app computes feature embeddings and cosine similarity between two
images on-device using the MediaPipe `ImageEmbedder` task, with a choice of
[MobileNet V3 Small](https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite)
or
[MobileNet V3 Large](https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/1/mobilenet_v3_large.tflite)
models.

![Image Embedder Demo](imageembedder.png?raw=true "Image Embedder Demo")

Models are downloaded automatically during Gradle build via
`app/download_models.gradle.kts` with SHA-256 checksum verification (`./gradlew verifyModels`).
Shared Material 3 UI components and test utilities live in
[`android-common/`](../../../android-common).

## Build and run

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17 or newer).
* An Android device or emulator running API level 24 (Android 7.0) or higher.

### Building from Android Studio

1. Open `examples/image_embedder/android` in Android Studio.
2. Wait for Gradle sync to finish (dependencies are resolved from
   `gradle/libs.versions.toml`).
3. Click **Run** to deploy to your device or emulator.

### Building and testing from the command line

```bash
cd examples/image_embedder/android

# Download and verify model checksums, build the debug APK, and run unit tests
./gradlew :app:verifyModels :app:assembleDebug :android-common:testing:testDebugUnitTest

# Run real-model quality and end-to-end Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```
