#!/usr/bin/env python3
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

"""Extract LATENCY_REPORT JSON lines from adb logcat output."""

import os
import re
import sys


def main() -> int:
  if len(sys.argv) != 2:
    print(f"Usage: {sys.argv[0]} <output_dir>", file=sys.stderr)
    return 2

  out_dir = sys.argv[1]
  os.makedirs(out_dir, exist_ok=True)
  pattern = re.compile(r"LATENCY_REPORT\s+([A-Za-z0-9_.-]+):\s*(\{.*\})\s*$")
  count = 0
  for line in sys.stdin:
    match = pattern.search(line)
    if match:
      path = os.path.join(out_dir, f"{match.group(1)}.json")
      with open(path, "w", encoding="utf-8") as handle:
        handle.write(match.group(2) + "\n")
      count += 1
  print(f"Wrote {count} latency report(s) to {out_dir}")
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
