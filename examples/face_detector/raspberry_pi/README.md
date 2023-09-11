# MediaPipe Face Detection example with Raspberry Pi

This example uses [MediaPipe](https://github.com/google/mediapipe) with Python on
a Raspberry Pi to perform real-time face detection using images streamed from
the Pi Camera. It draws a bounding box around each detected face in the camera
preview (when the object score is above a given threshold).

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

Run this script to install the required dependencies and download the TFLite models:

```
cd mediapipe/examples/face_detection/raspberry_pi
sh setup.sh
```

## Run the example

```
python3 detect.py \
  --model detector.tflite
```

You should see the camera feed appear on the monitor attached to your Raspberry
Pi. Ask people to appear in front of the camera and you'll be able to see boxes 
drawn around their faces, including the detection score for each. It also prints 
the number of frames per second (FPS) at the top-left corner of the screen. 
As the pipeline contains some processes other than model inference, including 
visualizing the detection results, you can expect a higher FPS if your inference
pipeline runs in headless mode without visualization.

*   You can optionally specify the `model` parameter to set the TensorFlow Lite
    model to be used:
    *   The default value is `detector.tflite`
    *   TensorFlow Lite face detection models **with metadata**  
        * Models from [MediaPipe Models](https://developers.google.com/mediapipe/solutions/vision/face_detector/index#models)
*   You can optionally specify the `minDetectionConfidence` parameter to adjust the
    minimum confidence score for face detection to be considered successful:
    *   Supported value: A floating-point number.
    *   Default value: `0.5`
*   You can optionally specify the `minSuppressionThreshold` parameter to adjust the
    minimum non-maximum-suppression threshold for face detection to be considered overlapped:
    *   Supported value: A floating-point number.
    *   Default value: `0.5`
*   Example usage:
    ```
    python3 detect.py \
      --model detector.tflite \
      --minDetectionConfidence 0.3 \
      --minSuppressionThreshold 0.5
    ```
