# Copyright 2026 The MediaPipe Authors. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Whether the Android samples can be driven through the cases of the spec.

The Android samples run in the validation app (android-validation/). For each
task, an adapter written in Kotlin drives the sample through its ViewModel:
android-validation/adapters/<task>/<Task>Adapter.kt. It is the counterpart of
python_adapter.py, and it fails a case at run time rather than run it with
other options than the spec states. What can be checked without a phone is
checked here, by self_test.py.
"""

import os
import re

import spec

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..', '..'))
ADAPTERS_DIR = os.path.join(REPO_DIR, 'android-validation', 'adapters')


def adapter_path(task):
  """Where the adapter of a task ("object_detector") is."""
  name = ''.join(word.capitalize() for word in task.split('_')) + 'Adapter'
  return os.path.join(ADAPTERS_DIR, task, name + '.kt')


def problems():
  """What keeps the Android cases of the spec from running: an empty list when nothing does."""
  found = []
  for task in spec.tasks():
    sample = task['samples'].get('android')
    if not sample:
      continue
    name = task['task']
    cases = [case for case in spec.build_matrix(name, platform='android') if case['task'] == name]
    build_file = os.path.join(REPO_DIR, sample, 'app', 'build.gradle.kts')
    if not os.path.isfile(build_file):
      found.append(f'{name}: {sample} is not an Android app (no app/build.gradle.kts)')
    path = adapter_path(name)
    relative = os.path.relpath(path, REPO_DIR)
    if not os.path.isfile(path):
      found.append(f'{name}: the spec has {len(cases)} Android cases, but there is no adapter {relative}')
      continue
    with open(path, encoding='utf-8') as f:
      source = f.read()
    # Every model of an Android case must be one the adapter knows, by its id in the spec.
    for model in sorted({case['modelId'] for case in cases}):
      if not re.search(r'"' + re.escape(model) + r'"', source):
        found.append(f'{name}: {relative} does not know the model "{model}" of the spec')
  return found
