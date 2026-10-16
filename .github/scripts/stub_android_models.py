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

"""Stubs ML models and test assets downloaded by Gradle to avoid build-time downloads."""

import argparse
import glob
import os
import re
import sys


def _strip_comments(text: str) -> str:
    """Removes block (/* ... */) and single-line (// ...) comments from Gradle text."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    text = re.sub(r"//.*", "", text)
    return text


def _extract_properties(gradle_texts: list[str], app_dir: str) -> dict[str, str]:
    """Extracts project.ext / ext string properties from app/build.gradle and download*.gradle."""
    props: dict[str, str] = {
        "ASSET_DIR": os.path.join(app_dir, "src", "main", "assets"),
        "TEST_ASSET_DIR": os.path.join(app_dir, "src", "androidTest", "assets"),
        "TEST_ASSETS_DIR": os.path.join(app_dir, "src", "androidTest", "assets"),
    }

    for text in gradle_texts:
        clean = _strip_comments(text)
        for match in re.finditer(
            r"(?:project\.ext\.)?([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.+)", clean
        ):
            key = match.group(1).strip()
            rhs = match.group(2).strip()
            if "projectDir" in rhs:
                m_rel = re.search(r"['\"]([^'\"]+)['\"]", rhs)
                if m_rel:
                    props[key] = os.path.join(app_dir, m_rel.group(1).lstrip("/"))
            else:
                m_str = re.match(r"^['\"]([^'\"]+)['\"]$", rhs)
                if m_str:
                    props[key] = m_str.group(1)

    return props


def _resolve_dest_expression(expr: str, props: dict[str, str], app_dir: str) -> str | None:
    """Evaluates a Gradle Download `dest` expression against extracted properties."""
    tokens = [t.strip() for t in expr.split("+")]
    parts: list[str] = []
    for token in tokens:
        m_lit = re.match(r"^['\"]([^'\"]*)['\"]$", token)
        if m_lit:
            parts.append(m_lit.group(1))
            continue
        m_prop = re.match(r"^(?:project\.ext\.|ext\.)?([A-Za-z_][A-Za-z0-9_]*)$", token)
        if m_prop and m_prop.group(1) in props:
            parts.append(props[m_prop.group(1)])
            continue
        return None

    combined = "".join(parts)
    if not os.path.isabs(combined):
        combined = os.path.join(app_dir, combined)
    return os.path.normpath(combined)


def stub_models_for_project(project_dir: str, dry_run: bool = False) -> list[str]:
    """Inspects app/download*.gradle and creates stub files for all Download task destinations."""
    project_dir = os.path.abspath(project_dir)
    app_dir = os.path.join(project_dir, "app")
    if not os.path.isdir(app_dir):
        raise FileNotFoundError(f"Could not find app/ directory in {project_dir}")

    download_gradles = sorted(glob.glob(os.path.join(app_dir, "download*.gradle*")))
    if not download_gradles:
        print(f"No download*.gradle files found in {app_dir}")
        return []

    gradle_texts: list[str] = []
    for build_name in ("build.gradle", "build.gradle.kts"):
        build_path = os.path.join(app_dir, build_name)
        if os.path.isfile(build_path):
            with open(build_path, encoding="utf-8") as f:
                gradle_texts.append(f.read())

    dl_contents: list[tuple[str, str]] = []
    for dl_path in download_gradles:
        with open(dl_path, encoding="utf-8") as f:
            content = f.read()
            gradle_texts.append(content)
            dl_contents.append((dl_path, content))

    props = _extract_properties(gradle_texts, app_dir)
    created_stubs: list[str] = []

    for _, content in dl_contents:
        clean = _strip_comments(content)
        for match in re.finditer(r"^\s*dest\s+([^\r\n]+)", clean, flags=re.MULTILINE):
            expr = match.group(1).strip()
            full_path = _resolve_dest_expression(expr, props, app_dir)
            if not full_path:
                print(f"Warning: Could not resolve dest expression: {expr}", file=sys.stderr)
                continue

            rel_path = os.path.relpath(full_path, project_dir)
            if not os.path.exists(full_path):
                if not dry_run:
                    os.makedirs(os.path.dirname(full_path), exist_ok=True)
                    with open(full_path, "wb"):
                        pass
                created_stubs.append(full_path)
                print(f"Stubbed referenced model/asset: {rel_path}")
            else:
                print(f"Model/asset already exists on disk: {rel_path}")

    return created_stubs


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Create empty stub files for models/assets referenced in Android Gradle download tasks."
    )
    parser.add_argument(
        "project",
        help="Path to an Android Gradle project root directory (containing app/).",
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
