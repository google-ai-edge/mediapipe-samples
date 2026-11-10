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

"""Updates MediaPipe Tasks versions inside self-contained gradle/libs.versions.toml catalogs."""

import argparse
import glob
import os
import re
import sys

_MEDIAPIPE_VERSION_RE = re.compile(
    r'^(mediapipeTasks[A-Za-z]+\s*=\s*)"[^"]+"',
    flags=re.MULTILINE,
)


def _find_all_catalogs(repo_root: str) -> list[str]:
    candidates: list[str] = []
    common_catalog = os.path.join(repo_root, "android-common", "gradle", "libs.versions.toml")
    if os.path.isfile(common_catalog):
        candidates.append(common_catalog)
    candidates.extend(
        glob.glob(os.path.join(repo_root, "examples", "*", "android", "gradle", "libs.versions.toml"))
    )
    candidates.extend(
        glob.glob(os.path.join(repo_root, "examples", "*", "android", "*", "gradle", "libs.versions.toml"))
    )
    return sorted(set(candidates))


def _resolve_target(repo_root: str, target: str) -> str:
    candidate = target if os.path.isabs(target) else os.path.join(repo_root, target)
    if os.path.isdir(candidate):
        candidate = os.path.join(candidate, "gradle", "libs.versions.toml")
    return candidate


def bump_catalog(catalog_path: str, version: str) -> bool:
    if not os.path.isfile(catalog_path):
        raise FileNotFoundError(f"Version catalog not found: {catalog_path}")
    with open(catalog_path, encoding="utf-8") as f:
        text = f.read()
    updated = _MEDIAPIPE_VERSION_RE.sub(rf'\1"{version}"', text)
    if updated == text:
        return False
    with open(catalog_path, "w", encoding="utf-8") as f:
        f.write(updated)
    return True


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Update mediapipeTasks* versions in Gradle libs.versions.toml catalogs."
    )
    parser.add_argument("version", help="MediaPipe Tasks version string (e.g. 1.3.0).")
    parser.add_argument(
        "targets",
        nargs="*",
        help="Catalog file or Android project directory paths to update.",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="Update all catalogs under android-common/ and examples/*/android**/gradle/.",
    )
    parser.add_argument(
        "--repo-root",
        default=".",
        help="Repository root path.",
    )
    args = parser.parse_args()

    if not args.all and not args.targets:
        parser.error("Specify one or more target paths, or pass --all.")

    catalogs = _find_all_catalogs(args.repo_root) if args.all else [
        _resolve_target(args.repo_root, t) for t in args.targets
    ]
    changed = []
    for path in catalogs:
        if bump_catalog(path, args.version):
            changed.append(os.path.relpath(path, args.repo_root))
    print(f"Updated {len(changed)} catalog(s) to {args.version}: {changed}")


if __name__ == "__main__":
    main()
