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

Open `HolisticLandmarker.xcodeproj` in Xcode. Xcode will automatically resolve
and download the Swift Package dependencies.

This application should be run on a physical iOS device to take advantage of the
camera, though the gallery tab will enable you to use a simulator for opening
locally stored image and video files.

### Prerequisites

*   The **[Xcode](https://apps.apple.com/us/app/xcode/id497799835)** IDE.
*   A physical iOS device or iOS Simulator (iOS 15.0+).

### Building

*   Open Xcode. From the Welcome screen, select `Open a project or file`.
*   From the file picker, navigate to and select `HolisticLandmarker.xcodeproj`.
    Click Open. Xcode will automatically resolve and download the Swift Package
    dependencies from `https://github.com/google-ai-edge/mediapipe`.
*   Select your development team under *Signing & Capabilities* if deploying to
    a physical device.
*   Select a target device or simulator and click Run.
