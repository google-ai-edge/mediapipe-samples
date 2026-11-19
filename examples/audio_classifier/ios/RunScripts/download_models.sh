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

# Download YamNet audio classifier model and sample audio
MODEL_FILE=./AudioClassifier/yamnet.tflite
if test -f "$MODEL_FILE"; then
    echo "INFO: yamnet.tflite exists. Skipping download."
else
    curl --fail -L -o ${MODEL_FILE} https://huggingface.co/litert-community/MediaPipe-AudioClassifier-YAMNet/resolve/main/yamnet.tflite
    echo "INFO: Downloaded yamnet.tflite to $MODEL_FILE ."
fi

AUDIO_FILE=./AudioClassifier/speech_16000_hz_mono.wav
if test -f "$AUDIO_FILE"; then
    echo "INFO: speech_16000_hz_mono.wav exists. Skipping download."
else
    curl -o ${AUDIO_FILE} https://storage.googleapis.com/mediapipe-assets/speech_16000_hz_mono.wav
    echo "INFO: Downloaded speech_16000_hz_mono.wav to $AUDIO_FILE ."
fi
