# Air Drawing with MediaPipe Hands in Android

> Using hand landmark recognition to draw on the screen.

The app uses [MediaPipe's Hand Landmark Detection](https://developers.google.com/mediapipe/solutions/vision/hand_landmarker/android) 
to detect specific landmarks on the user's hand and uses them to draw strokes on the screen.

The app's UI is written with [Jetpack Compose](https://developer.android.com/jetpack/compose) and uses [CameraX](https://developer.android.com/training/camerax) 
for displaying the camera preview.

![App Demo](https://user-images.githubusercontent.com/106607805/227761872-938aacfd-03ca-4d2e-b788-c0e6fe954b16.gif)

> Watch the [video on YouTube](https://youtu.be/hvw4MFvplok)
> *The demo is recorded on an Android 12 device with a 32-bit Samsung Exynos 850 processor (4GB RAM)

## Build the demo using Android Studio

### Prerequisites

* The **[Android Studio](https://developer.android.com/studio/index.html)** IDE. This sample has been tested on Android Studio Dolphin.

* A physical Android device with a minimum OS version of SDK 24 (Android 7.0 - 
Nougat) with developer mode enabled. The process of enabling developer mode may vary by device.

### Building

* Clone the repository on your system with,

```
git clone --depth=1 https://github.com/shubham0204/AirDrawing_with_Mediapipe_Android
```

* Open the resulting folder in Android Studio. You may be asked if you trust the project. Select Trust.

* If it asks you to do a Gradle Sync, click OK.

* With your Android device connected to your computer and developer mode enabled, click on the green Run arrow in
Android Studio.

### Models used

Downloading, extraction, and placing the models into the `assets` folder is
managed automatically by the `app/download_tasks.gradle` file.
