# MediaPipe Tasks Decision Maker iOS Demo

### Overview

This app demonstrates the `DecisionMaker` from the MediaPipe `MediaPipeTasksDecision` framework,
running on-device LLM decision-making with [EmbeddingGemma](https://ai.google.dev/gemma) (`.litertlm`).

The Decision API allows apps to perform rapid, structured, zero-shot evaluations across natural
language inputs without full generative token decode latency:

- **Boolean Decisions**: Evaluates binary predicates with calibrated probability `P(True)` and confidence score.
- **Categorical Choices**: Classifies queries across arbitrary candidate options with prior normalization.
- **Ordered Rubric Scoring**: Computes continuous expected rubric scores with calibrated certainty.
- **Polymorphic Multi-Question Schemas**: Evaluates complex, multi-faceted questions in a single forward pass.

### API in a nutshell

```swift
import MediaPipeTasksDecision

// 1. Initialize DecisionMaker with options
let options = DecisionMakerOptions(
  modelPath: modelPath,
  maxNumTokens: 128,
  delegate: .gpu
)
let decision = try DecisionMaker(options: options)

// 2. Evaluate a boolean question
let question = BooleanQuestion(
  prompt: "Is this message a credential phishing scam?",
  positiveDescription: "Phishing attack, credential harvest, or scam.",
  negativeDescription: "Legitimate corporate communication."
)
let result = try decision.evaluate(text: messageText, question: question)
print("Decision: \(result.value), Confidence: \(result.confidence)")
```

### CPU vs GPU

The sample supports both GPU (Metal) and CPU (XNNPACK) inference delegates. Use a physical iOS
device to experience hardware-accelerated GPU inference.

## Build the demo using Xcode

### Prerequisites

*   Xcode 15.0 or later.
*   A physical iOS device running iOS 17.0 or later (recommended), or the iOS simulator.

The MediaPipe dependency is resolved via Swift Package Manager using `MediaPipeTasksDecision`.

### Model

The app bundles a fast on-device test model (`embedding_gemma_v2_fake.litertlm`) for immediate
testing, and can also run the full EmbeddingGemma V2 LiteRT-LM model:

```
DecisionMaker/embeddinggemma-2-text-vision-440m.litertlm
```

The full model is downloaded automatically by `RunScripts/download_models.sh` when building in Xcode.

### Building

*   Open `DecisionMaker.xcodeproj` in Xcode.
*   Select your development team under **Signing & Capabilities**.
*   Select your connected device or simulator and click **Run**.
