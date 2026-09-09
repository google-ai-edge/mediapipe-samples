# MediaPipe Tasks Image Classification iOS Demo

### Overview

This is a camera app that continuously classifies the objects (classes and confidence) in the frames seen by your device's back camera, in an image imported from the device gallery,  or in a video imported by the device gallery, with the option to use a quantized [EfficientDet Lite 0](https://storage.googleapis.com/mediapipe-tasks/object_detector/efficientdet_lite0_uint8.tflite), or [EfficientDet Lite2](https://storage.googleapis.com/mediapipe-tasks/object_detector/efficientdet_lite2_uint8.tflite) model.

The model files are downloaded by a pre-written script when you build and run the app. You don't need to do any steps to download TFLite models into the project explicitly unless you wish to use your own models. If you do use your own models, place them into the app's ** directory.

Open `ImageClassifier.xcodeproj` in Xcode. Xcode will automatically resolve
and download the Swift Package dependencies.

This application should be run on a physical iOS device to take advantage of the physical camera, though the gallery tab will enable you to use an emulator for opening locally stored files.

### Prerequisites

*   The **[Xcode](https://apps.apple.com/us/app/xcode/id497799835)** IDE.

*   A physical iOS device. This app targets iOS Deployment Target 15.0.

### Building

*   Open Xcode. From the Welcome screen, select `Open a project or file`.

*   From the file picker, navigate to and select `ImageClassifier.xcodeproj`.
    Click Open. Xcode will automatically resolve and download the Swift Package
    dependencies from `https://github.com/google-ai-edge/mediapipe`.

*   You may need to select a team under *Signing & Capabilities*.