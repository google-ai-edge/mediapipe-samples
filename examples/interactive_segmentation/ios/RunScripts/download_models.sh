#!/bin/bash
# Copyright 2026 The MediaPipe Authors.
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


# Download interactive_segmentation.task from the internet if it doesn't exist.
MODEL_FILE=./InteractiveSegmenter/interactive_segmentation.task
if test -f "$MODEL_FILE"; then
    echo "INFO: interactive_segmentation.task existed. Skip downloading and use the local task."
else
    curl -o ${MODEL_FILE} https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task
    echo "INFO: Downloaded interactive_segmentation.task to $MODEL_FILE ."
fi
