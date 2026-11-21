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
DEST_DIR="$PROJECT_DIR/AudioTranscriber"
VERIFY_EXISTING=0

if [ "${1:-}" = "--verify" ]; then
  VERIFY_EXISTING=1
fi

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

download_verified_asset() {
  local filename="$1"
  local url="$2"
  local expected_sha="$3"
  local dest="$DEST_DIR/$filename"

  if [ -f "$dest" ]; then
    if [ "$VERIFY_EXISTING" -eq 0 ]; then
      echo "INFO: $filename existed. Skip downloading and use the local asset."
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

download_verified_asset \
  "parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm" \
  "https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/cd95f7bb97fd0757ab1bd6d5eff513cfcd7a2a10/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm" \
  "66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504"

download_verified_asset \
  "speech_16000_hz_mono.wav" \
  "https://storage.googleapis.com/mediapipe-assets/speech_16000_hz_mono.wav" \
  "71caf50b8757d6ab9cad5eae4d36669d3c20c225a51660afd7fe0dc44cdb74f6"
