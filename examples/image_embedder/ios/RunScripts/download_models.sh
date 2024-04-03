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
TFLITE_FILE=./ImageEmbedder/mobilenet_v3_small.tflite
if test -f "$TFLITE_FILE"; then
    echo "INFO: mobilenet_v3_small.tflite existed. Skip downloading and use the local model."
else
    curl -o ${TFLITE_FILE} https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/latest/mobilenet_v3_small.tflite
    echo "INFO: Downloaded mobilenet_v3_small.tflite to $TFLITE_FILE ."
fi

# Download efficientnet_lite2.tflite from the internet if it's not exist.
TFLITE_FILE=./ImageEmbedder/mobilenet_v3_large.tflite
if test -f "$TFLITE_FILE"; then
    echo "INFO: mobilenet_v3_large.tflite existed. Skip downloading and use the local model."
else
    curl -o ${TFLITE_FILE} https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_large/float32/latest/mobilenet_v3_large.tflite
    echo "INFO: Downloaded mobilenet_v3_large.tflite to $TFLITE_FILE ."
fi

