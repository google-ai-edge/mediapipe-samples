
# MediaPipe Text Classification with Raspberry Pi

### Overview

This sample will accept text entered in the command line and classify it as either
positive or negative with a provided confidence score. The supported
classification models include Average Word-Embedding and MobileBERT, both of which are
generated using
[MediaPipe Model Maker](https://developers.google.com/mediapipe/solutions/customization/text_classifier).
These instructions walk you through building and running the demo on a Raspberry Pi.

## Set up your hardware

Before you begin, you need to
[set up your Raspberry Pi](https://projects.raspberrypi.org/en/projects/raspberry-pi-setting-up)
with Raspberry 64-bit Pi OS (preferably updated to Buster).

## Install MediaPipe

You can install the required dependencies using the setup.sh script provided with this project.

## Download the examples repository

First, clone this Git repo onto your Raspberry Pi.

Run this script to install the required dependencies and download the TFLite models:

```
cd mediapipe/examples/text_classification/raspberry_pi
sh setup.sh
```

## Run the example
```
python3 classify.py --inputText "Your text goes here"
```

*   You can optionally specify the `model` parameter to set the TensorFlow Lite
    model to be used:
    *   The default value is `classifier.tflite`
    *   TensorFlow Lite text classification models **with metadata**  
        * Models from [MediaPipe Models](https://developers.google.com/mediapipe/solutions/text/text_classifier/index#models)
        * Models trained with [MediaPipe Model Maker](https://developers.google.com/mediapipe/solutions/customization/text_classifier) are supported.
*   Example usage:
    ```
    python3 classify.py \
        --model classifier.tflite \
        --inputText "Your text goes here"
    ```
