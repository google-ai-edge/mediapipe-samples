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


# Download selfie_segmenter.tflite from the internet if it's not exist.
MODEL_FILE=./ImageSegmenter/selfie_segmenter.tflite
if test -f "$MODEL_FILE"; then
    echo "INFO: selfie_segmenter.tflite existed. Skip downloading and use the local task."
else
    curl -o ${MODEL_FILE} https://storage.googleapis.com/mediapipe-models/image_segmenter/selfie_segmenter/float16/latest/selfie_segmenter.tflite
    echo "INFO: Downloaded selfie_segmenter.tflite to $MODEL_FILE ."
fi

# Download deeplab_v3.tflite from the internet if it's not exist.
MODEL_FILE=./ImageSegmenter/deeplab_v3.tflite
if test -f "$MODEL_FILE"; then
    echo "INFO: deeplab_v3.tflite existed. Skip downloading and use the local task."
else
    curl -o ${MODEL_FILE} https://storage.googleapis.com/mediapipe-models/image_segmenter/deeplab_v3/float32/latest/deeplab_v3.tflite
    echo "INFO: Downloaded deeplab_v3.tflite to $MODEL_FILE ."
fi
