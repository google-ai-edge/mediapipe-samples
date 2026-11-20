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
"""Runs the release validation of the Android samples on a phone.

  python3 .github/scripts/release_validate/run_android.py --build
  python3 .github/scripts/release_validate/run_android.py --apk app-debug.apk --serial R3CX80K1NQP
  python3 .github/scripts/release_validate/run_android.py --tasks vision/face_detector,text --delegates cpu

The phone runs the validation app (android-validation/), one app that holds
every Android sample and the cases of the spec. This script installs it, has
it run each case in the sample of its task (one process per case, started with
"am instrument"), pulls what the sample showed, checks that against the ground
truth of the spec with the verifiers the Python samples are checked with, and
writes the same report to --report-dir. The exit code is 0 only if every case
passed.

It needs adb and a phone with its screen unlocked, and nothing but the Python
standard library. --build also needs what the app is built with: a JDK and
the Android SDK (see android-validation/README.md).
"""

import argparse
import datetime
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import time
import zipfile

sys.dont_write_bytecode = True

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..', '..'))
sys.path.insert(0, SCRIPT_DIR)

import policy  # pylint: disable=g-import-not-at-top,wrong-import-position
import report  # pylint: disable=wrong-import-position
import self_test  # pylint: disable=wrong-import-position
import spec  # pylint: disable=wrong-import-position

PLATFORM = 'android'
APP_DIR = os.path.join(REPO_DIR, 'android-validation')
DEFAULT_APK = os.path.join(APP_DIR, 'app', 'build', 'outputs', 'apk', 'debug', 'app-debug.apk')
PACKAGE = 'com.google.mediapipe.examples.validation'
RUNNER = f'{PACKAGE}/{PACKAGE}.runner.ValidationInstrumentation'
DELEGATE_SWITCH_MARKER = '=== VALIDATION_DELEGATE_SWITCH ==='

# Lines of logcat ("threadtime"): date, time, process, thread, level, tag, message.
_LOG_LINE = re.compile(r'^\d\d-\d\d \d\d:\d\d:\d\d\.\d+\s+\d+\s+\d+\s+([A-Z])\s+(.*?)\s*: (.*)$')
# Tags that log errors on a healthy phone.
_NOISY_TAGS = ('Adreno', 'OpenGLRenderer', 'libEGL', 'gralloc', 'ion', 'Perf', 'QT', 'SurfaceFlinger', 'ViewRootImpl',
               'InputMethod', 'ActivityThread', 'Zygote', 'studio.deploy')


def parse_options(argv):
  parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument('--apk', default=None, metavar='FILE',
                      help='The validation app to install. Default: the debug build in android-validation/.')
  parser.add_argument('--build', action='store_true',
                      help='Build the app first, with the samples of the selected tasks (./gradlew :app:assembleDebug).')
  parser.add_argument('--mediapipe-version', metavar='VERSION',
                      help='With --build: the MediaPipe version under test. Default: the one the samples name.')
  parser.add_argument('--mediapipe-repo', metavar='PATH_OR_URL',
                      help='With --build: a Maven repository that has that version, for example a staging repository.')
  parser.add_argument('--no-install', action='store_true', help='Use the app that is on the phone already.')
  parser.add_argument('--serial', default=os.environ.get('ANDROID_SERIAL'), metavar='SERIAL',
                      help='The phone, as "adb devices" names it. Default: $ANDROID_SERIAL, or the only one connected.')
  parser.add_argument('--adb', default=shutil.which('adb') or 'adb', metavar='EXE')
  parser.add_argument(
      '--tasks', default='', metavar='LIST',
      help='Comma-separated domains ("vision"), tasks ("face_detector") or both ("text/text_embedder"). '
      'Default: all tasks.')
  parser.add_argument('--delegates', default=None, metavar='LIST',
                      help='Comma-separated delegates to validate. Default: cpu,gpu (or cpu when --cpu-only is given).')
  parser.add_argument('--cpu-only', action='store_true', help='Run only the CPU delegate cases.')
  parser.add_argument('--small-models-only', action='store_true',
                      help='Leave out the cases of the large models (speech recognition and synthesis).')
  parser.add_argument('--report-dir', default=os.path.join(REPO_DIR, 'release-validation-report-android'),
                      metavar='DIR', help='It is deleted and written anew.')
  parser.add_argument('--timeout', type=int, default=600, metavar='SECONDS', help='Time limit of one case.')
  parser.add_argument('--allow-unconfirmed-gpu', action='store_true',
                      help='Do not fail the run for GPU cases whose output is correct but whose log shows no GPU '
                      'delegate.')
  parser.add_argument('--list', action='store_true', help='Print the cases that would run, and exit.')
  options = parser.parse_args(argv)

  raw = 'cpu' if options.cpu_only and options.delegates is None else (options.delegates or 'cpu,gpu')
  options.delegates = [d.strip().upper() for d in raw.split(',') if d.strip()]
  if options.cpu_only and options.delegates != ['CPU']:
    parser.error(f'--cpu-only cannot be combined with --delegates {raw}')
  unknown = [d for d in options.delegates if d not in spec.DELEGATES]
  if unknown or not options.delegates:
    parser.error(f"--delegates must list cpu and/or gpu, got: {', '.join(unknown) or 'nothing'}")
  unknown = spec.unknown_task_filters(options.tasks)
  if unknown:
    parser.error(f"unknown task or domain in --tasks: {', '.join(unknown)}")
  if options.no_install and (options.build or options.apk):
    parser.error('--no-install cannot be combined with --build or --apk')
  if (options.mediapipe_version or options.mediapipe_repo) and not options.build:
    parser.error('--mediapipe-version and --mediapipe-repo say how to --build the app')
  options.report_dir = os.path.abspath(options.report_dir)
  options.apk = os.path.abspath(options.apk or DEFAULT_APK)
  return options


def log(message=''):
  print(message, flush=True)


def run(command, **kwargs):
  return subprocess.run(command, capture_output=True, text=True, encoding='utf-8', errors='replace',
                        check=False, **kwargs)


class Device:
  """A phone, through adb."""

  def __init__(self, adb, serial):
    self.adb, self.serial = adb, serial

  def command(self, *args):
    return [self.adb] + (['-s', self.serial] if self.serial else []) + list(args)

  def run(self, *args, timeout=120):
    return run(self.command(*args), timeout=timeout)

  def shell(self, *args, timeout=120):
    return self.run('shell', *args, timeout=timeout)

  def prop(self, name):
    return self.shell('getprop', name).stdout.strip()

  def pull(self, source, target):
    return self.run('pull', source, target).returncode == 0


def find_device(options):
  """The phone to use, or None with the reason logged."""
  listed = run([options.adb, 'devices'])
  if listed.returncode != 0:
    log(f'{options.adb} does not run: {listed.stderr.strip() or listed.stdout.strip()}')
    return None
  ready = [line.split('\t')[0] for line in listed.stdout.splitlines()[1:] if line.endswith('\tdevice')]
  if options.serial:
    if options.serial not in ready:
      log(f'The phone {options.serial} is not connected (adb devices: {", ".join(ready) or "none"}).')
      return None
    return Device(options.adb, options.serial)
  if len(ready) != 1:
    log(f'{len(ready)} phones are connected ({", ".join(ready) or "none"}); name one with --serial.')
    return None
  return Device(options.adb, ready[0])


def build_app(options, matrix):
  """Builds the app with the samples the selected cases need. Returns whether it worked."""
  tasks = sorted({case['task'] for case in matrix})
  command = [os.path.join(APP_DIR, 'gradlew'), '-q', '--console=plain', '-p', APP_DIR, ':app:assembleDebug']
  if options.tasks:
    command.append('-Ptasks=' + ','.join(tasks))
  if options.mediapipe_version:
    command.append(f'-PmediapipeVersion={options.mediapipe_version}')
  if options.mediapipe_repo:
    command.append(f'-PmediapipeRepo={options.mediapipe_repo}')
  log('Building the validation app: ' + ' '.join(command))
  completed = subprocess.run(command, check=False)
  return completed.returncode == 0


def instrument(device, timeout, **arguments):
  """Starts the runner in the app. Returns (output, timed out)."""
  args = []
  for name, value in arguments.items():
    args += ['-e', name, value]
  try:
    completed = device.shell('am', 'instrument', '-w', *args, RUNNER, timeout=timeout)
  except subprocess.TimeoutExpired:
    device.shell('am', 'force-stop', PACKAGE)
    return '', True
  return completed.stdout + completed.stderr, False


def log_errors(device_log, limit=6):
  """What the app logged as errors during a case: MediaPipe states its reasons there."""
  messages = []
  for line in device_log.splitlines():
    match = _LOG_LINE.match(line)
    if not match or match.group(1) not in 'EF':
      continue
    tag, message = match.group(2), match.group(3).strip()
    if tag.startswith(_NOISY_TAGS) or message.startswith(('at ', '... ')) or not message:
      continue
    if message not in messages:
      messages.append(message)
  return messages[:limit]


def crash_summary(device):
  """What the system logged about the crash of the app, as text."""
  crash = device.run('logcat', '-b', 'crash', '-d', '-t', '120').stdout
  lines = [line for line in crash.splitlines() if PACKAGE in line or 'FATAL' in line or 'Fatal signal' in line
           or 'Caused by' in line or 'Abort message' in line]
  headline = next((_LOG_LINE.match(line).group(3) for line in reversed(lines)
                   if _LOG_LINE.match(line) and ('Exception' in line or 'Fatal signal' in line
                                                 or 'Abort message' in line)), '')
  return headline, crash


def describe_environment(device, options, device_environment):
  """What ran, for the report."""
  versions = device_environment.get('versions') or {}
  mediapipe = sorted(set(versions.values())) if versions else []
  commit = run(['git', '-C', REPO_DIR, 'rev-parse', '--short', 'HEAD'])
  dirty = run(['git', '-C', REPO_DIR, 'status', '--porcelain', '--', 'examples', 'android-validation',
               'android-common', '.github/scripts/release_validate'])
  samples = f"commit {commit.stdout.strip() or 'unknown'}" + (' with local changes' if dirty.stdout.strip() else '')
  environment = {
      'MediaPipe under test': ', '.join(f'{name} {version}' for name, version in sorted(versions.items())) or 'unknown',
      'Device': f"{device_environment.get('device') or device.prop('ro.product.model')} "
                f"({device_environment.get('soc') or device.prop('ro.soc.model')}, "
                f"{device_environment.get('abi') or device.prop('ro.product.cpu.abi')})",
      'System': f"{device_environment.get('system') or 'Android ' + device.prop('ro.build.version.release')}, "
                f"build {device_environment.get('build') or device.prop('ro.build.fingerprint')}",
      'App': PACKAGE + ('' if options.no_install else f', {os.path.relpath(options.apk, REPO_DIR)}'),
      'Samples': samples,
  }
  if len(mediapipe) == 1:
    environment['mediapipeVersion'] = mediapipe[0]
  return environment


def run_case(case, device, options, results_dir, exported, app_cases):
  """Runs one case on the phone and returns its result record."""
  case_dir = os.path.join(options.report_dir, 'cases', case['id'])
  os.makedirs(case_dir)
  resolved = spec.resolve(case)
  unsupported = spec.known_unsupported(case, system='Android')
  result = dict(case, description=resolved['description'], inputs=[], outputs=[], logs={}, gpuEvidence=[],
                runs=[], error=None, traceback=None, note=None, baseline=None, knownUnsupported=unsupported,
                observationDigest=None, durationSec=0.0, stdout='',
                command=f"adb shell am instrument -w -e case {case['id']} {RUNNER}",
                verification={'status': 'UNVERIFIED', 'reason': '', 'checks': [], 'summary': '-'})
  for item in resolved['inputs']:
    entry = dict(item)
    if 'asset' in item and os.path.isfile(os.path.join(options.report_dir, 'inputs', item['asset'])):
      entry['file'] = f"inputs/{item['asset']}"
    result['inputs'].append(entry)

  # The app holds the cases it runs. It must hold this case as the spec has it now: what was
  # run with other options or inputs cannot be judged with these expectations.
  held = app_cases.get(case['id'])
  outdated = None
  if held is None:
    outdated = 'the app on the phone was built without this case; build it again (see --build)'
  elif any(held.get(key) != exported[case['id']].get(key) for key in ('options', 'steps', 'modelAsset')):
    outdated = 'the app on the phone was built from another state of the spec; build it again (see --build)'
  if outdated:
    result.update(status='FAIL', error=outdated, details='not run: ' + outdated)
    return result

  device.run('logcat', '-b', 'crash', '-c')
  started = time.perf_counter()
  output, timed_out = instrument(device, options.timeout, case=case['id'])
  result['durationSec'] = round(time.perf_counter() - started, 2)

  files = {'result.json': f"{case['id']}.json", 'screenshot.png': f"{case['id']}.png", 'device.log': f"{case['id']}.log"}
  pulled = {name: device.pull(f'{results_dir}/{remote}', os.path.join(case_dir, name)) for name, remote in files.items()}
  outcome, device_log = {}, ''
  if pulled['result.json']:
    try:
      with open(os.path.join(case_dir, 'result.json'), encoding='utf-8') as f:
        outcome = json.load(f)
    except ValueError:
      outcome = {}
  if pulled['device.log']:
    with open(os.path.join(case_dir, 'device.log'), encoding='utf-8', errors='replace') as f:
      device_log = f.read()

  if timed_out:
    outcome = {'error': f'timed out after {options.timeout} s'}
  elif not outcome:
    # The process died before the runner could write its result: a crash, most likely in native code.
    headline, crash = crash_summary(device)
    with open(os.path.join(case_dir, 'device.log'), 'w', encoding='utf-8') as f:
      f.write(crash)
    device_log = ''
    said = ' '.join(line.split('=', 1)[1] for line in output.splitlines() if 'shortMsg=' in line or 'longMsg=' in line)
    outcome = {'error': f"the app crashed while it ran the case ({headline or said or 'see the device log'})"}
  elif outcome.get('runnerError'):
    outcome['error'] = 'the case could not be run in the sample: ' + outcome['runnerError']
  elif outcome.get('error'):
    # The sample shows its user a short message; the reason MediaPipe gave is in the log.
    stated = log_errors(device_log)
    if stated:
      outcome['error'] += ' Log: ' + ' | '.join(stated)

  result['runs'] = outcome.get('runs') or []
  result['error'] = outcome.get('error')
  result['model'] = outcome.get('model')
  active_log = device_log.rsplit(DELEGATE_SWITCH_MARKER, 1)[-1] if DELEGATE_SWITCH_MARKER in device_log else device_log
  result['gpuEvidence'] = policy.find_gpu_evidence(active_log, PLATFORM)
  result['outputs'] = [f"cases/{case['id']}/screenshot.png"] if pulled['screenshot.png'] else []
  result['logs'] = {name.split('.')[0].replace('result', 'result of the app').replace('device', 'device log'):
                    f"cases/{case['id']}/{name}" for name in ('result.json', 'device.log')
                    if os.path.isfile(os.path.join(case_dir, name))}
  result.update(policy.judge(case, resolved['expectation'], outcome, result['gpuEvidence'], unsupported))
  return result


def main(argv=None):
  if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
  options = parse_options(sys.argv[1:] if argv is None else argv)
  matrix = spec.build_matrix(options.tasks, options.delegates, not options.small_models_only, PLATFORM)
  if not matrix:
    log(f'No validation case of the Android samples matches --tasks "{options.tasks}".')
    return 2
  if options.list:
    for case in matrix:
      mark = '  (known unsupported)' if spec.known_unsupported(case, system='Android') else ''
      log(case['id'] + mark)
    log(f'{len(matrix)} cases')
    return 0

  log('Self-test of the verifiers')
  if not self_test.run():
    log('The self-test failed; nothing was validated.')
    return 1

  started_at = datetime.datetime.now().astimezone().isoformat(timespec='seconds')
  device = find_device(options)
  if not device:
    return 1
  if options.build and not build_app(options, matrix):
    log('The validation app could not be built; nothing was validated.')
    return 1
  if not options.no_install:
    if not os.path.isfile(options.apk):
      log(f'{options.apk} does not exist. Build the app with --build, or name it with --apk.')
      return 1
    log(f'Installing {options.apk} ({os.path.getsize(options.apk) / 1e6:.0f} MB) on {device.serial}')
    installed = device.run('install', '-r', options.apk, timeout=1800)
    if installed.returncode != 0 or 'Success' not in installed.stdout:
      log(f'adb install failed: {(installed.stdout + installed.stderr).strip()}')
      return 1

  # The cases the app holds, and where it writes its results.
  device.shell('am', 'force-stop', PACKAGE)
  output, _ = instrument(device, 120, export='true')
  where = re.search(r'^Results in (\S+)', output, re.MULTILINE)
  if not where:
    log('The validation app does not run on the phone: ' + (output.strip() or 'no output from "am instrument"'))
    return 1
  results_dir = where.group(1)
  if os.path.isdir(options.report_dir):
    shutil.rmtree(options.report_dir)
  os.makedirs(os.path.join(options.report_dir, 'inputs'))
  held_path = os.path.join(options.report_dir, 'cases-of-the-app.json')
  if not device.pull(f'{results_dir}/cases.json', held_path):
    log(f'Could not pull {results_dir}/cases.json from the phone.')
    return 1
  with open(held_path, encoding='utf-8') as f:
    app_cases = {case['id']: case for case in json.load(f)['cases']}
  device.shell('rm', '-rf', results_dir)

  # The inputs of the cases, for the report: they are in the app.
  if os.path.isfile(options.apk):
    with zipfile.ZipFile(options.apk) as apk:
      for name in apk.namelist():
        if name.startswith('assets/validation/inputs/'):
          with open(os.path.join(options.report_dir, 'inputs', os.path.basename(name)), 'wb') as f:
            f.write(apk.read(name))

  log(f"\nRelease validation on {device.serial}: {len(matrix)} cases, delegates: {', '.join(options.delegates)}")
  exported = {case['id']: case for case in json.loads(json.dumps(
      spec.export(PLATFORM, options.tasks, options.delegates, not options.small_models_only)['cases']))}
  results = []
  for index, case in enumerate(matrix):
    result = run_case(case, device, options, results_dir, exported, app_cases)
    results.append(result)
    label = f"{case['domain']}/{case['task']} {case['modelId']} {case['mode']} {case['delegate']}"
    why = '' if result['status'] == 'PASS' else ': ' + result['details'][:260]
    log(f"[{index + 1:>2}/{len(matrix)}] {result['status']:<15} {label} ({result['durationSec']:.1f}s, "
        f"{result['verification']['summary']}){why}")

  device_environment = {}
  environment_path = os.path.join(options.report_dir, 'device-environment.json')
  if device.pull(f'{results_dir}/environment.json', environment_path):
    with open(environment_path, encoding='utf-8') as f:
      device_environment = json.load(f)
  environment = describe_environment(device, options, device_environment)
  if not options.no_install:
    digest = hashlib.sha256(pathlib.Path(options.apk).read_bytes()).hexdigest()
    environment['App'] += f' (SHA-256 {digest[:16]}...)'

  counts = report.summarize_counts(results)
  accepted = policy.ACCEPTED + (('GPU_UNCONFIRMED',) if options.allow_unconfirmed_gpu else ())
  failing = [r for r in results if r['status'] not in accepted]
  passed = not failing
  verdict = (f"PASSED: {counts['PASS']} of {counts['total']} cases passed" if passed
             else f"FAILED: {len(failing)} of {counts['total']} cases did not pass")
  if counts['UNSUPPORTED']:
    verdict += f", {counts['UNSUPPORTED']} unsupported by design"
  finished_at = datetime.datetime.now().astimezone().isoformat(timespec='seconds')
  report.write_report_files(options.report_dir, {
      'platform': PLATFORM,
      'startedAt': started_at,
      'finishedAt': finished_at,
      'environment': environment,
      'options': {'tasks': options.tasks, 'delegates': options.delegates,
                  'smallModelsOnly': options.small_models_only,
                  'allowUnconfirmedGpu': options.allow_unconfirmed_gpu, 'timeout': options.timeout},
      'baseline': None,
      'counts': counts,
      'passed': passed,
      'verdict': verdict,
      'results': results,
  })
  # What the phone observed, in the form verify_observations.py judges.
  with open(os.path.join(options.report_dir, 'observations.json'), 'w', encoding='utf-8') as f:
    json.dump({'platform': PLATFORM, 'system': 'Android', 'startedAt': started_at, 'finishedAt': finished_at,
               'environment': environment,
               'results': [{'id': r['id'], 'runs': r['runs'], 'error': r['error'], 'gpuEvidence': r['gpuEvidence'],
                            'outputs': r['outputs'], 'durationSec': r['durationSec']} for r in results]},
              f, indent=1, ensure_ascii=False)

  log()
  for status in report.ORDER:
    if counts.get(status):
      log(f'{status:<16}{counts[status]:>3}  {report.STATUSES[status][1]}')
  log(f'\n{verdict}')
  log('Report: ' + pathlib.Path(options.report_dir, 'index.html').resolve().as_uri())
  return 0 if passed else 1


if __name__ == '__main__':
  sys.exit(main())
