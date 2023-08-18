# MediaPipe Image Classifier example with Raspberry Pi

This example uses [MediaPipe](https://github.com/google/mediapipe) with Python on
a Raspberry Pi to perform real-time image classification using images
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

Run this script to install the required dependencies and download the TFLite models:

```
cd mediapipe/examples/image_classification/raspberry_pi
sh setup.sh
```

## Run the example
```
python3 classify.py
```
*   You can optionally specify the `model` parameter to set the TensorFlow Lite
    model to be used:
    *   The default value is `classifier.tflite`
    *   TensorFlow Lite image classification models **with metadata**  
        * Models from [TensorFlow Hub](https://tfhub.dev/tensorflow/collections/lite/task-library/image-classifier/1)
        * Models from [MediaPipe Models](https://developers.google.com/mediapipe/solutions/vision/image_classifier/index#models)
        * Models trained with [MediaPipe Model Maker](https://developers.google.com/mediapipe/solutions/customization/image_classifier) are supported.
*   You can optionally specify the `maxResults` parameter to limit the list of
    classification results:
    *   Supported value: A positive integer.
    *   Default value: `3`
*   You can optionally specify the `scoreThreshold` parameter to adjust the
    score threshold of classification results:
    *   Supported value: A floating-point number.
    *   Default value: `0.0`.
*   Example usage:
    ```
    python3 classify.py \
      --model efficientnet_lite0.tflite \
      --maxResults 5 \
      --scoreThreshold 0.5
    ```
