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
val embedder =
    ParcelFileDescriptor.open(modelFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        val options = UniversalEmbedderOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetFileDescriptor(pfd.fd)
                    .setDelegate(Delegate.GPU)
                    .build()
            )
            .setL2Normalize(true)
            .build()

        UniversalEmbedder.createFromOptions(context, options)
    }

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

The app uses the [EmbeddingGemma 2 (Text + Vision 440M) LiteRT-LM model][model-url] at:

```
app/src/main/assets/embeddinggemma-2-text-vision-440m.litertlm
```

`app/download.gradle` automatically downloads the model from
<https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm>
at build time. If the model is not yet live at that URL or requires authentication, download it
manually and place it at the path above.

Available EmbeddingGemma 2 LiteRT-LM models:

*   **Text (270M)**:
    <https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm>
*   **Text + Vision (440M)**:
    <https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm>
*   **Text + Vision + Audio (740M)**:
    <https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm>

On first launch the model is copied from assets into `filesDir` and opened via a
`ParcelFileDescriptor` passed to `BaseOptions.setModelAssetFileDescriptor`.

### Building

*   Open Android Studio. From the Welcome screen, select Open an existing Android Studio project.

*   Navigate to and select the `mediapipe/examples/universal_embedder/android` directory. Click OK.

*   If it asks you to do a Gradle Sync, click OK.

*   With your Android device connected and developer mode enabled, click the green Run arrow.

### Related samples

*   `examples/semantic_retriever` builds on the same embedder, adding a vector store and
    retrieval on top of it for semantic image search.

[model-url]: https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm
