# MediaPipe Text Proofreader iOS Demo

This sample app demonstrates how to use the MediaPipe Text Proofreader Task on iOS. It proofreads input text, correcting grammatical and spelling errors.

## Prerequisites

-   A physical iOS device (iPhone or iPad) with iOS 15.0 or later.
-   Xcode 14.1 or later.

## Setup

1.  **Download the model:**
    Run the following script to download the text proofreader model.
    ```bash
    sh RunScripts/download_models.sh
    ```

2.  **Open the project:**
    Open `TextProofreader.xcodeproj` in Xcode. Xcode will automatically resolve
    and download the Swift Package dependencies from
    `https://github.com/google-ai-edge/mediapipe`.

3.  **Run the app:**
    Select your physical iOS device as the target and run the app.

## How it works

The app uses the `MediaPipeTasksText` library to perform text proofreading.

### TextProofreader Options

The `TextProofreaderOptions` allows you to configure:
-   `modelAssetPath`: Path to the LiteRT-LM model.
-   `maxTokens`: Maximum number of tokens for the task.

### Inference

The app demonstrates both synchronous and streaming proofreading:

-   **Synchronous:** `proofread(_:)` returns the complete proofread text and a list of corrections.
-   **Streaming:** `proofreadStreaming(_:completion:)` returns the proofread text incrementally as it's being generated.
