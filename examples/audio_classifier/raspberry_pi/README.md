# MediaPipe Audio classifier example with Raspberry Pi.

This example uses [MediaPipe](https://github.com/google/mediapipe) with Python on
a Raspberry Pi to perform real-time audio classification using audio streamed
from the microphone.

## Set up your hardware

Before you begin, you need to
[set up your Raspberry Pi](https://projects.raspberrypi.org/en/projects/raspberry-pi-setting-up)
with Raspberry Pi OS (preferably updated to Buster).

Raspberry Pi doesn't have a microphone integrated on its board, so you need to
plug in a USB microphone to record audio.

## Install PortAudio and MediaPipe

This project requires the installation of the PortAudio library and MediaPipe for running inference.

You can install these dependencies by using the provided script.

```
sh setup.sh
```

## Download the examples repository

First, clone this Git repo onto your Raspberry Pi like this:

```
https://github.com/googlesamples/mediapipe.git
```

Run this script to install the required dependencies and download the TFLite models:

```
cd mediapipe/examples/audio_classifier/raspberry_pi
sh setup.sh
```

## Run the example
```
python3 classify.py
```

*   You can optionally specify the `model` parameter to set the TensorFlow Lite
    model to be used:
    *   The default value is `yamnet.tflite`
*   You can optionally specify the `maxResults` parameter to limit the list of
    classification results:
    *   Supported value: A positive integer.
    *   Default value: `5`.
*   Example usage:
    ```
    python3 classify.py \
        --model yamnet.tflite \
        --maxResults 5
    ```
