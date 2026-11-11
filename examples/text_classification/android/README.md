# MediaPipe Tasks Text Classification Android Demo

### Overview

This Android app classifies sentiment (`Positive` / `Negative`) for input text on-device
using the MediaPipe `TextClassifier` task, with a choice of
[MobileBERT](https://storage.googleapis.com/mediapipe-models/text_classifier/bert_classifier/float32/1/bert_classifier.tflite)
or
[Average WordVec](https://storage.googleapis.com/mediapipe-models/text_classifier/average_word_classifier/float32/1/average_word_classifier.tflite)
classification models.

![Text Classification Demo](textclassification.png?raw=true "Text Classification Demo")

Models are downloaded automatically during Gradle build via
`app/download_model.gradle.kts` with SHA-256 checksum verification (`./gradlew verifyModels`).
Shared Material 3 UI components and test utilities live in
[`android-common/`](../../../android-common).

## Build and run

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17 or newer).
* An Android device or emulator running API level 24 (Android 7.0) or higher.

### Building from Android Studio

1. Open `examples/text_classification/android` in Android Studio.
2. Wait for Gradle sync to finish (dependencies are resolved from
   `gradle/libs.versions.toml`).
3. Click **Run** to deploy to your device or emulator.

### Building and testing from the command line

```bash
cd examples/text_classification/android

# Download and verify model checksums, build the debug APK, and run unit tests
./gradlew :app:verifyModels :app:assembleDebug :android-common:testing:testDebugUnitTest

# Run real-model quality and end-to-end Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```
