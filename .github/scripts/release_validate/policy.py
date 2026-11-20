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
"""What the outcome of a case means: its status, and what counts as evidence.

Kept apart from the runners so that self_test.py can test it, and so that the
observations of every platform are judged the same way (see judge()).
"""

import hashlib
import json
import re

import verify

# Lines the native code writes to stderr when it creates a GPU delegate for a
# model. MediaPipe has no API that tells which delegate ran, so this log is the
# only evidence that a GPU request did not quietly run on the CPU. These are
# the lines of the Metal delegate, seen on macOS with release 1.2.3. If a
# release words them differently, GPU cases are reported as GPU_UNCONFIRMED
# (never as PASS) until this list is updated.
GPU_EVIDENCE = [
    r'Created TensorFlow Lite delegate for Metal',
    r'Initializing Metal-based API from graph',
    r'Created a Metal device',
]

# Statuses that do not fail the run.
ACCEPTED = ('PASS', 'UNSUPPORTED')


def find_gpu_evidence(native_log):
  """The lines of the native log that show a GPU delegate was created."""
  pattern = re.compile('|'.join(GPU_EVIDENCE))
  # The log prefixes its lines with a time and a thread id; keep the message.
  lines = {re.sub(r'^[A-Z]\d{4} \S+\s+\d+ ', '', line.strip()) for line in native_log.splitlines()
           if pattern.search(line)}
  return sorted(lines)


def _rounded(value):
  if isinstance(value, float):
    return round(value, 4)
  if isinstance(value, list):
    return [_rounded(v) for v in value]
  if isinstance(value, dict):
    return {k: _rounded(v) for k, v in sorted(value.items()) if k != 'seconds'}
  return value


def observation_digest(runs):
  """A fingerprint of what a case returned, to compare two runs."""
  canonical = json.dumps(_rounded(runs), sort_keys=True, ensure_ascii=False)
  return hashlib.sha256(canonical.encode('utf-8')).hexdigest()[:16]


def short_error(error):
  """An error message on one line, without the long graph node names."""
  text = ' '.join(str(error).split())
  # MediaPipe names the failing node by its full path in the graph.
  return re.sub(r'node "[^"]{60,}?([A-Za-z]+)"', r'node "...\1"', text)


def decide(case, worker, verification, gpu_evidence, unsupported):
  """The status of a case, with the reason and an optional note.

  `worker` is what worker.py wrote (or a dict with only `error` when it wrote
  nothing usable).
  """
  error = worker.get('error')
  if unsupported:
    if error and unsupported['error'] in error:
      return 'UNSUPPORTED', f"{unsupported['reason']} MediaPipe says: {unsupported['error']}", None
    if error:
      return 'FAIL', (f"listed as unsupported, but it failed differently: expected the error "
                      f"\"{unsupported['error']}\", got: {short_error(error)}"), None
  if error:
    return 'FAIL', short_error(error), None
  note = None
  if unsupported:
    note = ('This combination is listed as "unsupported" in the spec of the task, but it ran. If the checks '
            'pass, remove it from that list.')
  if verification['status'] == verify.FAILED:
    return 'FAIL', verification['reason'], note
  if verification['status'] == verify.UNVERIFIED:
    return 'UNVERIFIED', verification['reason'], note
  if case['delegate'] == 'GPU' and not gpu_evidence:
    return 'GPU_UNCONFIRMED', ('the output is correct, but the native log has no sign that a GPU delegate was '
                               'created (see GPU_EVIDENCE in policy.py)'), note
  if case['delegate'] == 'CPU' and gpu_evidence:
    return 'FAIL', f"the CPU was requested, but the native log shows a GPU delegate: {gpu_evidence[0]}", note
  return 'PASS', verification['summary'], note


def judge(case, expectation, outcome, gpu_evidence, unsupported):
  """Checks what a runner observed for a case, and decides its status.

  Args:
    case: the case, from spec.build_matrix().
    expectation: its expectation, from spec.resolve().
    outcome: what the runner reports: `runs`, the observations, and `error`,
      the error that ended the case, if any.
    gpu_evidence: the lines of the native log that show a GPU delegate.
    unsupported: the "unsupported" entry of the spec for the case, if any.

  Returns:
    The fields of the result record that say how the case went.
  """
  runs = outcome.get('runs') or []
  verification = {'status': verify.UNVERIFIED, 'reason': '', 'checks': [], 'summary': '-'}
  digest = None
  if not outcome.get('error'):
    verification = verify.verify_case(expectation, runs)
    passed = sum(1 for c in verification['checks'] if c['pass'])
    verification['summary'] = f"{passed}/{len(verification['checks'])} checks" if verification['checks'] else '-'
    digest = observation_digest(runs)
  status, details, note = decide(case, outcome, verification, gpu_evidence, unsupported)
  return {'verification': verification, 'observationDigest': digest, 'status': status, 'details': details,
          'note': note}
