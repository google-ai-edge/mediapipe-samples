# MediaPipe Tasks Holistic Landmarker iOS Demo

### Overview

This is a camera app that detects holistic landmarks (pose, face, and hands)
either from continuous camera frames seen by your device's camera, an image, or
a video from the device's gallery using a custom **task** file.

The task file is downloaded by a build script when you build and run the app.
You don't need to do any additional steps to download task files into the
project explicitly unless you wish to use your own landmark detection task. If
you do use your own task file, place it into the app's *HolisticLandmarker*
directory.

Before running your app, you will need to run `pod install` from the iOS
directory under the holistic_landmarker example directory (the one you're
reading this from right now!).

This application should be run on a physical iOS device to take advantage of the
camera, though the gallery tab will enable you to use a simulator for opening
locally stored image and video files.

### Prerequisites

*   The **[Xcode](https://apps.apple.com/us/app/xcode/id497799835)** IDE.
*   CocoaPods (`gem install cocoapods`).
*   A physical iOS device or iOS Simulator (iOS 15.0+).

### Building

*   From a terminal window, navigate to `examples/holistic_landmarker/ios/` and
    run `pod install`.
*   Open Xcode. Select `Open a project or file` and open
    `HolisticLandmarker.xcworkspace`.
*   Select your development team under *Signing & Capabilities* if deploying to
    a physical device.
*   Select a target device or simulator and click Run.
