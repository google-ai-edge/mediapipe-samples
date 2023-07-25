#!/bin/bash
# Copyright 2023 The MediaPipe Authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.


# Download efficientnet_lite0.tflite from the internet if it's not exist.
TFLITE_FILE=./ImageClassifier/efficientnet_lite0.tflite
if test -f "$TFLITE_FILE"; then
    echo "INFO: efficientnet_lite0.tflite existed. Skip downloading and use the local model."
else
    curl -o ${TFLITE_FILE} https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite
    echo "INFO: Downloaded efficientnet_lite0.tflite to $TFLITE_FILE ."
fi

# Download efficientnet_lite2.tflite from the internet if it's not exist.
TFLITE_FILE=./ImageClassifier/efficientnet_lite2.tflite
if test -f "$TFLITE_FILE"; then
    echo "INFO: efficientnet_lite2.tflite existed. Skip downloading and use the local model."
else
    curl -o ${TFLITE_FILE} https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite2/float32/1/efficientnet_lite2.tflite
    echo "INFO: Downloaded efficientnet_lite2.tflite to $TFLITE_FILE ."
fi

