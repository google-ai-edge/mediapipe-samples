# MediaPipe Tasks Semantic Retriever iOS Demo

### Overview

This app demonstrates on-device **semantic image search** with the MediaPipe
`MediaPipeTasksRetrieval` framework. Ten sample images are embedded with
[EmbeddingGemma V2](https://ai.google.dev/gemma) running entirely on the device, stored in a
vector store, and then retrieved by meaning — searching for *"a piece of fruit"* returns the
banana and the apple even though neither the query nor the images contain that text.

iOS ships a single vector store implementation, `SqliteVectorStore`, backed by a local SQLite
database with a vector extension. (The Android sample additionally offers `AppSearchVectorStore`,
which has no iOS counterpart.) The database lives in the app's `Documents` directory and is
emptied on launch, so every run starts from a clean index.

### API in a nutshell

```swift
let options = UniversalEmbedderOptions()
options.baseOptions.modelAssetPath = modelPath
options.textDelegate = .GPU    // per-modality accelerators
options.visionDelegate = .GPU
options.l2Normalize = true
let embedder = try UniversalEmbedder(options: options)

let store = SqliteVectorStore(
  databasePath: databasePath,
  embeddingDimension: MPPSemanticRetrieverDefaultEmbeddingDimension)
let components = try SemanticRetrieverComponents(
  vectorStore: store, chunker: nil, providers: [embedder])
let retriever = try SemanticRetriever(components: components)

try retriever.insertImage(withId: "red_apple", filePath: path)
let results = try retriever.retrieve(withText: "a piece of fruit", topK: 5)
```

`insertDocument(withId:text:)`, `insertAudio(withId:filePath:)` and
`insertContent(withId:parts:)` round out the API for text, audio and mixed multimodal records.

### CPU vs GPU

The card at the top has a GPU switch. The accelerator is baked into the engine, so flipping it
rebuilds the embedder and reopens the store. Use a physical device to see the GPU path: the
simulator has no Metal accelerator for LiteRT and silently falls back to XNNPack on the CPU.

## Build the demo using Xcode

### Prerequisites

*   Xcode 14.1 or later.

*   A physical iOS device running iOS 15.0 or later (recommended), or the iOS simulator.

The MediaPipe dependency is resolved with Swift Package Manager from
`https://github.com/google-ai-edge/mediapipe`.
`MediaPipeTasksRetrieval` requires MediaPipe 1.1.0 or newer.

### Model

The app uses the EmbeddingGemma V2 LiteRT-LM model at:

```
SemanticRetriever/embeddinggemma-2-text-vision-440m.litertlm
```

The model (~370 MB) is downloaded automatically by `RunScripts/download_models.sh` when you build
the app in Xcode. The first build and launch take a while.

### Building

*   Open `SemanticRetriever.xcodeproj` in Xcode and let it resolve the Swift package.

*   Select your device or a simulator and press Run.

### Using the app

1.  Wait for the status line to read *Ready on CPU · store is empty*.
2.  Tap **Index 10 sample images** and wait for the progress bar to complete.
3.  Type a query, or tap one of the suggestion chips, to retrieve the closest images ranked by
    cosine similarity.

### Related samples

*   `examples/universal_embedder` uses the same embedder directly, without a vector store, to
    compare any two inputs.
