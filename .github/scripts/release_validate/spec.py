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
"""The validation spec: the JSON files in spec/, the one source of truth.

spec/ says, for every task, which models and modes are validated, what each
test feeds to the task, and what must be true about the result. None of it is
specific to the sample of one platform. The Python runner (run_local.py with
python_adapter.py) reads it through this module; a runner for the Android or
iOS apps reads the flat list of cases that `export` writes:

  python3 .github/scripts/release_validate/spec.py check
  python3 .github/scripts/release_validate/spec.py export --platform android --out cases.json

spec/README.md describes the format. This module needs nothing but the Python
standard library.
"""

import argparse
import copy
import functools
import json
import os
import platform as host_platform
import re
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..', '..'))
SPEC_DIR = os.path.join(SCRIPT_DIR, 'spec')

# Expectation types whose result is one observation per step: the expectation
# of each step becomes one entry of `runs`. The other types observe one step.
RUN_TYPES = ('landmarks', 'classification', 'similarity', 'textLabels', 'retrieval', 'decisions', 'selection',
             'proofreading')
SINGLE_TYPES = ('detections', 'faces', 'segmentation', 'audioClassification', 'transcript', 'speech', 'summary')

# Where the name of a file that the sample writes goes in the expectation, by
# (expectation type, where `files` is declared, role of the file). A file is
# checked only on platforms whose samples write files (see index.json).
_FILE_KEYS = {
    ('detections', 'test', 'annotated'): ('annotatedFile',),
    ('faces', 'test', 'annotated'): ('annotatedFile',),
    ('classification', 'test', 'figure'): ('figureFile',),
    ('segmentation', 'test', 'mask'): ('maskFile',),
    ('segmentation', 'test', 'blurred'): ('blurFile',),
    ('speech', 'test', 'audio'): ('wavFile',),
    ('landmarks', 'test', 'figure'): ('figureFile',),
    ('landmarks', 'step', 'annotated'): ('annotatedFile',),
    ('landmarks', 'step', 'figure'): ('figureFile',),
    ('landmarks', 'step', 'mask'): ('mask', 'fileName'),
    ('selection', 'step', 'overlay'): ('overlayFile',),
}

_TASK_KEYS = {'domain', 'task', 'heading', 'samples', 'models', 'modes', 'unsupported', 'tests'}
# What a test may hold besides its steps: data of the task that all steps share.
FIXTURES = ('probes', 'index', 'questions', 'listenWith')
_TEST_KEYS = {'name', 'models', 'modes', 'about', 'options', 'files', 'inputs', 'steps', 'expect', 'inMode',
              *FIXTURES}
# The inputs a step can have; everything else in a step is listed in _STEP_ONLY.
STEP_INPUTS = ('image', 'audio', 'text', 'pair', 'query', 'point', 'lasso', 'negativeStroke')
_STEP_ONLY = ('caption', 'files', 'expect')
_OVERLAY_KEYS = {'about', 'options', 'expect'}


class SpecError(Exception):
  """The spec is inconsistent, or a case is not part of it."""


def _without_notes(value):
  """A "note" is a comment: JSON has none. It is dropped when the spec is read."""
  if isinstance(value, dict):
    return {k: _without_notes(v) for k, v in value.items() if k != 'note'}
  if isinstance(value, list):
    return [_without_notes(v) for v in value]
  return value


def _read(path):
  with open(path, encoding='utf-8') as f:
    return _without_notes(json.load(f))


@functools.lru_cache(maxsize=None)
def _load(spec_dir):
  index = _read(os.path.join(spec_dir, 'index.json'))
  manifest = _read(os.path.join(spec_dir, 'assets.json'))
  assets = {}
  for kind, group in (('model', 'models'), ('input', 'inputs')):
    for name, entry in manifest[group].items():
      assets[name] = dict(entry, kind=kind)
  tasks = [_read(os.path.join(spec_dir, *path.split('/')) + '.json') for path in index['tasks']]
  return {'index': index, 'assets': assets, 'tasks': tasks}


def index():
  return _load(SPEC_DIR)['index']


def tasks():
  """The task specs, in the order of the matrix. Do not modify what is returned."""
  return _load(SPEC_DIR)['tasks']


def assets():
  """Every pinned file by name, with "kind" set to "model" or "input"."""
  return _load(SPEC_DIR)['assets']


def platforms():
  return index()['platforms']


DELEGATES = tuple(index()['delegates'])


def task_spec(name):
  for task in tasks():
    if task['task'] == name:
      return task
  raise SpecError(f'no task "{name}" in the spec')


def _parse_task_filter(task_filter):
  return [s.strip().lower() for s in task_filter.split(',') if s.strip()]


def _filter_selects(entry, task):
  """Whether one filter entry selects a task.

  An entry is a domain ("text"), a task ("text_embedder") or both
  ("text/text_embedder"), matched exactly.
  """
  return entry in (task['domain'], task['task'], f"{task['domain']}/{task['task']}")


def unknown_task_filters(task_filter=''):
  """The entries of a task filter that select nothing (typos, removed tasks)."""
  return [entry for entry in _parse_task_filter(task_filter) if not any(_filter_selects(entry, t) for t in tasks())]


def build_matrix(task_filter='', delegates=DELEGATES, include_large_models=True, platform='python'):
  """The cases to run: every task, model, mode and delegate of the spec.

  A case is identified by its `id`, which is the same on every platform.
  `sample` is where the sample of the platform lives; tasks without a sample
  for the platform are left out.
  """
  filters = _parse_task_filter(task_filter)
  cases = []
  for task in tasks():
    if platform not in task['samples']:
      continue
    if filters and not any(_filter_selects(f, task) for f in filters):
      continue
    for model in task['models']:
      if model.get('large') and not include_large_models:
        continue
      for mode in task['modes']:
        for delegate in DELEGATES:
          if delegate not in delegates:
            continue
          cases.append({
              'id': '__'.join([task['domain'], task['task'], model['id'], mode['id'], delegate]),
              'platform': platform,
              'domain': task['domain'],
              'task': task['task'],
              'heading': task['heading'],
              'sample': task['samples'][platform],
              'modelId': model['id'],
              'modelAsset': model['asset'],
              'largeModel': bool(model.get('large')),
              'mode': mode['id'],
              'delegate': delegate,
          })
  return cases


def known_unsupported(case, system=None, platform=None):
  """The "unsupported" entry of the task that applies to a case, or None.

  These are the combinations MediaPipe itself declares unsupported: it rejects
  them with the error text of the entry. Such a case is UNSUPPORTED (never a
  pass) as long as it fails with exactly that error; any other failure is a
  FAIL. An entry may be limited to a platform, a model, and an operating system
  (`system`, as `platform.system()` names it).
  """
  platform = platform or case.get('platform', 'python')
  system = system or host_platform.system()
  for entry in task_spec(case['task']).get('unsupported', []):
    if entry['delegate'] != case['delegate']:
      continue
    if 'platform' in entry and entry['platform'] != platform:
      continue
    if 'model' in entry and entry['model'] != case['modelId']:
      continue
    if 'system' in entry and entry['system'] != system:
      continue
    return dict(entry, task=case['task'])
  return None


def _applies(test, task, model_id, mode):
  models = test.get('models') or [m['id'] for m in task['models']]
  modes = test.get('modes') or [m['id'] for m in task['modes']]
  return model_id in models and mode in modes


def find_test(task, model_id, mode):
  """The one test of a task that covers a model and a mode."""
  matches = [test for test in task['tests'] if _applies(test, task, model_id, mode)]
  if len(matches) != 1:
    raise SpecError(f"{task['task']}: {len(matches)} tests cover model \"{model_id}\" in mode \"{mode}\", "
                    'expected exactly one')
  return matches[0]


def _set_file(target, path, name):
  for key in path[:-1]:
    target = target.get(key)
    if not isinstance(target, dict):
      return  # For example a mask file without a mask expectation.
  target[path[-1]] = name


def _expectation(expect, steps, files, with_files):
  """What verify.py checks: the expectation of the test with those of its steps.

  The verifier sees a step as a whole: its inputs (a check may need the text
  that was proofread) together with what is expected of its result.
  """
  expectation = copy.deepcopy(expect)
  kind = expectation['type']
  if kind in RUN_TYPES:
    runs = []
    for step in steps:
      run = {k: copy.deepcopy(v) for k, v in step.items() if k not in _STEP_ONLY}
      run.update(copy.deepcopy(step.get('expect', {})))
      if with_files:
        for role, name in step.get('files', {}).items():
          _set_file(run, _FILE_KEYS[(kind, 'step', role)], name)
      runs.append(run)
    expectation['runs'] = runs
    declared = files
  else:
    step = steps[0]
    for key, value in step.items():
      if key not in _STEP_ONLY:
        expectation.setdefault(key, copy.deepcopy(value))
    declared = {**files, **step.get('files', {})}
  if with_files:
    for role, name in declared.items():
      _set_file(expectation, _FILE_KEYS[(kind, 'test', role)], name)
  return expectation


def _shown(steps):
  """The inputs of the steps, as the report lists them."""
  shown = []
  for step in steps:
    label = step.get('caption', step['key'])
    entries = [{'kind': kind, 'asset': step[kind], 'label': label} for kind in ('image', 'audio') if kind in step]
    if 'text' in step:
      entries.append({'kind': 'text', 'value': step['text'], 'label': label})
    shown += [entry for entry in entries if entry not in shown]
  return shown


def resolve(case, platform=None):
  """Everything the spec says about one case.

  Returns a dict with:
    options: the task options of the case (those of its mode, of its test, and
      of the test in that mode, in this order of precedence).
    steps: what is fed to the task, one step per observation. Each has a `key`
      and inputs (see STEP_INPUTS); `files` names what the sample writes.
    files: the files the sample writes once for all steps.
    expectation: what verify.py checks. On platforms whose samples write no
      files, it has no file checks.
    description, inputs: for the report.
    name: the name of the test, if it has one.
    The entries of FIXTURES that the test defines.
  """
  platform = platform or case.get('platform', 'python')
  task = task_spec(case['task'])
  test = find_test(task, case['modelId'], case['mode'])
  overlay = test.get('inMode', {}).get(case['mode'], {})
  mode = next(m for m in task['modes'] if m['id'] == case['mode'])
  steps = copy.deepcopy(test['steps'])
  files = dict(test.get('files', {}))
  with_files = bool(platforms()[platform].get('files'))
  about = overlay.get('about', test.get('about', ''))
  written = list(files.values())
  for step in steps:
    written += [name for name in step.get('files', {}).values() if name not in written]
  if with_files and written:
    about += ' The files the sample writes are checked too: ' + ', '.join(written) + '.'
  setup = (f"Runs {case['sample']} with model \"{case['modelId']}\" in mode \"{case['mode']}\" on the "
           f"{case['delegate']} delegate.")
  resolved = {
      'name': test.get('name'),
      'options': {**mode.get('options', {}), **test.get('options', {}), **overlay.get('options', {})},
      'steps': steps,
      'files': files,
      'expectation': _expectation({**test['expect'], **overlay.get('expect', {})}, steps, files, with_files),
      'description': f'{setup} {about}'.strip(),
      'inputs': copy.deepcopy(test['inputs']) if 'inputs' in test else _shown(steps),
  }
  for name in FIXTURES:
    if name in test:
      resolved[name] = copy.deepcopy(test[name])
  return resolved


def step_assets(resolved):
  """The names of the input files a resolved case uses (not its model)."""
  names = set()
  for step in resolved['steps']:
    names.update(step[kind] for kind in ('image', 'audio') if kind in step)
    names.update(item['image'] for item in step.get('pair', []) if 'image' in item)
  names.update(resolved.get('index', {}).get('images', []))
  if 'listenWith' in resolved:
    names.add(resolved['listenWith'])
  names.update(item['asset'] for item in resolved['inputs'] if 'asset' in item)
  return sorted(names)


def export(platform, task_filter='', delegates=DELEGATES, include_large_models=True):
  """The cases of a platform as one flat, self-contained list.

  This is what a runner on another platform reads: every case with its
  options, its steps and its expectation already resolved, and the pinned files
  it needs. Nothing in it has to be looked up in spec/.
  """
  if platform not in platforms():
    raise SpecError(f'unknown platform "{platform}"; index.json lists: ' + ', '.join(platforms()))
  cases, needed = [], set()
  for case in build_matrix(task_filter, delegates, include_large_models, platform):
    resolved = resolve(case)
    entry = dict(case)
    entry['options'] = resolved['options']
    entry['steps'] = [{k: v for k, v in step.items() if k != 'expect'} for step in resolved['steps']]
    entry['files'] = resolved['files']
    for name in FIXTURES:
      if name in resolved:
        entry[name] = resolved[name]
    entry['expectation'] = resolved['expectation']
    entry['description'] = resolved['description']
    entry['inputs'] = resolved['inputs']
    cases.append(entry)
    needed.update([case['modelAsset']] + step_assets(resolved))
  return {
      'version': index()['version'],
      'platform': platform,
      'cases': cases,
      'assets': {name: assets()[name] for name in sorted(needed)},
  }


def _check_asset(found, name, kind, where):
  entry = assets().get(name)
  if entry is None:
    found.append(f'{where}: "{name}" is not in assets.json')
  elif entry['kind'] != kind:
    found.append(f'{where}: "{name}" is listed under {entry["kind"]}s in assets.json, expected a {kind}')


def _test_problems(task, test, where):
  found = []
  unknown = sorted(set(test) - _TEST_KEYS)
  if unknown:
    found.append(f"{where}: unknown keys {unknown}")
  for name in ('steps', 'expect'):
    if not test.get(name):
      found.append(f'{where}: "{name}" is missing')
      return found
  model_ids, mode_ids = [m['id'] for m in task['models']], [m['id'] for m in task['modes']]
  found += [f'{where}: unknown model "{m}"' for m in test.get('models', []) if m not in model_ids]
  found += [f'{where}: unknown mode "{m}"' for m in test.get('modes', []) if m not in mode_ids]
  modes = test.get('modes') or mode_ids
  for mode, overlay in test.get('inMode', {}).items():
    if mode not in modes:
      found.append(f'{where}: inMode "{mode}" is not a mode of the test')
    if set(overlay) - _OVERLAY_KEYS:
      found.append(f'{where}: inMode "{mode}" may only hold {sorted(_OVERLAY_KEYS)}')
  for mode in modes:
    if not test.get('inMode', {}).get(mode, {}).get('about', test.get('about')):
      found.append(f'{where}: mode "{mode}" has no "about"')

  kind = test['expect'].get('type')
  if kind not in RUN_TYPES + SINGLE_TYPES:
    found.append(f'{where}: unknown expectation type "{kind}"')
    return found
  steps = test['steps']
  keys = [step.get('key') for step in steps]
  if None in keys or len(set(keys)) != len(keys):
    found.append(f'{where}: every step needs a "key" of its own')
  if kind in SINGLE_TYPES and len(steps) != 1:
    found.append(f'{where}: "{kind}" observes one step, the test has {len(steps)}')
  for role in test.get('files', {}):
    if (kind, 'test', role) not in _FILE_KEYS:
      found.append(f'{where}: no check is defined for the file "{role}" of a "{kind}" test')
  shown = 'inputs' in test
  for step in steps:
    at = f"{where}, step \"{step.get('key')}\""
    unknown = sorted(set(step) - {'key', *STEP_INPUTS, *_STEP_ONLY})
    if unknown:
      found.append(f'{at}: unknown keys {unknown}')
    if not any(name in step for name in STEP_INPUTS):
      found.append(f'{at}: has no input')
    if kind in SINGLE_TYPES and 'expect' in step:
      found.append(f'{at}: a "{kind}" test states its expectation in the test, not in the step')
    level = 'step' if kind in RUN_TYPES else 'test'
    for role in step.get('files', {}):
      if (kind, level, role) not in _FILE_KEYS:
        found.append(f'{at}: no check is defined for the file "{role}" of a "{kind}" step')
    for media in ('image', 'audio'):
      if media in step:
        _check_asset(found, step[media], 'input', at)
    for item in step.get('pair', []):
      if set(item) not in ({'image'}, {'text'}):
        found.append(f'{at}: an entry of "pair" is {{"image": ...}} or {{"text": ...}}')
      elif 'image' in item:
        _check_asset(found, item['image'], 'input', at)
    if not shown and not any(name in step for name in ('image', 'audio', 'text')):
      found.append(f'{at}: the report cannot show this step; give the test "inputs"')
    probes = test.get('probes', {})
    for name in step.get('expect', {}).get('selected', []) + step.get('expect', {}).get('notSelected', []):
      if name not in probes:
        found.append(f'{at}: "{name}" is not one of the probes of the test')
  for item in test.get('inputs', []):
    if 'asset' in item:
      _check_asset(found, item['asset'], 'input', f'{where}, inputs')
  for name in test.get('index', {}).get('images', []):
    _check_asset(found, name, 'input', f'{where}, index')
  if 'listenWith' in test:
    _check_asset(found, test['listenWith'], 'model', f'{where}, listenWith')
  return found


def problems():
  """What is wrong with the spec: an empty list when it is consistent."""
  found = []
  listed = set(index()['tasks'])
  on_disk = set()
  for root, _, names in os.walk(SPEC_DIR):
    for name in names:
      relative = os.path.relpath(os.path.join(root, name), SPEC_DIR).replace(os.sep, '/')
      if name.endswith('.json') and relative not in ('index.json', 'assets.json'):
        on_disk.add(relative[:-len('.json')])
  found += [f'index.json lists "{path}", which does not exist' for path in sorted(listed - on_disk)]
  found += [f'spec/{path}.json is not listed in index.json' for path in sorted(on_disk - listed)]

  used = set()
  for path, task in zip(index()['tasks'], tasks()):
    where = f'{path}.json'
    unknown = sorted(set(task) - _TASK_KEYS)
    if unknown:
      found.append(f'{where}: unknown keys {unknown}')
    if f"{task.get('domain')}/{task.get('task')}" != path:
      found.append(f'{where}: "domain" and "task" do not match the name of the file')
    for platform, sample in task['samples'].items():
      if platform not in platforms():
        found.append(f'{where}: unknown platform "{platform}"')
      if not os.path.exists(os.path.join(REPO_DIR, sample)):
        found.append(f'{where}: the {platform} sample {sample} does not exist')
    for group in ('models', 'modes'):
      ids = [item['id'] for item in task[group]]
      if not ids or len(set(ids)) != len(ids):
        found.append(f'{where}: "{group}" needs at least one entry, each with an "id" of its own')
    for model in task['models']:
      _check_asset(found, model['asset'], 'model', where)
      used.add(model['asset'])
    for number, test in enumerate(task['tests']):
      found += _test_problems(task, test, f'{where}, test {number + 1}')
    for model in task['models']:
      for mode in task['modes']:
        count = sum(1 for test in task['tests'] if _applies(test, task, model['id'], mode['id']))
        if count != 1:
          found.append(f"{where}: {count} tests cover model \"{model['id']}\" in mode \"{mode['id']}\", "
                       'expected exactly one')
    for entry in task.get('unsupported', []):
      if entry.get('delegate') not in DELEGATES or not entry.get('error') or not entry.get('reason'):
        found.append(f'{where}: an "unsupported" entry needs a delegate, an error and a reason')
      if 'model' in entry and entry['model'] not in [m['id'] for m in task['models']]:
        found.append(f"{where}: \"unsupported\" names the unknown model \"{entry['model']}\"")
      if 'platform' in entry and entry['platform'] not in platforms():
        found.append(f"{where}: \"unsupported\" names the unknown platform \"{entry['platform']}\"")
  if found:
    return found  # What follows resolves the cases, which needs a consistent spec.

  for platform in platforms():
    for case in build_matrix(platform=platform):
      used.update(step_assets(resolve(case)))
  found += [f'assets.json: "{name}" is not used by any case' for name in sorted(set(assets()) - used)]
  for name, entry in assets().items():
    pinned = re.fullmatch(r'[0-9a-f]{64}', str(entry.get('sha256'))) and isinstance(entry.get('bytes'), int)
    hosted = (all(entry.get(key) for key in ('repo', 'file', 'revision')) and 'url' not in entry
              and re.fullmatch(r'[0-9a-f]{40}', entry['revision']))
    if not pinned or not (hosted or (entry.get('url') and 'repo' not in entry)):
      found.append(f'assets.json: "{name}" needs "sha256", "bytes", and either "url" or "repo", "file" and a '
                   '40-character "revision"')
  return found


def main(argv=None):
  parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  commands = parser.add_subparsers(dest='command', required=True)
  commands.add_parser('check', help='Check that the spec is consistent.')
  exporter = commands.add_parser('export', help='Write the cases of a platform as one flat JSON file.')
  exporter.add_argument('--platform', required=True, choices=sorted(platforms()))
  exporter.add_argument('--tasks', default='', metavar='LIST', help='As for run_local.py. Default: all tasks.')
  exporter.add_argument('--delegates', default=','.join(DELEGATES), metavar='LIST')
  exporter.add_argument('--small-models-only', action='store_true')
  exporter.add_argument('--out', metavar='FILE', help='Default: standard output.')
  options = parser.parse_args(argv)

  found = problems()
  if found:
    print('\n'.join(found), file=sys.stderr)
    print(f'{len(found)} problem(s) in {SPEC_DIR}', file=sys.stderr)
    return 1
  if options.command == 'check':
    matrix = build_matrix()
    print(f'{len(tasks())} tasks, {len(matrix)} cases per platform, {len(assets())} pinned files: consistent')
    return 0
  unknown = unknown_task_filters(options.tasks)
  if unknown:
    parser.error(f"unknown task or domain in --tasks: {', '.join(unknown)}")
  delegates = [d.strip().upper() for d in options.delegates.split(',') if d.strip()]
  exported = export(options.platform, options.tasks, delegates, not options.small_models_only)
  text = json.dumps(exported, indent=1, ensure_ascii=False) + '\n'
  if options.out:
    with open(options.out, 'w', encoding='utf-8') as f:
      f.write(text)
    print(f"Wrote {len(exported['cases'])} cases to {options.out}")
  else:
    if hasattr(sys.stdout, 'reconfigure'):
      sys.stdout.reconfigure(encoding='utf-8')
    sys.stdout.write(text)
  return 0


if __name__ == '__main__':
  sys.exit(main())
