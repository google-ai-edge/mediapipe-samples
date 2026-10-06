# MediaPipe Language Detector iOS Demo

This sample app demonstrates how to use the MediaPipe Language Detector Task on iOS. It predicts the language of input text along with confidence probabilities.

## Prerequisites

-   A physical iOS device (iPhone or iPad) or iOS Simulator with iOS 15.0 or later.
-   Xcode 14.1 or later.

## Setup

1.  **Download the model:**
    Run the following script to download the language detection model.
    ```bash
    sh RunScripts/download_models.sh
    ```

2.  **Open the project:**
    Open `LanguageDetector.xcodeproj` in Xcode. Xcode will automatically resolve
    and download the Swift Package dependencies from
    `https://github.com/google-ai-edge/mediapipe`.

3.  **Run the app:**
    Select your target device or simulator and run the app.

## How it works

The app uses the `MediaPipeTasksText` library to perform language detection.

### LanguageDetector Options

The `LanguageDetectorOptions` allows you to configure:
-   `baseOptions.modelAssetPath`: Path to the TFLite model.
-   `scoreThreshold`: Prediction confidence threshold to filter results.
-   `maxResults`: Maximum number of top language predictions to return.

### Inference

The app performs language detection synchronously on a background thread:
-   `detect(text:)` returns a `LanguageDetectorResult` containing an array of `LanguagePrediction` objects (`languageCode` and `probability`).
