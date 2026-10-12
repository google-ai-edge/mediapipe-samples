# MediaPipe Tasks Pose Landmark Detection Android Demo

### Overview

This is a camera app that can detects landmarks on a person either from continuous camera frames seen by your device's back camera, an image, or a video from the device's gallery using a custom **task** file.

The task file is downloaded by a Gradle script when you build and run the app. You don't need to do any additional steps to download task files into the project explicitly unless you wish to use your own landmark detection task. If you do use your own task file, place it into the app's *assets* directory.

This application should be run on a physical Android device to take advantage of the camera.

![Pose Landmarker Demo](pose_landmarker.png?raw=true "Pose Landmarker Demo")
[Public domain video from Lance Foss](https://www.youtube.com/watch?v=KALIKOd1pbA)

## Build the demo using Android Studio

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio/index.html)** IDE. This sample has been tested on Android Studio Dolphin.

*   A physical Android device with a minimum OS version of SDK 24 (Android 7.0 -
    Nougat) with developer mode enabled. The process of enabling developer mode
    may vary by device.

### Building

*   Open Android Studio. From the Welcome screen, select Open an existing
    Android Studio project.

*   From the Open File or Project window that appears, navigate to and select
    the mediapipe/examples/pose_landmarker/android directory. Click OK. You may
    be asked if you trust the project. Select Trust.

*   If it asks you to do a Gradle Sync, click OK.

*   With your Android device connected to your computer and developer mode
    enabled, click on the green Run arrow in Android Studio.

### Models used

Downloading, extraction, and placing the models into the *assets* folder is
managed automatically by the **download.gradle** file.
## Desensitize Mode

The demo includes a **Desensitize Mode** toggle in the bottom settings sheet for privacy-sensitive scenarios (e.g. medical, fitness coaching, tele-rehabilitation). When enabled:

1. The raw camera preview (`PreviewView`) is hidden.
2. The background is replaced with a **pixelated mosaic** of the live frame (downscaled to 8% then drawn back upscaled with bitmap filtering disabled), plus a semi-transparent dark scrim. This preserves body silhouette and motion for the skeleton overlay while fully obscuring face and clothing details.
3. The pose skeleton is drawn on top, aligned 1:1 with the mosaic background (both use the same `scaleFactor` and `FILL_START` alignment as the original `PreviewView`).

### Usage

1. Open the bottom settings sheet.
2. Toggle **"Desensitize Mode (Mosaic Background)"** at the bottom.
3. Toggle off to return to the raw RGB preview with skeleton overlay.

### Implementation notes

- `OverlayView` gains a `RenderMode` enum (`RGB_OVERLAY` / `DESENSITIZED`). The mode is toggled at runtime without rebinding the camera.
- `PoseLandmarkerHelper` caches the most recent rotated frame bitmap (`lastInputBitmap`) and exposes it via `ResultBundle.inputBitmap` so `OverlayView` can render the mosaic background.
- Mosaic strength is controlled by `OverlayView.mosaicScale` (default `0.08f`). Lower values produce coarser blocks (stronger desensitization).