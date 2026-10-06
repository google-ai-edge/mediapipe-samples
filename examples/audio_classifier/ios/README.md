# MediaPipe Audio Classifier iOS Demo

This sample app demonstrates how to use the MediaPipe Audio Classifier Task on iOS with the pre-trained YamNet model. It classifies live audio from the device microphone into 521 audio event categories.

## Prerequisites

-   A physical iOS device (iPhone or iPad) with iOS 15.0 or later (microphone required for live audio streaming).
-   Xcode 14.1 or later.

## Setup

1.  **Download the model and sample audio:**
    Run the following script to download the YamNet model and sample audio file:
    ```bash
    sh RunScripts/download_models.sh
    ```

2.  **Open the project:**
    Open `AudioClassifier.xcodeproj` in Xcode. Xcode will automatically resolve
    and download the Swift Package dependencies from
    `https://github.com/google-ai-edge/mediapipe`.

3.  **Run the app:**
    Select your physical iOS device as the target and build and run the app. Grant microphone permissions when prompted.

## How it works

The app uses the `MediaPipeTasksAudio` library to perform real-time audio classification.

### AudioClassifier Options

The `AudioClassifierOptions` allows you to configure:
-   `baseOptions.modelAssetPath`: Path to the YamNet TFLite model.
-   `runningMode`: `.audioStream` for live audio or `.audioClips` for pre-recorded files.
-   `scoreThreshold`: Confidence threshold to filter classification results.
-   `maxResults`: Maximum number of top classification categories to return.
-   `audioClassifierStreamDelegate`: Delegate receiving asynchronous classification callbacks.

### Inference

-   Audio is captured via `AudioRecord` at 16 kHz mono.
-   Audio chunks of 0.975s (the required YamNet window length) are fed into `classifyAsync(audioBlock:timestampInMilliseconds:)`.
-   Results are delivered asynchronously via the `audioClassifier(_:didFinishClassification:timestampInMilliseconds:error:)` delegate method and displayed on the UI in real time.
