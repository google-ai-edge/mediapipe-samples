#!/bin/bash
# Copyright 2024 The MediaPipe Authors.
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

# Download holistic_landmarker.task from the internet if it doesn't exist.
MODEL_FILE=./HolisticLandmarker/holistic_landmarker.task
if test -f "$MODEL_FILE"; then
    echo "INFO: holistic_landmarker.task exists. Skip downloading and use the local task."
else
    curl -o ${MODEL_FILE} https://storage.googleapis.com/mediapipe-models/holistic_landmarker/holistic_landmarker/float16/1/holistic_landmarker.task
    echo "INFO: Downloaded holistic_landmarker.task to $MODEL_FILE ."
fi
