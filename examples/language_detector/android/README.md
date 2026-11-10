# MediaPipe Tasks Language Detector Android Demo

### Overview

This Android app detects the language of input text on-device across 100+ languages
using the MediaPipe `LanguageDetector` task and displays ranked BCP-47 language codes
and confidence scores.

![Language Detector Demo](language_detector.png?raw=true "Language Detector Demo")

The model is downloaded automatically during Gradle build via
`app/download_model.gradle` with SHA-256 checksum verification (`./gradlew verifyModels`).
Shared dependency versions, Material 3 UI components, and test utilities live in
[`android-common/`](../../../android-common).

## Build and run

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17 or newer).
* An Android device or emulator running API level 24 (Android 7.0) or higher.

### Building from Android Studio

1. Open `examples/language_detector/android` in Android Studio.
2. Wait for Gradle sync to finish (dependencies are resolved from
   `android-common/gradle/libs.versions.toml`).
3. Click **Run** to deploy to your device or emulator.

### Building and testing from the command line

```bash
cd examples/language_detector/android

# Download and verify model checksums, build the debug APK, and run unit tests
./gradlew :app:verifyModels :app:assembleDebug :android-common:testing:testDebugUnitTest

# Run real-model quality and end-to-end Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest

# Test against a specific MediaPipe Tasks version (canary / release verification)
./gradlew :app:connectedDebugAndroidTest -PmediapipeVersion=1.2.2
```
