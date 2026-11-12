# MediaPipe Tasks Text Summarizer Android Demo

### Overview

This sample app demonstrates on-device text summarization using the MediaPipe Tasks Text library (`com.google.mediapipe:tasks-text`) and Gemma 200M (`summarization_quant_200m_2modes.litertlm`).

* **Keypoints & TL;DR Modes:** Switch between bulleted keypoints (`Mode.KEYPOINTS`) and concise paragraph summaries (`Mode.TLDR`).
* **Unary & Streaming Summarization:** Generate full summaries or stream tokens live (`summarizeStreaming`).
* **Sample Passages & Token Settings:** Test curated sample passages and configure `maxNumTokens` (`512`, `1024`, `2048`).
* **Material 3 Compose UI:** Built with `:android-common:ui` theming and edge-to-edge layout.

![Text Summarizer Demo](text_summarizer.png?raw=true "Text Summarizer Demo")

## Building and Running

This project is self-contained using Gradle Kotlin DSL and a local version catalog (`gradle/libs.versions.toml`). Model downloads are verified via SHA-256 in `app/download_models.gradle.kts`.

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17+).
* An Android device or emulator running API level 28 (Android 9.0) or higher.

### Build and Test via Gradle

```bash
cd examples/text_summarizer/android
./gradlew :app:verifyModels :android-common:testing:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

### Models Used

Model assets are automatically downloaded and verified in `app/src/main/assets/` by `app/download_models.gradle.kts`:

* `summarization_quant_200m_2modes.litertlm`: Quantized 2-mode Gemma 200M LiteRT-LM summarization model.
