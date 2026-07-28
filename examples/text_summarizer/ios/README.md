# MediaPipe Text Summarizer iOS Demo

This sample app demonstrates how to use the MediaPipe Text Summarizer Task on iOS. It summarizes input text into a concise format, supporting different summarization modes like TL;DR and Keypoints.

## Prerequisites

-   A physical iOS device (iPhone or iPad) with iOS 15.0 or later.
-   Xcode 14.1 or later.
-   CocoaPods installed.

## Setup

1.  **Download the model:**
    Run the following script to download the text summarization model.
    ```bash
    sh RunScripts/download_models.sh
    ```

2.  **Install dependencies:**
    Run the following command in the `ios` directory to install the required CocoaPods.
    ```bash
    pod install
    ```

3.  **Open the project:**
    Open the `TextSummarizer.xcworkspace` file in Xcode.

4.  **Run the app:**
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
