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

"""Auto-detects Android examples in the repository for GitHub Actions dynamic matrix."""

import argparse
import json
import os


def _is_gradle_root(directory: str) -> bool:
    """Returns True if directory contains a Gradle wrapper and root build script."""
    has_gradlew = os.path.isfile(os.path.join(directory, "gradlew"))
    has_build = (
        os.path.isfile(os.path.join(directory, "build.gradle"))
        or os.path.isfile(os.path.join(directory, "build.gradle.kts"))
        or os.path.isfile(os.path.join(directory, "settings.gradle"))
        or os.path.isfile(os.path.join(directory, "settings.gradle.kts"))
    )
    return has_gradlew and has_build


def detect_android_examples(
    repo_root: str, target_name: str | None = None
) -> list[dict[str, str]]:
    """Finds all Android sample Gradle projects under examples/*/android*."""
    examples: list[dict[str, str]] = []
    examples_dir = os.path.join(repo_root, "examples")

    if not os.path.isdir(examples_dir):
        return []

    for task_name in sorted(os.listdir(examples_dir)):
        task_dir = os.path.join(examples_dir, task_name)
        if not os.path.isdir(task_dir):
            continue

        for subdir in sorted(os.listdir(task_dir)):
            if not subdir.startswith("android"):
                continue

            android_dir = os.path.join(task_dir, subdir)
            if not os.path.isdir(android_dir):
                continue

            if _is_gradle_root(android_dir):
                example_name = (
                    task_name
                    if subdir == "android"
                    else f"{task_name}/{subdir}"
                )
                if (
                    target_name
                    and target_name != "all"
                    and target_name not in (task_name, example_name)
                ):
                    continue
                examples.append({
                    "name": example_name,
                    "path": os.path.relpath(android_dir, repo_root),
                })
            else:
                # Check one level deeper (e.g., examples/image_segmentation/android/{category_mask,confidence_mask})
                for nested in sorted(os.listdir(android_dir)):
                    nested_dir = os.path.join(android_dir, nested)
                    if os.path.isdir(nested_dir) and _is_gradle_root(nested_dir):
                        example_name = f"{task_name}/{nested}"
                        if (
                            target_name
                            and target_name != "all"
                            and target_name not in (task_name, example_name)
                        ):
                            continue
                        examples.append({
                            "name": example_name,
                            "path": os.path.relpath(nested_dir, repo_root),
                        })

    return examples


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Auto-detect Android examples for GitHub Actions matrix."
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

    examples = detect_android_examples(args.repo_root, target_name=args.example)
    matrix = {"include": examples}
    print(json.dumps(matrix))


if __name__ == "__main__":
    main()
