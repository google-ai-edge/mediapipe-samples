# MediaPipe Text Summarizer iOS Demo

This sample app demonstrates how to use the MediaPipe Text Summarizer Task on iOS. It summarizes input text into a concise format, supporting different summarization modes like TL;DR and Keypoints.

## Prerequisites

-   A physical iOS device (iPhone or iPad) with iOS 15.0 or later.
-   Xcode 14.1 or later.

## Setup

1.  **Download the model:**
    Run the following script to download the text summarization model.
    ```bash
    sh RunScripts/download_models.sh
    ```

2.  **Open the project:**
    Open `TextSummarizer.xcodeproj` in Xcode. Xcode will automatically resolve
    and download the Swift Package dependencies from
    `https://github.com/google-ai-edge/mediapipe`.

3.  **Run the app:**
    Select your physical iOS device as the target and run the app.

## How it works

The app uses the `MediaPipeTasksText` library to perform text summarization. 

### TextSummarizer Options

The `TextSummarizerOptions` allows you to configure:
-   `modelAssetPath`: Path to the LiteRT-LM model.
-   `mode`: Summarization mode (`.tldr` or `.keyPoints`).
-   `maxTokens`: Maximum number of tokens for the task.

### Inference

The app demonstrates both synchronous and streaming summarization:

-   **Synchronous:** `summarize(text:)` returns the complete summary.
-   **Streaming:** `summarizeStreaming(text:completion:)` returns the summary incrementally as it's being generated.
