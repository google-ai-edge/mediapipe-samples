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

# Download EmbeddingGemma 2 (Text + Vision 440M) LiteRT-LM model from Hugging Face if it doesn't exist:
# - Text (270M): https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/main/embeddinggemma-2-text-270m.litertlm
# - Text + Vision (440M): https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm
# - Multimodal (740M): https://huggingface.co/litert-community/embeddinggemma-2-740m-litert-lm/resolve/main/embeddinggemma-2-740m.litertlm
MODEL_FILE=./SemanticRetriever/embeddinggemma-2-text-vision-440m.litertlm
if test -f "$MODEL_FILE"; then
    echo "INFO: embeddinggemma-2-text-vision-440m.litertlm exists. Skipping download."
else
    curl --fail -L -o ${MODEL_FILE} https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm
    echo "INFO: Downloaded embeddinggemma-2-text-vision-440m.litertlm to $MODEL_FILE ."
fi
