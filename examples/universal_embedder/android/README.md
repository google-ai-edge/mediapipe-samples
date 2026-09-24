# MediaPipe Tasks Universal Embedder Android Demo

### Overview

This app demonstrates the `UniversalEmbedder` from the MediaPipe `tasks-retrieval` library,
running [EmbeddingGemma V2](https://ai.google.dev/gemma) fully on-device.

The embedder projects **text, images and audio into a single shared vector space**. That is what
makes cross-modal comparison possible: the sentence *"a yellow piece of fruit"* and a photo of a
banana end up close together, even though one is words and the other is pixels.

The demo gives you two inputs, each of which can be either text or one of ten bundled images, and
reports the cosine similarity between their embeddings along with the vector size and how long
each embedding took.

Measured on a Pixel-class device with an Adreno 750:

| Input A | Input B | Cosine similarity |
| --- | --- | --- |
| "a yellow piece of fruit" | banana photo | 0.6905 |
| "a yellow piece of fruit" | kitten photo | 0.5061 |

### API in a nutshell

```kotlin
val options = UniversalEmbedderOptions.builder()
    .setBaseOptions(BaseOptions.builder().setModelAssetPath(modelPath).build())
    .setTextDelegate(Delegate.GPU)    // per-modality accelerators
    .setVisionDelegate(Delegate.GPU)
    .setL2Normalize(true)
    .build()

val embedder = UniversalEmbedder.createFromOptions(context, options)

val text = embedder.embedText("a yellow piece of fruit").embeddings().first()
val image = embedder.embedImage(BitmapImageBuilder(bitmap).build()).embeddings().first()

val score = UniversalEmbedder.cosineSimilarity(text, image)
```

`embedAudio(AudioData)` and `embedContent(List<Object>)` round out the API for audio and mixed
multimodal input.

### CPU vs GPU

The header has a GPU switch. On GPU the vision towers and text encoders are delegated to LiteRT
OpenCL; on CPU they run through XNNPack. Note that `AndroidManifest.xml` declares
`libOpenCL.so` via `<uses-native-library>` — without it the delegate falls back to a WebGPU path
that is unstable on some Qualcomm drivers.

## Build the demo using Android Studio

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio/index.html)** IDE.

*   A physical Android device with a minimum OS version of SDK 24 (Android 7.0 - Nougat) with
    developer mode enabled.

### Model

The app expects the EmbeddingGemma V2 LiteRT-LM model at:

```
app/src/main/assets/embedding_gemma_v2_q4c_multisig.litertlm
```

The model is not yet published to a public endpoint, so it has to be placed there manually. On
first launch it is copied from assets into `filesDir`, because the LiteRT JNI layer opens it by
absolute path.

### Building

*   Open Android Studio. From the Welcome screen, select Open an existing Android Studio project.

*   Navigate to and select the `mediapipe/examples/universal_embedder/android` directory. Click OK.

*   If it asks you to do a Gradle Sync, click OK.

*   With your Android device connected and developer mode enabled, click the green Run arrow.

### Related samples

*   `examples/semantic_retriever` builds on the same embedder, adding a vector store and
    retrieval on top of it for semantic image search.
