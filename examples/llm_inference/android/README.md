# MediaPipe LLM Inference Android Demo

### Overview

This is a sample app that demonstrates how to use the LLM Inference API to run common text-to-text generation tasks like information retrieval, email drafting, and document summarization.

This application must be run on a physical Android device to take advantage of the device GPU.

![LLM Inference Demo](llm_inference.png)

## Build the demo using Android Studio

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio/index.html)**
    IDE. This sample has been tested on Android Studio Hedgehog.

*   A physical Android device with a minimum OS version of SDK 24 (Android 7.0 -
    Nougat) with developer mode enabled.

### Building

*   Open Android Studio. From the Welcome screen, select Open an existing
    Android Studio project.

*   From the Open File or Project window that appears, navigate to and select
    the mediapipe/examples/llm_inference/android directory. Click OK. You may
    be asked if you trust the project. Select Trust.

*   If it asks you to do a Gradle Sync, click OK.

*   With your Android device connected to your computer and developer mode
    enabled, click on the green Run arrow in Android Studio.

### Models used

You can download one of the [compatible models](https://developers.google.com/mediapipe/solutions/genai/llm_inference#models).

Once you download it, place it under the path defined as MODEL_PATH in InferenceModel on the Android device
 (eg. /data/local/tmp/llm/model.bin).

The easiest way to do that would be to use Android Studio's [Device Explorer](https://developer.android.com/studio/debug/device-file-explorer).

For more details, see the [models section](https://developers.google.com/mediapipe/solutions/genai/llm_inference/android#model) in the LLM Inference guide for Android.
