#!/bin/bash
# Download Parakeet TDT 0.6B streaming speech recognition LiteRT-LM model
MODEL_DIR="./app/src/main/assets"
mkdir -p "${MODEL_DIR}"
MODEL_FILE="${MODEL_DIR}/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm"
if test -f "$MODEL_FILE"; then
    echo "INFO: parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm exists. Skipping download."
else
    echo "INFO: Downloading parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm..."
    curl -L -o "${MODEL_FILE}" "https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/main/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm"
fi
