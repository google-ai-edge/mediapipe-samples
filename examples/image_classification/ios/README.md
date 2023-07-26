# MediaPipe Tasks Image Classification iOS Demo

### Overview

This is a camera app that continuously classifies the objects (classes and confidence) in the frames seen by your device's back camera, in an image imported from the device gallery,  or in a video imported by the device gallery, with the option to use a quantized [EfficientDet Lite 0](https://storage.googleapis.com/mediapipe-tasks/object_detector/efficientdet_lite0_uint8.tflite), or [EfficientDet Lite2](https://storage.googleapis.com/mediapipe-tasks/object_detector/efficientdet_lite2_uint8.tflite) model.

The model files are downloaded by a pre-written script when you build and run the app. You don't need to do any steps to download TFLite models into the project explicitly unless you wish to use your own models. If you do use your own models, place them into the app's ** directory.

Before running your app, you will need to run `pod install` from the iOS directory under the image_classifier example directory (the one you're reading this from right now!).

This application should be run on a physical iOS device to take advantage of the physical camera, though the gallery tab will enable you to use an emulator for opening locally stored files.

### Prerequisites

*   The **[xCode](https://apps.apple.com/us/app/xcode/id497799835)** IDE. This sample has been tested on xCode 14.3.1.

*   A physical iOS device. This app targets iOS Deployment Target 15

### Building

*   Open xCode. From the Welcome screen, select `Open a project or file`

*   From the window that appears, navigate to and select
    the Runner.xcworkspace file under mediapipe/examples/image_classification/ios directory. Click Open. 

*   From a terminal window, run `pod install`

*   You may need to select a team under *Signing and Capabilities*