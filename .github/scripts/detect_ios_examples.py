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

"""Auto-detects iOS examples in the repository for GitHub Actions dynamic matrix."""

import argparse
import json
import os
import sys


def detect_ios_examples(repo_root: str, target_name: str | None = None) -> list[dict]:
    """Finds all iOS sample projects under examples/*/ios/."""
    examples = []
    examples_dir = os.path.join(repo_root, "examples")

    if not os.path.isdir(examples_dir):
        return []

    for task_name in sorted(os.listdir(examples_dir)):
        if target_name and target_name != "all" and task_name != target_name:
            continue

        ios_dir = os.path.join(examples_dir, task_name, "ios")
        if not os.path.isdir(ios_dir):
            continue

        xcodeproj_files = [f for f in os.listdir(ios_dir) if f.endswith(".xcodeproj")]
        if not xcodeproj_files:
            continue

        xcodeproj = sorted(xcodeproj_files)[0]
        scheme_name = os.path.splitext(xcodeproj)[0]

        schemes_dir = os.path.join(ios_dir, xcodeproj, "xcshareddata", "xcschemes")
        if os.path.isdir(schemes_dir):
            shared_schemes = [
                os.path.splitext(f)[0]
                for f in os.listdir(schemes_dir)
                if f.endswith(".xcscheme")
            ]
            if shared_schemes:
                scheme_name = (
                    scheme_name if scheme_name in shared_schemes else shared_schemes[0]
                )

        rel_path = os.path.relpath(ios_dir, repo_root)
        has_podfile = os.path.isfile(os.path.join(ios_dir, "Podfile"))

        examples.append({
            "name": task_name,
            "path": rel_path,
            "project": xcodeproj,
            "scheme": scheme_name,
            "has_podfile": has_podfile,
        })

    return examples


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Auto-detect iOS examples for GitHub Actions matrix."
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Root path of the repository.",
    )
    parser.add_argument(
        "--example",
        default=None,
        help="Optional specific example name to filter by (or 'all').",
    )
    args = parser.parse_args()

    examples = detect_ios_examples(args.repo_root, target_name=args.example)
    matrix = {"include": examples}
    print(json.dumps(matrix))


if __name__ == "__main__":
    main()
