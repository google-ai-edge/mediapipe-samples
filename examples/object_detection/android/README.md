# MediaPipe Tasks Object Detection Android Demo

### Overview

This is a camera app that continuously detects the objects (bounding boxes and
classes) in the frames seen by your device's back camera, in an image imported from the device gallery, 
or in a video imported by the device gallery, with the option to use a quantized
[MobileNetV2](https://storage.cloud.google.com/tf_model_garden/vision/qat/mobilenetv2_ssd_coco/mobilenetv2_ssd_256_uint8.tflite)
[EfficientDet Lite 0](https://storage.googleapis.com/mediapipe-tasks/object_detector/efficientdet_lite0_uint8.tflite),
or [EfficientDet Lite2](https://storage.googleapis.com/mediapipe-tasks/object_detector/efficientdet_lite2_uint8.tflite)

The model files are downloaded via Gradle scripts when you build and run the
app. You don't need to do any steps to download TFLite models into the project
explicitly.

This application should be run on a physical Android device.

## Build the demo using Android Studio

### Prerequisites

*   The **[Android Studio](https://developer.android.com/studio/index.html)**
    IDE. This sample has been tested on Android Studio Dolphin.

*   A physical Android device with a minimum OS version of SDK 24 (Android 7.0 -
    Nougat) with developer mode enabled. The process of enabling developer mode
    may vary by device.

### Building

*   Open Android Studio. From the Welcome screen, select Open an existing
    Android Studio project.

*   From the Open File or Project window that appears, navigate to and select
    the mediapipe/examples/object_detection/android directory. Click OK.

*   If it asks you to do a Gradle Sync, click OK.

*   With your Android device connected to your computer and developer mode
    enabled, click on the green Run arrow in Android Studio.

### Models used

Downloading, extraction, and placing the models into the assets folder is
managed automatically by the download.gradle file.