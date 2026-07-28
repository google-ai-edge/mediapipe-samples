# MediaPipe Text Proofreader iOS Demo

This sample app demonstrates how to use the MediaPipe Text Proofreader Task on iOS. It proofreads input text, correcting grammatical and spelling errors.

## Prerequisites

-   A physical iOS device (iPhone or iPad) with iOS 15.0 or later.
-   Xcode 14.1 or later.
-   CocoaPods installed.

## Setup

1.  **Download the model:**
    Run the following script to download the text proofreader model.
    ```bash
    sh RunScripts/download_models.sh
    ```

2.  **Install dependencies:**
    Run the following command in the `ios` directory to install the required CocoaPods.
    ```bash
    pod install
    ```

3.  **Open the project:**
    Open the `TextProofreader.xcworkspace` file in Xcode.

4.  **Run the app:**
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
