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
DEST_DIR="$PROJECT_DIR/TextSummarizer"
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
  "summarization_quant_200m_2modes.litertlm" \
  "https://huggingface.co/litert-community/MediaPipe-TextSummarizer-200M/resolve/e59af6de5d4753422ce706280d0371fdc223b1ba/summarization_quant_200m_2modes.litertlm" \
  "8b2d4ef09236adb9ead3127325526ba1aa5a59feb7c5de2d3f5958f27479de59"
