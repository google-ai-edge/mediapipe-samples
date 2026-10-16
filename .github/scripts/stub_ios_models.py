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

"""Stubs ML models referenced by an Xcode project to avoid build-time downloads."""

import argparse
import json
import os
import subprocess
import sys

MODEL_EXTENSIONS = (".tflite", ".task", ".litertlm")


def resolve_pbxproj_path(path: str) -> str:
    """Finds the project.pbxproj file given a project path or directory."""
    if os.path.isfile(path) and path.endswith("project.pbxproj"):
        return path
    if path.endswith(".xcodeproj"):
        candidate = os.path.join(path, "project.pbxproj")
        if os.path.isfile(candidate):
            return candidate
    for root, _, files in os.walk(path):
        if "project.pbxproj" in files:
            return os.path.join(root, "project.pbxproj")
    raise FileNotFoundError(f"Could not find project.pbxproj in {path}")


def stub_models_for_project(project_path: str, dry_run: bool = False) -> list[str]:
    """Inspects project.pbxproj and creates stub files for referenced models."""
    pbx_path = resolve_pbxproj_path(project_path)
    base_dir = os.path.dirname(os.path.dirname(pbx_path))

    raw_json = subprocess.check_output(["plutil", "-convert", "json", "-o", "-", pbx_path])
    data = json.loads(raw_json)
    objects = data.get("objects", {})

    # Map child UUID -> parent group relative path
    parent_paths: dict[str, str] = {}
    for obj in objects.values():
        if obj.get("isa") == "PBXGroup":
            group_path = obj.get("path", "")
            for child_id in obj.get("children", []):
                parent_paths[child_id] = group_path

    created_stubs: list[str] = []
    for obj_id, obj in objects.items():
        if obj.get("isa") == "PBXFileReference":
            file_path = obj.get("path", "")
            if file_path.endswith(MODEL_EXTENSIONS):
                group_prefix = parent_paths.get(obj_id, "")
                rel_path = os.path.join(group_prefix, file_path) if group_prefix else file_path
                full_path = os.path.join(base_dir, rel_path)

                if not os.path.exists(full_path):
                    if not dry_run:
                        os.makedirs(os.path.dirname(full_path), exist_ok=True)
                        with open(full_path, "wb"):
                            pass
                    created_stubs.append(full_path)
                    print(f"Stubbed referenced model: {rel_path}")
                else:
                    print(f"Model already exists on disk: {rel_path}")

    return created_stubs


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create empty stub files for models referenced in Xcode project files."
    )
    parser.add_argument(
        "project",
        help="Path to an .xcodeproj bundle, project.pbxproj, or directory containing one.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print referenced models without creating stub files.",
    )
    args = parser.parse_args()

    try:
        stub_models_for_project(args.project, dry_run=args.dry_run)
    except Exception as e:
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)


if __name__ == "__main__":
    main()
