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
"""Runs the release validation of the Python samples on this machine.

  python3 .github/scripts/release_validate/run_local.py
  python3 .github/scripts/release_validate/run_local.py --mediapipe mediapipe==1.2.3
  python3 .github/scripts/release_validate/run_local.py --tasks vision/face_detector,audio --delegates cpu

It creates a Python environment with the MediaPipe under test and pinned
versions of everything else, downloads the models and inputs (pinned by
checksum), runs every sample once per delegate in a process of its own, checks
what each returned against the ground truth in expectations.py, and writes the
report to --report-dir. The exit code is 0 only if every case passed.

This script itself needs nothing but the Python standard library.
"""

import argparse
import datetime
import json
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys
import time

sys.dont_write_bytecode = True

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..', '..'))
sys.path.insert(0, SCRIPT_DIR)

import assets  # pylint: disable=g-import-not-at-top,wrong-import-position
import cases  # pylint: disable=wrong-import-position
import expectations  # pylint: disable=wrong-import-position
import policy  # pylint: disable=wrong-import-position
import report  # pylint: disable=wrong-import-position
import self_test  # pylint: disable=wrong-import-position
import verify  # pylint: disable=wrong-import-position

CONSTRAINTS = os.path.join(SCRIPT_DIR, 'constraints.txt')
WORKER = os.path.join(SCRIPT_DIR, 'worker.py')

# Environment variables that would hide the native log, and with it the
# evidence above.
LOG_SILENCERS = ('GLOG_minloglevel', 'TF_CPP_MIN_LOG_LEVEL', 'ABSL_MIN_LOG_LEVEL')

def parse_options(argv):
  parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--mediapipe', default='mediapipe', metavar='SPEC',
      help='What pip installs as the MediaPipe under test: a requirement such as "mediapipe==1.2.3", or the path '
      'or URL of a wheel. Default: the latest release.')
  parser.add_argument(
      '--pip-arg', action='append', default=[], metavar='ARG',
      help='Extra argument for that pip install; repeat it for several. Example: --pip-arg=--pre '
      '--pip-arg=--extra-index-url=https://test.pypi.org/simple/')
  parser.add_argument(
      '--tasks', default='', metavar='LIST',
      help='Comma-separated domains ("vision"), tasks ("face_detector") or both ("text/text_embedder"). '
      'Default: all tasks.')
  parser.add_argument(
      '--delegates', default='cpu,gpu', metavar='LIST', help='Comma-separated delegates to validate. Default: cpu,gpu.')
  parser.add_argument(
      '--small-models-only', action='store_true',
      help='Leave out the cases of the large models (speech recognition and synthesis), which are several hundred '
      'megabytes each.')
  parser.add_argument('--report-dir', default=os.path.join(REPO_DIR, 'release-validation-report'), metavar='DIR')
  parser.add_argument(
      '--work-dir', default=os.path.join(REPO_DIR, '.release_validation'), metavar='DIR',
      help='Holds the Python environment and the downloaded models and inputs.')
  parser.add_argument(
      '--python', default=sys.executable, metavar='EXE', help='Python that creates the environment. Default: this one.')
  parser.add_argument('--fresh-venv', action='store_true', help='Delete the Python environment and create it again.')
  parser.add_argument(
      '--no-constraints', action='store_true',
      help='Do not pin the dependencies of MediaPipe to constraints.txt. Use it when a release needs versions that '
      'the pins exclude; the run is then no longer reproducible.')
  parser.add_argument('--timeout', type=int, default=600, metavar='SECONDS', help='Time limit of one case.')
  parser.add_argument(
      '--allow-unconfirmed-gpu', action='store_true',
      help='Do not fail the run for GPU cases whose output is correct but whose log shows no GPU delegate.')
  parser.add_argument(
      '--baseline', metavar='STATUS_JSON',
      help='status.json of an earlier run. The report then says, per case, whether the output is identical.')
  parser.add_argument('--list', action='store_true', help='Print the cases that would run, and exit.')
  options = parser.parse_args(argv)

  options.delegates = [d.strip().upper() for d in options.delegates.split(',') if d.strip()]
  unknown = [d for d in options.delegates if d not in cases.DELEGATES]
  if unknown or not options.delegates:
    parser.error(f"--delegates must list cpu and/or gpu, got: {', '.join(unknown) or 'nothing'}")
  unknown = cases.unknown_task_filters(options.tasks)
  if unknown:
    parser.error(f"unknown task or domain in --tasks: {', '.join(unknown)}")
  options.report_dir = os.path.abspath(options.report_dir)
  options.work_dir = os.path.abspath(options.work_dir)
  return options


def log(message=''):
  print(message, flush=True)


def run(command, **kwargs):
  return subprocess.run(command, capture_output=True, text=True, check=False, **kwargs)


def venv_python(work_dir):
  return os.path.join(work_dir, 'venv', 'bin', 'python')


def constraint_python():
  """The Python version the pins of constraints.txt were resolved for."""
  with open(CONSTRAINTS) as f:
    match = re.search(r'^# Python (\d+\.\d+)', f.read(), re.MULTILINE)
  return match.group(1) if match else None


def prepare_environment(options):
  """Creates the environment and installs the MediaPipe under test into it.

  Returns the path of its Python, or None if the installation failed.
  """
  venv_dir = os.path.join(options.work_dir, 'venv')
  python = venv_python(options.work_dir)
  if options.fresh_venv and os.path.isdir(venv_dir):
    shutil.rmtree(venv_dir)
  if not os.path.exists(python):
    log(f'Creating the Python environment in {venv_dir}')
    created = run([options.python, '-m', 'venv', venv_dir])
    if created.returncode != 0:
      log(created.stdout + created.stderr)
      return None
  # The pins only resolve on the Python version they were made for; say so
  # here, rather than let pip fail on a package it cannot find for this version.
  version = run([python, '-c', 'import platform; print(platform.python_version())']).stdout.strip()
  pinned = constraint_python()
  if pinned and not version.startswith(pinned + '.') and not options.no_constraints:
    log(f'The environment in {venv_dir} has Python {version}, but constraints.txt pins the dependencies for Python '
        f'{pinned}. Nothing was validated.\nRun again with --python <a Python {pinned}> --fresh-venv, or with '
        '--no-constraints for a run that is not reproducible.')
    return None
  # The package under test, plus what the samples import beyond it (scipy reads
  # the WAV file of the audio sample). Everything except MediaPipe is pinned.
  command = [python, '-m', 'pip', 'install', '--disable-pip-version-check', '--quiet', options.mediapipe, 'scipy']
  if not options.no_constraints:
    command += ['--constraint', CONSTRAINTS]
  command += options.pip_arg
  log('Installing: ' + ' '.join(shlex.quote(c) for c in command[3:]))
  installed = run(command)
  if installed.returncode != 0:
    log(installed.stdout + installed.stderr)
    log('The installation failed; nothing was validated.')
    if not options.no_constraints:
      log('If this release needs a dependency version that constraints.txt excludes, update constraints.txt, or '
          'run with --no-constraints.')
    return None
  return python


def describe_machine():
  """What this machine is, for the report."""
  info = {'system': f'{platform.system()} {platform.release()} ({platform.machine()})', 'processor': platform.processor(),
          'gpu': 'unknown'}
  if platform.system() != 'Darwin':
    return info
  version = run(['sw_vers', '-productVersion'])
  if version.returncode == 0:
    info['system'] = f'macOS {version.stdout.strip()} ({platform.machine()})'
  brand = run(['sysctl', '-n', 'machdep.cpu.brand_string'])
  if brand.returncode == 0 and brand.stdout.strip():
    info['processor'] = brand.stdout.strip()
  displays = run(['system_profiler', 'SPDisplaysDataType', '-json'])
  if displays.returncode == 0:
    try:
      gpus = json.loads(displays.stdout).get('SPDisplaysDataType', [])
      names = [f"{g.get('sppci_model', 'GPU')} ({g.get('spdisplays_mtlgpufamilysupport', 'Metal: unknown')})"
               for g in gpus]
      # system_profiler reports the Metal version as "spdisplays_metal4".
      names = [re.sub(r'spdisplays_metal(\d*)', r'Metal \1', name).replace('Metal )', 'Metal)') for name in names]
      info['gpu'] = ', '.join(names) or 'none found'
    except (ValueError, AttributeError):
      pass
  return info


def describe_environment(options, python):
  env = describe_machine()
  env.update({'mediapipeSpec': ' '.join([options.mediapipe] + options.pip_arg), 'mediapipeVersion': None,
              'pythonVersion': None, 'constraints': not options.no_constraints, 'pythonPinned': constraint_python(),
              'command': ' '.join(shlex.quote(a) for a in sys.argv)})
  probe = run([python, '-c', 'import platform, mediapipe; print(platform.python_version(), mediapipe.__version__)'])
  if probe.returncode == 0:
    env['pythonVersion'], env['mediapipeVersion'] = probe.stdout.split()[-2:]
  else:
    env['importError'] = (probe.stderr.strip().splitlines() or ['import mediapipe failed'])[-1]
  env['pythonMatchesPins'] = bool(env['pythonVersion']) and env['pythonVersion'].startswith(
      (env['pythonPinned'] or '') + '.')
  commit = run(['git', '-C', REPO_DIR, 'rev-parse', 'HEAD'])
  env['gitCommit'] = commit.stdout.strip()[:12] if commit.returncode == 0 else 'unknown'
  # Local changes to what this run uses: the samples and this suite, tracked or not.
  dirty = run(['git', '-C', REPO_DIR, 'status', '--porcelain', '--', 'examples', '.github/scripts/release_validate'])
  env['gitDirty'] = bool(dirty.stdout.strip()) if dirty.returncode == 0 else None
  return env


def assets_of(case, plan):
  """The names of the assets a case needs."""
  names = {case['modelAsset']}
  for invocation in plan['invocations']:
    values = list(invocation['args'])
    for value in invocation.get('params', {}).values():
      values += value if isinstance(value, list) else [value]
    names.update(v[len('@asset:'):] for v in values if isinstance(v, str) and v.startswith('@asset:'))
  for item in expectations.describe_case(case)['inputs']:
    if 'asset' in item:
      names.add(item['asset'])
  return sorted(names)


def resolve(value, case, asset_dir, case_dir):
  """Fills in the "@" placeholders of a plan (see expectations.py)."""
  if isinstance(value, list):
    return [resolve(v, case, asset_dir, case_dir) for v in value]
  if isinstance(value, dict):
    return {k: resolve(v, case, asset_dir, case_dir) for k, v in value.items()}
  if not isinstance(value, str) or not value.startswith('@'):
    return value
  if value == '@model':
    return os.path.join(asset_dir, case['modelAsset'])
  if value == '@delegate':
    return case['delegate'].lower()
  if value.startswith('@asset:'):
    return os.path.join(asset_dir, value[len('@asset:'):])
  if value.startswith('@out:'):
    return os.path.join(case_dir, value[len('@out:'):])
  raise ValueError(f'unknown placeholder: {value}')


def run_case(case, options, python, asset_dir, asset_errors):
  """Runs one case and returns its result record."""
  case_dir = os.path.join(options.report_dir, 'cases', case['id'])
  os.makedirs(case_dir)
  plan = expectations.get_plan(case)
  description = expectations.describe_case(case)
  unsupported = cases.known_unsupported(case)
  result = dict(case, description=description['description'], inputs=[], outputs=[], logs={}, gpuEvidence=[],
                runs=[], error=None, traceback=None, note=None, baseline=None, command='',
                knownUnsupported=unsupported, observationDigest=None, durationSec=0.0,
                verification={'status': verify.UNVERIFIED, 'reason': '', 'checks': [], 'summary': '-'})
  for item in description['inputs']:
    entry = dict(item)
    if 'asset' in item:
      entry['file'] = f"inputs/{item['asset']}"
    result['inputs'].append(entry)

  unusable = [asset_errors[name] for name in assets_of(case, plan) if name in asset_errors]
  if unusable:
    result.update(status='FAIL', error='; '.join(unusable), details='not run: ' + '; '.join(unusable))
    return result

  job = {
      'caseId': case['id'],
      'sample': os.path.join(REPO_DIR, case['sample']),
      'observer': plan['observer'],
      'invocations': resolve(plan['invocations'], case, asset_dir, case_dir),
  }
  job_path, out_path = os.path.join(case_dir, 'job.json'), os.path.join(case_dir, 'result.json')
  with open(job_path, 'w') as f:
    json.dump(job, f, indent=1, ensure_ascii=False)
  result['command'] = ' ; '.join(
      ' '.join(shlex.quote(a) for a in ['python', case['sample']] + invocation['args'])
      for invocation in job['invocations'])

  # Every case starts from the pinned files only (see assets.remove_derived_files),
  # and with its own temporary directory so cached kernels never cross cases.
  assets.remove_derived_files(asset_dir)
  case_tmp = os.path.join(case_dir, '.tmp')
  os.makedirs(case_tmp, exist_ok=True)
  env = {k: v for k, v in os.environ.items() if k not in LOG_SILENCERS}
  env.update({'PYTHONDONTWRITEBYTECODE': '1', 'PYTHONHASHSEED': '0', 'PYTHONUNBUFFERED': '1',
              'TMPDIR': case_tmp, 'TEMP': case_tmp, 'TMP': case_tmp})
  stdout_path, native_path = os.path.join(case_dir, 'stdout.txt'), os.path.join(case_dir, 'native.log')
  started = time.perf_counter()
  timed_out = False
  try:
    with open(stdout_path, 'w') as out, open(native_path, 'w') as err:
      try:
        process = subprocess.run([python, WORKER, '--job', job_path, '--out', out_path], cwd=case_dir, env=env,
                                 stdout=out, stderr=err, timeout=options.timeout, check=False)
        exit_code = process.returncode
      except subprocess.TimeoutExpired:
        timed_out, exit_code = True, None
  finally:
    shutil.rmtree(case_tmp, ignore_errors=True)
  result['durationSec'] = round(time.perf_counter() - started, 2)
  result['logs'] = {'sample output': f"cases/{case['id']}/stdout.txt", 'native log': f"cases/{case['id']}/native.log",
                    'job': f"cases/{case['id']}/job.json"}

  worker = {}
  if os.path.exists(out_path):
    try:
      with open(out_path) as f:
        worker = json.load(f)
    except ValueError:
      worker = {}
  if timed_out:
    worker['error'] = f'timed out after {options.timeout} s'
  elif not worker.get('finished'):
    # The process died before it could report: a crash in native code.
    how = f'signal {-exit_code}' if exit_code is not None and exit_code < 0 else f'exit code {exit_code}'
    worker['error'] = f'the sample process crashed ({how}); see the native log'
  with open(native_path, errors='replace') as f:
    native_log = f.read()

  result['runs'] = worker.get('runs', [])
  result['error'] = worker.get('error')
  result['traceback'] = worker.get('traceback')
  result['gpuEvidence'] = policy.find_gpu_evidence(native_log)
  result['outputs'] = sorted(f"cases/{case['id']}/{name}" for name in os.listdir(case_dir)
                             if name.endswith(('.png', '.wav')))
  if not result['error']:
    verification = verify.verify_case(expectations.get_expectation(case), result['runs'])
    passed = sum(1 for c in verification['checks'] if c['pass'])
    verification['summary'] = f"{passed}/{len(verification['checks'])} checks" if verification['checks'] else '-'
    result['verification'] = verification
    result['observationDigest'] = policy.observation_digest(result['runs'])
  result['status'], result['details'], result['note'] = policy.decide(
      case, worker, result['verification'], result['gpuEvidence'], unsupported)
  return result


def compare_with_baseline(results, path):
  """Marks each result as identical to, or different from, an earlier run."""
  with open(path) as f:
    earlier = {r['id']: r for r in json.load(f)['results']}
  summary = {'path': path, 'same': 0, 'different': 0, 'new': 0}
  for result in results:
    before = earlier.get(result['id'])
    if before is None:
      result['baseline'] = 'not in the baseline'
      summary['new'] += 1
    elif (before.get('observationDigest') == result['observationDigest']
          and before.get('status') == result['status'] and before.get('error') == result['error']):
      result['baseline'] = 'identical'
      summary['same'] += 1
    else:
      was = before.get('status')
      result['baseline'] = (f'different (status was {was})' if was != result['status']
                            else 'different (same status, other output)')
      summary['different'] += 1
  return summary


def main(argv=None):
  options = parse_options(sys.argv[1:] if argv is None else argv)
  matrix = cases.build_validation_matrix(options.tasks, options.delegates, not options.small_models_only)
  if not matrix:
    log(f'No validation case matches --tasks "{options.tasks}".')
    return 2
  if options.list:
    for case in matrix:
      mark = '  (known unsupported)' if cases.known_unsupported(case) else ''
      log(case['id'] + mark)
    log(f'{len(matrix)} cases')
    return 0
  if options.baseline and not os.path.exists(options.baseline):
    log(f'--baseline: {options.baseline} does not exist.')
    return 2
  if options.baseline:
    # Read it now: the report directory is cleared below, and the baseline may be in it.
    baseline_copy = os.path.join(options.work_dir, 'baseline-status.json')
    os.makedirs(options.work_dir, exist_ok=True)
    shutil.copyfile(options.baseline, baseline_copy)

  log('Self-test of the verifiers')
  if not self_test.run():
    log('The self-test failed; nothing was validated.')
    return 1

  started_at = datetime.datetime.now().astimezone().isoformat(timespec='seconds')
  python = prepare_environment(options)
  if not python:
    return 1
  environment = describe_environment(options, python)
  if not environment['mediapipeVersion']:
    log(f"MediaPipe cannot be imported: {environment.get('importError')}. Nothing was validated.")
    return 1
  log(f"MediaPipe {environment['mediapipeVersion']} on Python {environment['pythonVersion']}, {environment['system']}, "
      f"GPU: {environment['gpu']}")
  if environment['pythonPinned'] and not environment['pythonMatchesPins']:
    log(f"WARNING: constraints.txt was resolved for Python {environment['pythonPinned']}; this is "
        f"{environment['pythonVersion']}.")

  if os.path.isdir(options.report_dir):
    shutil.rmtree(options.report_dir)
  os.makedirs(os.path.join(options.report_dir, 'inputs'))
  freeze = run([python, '-m', 'pip', 'freeze', '--disable-pip-version-check'])
  with open(os.path.join(options.report_dir, 'pip-freeze.txt'), 'w') as f:
    f.write(freeze.stdout)

  # Download and check every file the selected cases need.
  asset_dir = os.path.join(options.work_dir, 'assets')
  needed = sorted({name for case in matrix for name in assets_of(case, expectations.get_plan(case))})
  asset_errors, asset_record = {}, {}
  log(f'Checking {len(needed)} models and inputs in {asset_dir}')
  for name in needed:
    try:
      path = assets.ensure_asset(name, asset_dir, log)
      asset_record[name] = assets.ASSETS[name]['sha256']
      if not name.endswith(('.tflite', '.task', '.litertlm')):
        shutil.copyfile(path, os.path.join(options.report_dir, 'inputs', name))
    except assets.AssetError as error:
      asset_errors[name] = str(error)
      log(f'  ERROR: {error}')

  log(f"\nRelease validation: {len(matrix)} cases, delegates: {', '.join(options.delegates)}")
  results = []
  for index, case in enumerate(matrix):
    result = run_case(case, options, python, asset_dir, asset_errors)
    results.append(result)
    label = f"{case['domain']}/{case['task']} {case['modelId']} {case['mode']} {case['delegate']}"
    why = '' if result['status'] == 'PASS' else ': ' + result['details'][:260]
    log(f"[{index + 1:>2}/{len(matrix)}] {result['status']:<15} {label} ({result['durationSec']:.1f}s, "
        f"{result['verification']['summary']}){why}")

  baseline = compare_with_baseline(results, baseline_copy) if options.baseline else None
  if baseline:
    baseline['path'] = options.baseline
  counts = report.summarize_counts(results)
  accepted = policy.ACCEPTED + (('GPU_UNCONFIRMED',) if options.allow_unconfirmed_gpu else ())
  failing = [r for r in results if r['status'] not in accepted]
  passed = not failing
  verdict = (f"PASSED: {counts['PASS']} of {counts['total']} cases passed" if passed
             else f"FAILED: {len(failing)} of {counts['total']} cases did not pass")
  if counts['UNSUPPORTED']:
    verdict += f", {counts['UNSUPPORTED']} unsupported by design"
  state = {
      'startedAt': started_at,
      'finishedAt': datetime.datetime.now().astimezone().isoformat(timespec='seconds'),
      'environment': environment,
      'assets': asset_record,
      'assetErrors': asset_errors,
      'options': {'tasks': options.tasks, 'delegates': options.delegates,
                  'smallModelsOnly': options.small_models_only,
                  'allowUnconfirmedGpu': options.allow_unconfirmed_gpu, 'timeout': options.timeout},
      'baseline': baseline,
      'counts': counts,
      'passed': passed,
      'verdict': verdict,
      'results': results,
  }
  report.write_report_files(options.report_dir, state)

  log()
  for status in report.ORDER:
    if counts.get(status):
      log(f'{status:<16}{counts[status]:>3}  {report.STATUSES[status][1]}')
  if baseline:
    log(f"Compared with {baseline['path']}: {baseline['same']} identical, {baseline['different']} different, "
        f"{baseline['new']} not in the baseline")
  log(f'\n{verdict}')
  log(f"Report: file://{os.path.join(options.report_dir, 'index.html')}")
  return 0 if passed else 1


if __name__ == '__main__':
  sys.exit(main())
