# MediaPipe Tasks Text Embedder Android Demo

### Overview

This Android app compares the semantic similarity of two text inputs on-device
using the MediaPipe `TextEmbedder` task, with a choice of
[EmbeddingGemma 300M](https://huggingface.co/litert-community/MediaPipe-TextEmbedder-EmbeddingGemma-300M/resolve/main/embedding_gemma.task)
(768d, with optional `TextFormatContext` task prompts such as Semantic Similarity,
Query → Document retrieval, Q&A, and Clustering),
[MobileBERT](https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite)
(512d), or
[Universal Sentence Encoder](https://huggingface.co/litert-community/MediaPipe-TextEmbedder-Universal-Sentence-Encoder/resolve/main/universal_sentence_encoder.tflite)
(100d) embedding models, plus configurable CPU / GPU delegate, L2 normalization, and scalar quantization options in settings.

![Text Embedder Demo](textembedder.png?raw=true "Text Embedder Demo")

Models are downloaded automatically during Gradle build via
`app/download_models.gradle.kts` with SHA-256 checksum verification (`./gradlew verifyModels`).
Shared Material 3 UI components and test utilities live in
[`android-common/`](../../../android-common).

## Build and run

### Prerequisites

* **[Android Studio](https://developer.android.com/studio/index.html)** (JDK 17 or newer).
* An Android device or emulator running API level 28 (Android 9.0) or higher.

### Building from Android Studio

1. Open `examples/text_embedder/android` in Android Studio.
2. Wait for Gradle sync to finish (dependencies are resolved from
   `gradle/libs.versions.toml`).
3. Click **Run** to deploy to your device or emulator.

### Building and testing from the command line

```bash
cd examples/text_embedder/android

# Download and verify model checksums, build the debug APK, and run unit tests
./gradlew :app:verifyModels :app:assembleDebug :android-common:testing:testDebugUnitTest

# Run real-model quality and end-to-end Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```
