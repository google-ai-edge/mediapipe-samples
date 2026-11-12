# MediaPipe Tasks Text Proofreader Android Demo

### Overview

This sample app demonstrates on-device proofreading and grammar/spelling correction using the MediaPipe Tasks Text library (`com.google.mediapipe:tasks-text`) and Gemma 200M (`proofread_quant_200m.litertlm`).

* **Unary & Streaming Proofreading:** Run either one-shot proofreading or live token streaming (`proofreadStreaming`).
* **Highlighted Inline Diff:** Displays insertions and deletions color-coded in light and dark themes.
* **Preset Examples & Token Settings:** Test realistic grammar/spelling examples with one tap and configure `maxNumTokens` (`512`, `1024`, `2048`).
* **Material 3 Compose UI:** Built with `:android-common:ui` theming and edge-to-edge layout.

![Text Proofreader Demo](text_proofreader.png?raw=true "Text Proofreader Demo")

## Building and Running

This project is self-contained using Gradle Kotlin DSL and a local version catalog (`gradle/libs.versions.toml`). Model downloads are verified via SHA-256 in `app/download_models.gradle.kts`.

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17+).
* An Android device or emulator running API level 28 (Android 9.0) or higher.

### Build and Test via Gradle

```bash
cd examples/text_proofreader/android
./gradlew :app:verifyModels :android-common:testing:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

### Models Used

Model assets are automatically downloaded and verified in `app/src/main/assets/` by `app/download_models.gradle.kts`:

* `proofread_quant_200m.litertlm`: Quantized Gemma 200M LiteRT-LM proofreading model.
