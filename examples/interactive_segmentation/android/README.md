# MediaPipe Tasks Interactive Image Segmentation Android Demo

### Overview

This is a camera app that accepts an image and a point within that image, then 
attempts to determine the surrounding area of significance.

The model files are downloaded by a Gradle script when you build and run the
app. You don't need to do any steps to download TFLite models into the project
explicitly unless you wish to use your own models. If you do use your own
models, place them into the app's *assets* directory.

This application should be run on a physical Android device to take advantage of
the physical camera, though the gallery option will allow you to use an emulator
for opening locally stored files.

![Interactive Segmenter Demo](interactive_segmenter.png?raw=true "Interactive Segmenter Demo")

## Build the demo using Android Studio

### Prerequisites

* The **[Android Studio](https://developer.android.com/studio/index.html)**
  IDE. This sample has been tested on Android Studio Dolphin.

* A physical Android device with a minimum OS version of SDK 24 (Android 7.0 -
  Nougat) with developer mode enabled. The process of enabling developer mode
  may vary by device. You may also use an Android emulator with more limited
  functionality.

### Building

* Open Android Studio. From the Welcome screen, select Open an existing
  Android Studio project.

* From the Open File or Project window that appears, navigate to and select
  the mediapipe/examples/interactive_segmentation/android directory. Click OK. You may
  be asked if you trust the project. Select Trust.

* If it asks you to do a Gradle Sync, click OK.

* With your Android device connected to your computer and developer mode
  enabled, click on the green Run arrow in Android Studio.

### Models used

Downloading, extraction, and placing the models into the *assets* folder is
managed automatically by the **download.gradle** file.
