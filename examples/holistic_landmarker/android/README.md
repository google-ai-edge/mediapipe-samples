# MediaPipe Tasks Holistic Landmark Detection Android Demo

### Overview

This is a camera app that continuously detects the body, hand, and face
landmarks in the frames seen by your device's back camera, using a
custom **task** file.

The task file is downloaded by a Gradle script when you build and run the app.
You don't need to do any additional steps to download task files into the
project explicitly unless you wish to use your own landmark detection task. If
you do use your own task file, place it into the app's assets/tasks directory.

This application should be run on physical Android devices with a back camera.

![Holistic Landmarker Demo](screenshot.jpg?raw=true "Holistic Landmarker Demo")

## Build the demo using Android Studio

### Prerequisites

* The **[Android Studio](https://developer.android.com/studio/index.html)** IDE.
  This sample has been tested on Android Studio Giraffe.

* A physical Android device with a minimum OS version of SDK 24 (Android 7.0 -
  Nougat) with developer mode enabled. The process of enabling developer mode
  may vary by device.

### Building

* Open Android Studio. From the Welcome screen, select Open an existing
  Android Studio project.

* From the Open File or Project window that appears, navigate to and select
  the mediapipe/examples/holistic_landmarker/android directory. Click OK. You
  may
  be asked if you trust the project. Select Trust.

* If it asks you to do a Gradle Sync, click OK.

* With your Android device connected to your computer and developer mode
  enabled, click on the green Run arrow in Android Studio.

### Models used

Downloading, extraction, and placing the models into the *assets/tasks* folder
is
managed automatically by the **app/build.gradle.kts** file.