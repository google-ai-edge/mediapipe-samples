# MediaPipe Hand Landmarker example with Raspberry Pi

This example uses [MediaPipe](https://github.com/google/mediapipe) with Python on
a Raspberry Pi to perform real-time hand landmarks detection using images
streamed from the camera.

## Set up your hardware

Before you begin, you need to
[set up your Raspberry Pi](https://projects.raspberrypi.org/en/projects/raspberry-pi-setting-up)
with Raspberry 64-bit Pi OS (preferably updated to Buster).

You also need to [connect and configure the Pi Camera](
https://www.raspberrypi.org/documentation/configuration/camera.md) if you use
the Pi Camera. This code also works with USB camera connect to the Raspberry Pi.

And to see the results from the camera, you need a monitor connected
to the Raspberry Pi. It's okay if you're using SSH to access the Pi shell
(you don't need to use a keyboard connected to the Pi)—you only need a monitor
attached to the Pi to see the camera stream.

## Install MediaPipe

You can install the required dependencies using the setup.sh script provided with this project.

## Download the examples repository

First, clone this Git repo onto your Raspberry Pi.

Run this script to install the required dependencies and download the task file:

```
cd mediapipe/examples/hand_landmarker/raspberry_pi
sh setup.sh
```

## Run the example
```
python3 detect.py
```
*   You can optionally specify the `model` parameter to set the task file to be used:
    *   The default value is `hand_landmarker.task`
    *   TensorFlow Lite gesture recognizer models **with metadata**  
        * Models from [MediaPipe Models](https://developers.google.com/mediapipe/solutions/vision/hand_landmarker/index#models)
*   You can optionally specify the `numHands` parameter to the maximum 
    number of hands that can be detected by the landmarker:
    *   Supported value: A positive integer (1-2)
    *   Default value: `1`
*   You can optionally specify the `minHandDetectionConfidence` parameter to adjust the
    minimum confidence score for hand detection to be considered successful:
    *   Supported value: A floating-point number.
    *   Default value: `0.5`
*   You can optionally specify the `minHandPresenceConfidence` parameter to adjust the 
    minimum confidence score of hand presence score in the hand landmark detection:
    *   Supported value: A floating-point number.
    *   Default value: `0.5`
*   You can optionally specify the `minTrackingConfidence` parameter to adjust the 
    minimum confidence score for the hand tracking to be considered successful:
    *   Supported value: A floating-point number.
    *   Default value: `0.5` 
*   Example usage:
    ```
    python3 detect.py \
      --model hand_landmarker.task \
      --numHands 1 \
      --minHandDetectionConfidence 0.5
    ```