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

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="${SRCROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
DEST_DIR="$PROJECT_DIR/TextEmbedder"
VERIFY_EXISTING=0

if [ "${1:-}" = "--verify" ]; then
  VERIFY_EXISTING=1
fi

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

download_verified_model() {
  local filename="$1"
  local url="$2"
  local expected_sha="$3"
  local dest="$DEST_DIR/$filename"

  if [ -f "$dest" ]; then
    if [ "$VERIFY_EXISTING" -eq 0 ]; then
      echo "INFO: $filename existed. Skip downloading and use the local model."
      return 0
    fi
    if [ ! -s "$dest" ]; then
      echo "ERROR: $filename is empty (stubbed?). Re-downloading." >&2
      rm -f "$dest"
    else
      local existing_sha
      existing_sha="$(sha256_file "$dest")"
      if [ "$existing_sha" = "$expected_sha" ]; then
        echo "INFO: $filename verified ($existing_sha)."
        return 0
      fi
      echo "WARN: $filename checksum mismatch (got $existing_sha, expected $expected_sha). Re-downloading." >&2
      rm -f "$dest"
    fi
  fi

  local tmp="${dest}.tmp"
  rm -f "$tmp"
  echo "INFO: Downloading $filename..."
  curl --fail -L --retry 3 -o "$tmp" "$url"
  local actual_sha
  actual_sha="$(sha256_file "$tmp")"
  if [ "$actual_sha" != "$expected_sha" ]; then
    rm -f "$tmp"
    echo "ERROR: Checksum mismatch for $filename: expected $expected_sha, got $actual_sha" >&2
    exit 1
  fi
  mv "$tmp" "$dest"
  echo "INFO: Downloaded and verified $filename ($actual_sha)."
}

download_verified_model \
  "universal_sentence_encoder.tflite" \
  "https://huggingface.co/litert-community/MediaPipe-TextEmbedder-Universal-Sentence-Encoder/resolve/832fd94a3ddabfb9ed79abee611e95040c3c756a/universal_sentence_encoder.tflite" \
  "89ad3c74175dd8caa398cc22b657296d94302d20c525c12b58b29420f7249749"

download_verified_model \
  "bert_embedder.tflite" \
  "https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite" \
  "02ae6279faf86c2cd4ff18f61876c878bcc0b572b472f0678897a184c4ac7ef6"

download_verified_model \
  "embedding_gemma.task" \
  "https://huggingface.co/litert-community/MediaPipe-TextEmbedder-EmbeddingGemma-300M/resolve/93c96b51d328c85e4cbbfe3039d3cd9b98d8a55d/embedding_gemma.task" \
  "913b7a1edc7c7c3d1da3979ec1d0648ed9e0a370f181bb59ab177ca4b97707ad"
