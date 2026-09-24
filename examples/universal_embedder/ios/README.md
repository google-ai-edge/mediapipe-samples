# MediaPipe Tasks Universal Embedder iOS Demo

### Overview

This app demonstrates the `UniversalEmbedder` from the MediaPipe `MediaPipeTasksRetrieval`
framework, running [EmbeddingGemma V2](https://ai.google.dev/gemma) fully on-device.

The embedder projects **text, images and audio into a single shared vector space**. That is what
makes cross-modal comparison possible: the sentence *"a yellow piece of fruit"* and a photo of a
banana end up close together, even though one is words and the other is pixels.

The demo gives you two inputs, each of which can be either text or one of ten bundled images, and
reports the cosine similarity between their embeddings along with the vector size and how long
each embedding took.

| Input A | Input B | Cosine similarity |
| --- | --- | --- |
| "a yellow piece of fruit" | banana photo | ≈ 0.69 |
| "a yellow piece of fruit" | kitten photo | ≈ 0.51 |

### API in a nutshell

```swift
let options = UniversalEmbedderOptions()
options.baseOptions.modelAssetPath = modelPath
options.textDelegate = .GPU    // per-modality accelerators
options.visionDelegate = .GPU
options.l2Normalize = true

let embedder = try UniversalEmbedder(options: options)

let text = try embedder.embed(text: "a yellow piece of fruit").embeddings[0]
let image = try embedder.embed(image: MPImage(uiImage: photo)).embeddings[0]

let score = try UniversalEmbedder.cosineSimilarity(embedding1: text, embedding2: image)
```

`embed(audio:)` and `embed(content:)` round out the API for audio and mixed multimodal input.

### CPU vs GPU

The card at the top has a GPU switch. The accelerator is baked into the engine, so flipping it
rebuilds the embedder. Use a physical device to see the GPU path: the simulator has no Metal
accelerator for LiteRT and silently falls back to XNNPack on the CPU.

## Build the demo using Xcode

### Prerequisites

*   Xcode 14.1 or later.

*   A physical iOS device running iOS 15.0 or later (recommended), or the iOS simulator.

The MediaPipe dependency is resolved with Swift Package Manager from
`https://github.com/google-ai-edge/mediapipe`; no `pod install` step is needed.
`MediaPipeTasksRetrieval` requires MediaPipe 1.1.0 or newer.

### Model

The app uses the EmbeddingGemma V2 LiteRT-LM model at:

```
UniversalEmbedder/embeddinggemma-2-text-vision-440m.litertlm
```

The model (~370 MB) is downloaded automatically by `RunScripts/download_models.sh` when you build
the app in Xcode. The first build and launch take a while.

### Building

*   Open `UniversalEmbedder.xcodeproj` in Xcode and let it resolve the Swift package.

*   Select your device or a simulator and press Run.

### Using the app

1.  Wait for the status line to read *Ready · running on CPU*.
2.  Leave Input A as the preset sentence and Input B as the banana photo, or pick your own
    combination of text and images.
3.  Tap **Compare embeddings** to see the cosine similarity, the first dimensions of both
    vectors, and the per-input embedding latency.

### Related samples

*   `examples/semantic_retriever` builds on the same embedder, adding a vector store and
    retrieval on top of it for semantic image search.
