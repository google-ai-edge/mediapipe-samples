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


def _has_common_instrumented_tests(directory: str) -> bool:
    """Returns True if the example uses :android-common:testing and has androidTest sources."""
    android_test_dir = os.path.join(directory, "app", "src", "androidTest")
    if not os.path.isdir(android_test_dir):
        return False
    for settings_name in ("settings.gradle", "settings.gradle.kts"):
        settings_path = os.path.join(directory, settings_name)
        if os.path.isfile(settings_path):
            with open(settings_path, encoding="utf-8") as f:
                if "android-common:testing" in f.read():
                    return True
    return False


def detect_android_examples(
    repo_root: str,
    target_name: str | None = None,
    require_common_testing: bool = False,
) -> list[dict[str, str]]:
    """Finds Android sample Gradle projects under examples/*/android*."""
    examples: list[dict[str, str]] = []
    examples_dir = os.path.join(repo_root, "examples")

    if not os.path.isdir(examples_dir):
        return []

    def maybe_add(example_name: str, task_name: str, candidate_dir: str) -> None:
        if (
            target_name
            and target_name != "all"
            and target_name not in (task_name, example_name)
        ):
            return
        if require_common_testing and not _has_common_instrumented_tests(candidate_dir):
            return
        examples.append({
            "name": example_name,
            "slug": example_name.replace("/", "-"),
            "path": os.path.relpath(candidate_dir, repo_root),
        })

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
                maybe_add(example_name, task_name, android_dir)
            else:
                # Check one level deeper (e.g., examples/image_segmentation/android/{category_mask,confidence_mask})
                for nested in sorted(os.listdir(android_dir)):
                    nested_dir = os.path.join(android_dir, nested)
                    if os.path.isdir(nested_dir) and _is_gradle_root(nested_dir):
                        example_name = f"{task_name}/{nested}"
                        maybe_add(example_name, task_name, nested_dir)

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
    parser.add_argument(
        "--require-common-testing",
        action="store_true",
        help="Only include examples migrated to :android-common:testing with androidTest sources.",
    )
    args = parser.parse_args()

    examples = detect_android_examples(
        args.repo_root,
        target_name=args.example,
        require_common_testing=args.require_common_testing,
    )
    matrix = {"include": examples}
    print(json.dumps(matrix))


if __name__ == "__main__":
    main()
