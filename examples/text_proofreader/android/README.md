# MediaPipe Tasks Text Proofreader Android Demo

### Overview

This sample app demonstrates how to use the MediaPipe Text Proofreader Task on Android. It evaluates and corrects text, providing grammar and style corrections for the user.

The model file is downloaded automatically by a Gradle script during the build process, so you do not need to manually manage the model assets.

![Text Proofreader Demo](text_proofreader.gif?raw=true "Text Proofreader Demo")

## Build the demo using Android Studio

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio/index.html)** IDE.
*   A physical or emulated Android device with a minimum OS version of SDK 24 (Android 7.0).
*   Developer mode enabled on your physical device.

### Building

1.  Open Android Studio.
2.  Select **Open an existing Android Studio project**.
3.  Navigate to and select the `examples/text_proofreader/android` directory.
4.  Allow the Gradle sync to complete.
5.  With your device connected, click the green **Run** arrow in Android Studio.

### Models used

Downloading, extraction, and placement of the model into the `assets` folder are managed automatically by the `download_models.gradle` file.
