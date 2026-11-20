#!/usr/bin/env python3
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
"""Checks what the runner of another platform observed, against the spec.

  python3 .github/scripts/release_validate/spec.py export --platform android --out cases.json
  (the Android runner runs these cases in the apps, and writes observations.json)
  python3 .github/scripts/release_validate/verify_observations.py observations.json

A runner for the Android or iOS apps needs no checks of its own. It reports,
for every case, what the app returned, in the form spec/README.md describes
("Observations"). This script judges that with the expectations, the verifiers
and the rules that the Python samples are judged with, and writes the same
report. The exit code is 0 only if every case passed.

The file it reads is JSON:

  {"platform": "android",
   "environment": {"mediapipeVersion": "1.3.0", "device": "Pixel 10", "system": "Android 16"},
   "results": [
     {"id": "vision__object_detector__efficientdet_lite0_int8__image__CPU",
      "runs": [{"key": "cat_and_dog.jpg", "data": {"imageWidth": 640, "imageHeight": 416, "detections": []}}],
      "error": null,
      "gpuEvidence": [],
      "durationSec": 0.4}]}

`error` is the message of the error that ended a case, `gpuEvidence` the lines
of the native log of the app that show a GPU delegate was created for it. A
case that was to run (see --tasks, --delegates) and has no result fails.

This script needs nothing but the Python standard library.
"""

import argparse
import datetime
import json
import os
import pathlib
import shutil
import sys

sys.dont_write_bytecode = True

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..', '..'))
sys.path.insert(0, SCRIPT_DIR)

import policy  # pylint: disable=g-import-not-at-top,wrong-import-position
import report  # pylint: disable=wrong-import-position
import spec  # pylint: disable=wrong-import-position


class ObservationError(Exception):
  """The file of observations cannot be judged at all."""


def judge_observations(document, task_filter='', delegates=spec.DELEGATES, include_large_models=True):
  """Judges every case that was to run, and returns the result records.

  Raises:
    ObservationError: if the document names no known platform, or reports a
      result for something that is not a case of the spec.
  """
  platform = document.get('platform')
  if platform not in spec.platforms():
    raise ObservationError(f'"platform" is {platform!r}; the spec knows: ' + ', '.join(spec.platforms()))
  reported = {}
  for entry in document.get('results') or []:
    if entry.get('id') in reported:
      raise ObservationError(f"two results are reported for {entry.get('id')}")
    reported[entry.get('id')] = entry
  known = {case['id'] for case in spec.build_matrix(platform=platform)}
  unknown = sorted(str(name) for name in set(reported) - known)
  if unknown:
    raise ObservationError('not cases of the spec: ' + ', '.join(unknown[:5]) +
                           (f' and {len(unknown) - 5} more' if len(unknown) > 5 else ''))

  results = []
  for case in spec.build_matrix(task_filter, delegates, include_large_models, platform):
    resolved = spec.resolve(case)
    outcome = reported.get(case['id']) or {'error': 'the runner reported no result for this case'}
    evidence = [str(line) for line in outcome.get('gpuEvidence') or []]
    unsupported = spec.known_unsupported(case, system=document.get('system'))
    result = dict(case, description=resolved['description'], inputs=resolved['inputs'],
                  outputs=list(outcome.get('outputs') or []), logs={}, gpuEvidence=evidence,
                  runs=outcome.get('runs') or [], error=outcome.get('error'), traceback=outcome.get('traceback'),
                  baseline=None, command='', stdout=outcome.get('log') or '', knownUnsupported=unsupported,
                  durationSec=float(outcome.get('durationSec') or 0.0))
    result.update(policy.judge(case, resolved['expectation'], outcome, evidence, unsupported))
    results.append(result)
  return results


def main(argv=None):
  parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument('observations', metavar='OBSERVATIONS_JSON')
  parser.add_argument('--tasks', default='', metavar='LIST',
                      help='The tasks that were to run, as for run_local.py. Default: all tasks.')
  parser.add_argument('--delegates', default=','.join(spec.DELEGATES), metavar='LIST',
                      help='The delegates that were to run. Default: cpu,gpu.')
  parser.add_argument('--small-models-only', action='store_true', help='The large models were not to run.')
  parser.add_argument('--report-dir', metavar='DIR',
                      help='Default: release-validation-report-<platform> in the repo. It is deleted and written anew.')
  parser.add_argument('--assets-dir', default=os.path.join(REPO_DIR, '.release_validation', 'assets'), metavar='DIR',
                      help='Where the pinned inputs are, to show them in the report. Nothing is downloaded.')
  parser.add_argument('--allow-unconfirmed-gpu', action='store_true',
                      help='Do not fail for GPU cases whose output is correct but that come without GPU evidence.')
  options = parser.parse_args(argv)

  delegates = [d.strip().upper() for d in options.delegates.split(',') if d.strip()]
  unknown = [d for d in delegates if d not in spec.DELEGATES] + spec.unknown_task_filters(options.tasks)
  if unknown or not delegates:
    parser.error(f"unknown in --tasks or --delegates: {', '.join(unknown) or 'no delegate'}")
  try:
    with open(options.observations, encoding='utf-8') as f:
      document = json.load(f)
    results = judge_observations(document, options.tasks, delegates, not options.small_models_only)
  except (OSError, ValueError, ObservationError) as error:
    print(f'{options.observations}: {error}', file=sys.stderr)
    return 2
  if not results:
    print(f'No case matches --tasks "{options.tasks}".', file=sys.stderr)
    return 2

  platform = document['platform']
  report_dir = os.path.abspath(options.report_dir or os.path.join(REPO_DIR, f'release-validation-report-{platform}'))
  if os.path.isdir(report_dir):
    shutil.rmtree(report_dir)
  os.makedirs(os.path.join(report_dir, 'inputs'))
  for result in results:
    for item in result['inputs']:
      source = os.path.join(options.assets_dir, item.get('asset', ''))
      if 'asset' in item and os.path.isfile(source):
        item['file'] = f"inputs/{item['asset']}"
        shutil.copyfile(source, os.path.join(report_dir, 'inputs', item['asset']))

  counts = report.summarize_counts(results)
  accepted = policy.ACCEPTED + (('GPU_UNCONFIRMED',) if options.allow_unconfirmed_gpu else ())
  failing = [r for r in results if r['status'] not in accepted]
  verdict = (f"PASSED: {counts['PASS']} of {counts['total']} cases passed" if not failing
             else f"FAILED: {len(failing)} of {counts['total']} cases did not pass")
  if counts['UNSUPPORTED']:
    verdict += f", {counts['UNSUPPORTED']} unsupported by design"
  now = datetime.datetime.now().astimezone().isoformat(timespec='seconds')
  report.write_report_files(report_dir, {
      'platform': platform,
      'startedAt': document.get('startedAt') or now,
      'finishedAt': document.get('finishedAt') or now,
      'environment': dict(document.get('environment') or {}),
      'baseline': None,
      'counts': counts,
      'passed': not failing,
      'verdict': verdict,
      'results': results,
  })

  for index, result in enumerate(results):
    label = f"{result['domain']}/{result['task']} {result['modelId']} {result['mode']} {result['delegate']}"
    why = '' if result['status'] == 'PASS' else ': ' + result['details'][:260]
    print(f"[{index + 1:>2}/{len(results)}] {result['status']:<15} {label} ({result['verification']['summary']}){why}")
  print()
  for status in report.ORDER:
    if counts.get(status):
      print(f'{status:<16}{counts[status]:>3}  {report.STATUSES[status][1]}')
  print(f'\n{verdict}')
  print('Report: ' + pathlib.Path(report_dir, 'index.html').resolve().as_uri())
  return 0 if not failing else 1


if __name__ == '__main__':
  sys.exit(main())
