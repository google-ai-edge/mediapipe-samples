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

# Download summarization model from the internet if it doesn't exist.
MODEL_FILE=./TextSummarizer/summarization_quant_200m_2modes.litertlm
if test -f "$MODEL_FILE"; then
    echo "INFO: summarization_quant_200m_2modes.litertlm exists. Skipping download."
else
    curl -o ${MODEL_FILE} https://storage.googleapis.com/mediapipe-models/text_summarizer/gemma_200m/1/summarization_quant_200m_2modes.litertlm
    echo "INFO: Downloaded summarization_quant_200m_2modes.litertlm to $MODEL_FILE ."
fi
