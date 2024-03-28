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


# Download pose_landmarker_lite.task from the internet if it's not exist.
TASK_FILE=./PoseLandmarker/pose_landmarker_lite.task
if test -f "$TASK_FILE"; then
    echo "INFO: pose_landmarker_lite.task existed. Skip downloading and use the local model."
else
    curl -o ${TASK_FILE} https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_lite/float16/latest/pose_landmarker_lite.task
    echo "INFO: Downloaded pose_landmarker_lite.task to $TASK_FILE ."
fi

# Download pose_landmarker_full.task from the internet if it's not exist.
TASK_FILE=./PoseLandmarker/pose_landmarker_full.task
if test -f "$TASK_FILE"; then
    echo "INFO: pose_landmarker_full.task existed. Skip downloading and use the local model."
else
    curl -o ${TASK_FILE} https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task
    echo "INFO: Downloaded pose_landmarker_full.task to $TASK_FILE ."
fi

# Download pose_landmarker_heavy.task from the internet if it's not exist.
TASK_FILE=./PoseLandmarker/pose_landmarker_heavy.task
if test -f "$TASK_FILE"; then
    echo "INFO: pose_landmarker_heavy.task existed. Skip downloading and use the local model."
else
    curl -o ${TASK_FILE} https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_heavy/float16/latest/pose_landmarker_heavy.task
    echo "INFO: Downloaded pose_landmarker_heavy.task to $TASK_FILE ."
fi

