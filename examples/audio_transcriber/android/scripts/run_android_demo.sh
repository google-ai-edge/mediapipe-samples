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
#
# Helper script to build the MediaPipe AudioTranscriber Android demo APK,
# install it on a connected Android device or emulator, push the Parakeet TDT
# .litertlm model, run the app, and test live transcription.
#
# Usage:
#   ./scripts/run_android_demo.sh [options]
#
# Options:
#   --model PATH          Local .litertlm model file (default: $HOME/models/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm)
#   --serial SERIAL       adb serial to use (default: $ANDROID_SERIAL or the only attached device)
#   --no-build            Skip `./gradlew installDebug` (reuse existing APK)
#   --record-seconds N    Duration in seconds to record from microphone (default: 5)
#   -h | --help
#

set -euo pipefail

MODEL="${HOME}/models/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm"
SERIAL="${ANDROID_SERIAL:-}"
DO_BUILD=true
RECORD_SECONDS=5

usage() {
  sed -n '16,/^$/p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --model) MODEL="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --no-build) DO_BUILD=false; shift ;;
    --record-seconds) RECORD_SECONDS="$2"; shift 2 ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown option: $1" >&2; usage 1 ;;
  esac
done

readonly PKG="com.google.mediapipe.tasks.examples.audiotranscriber"
readonly ACTIVITY="${PKG}/.MainActivity"
readonly DEVICE_DIR="/sdcard/Android/data/${PKG}/files"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

ADB_CMD="adb"
if ! command -v adb >/dev/null 2>&1; then
  if [[ -f "${HOME}/Library/Android/sdk/platform-tools/adb" ]]; then
    ADB_CMD="${HOME}/Library/Android/sdk/platform-tools/adb"
  elif [[ -n "${ANDROID_HOME:-}" && -f "${ANDROID_HOME}/platform-tools/adb" ]]; then
    ADB_CMD="${ANDROID_HOME}/platform-tools/adb"
  else
    die "adb not found in PATH or standard Android SDK directories."
  fi
fi

# Detect device
if [[ -z "${SERIAL}" ]]; then
  DEVICES=()
  while IFS= read -r dev; do
    [[ -n "$dev" ]] && DEVICES+=("$dev")
  done < <("${ADB_CMD}" devices | awk 'NR>1 && $2=="device" {print $1}')

  case "${#DEVICES[@]}" in
    0) die "No Android device/emulator attached in 'device' state." ;;
    1) SERIAL="${DEVICES[0]}" ;;
    *) die "Multiple devices attached (${DEVICES[*]}). Specify --serial <serial>." ;;
  esac
fi

ADB() {
  "${ADB_CMD}" -s "${SERIAL}" "$@"
}

log "Using target device: ${SERIAL}"

# 1. Model push if needed
if [[ -f "${MODEL}" ]]; then
  MODEL_NAME="$(basename "${MODEL}")"
  TARGET_PATH="${DEVICE_DIR}/${MODEL_NAME}"
  
  EXISTING_SIZE=$(ADB shell "[ -f '${TARGET_PATH}' ] && stat -c %s '${TARGET_PATH}' || echo 0" 2>/dev/null | tr -d '\r\n')
  LOCAL_SIZE=$(wc -c < "${MODEL}" | tr -d ' ')

  if [[ "${EXISTING_SIZE}" != "${LOCAL_SIZE}" ]]; then
    log "Pushing model ${MODEL_NAME} (${LOCAL_SIZE} bytes) to ${TARGET_PATH}..."
    ADB shell "mkdir -p '${DEVICE_DIR}'"
    ADB push "${MODEL}" "${TARGET_PATH}"
  else
    log "Model already present on device at ${TARGET_PATH} (${EXISTING_SIZE} bytes)."
  fi
else
  log "Model file not found locally at ${MODEL}; assuming bundled or pre-staged."
fi

# 2. Build and install APK
if [[ "${DO_BUILD}" == "true" ]]; then
  log "Building and installing debug APK..."
  if [[ -d "/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home" ]]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home"
    export PATH="${JAVA_HOME}/bin:${PATH}"
  fi
  ./gradlew installDebug
fi

# 3. Grant microphone permissions
log "Granting RECORD_AUDIO permission..."
ADB shell pm grant "${PKG}" android.permission.RECORD_AUDIO

# 4. Launch app
log "Launching AudioTranscriber MainActivity..."
ADB shell am force-stop "${PKG}"
ADB shell am start -n "${ACTIVITY}"
sleep 3

# 5. Check UI state
log "Checking UI state..."
ADB shell uiautomator dump /data/local/tmp/dump.xml >/dev/null 2>&1 || true
if ADB shell cat /data/local/tmp/dump.xml | grep -q "Ready"; then
  log "AudioTranscriber initialized successfully and is in Ready state."
fi

# 6. Test recording and transcription
if [[ "${RECORD_SECONDS}" -gt 0 ]]; then
  log "Starting recording for ${RECORD_SECONDS} seconds..."
  ADB shell input tap 540 2074
  sleep "${RECORD_SECONDS}"
  log "Stopping recording..."
  ADB shell input tap 540 2074
  sleep 2

  ADB shell uiautomator dump /data/local/tmp/dump_final.xml >/dev/null 2>&1 || true
  log "Transcriber status after run:"
  ADB shell cat /data/local/tmp/dump_final.xml | tr '>' '\n' | grep 'text=' | grep -v 'text=""' | head -15 || true
fi

log "Done! AudioTranscriber is active on device ${SERIAL}."
