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

The app expects the EmbeddingGemma V2 LiteRT-LM model at:

```
app/src/main/assets/embedding_gemma_v2_q4c_multisig.litertlm
```

The model is not yet published to a public endpoint, so it has to be placed there manually. Once
it is available on Hugging Face, `app/download.gradle` can fetch it at build time — set
`HF_MODEL_URL` and uncomment the `preBuild.dependsOn downloadEmbeddingModel` line.

On first launch the model is copied from assets into `filesDir`, because the LiteRT JNI layer
opens it by absolute path. Expect a few seconds of load time on the first start.

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
