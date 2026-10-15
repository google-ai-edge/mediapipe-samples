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

# Check for Kokoro-82M LiteRT-LM model bundle:
# The model can be placed at ./TextToSpeech/kokoro.litertlm or ~/models/kokoro.litertlm
MODEL_FILE=./TextToSpeech/kokoro.litertlm
LOCAL_MODEL="${HOME}/models/kokoro.litertlm"

if [ -f "$MODEL_FILE" ]; then
    echo "INFO: Kokoro model exists at $MODEL_FILE. Skipping."
elif [ -f "$LOCAL_MODEL" ]; then
    echo "INFO: Copying model from $LOCAL_MODEL to $MODEL_FILE"
    cp "$LOCAL_MODEL" "$MODEL_FILE"
elif [ -n "${KOKORO_MODEL_URL:-}" ]; then
    echo "INFO: Downloading model from $KOKORO_MODEL_URL"
    curl --fail -L -o "$MODEL_FILE" "$KOKORO_MODEL_URL"
else
    echo "INFO: No Kokoro model file found at $MODEL_FILE or $LOCAL_MODEL."
    echo "INFO: To bundle the model, copy kokoro.litertlm to $MODEL_FILE"
    echo "INFO: Or place it in the app's Documents folder or ~/models/."
fi
