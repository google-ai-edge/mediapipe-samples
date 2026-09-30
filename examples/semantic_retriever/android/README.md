# MediaPipe Tasks Semantic Retriever Android Demo

### Overview

This app demonstrates on-device **semantic image search** with the MediaPipe
`tasks-retrieval` library. Ten sample images are embedded with
[EmbeddingGemma V2](https://ai.google.dev/gemma) running entirely on the device, stored in a
vector store, and then retrieved by meaning — searching for *"a piece of fruit"* returns the
banana and the apple even though neither the query nor the images contain that text.

The sample supports both vector store backends and lets you switch between them at runtime:

| Backend | Class | Notes |
| --- | --- | --- |
| AppSearch | `AppSearchVectorStore` | Backed by Android's on-device AppSearch (Icing) index |
| SQLite | `SqliteVectorStore` | Backed by a local SQLite database with a vector extension |

Switching backends closes the current store, opens the other one, and clears it, so you can
index and query each backend independently. The embedding engine itself is loaded once and
shared by both.

This application should be run on a physical Android device (arm64).

## Build the demo using Android Studio

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio/index.html)** IDE.

*   A physical Android device with a minimum OS version of SDK 24 (Android 7.0 - Nougat) with
    developer mode enabled.

### Model

The app uses the [EmbeddingGemma 2 LiteRT-LM model][model-url] at:

```
app/src/main/assets/embedding_gemma_v2_q4c_multisig.litertlm
```

`app/download.gradle` automatically downloads the model from
<https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm> at build time.
If the model is not yet live at that URL or requires authentication, download it manually and place
it at the path above.

On first launch the model is copied from assets into `filesDir` and opened via a
`ParcelFileDescriptor` passed to `BaseOptions.setModelAssetFileDescriptor`. Expect a few seconds of
load time on the first start.

### Building

*   Open Android Studio. From the Welcome screen, select Open an existing Android Studio project.

*   Navigate to and select the `mediapipe/examples/semantic_retriever/android` directory. Click OK.

*   If it asks you to do a Gradle Sync, click OK.

*   With your Android device connected and developer mode enabled, click the green Run arrow.

### Using the app

1.  Pick a vector store (AppSearch or SQLite).
2.  Tap **Index 10 sample images** and wait for the progress bar to complete (~15s).
3.  Type a query, or tap one of the suggestion chips, to retrieve the closest images ranked by
    cosine similarity.

[model-url]: https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm
