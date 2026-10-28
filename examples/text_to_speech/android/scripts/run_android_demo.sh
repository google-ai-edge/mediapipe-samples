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
# Helper script to build the MediaPipe TTS Android demo APK, install it on a
# connected Android device, push the Kokoro-82M .litertlm model, run the demo,
# stream its logcat output, and pull the synthesized WAV back to the workstation.
#
# Usage:
#   ./scripts/run_android_demo.sh [options]
#
# Options:
#   --text "..."          Text to synthesize (default: sample English sentence)
#   --voice ID            Kokoro voice id, e.g. af_heart, ef_dora, zf_xiaobei
#                         (default: af_heart)
#   --model PATH          Local .litertlm model file (default: $HOME/models/kokoro.litertlm)
#   --serial SERIAL       adb serial to use (default: $ANDROID_SERIAL or the only attached device)
#   --threads N           CPU threads (default: 4)
#   --target-bucket N     Kokoro padding bucket (default: 64)
#   --stream              Run clause-by-clause streaming (synthesizeStream)
#   --no-play             Do not play the audio on the phone's speaker
#   --no-build            Skip `./gradlew installDebug` (reuse existing APK)
#   --timeout SECONDS     Max time to wait for the on-device run (default: 300)
#   -h | --help

set -euo pipefail

# ---------------------------------------------------------------------------
# Defaults & argument parsing
# ---------------------------------------------------------------------------
TEXT="Hello from Kokoro 82M running in-process on Android via the "
TEXT+="MediaPipe TTS Kotlin SDK!"
VOICE="af_heart"
MODEL="${HOME}/models/kokoro.litertlm"
SERIAL="${ANDROID_SERIAL:-}"
THREADS=4
TARGET_BUCKET=64
STREAM=false
PLAY_AUDIO=true
DO_BUILD=true
TIMEOUT=300

usage() {
  sed -n '16,/^$/p' "$0" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --text) TEXT="$2"; shift 2 ;;
    --voice) VOICE="$2"; shift 2 ;;
    --model) MODEL="$2"; shift 2 ;;
    --serial) SERIAL="$2"; shift 2 ;;
    --threads) THREADS="$2"; shift 2 ;;
    --target-bucket) TARGET_BUCKET="$2"; shift 2 ;;
    --stream) STREAM=true; shift ;;
    --no-play) PLAY_AUDIO=false; shift ;;
    --no-build) DO_BUILD=false; shift ;;
    --timeout) TIMEOUT="$2"; shift 2 ;;
    -h|--help) usage 0 ;;
    *) echo "Unknown option: $1" >&2; usage 1 ;;
  esac
done

readonly PKG="com.google.mediapipe.tasks.audio.texttospeech.examples"
readonly ACTIVITY="${PKG}/.MainActivity"
readonly LOG_TAG="MediaPipeTtsDemo"
readonly RESULT_MARKER="MEDIAPIPE_TTS_DEMO_RESULT"
readonly DEVICE_DIR="/sdcard/Android/data/${PKG}/files"
readonly LOCAL_WAV="/tmp/kokoro_android_demo.wav"

log() { printf '\n\033[1;34m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_ROOT}"

if ! command -v adb >/dev/null 2>&1; then
  die "adb not found in PATH. Please install Android SDK platform-tools."
fi

# ---------------------------------------------------------------------------
# 1. Device detection
# ---------------------------------------------------------------------------
if [[ -z "${SERIAL}" ]]; then
  DEVICES=()
  while IFS= read -r dev; do
    [[ -n "$dev" ]] && DEVICES+=("$dev")
  done < <(adb devices | awk '$2 == "device" { print $1 }')
  if [[ ${#DEVICES[@]} -eq 0 ]]; then
    die "No Android device connected. Please connect a device with USB debugging enabled or start an emulator."
  elif [[ ${#DEVICES[@]} -gt 1 ]]; then
    echo "Multiple devices attached: ${DEVICES[*]}; using the first (override with --serial)."
  fi
  SERIAL="${DEVICES[0]}"
fi

D=(adb -s "${SERIAL}")
"${D[@]}" wait-for-device
MODEL_NAME="$("${D[@]}" shell getprop ro.product.model | tr -d '\r')"
SDK="$("${D[@]}" shell getprop ro.build.version.sdk | tr -d '\r')"
ABI="$("${D[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')"
echo "Using ${SERIAL}: ${MODEL_NAME}, API ${SDK}, ABI ${ABI}"
[[ "${SDK}" -ge 26 ]] || die "Device API ${SDK} < 26 (minSdk)"

# ---------------------------------------------------------------------------
# 2. Build and install APK
# ---------------------------------------------------------------------------
if [[ "${DO_BUILD}" == true ]]; then
  log "Building and installing APK via Gradle"
  if [[ -f "./gradlew" ]]; then
    ./gradlew installDebug
  else
    die "gradlew not found at ${PROJECT_ROOT}/gradlew"
  fi
fi

# ---------------------------------------------------------------------------
# 3. Push model
# ---------------------------------------------------------------------------
if [[ -f "${MODEL}" ]]; then
  log "Pushing model to ${DEVICE_DIR}/kokoro.litertlm (skipped if unchanged)"
  "${D[@]}" shell "mkdir -p '${DEVICE_DIR}'"
  "${D[@]}" push --sync "${MODEL}" "${DEVICE_DIR}/kokoro.litertlm"
else
  echo "Notice: Local model ${MODEL} not found."
  echo "Checking if model already exists on device..."
  if ! "${D[@]}" shell "[ -f '${DEVICE_DIR}/kokoro.litertlm' ] || [ -f '/data/local/tmp/kokoro.litertlm' ]"; then
    die "Model not found at ${MODEL} and not present on device. Please provide --model <path_to_kokoro.litertlm>"
  fi
fi

# ---------------------------------------------------------------------------
# 4. Run the demo and stream its log
# ---------------------------------------------------------------------------
log "Launching ${ACTIVITY}"
"${D[@]}" shell am force-stop "${PKG}" >/dev/null 2>&1 || true
"${D[@]}" logcat -c >/dev/null 2>&1 || true
LAUNCH_TS="$("${D[@]}" shell "date '+%m-%d %H:%M:%S.000'" | tr -d '\r')"
LOGCAT_SINCE=(-T "${LAUNCH_TS}")
TEXT_Q="'${TEXT//\'/\'\\\'\'}'"
"${D[@]}" shell \
  "am start -W -n ${ACTIVITY} --es text ${TEXT_Q} --es voice '${VOICE}'" \
  "--ei num_threads ${THREADS} --ei target_bucket ${TARGET_BUCKET}" \
  "--ez play_audio ${PLAY_AUDIO} --ez stream ${STREAM}" >/dev/null

RESULT_LINE=""
SEEN=0
DEADLINE=$((SECONDS + TIMEOUT))
while [[ ${SECONDS} -lt ${DEADLINE} ]]; do
  LINES="$(
    "${D[@]}" logcat -d "${LOGCAT_SINCE[@]}" -s "${LOG_TAG}:I" -v raw \
      2>/dev/null | grep -v '^--------' || true
  )"
  TOTAL="$(grep -c . <<<"${LINES}" || true)"
  if [[ "${TOTAL}" -gt "${SEEN}" ]]; then
    tail -n "$((TOTAL - SEEN))" <<<"${LINES}" | sed 's/^/  [device] /'
    SEEN="${TOTAL}"
  fi
  RESULT_LINE="$(grep -m1 "${RESULT_MARKER}" <<<"${LINES}" || true)"
  [[ -n "${RESULT_LINE}" ]] && break
  if "${D[@]}" logcat -d "${LOGCAT_SINCE[@]}" -s AndroidRuntime:E \
    2>/dev/null | grep -q "Process: ${PKG}"; then
    "${D[@]}" logcat -d "${LOGCAT_SINCE[@]}" -s AndroidRuntime:E | tail -40
    die "The demo app crashed (see AndroidRuntime log above)"
  fi
  sleep 2
done
if [[ -z "${RESULT_LINE}" ]]; then
  die "Timed out after ${TIMEOUT}s waiting for ${RESULT_MARKER} in logcat"
fi

# ---------------------------------------------------------------------------
# 5. Pull WAV
# ---------------------------------------------------------------------------
STATUS=OK
if grep -q "status=OK" <<<"${RESULT_LINE}"; then
  log "Pulling WAV to ${LOCAL_WAV}"
  "${D[@]}" pull "${DEVICE_DIR}/kokoro_android_demo.wav" "${LOCAL_WAV}" 2>/dev/null || true
  if [[ -f "${LOCAL_WAV}" ]] && command -v file >/dev/null; then
    file "${LOCAL_WAV}"
  fi
else
  STATUS=ERROR
fi

log "Summary"
echo "  Device : ${SERIAL} (${MODEL_NAME}, API ${SDK}, ${ABI})"
echo "  Result : ${RESULT_LINE#*${RESULT_MARKER} }"
[[ "${STATUS}" == OK && -f "${LOCAL_WAV}" ]] && echo "  WAV    : ${LOCAL_WAV}"

[[ "${STATUS}" == OK ]]
