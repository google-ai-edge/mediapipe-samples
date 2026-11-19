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


# Download face_landmarker.task from the internet if it's not exist.
MODEL_FILE=./FaceLandmarker/face_landmarker.task
if test -f "$MODEL_FILE"; then
    echo "INFO: face_landmarker.task existed. Skip downloading and use the local task."
else
    curl --fail -L -o ${MODEL_FILE} https://huggingface.co/litert-community/MediaPipe-FaceLandmarker/resolve/main/face_landmarker.task
    echo "INFO: Downloaded face_landmarker.task to $MODEL_FILE ."
fi
