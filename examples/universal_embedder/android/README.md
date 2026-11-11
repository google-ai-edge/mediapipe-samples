# MediaPipe Universal Embedder — Android Sample

This Android sample app demonstrates on-device multimodal text and image embedding similarity using MediaPipe `UniversalEmbedder` (`com.google.mediapipe:tasks-retrieval`) and **EmbeddingGemma 2 Multimodal (440M)** (`embeddinggemma-2-text-vision-440m.litertlm`), built with Compose Material 3 (`:android-common:ui`).

## Features

* **Multimodal Slots (Text ↔ Image / Text ↔ Text / Image ↔ Image):** Toggle Item A and Item B independently between text input and image input (bundled sample photos or Photo Picker).
* **Cross-Modal Presets:** Quick pairs comparing an image against a matching text caption (`cute_cat.jpg` ↔ `"a cute fluffy cat"`), text pairs, and image pairs.
* **Cosine Similarity & Metrics:** Animated score gauge displaying cosine similarity, inference latency, vector dimensionality, and active accelerator (`GPU` with automatic `CPU` fallback).

![Universal Embedder Demo](universalembedder.png?raw=true "Universal Embedder Demo")

## Model

The LiteRT-LM multimodal model is downloaded automatically during Gradle build and verified via SHA-256 in `app/download_models.gradle.kts`:

* **EmbeddingGemma 2 Text+Vision 440M** (`embeddinggemma-2-text-vision-440m.litertlm`): https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm

## Build and Test

```bash
cd examples/universal_embedder/android

# Download and verify model checksum, run unit tests, and build debug APK
./gradlew :app:verifyModels :android-common:testing:testDebugUnitTest :app:assembleDebug

# Run real-model quality and end-to-end Compose UI tests on a connected device/emulator
./gradlew :app:connectedDebugAndroidTest
```
