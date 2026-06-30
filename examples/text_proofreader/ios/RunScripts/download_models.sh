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

# Download proofreader model from the internet if it doesn't exist.
MODEL_FILE=./TextProofreader/proofread_quant_200m.litertlm
if test -f "$MODEL_FILE"; then
    echo "INFO: proofread_quant_200m.litertlm exists. Skipping download."
else
    curl -o ${MODEL_FILE} https://storage.googleapis.com/mediapipe-models/text_proofreader/gemma_200m/1/proofread_quant_200m.litertlm
    echo "INFO: Downloaded proofread_quant_200m.litertlm to $MODEL_FILE ."
fi
