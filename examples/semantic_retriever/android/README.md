# MediaPipe Semantic Retriever — Android Sample

This Android sample app demonstrates local semantic image search using MediaPipe `SemanticRetriever` (`com.google.mediapipe:tasks-retrieval`) and **EmbeddingGemma 2 Multimodal (440M)** (`embeddinggemma-2-text-vision-440m.litertlm`), built with Compose Material 3 (`:android-common:ui`).

## Features

* **Automatic Sample Gallery Indexing:** Indexes 10 bundled photos into the local vector store (`SQLite` or `AppSearch`), plus Photo Picker support for adding custom photos.
* **Natural-Language Search:** Query indexed photos with freeform text or quick suggestion chips (`Beach`, `Cat`, `Mountain`, `Coffee`, `Sports car`, `Apple`).
* **Vector Store & Hardware Settings:** Switch between `SQLite` (`SqliteVectorStore`) and `AppSearch` (`AppSearchVectorStore`) backends and `GPU` (with automatic `CPU` fallback) / `CPU` delegates.

![Semantic Retriever Demo](semanticretriever.png?raw=true "Semantic Retriever Demo")

## Model

The LiteRT-LM multimodal model is downloaded automatically during Gradle build and verified via SHA-256 in `app/download_models.gradle.kts`:

* **EmbeddingGemma 2 Text+Vision 440M** (`embeddinggemma-2-text-vision-440m.litertlm`): https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm

## Build and Test

```bash
cd examples/semantic_retriever/android

# Download and verify model checksum, run unit tests, and build debug APK
./gradlew :app:verifyModels :android-common:testing:testDebugUnitTest :app:assembleDebug

# Run real-model quality and end-to-end Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```
