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
"""Downloads the models and inputs of the validation, pinned by SHA-256.

The files are listed in spec/assets.json. Each is downloaded once into the
asset directory and checked against its checksum before each run, so that two
runs validate exactly the same inputs. Hugging Face models are downloaded with
`huggingface_hub.hf_hub_download` at an immutable commit `revision`; sample
media inputs on Google Cloud Storage are downloaded with `urllib.request`. A
file whose content changed upstream is an error: review the new file, then
update its entry in spec/assets.json (and the expectations that depend on it).
"""

import hashlib
import os
import shutil
import subprocess
import tempfile
import urllib.request

import spec

# Run with the Python of the validation environment, which has huggingface_hub;
# the Python that runs run_local.py needs nothing but the standard library.
_HF_DOWNLOAD_SNIPPET = (
    'import shutil, sys; '
    'from huggingface_hub import hf_hub_download; '
    'repo_id, filename, revision, local_dir, cache_dir, dest = sys.argv[1:7]; '
    'downloaded = hf_hub_download('
    'repo_id=repo_id, filename=filename, revision=revision, '
    'local_dir=local_dir, cache_dir=cache_dir or None); '
    'shutil.move(downloaded, dest)'
)

# Every pinned file by name: spec/assets.json, read through spec.py.
ASSETS = spec.assets()


class AssetError(Exception):
  """An asset could not be downloaded, or is not the pinned file."""


def sha256_of(path):
  digest = hashlib.sha256()
  with open(path, 'rb') as f:
    for chunk in iter(lambda: f.read(1 << 20), b''):
      digest.update(chunk)
  return digest.hexdigest()


def _source_label(entry):
  if 'repo' in entry:
    return f"{entry['repo']}/{entry['file']}@{entry['revision']}"
  return entry['url']


def _download_url(url, path):
  partial = path + '.download'
  try:
    urllib.request.urlretrieve(url, partial)
  except Exception as error:  # pylint: disable=broad-except
    if os.path.exists(partial):
      os.remove(partial)
    raise AssetError(f'could not download {url}: {error}') from error
  os.replace(partial, path)


def _download_hf(entry, path, python_exe=None, hf_cache_dir=None):
  """Downloads a pinned Hugging Face asset via `huggingface_hub.hf_hub_download`."""
  source = _source_label(entry)
  partial = path + '.download'
  stage_root = hf_cache_dir or os.path.dirname(path)
  os.makedirs(stage_root, exist_ok=True)
  stage_dir = tempfile.mkdtemp(prefix='.hf_stage_', dir=stage_root)
  try:
    if python_exe:
      env = dict(os.environ, PYTHONUTF8='1', PYTHONIOENCODING='utf-8',
                 HF_HUB_DISABLE_PROGRESS_BARS='1', HF_HUB_DISABLE_SYMLINKS_WARNING='1')
      if hf_cache_dir:
        env['HF_HOME'] = hf_cache_dir
      completed = subprocess.run(
          [python_exe, '-c', _HF_DOWNLOAD_SNIPPET, entry['repo'], entry['file'],
           entry['revision'], stage_dir, hf_cache_dir or '', partial],
          capture_output=True, text=True, encoding='utf-8', errors='replace', env=env, check=False)
      if completed.returncode != 0:
        detail = (completed.stderr.strip() or completed.stdout.strip() or
                  f'exit code {completed.returncode}').splitlines()[-1]
        raise AssetError(f'could not download {source}: {detail}')
    else:
      try:
        from huggingface_hub import hf_hub_download  # pylint: disable=g-import-not-at-top
      except ImportError as error:
        raise AssetError(
            f'could not download {source}: huggingface_hub is not installed in this Python'
        ) from error
      downloaded = hf_hub_download(
          repo_id=entry['repo'],
          filename=entry['file'],
          revision=entry['revision'],
          local_dir=stage_dir,
          cache_dir=hf_cache_dir,
      )
      shutil.move(downloaded, partial)
  except AssetError:
    if os.path.exists(partial):
      os.remove(partial)
    raise
  except Exception as error:  # pylint: disable=broad-except
    if os.path.exists(partial):
      os.remove(partial)
    raise AssetError(f'could not download {source}: {error}') from error
  finally:
    shutil.rmtree(stage_dir, ignore_errors=True)
  os.replace(partial, path)


def remove_derived_files(directory):
  """Deletes what tasks wrote next to the pinned files, and returns the names.

  Some tasks compile a model on first use and store the result next to it (for
  example `<model>.xnnpack_cache`). Such a file must not carry over to another
  case, or to a run with another MediaPipe version. Files kept for review
  (`.unexpected`) stay.
  """
  removed = []
  for name in sorted(os.listdir(directory)):
    path = os.path.join(directory, name)
    if name in ASSETS or name.endswith('.unexpected') or not os.path.isfile(path):
      continue
    os.remove(path)
    removed.append(name)
  return removed


def ensure_asset(name, directory, log=None, python_exe=None, hf_cache_dir=None):
  """Returns the path of a pinned asset, downloading it if it is missing.

  Raises:
    AssetError: if the file cannot be downloaded, or its content is not the
      pinned content.
  """
  if name not in ASSETS:
    raise AssetError(f'{name} is not listed in spec/assets.json')
  entry = ASSETS[name]
  path = os.path.join(directory, name)
  os.makedirs(directory, exist_ok=True)
  if os.path.exists(path) and sha256_of(path) != entry['sha256']:
    # A file from an earlier state of the manifest, or a damaged one.
    os.remove(path)
  if not os.path.exists(path):
    if log:
      log(f"Downloading {name} ({entry['bytes'] / 1e6:.1f} MB)")
    if 'repo' in entry:
      _download_hf(entry, path, python_exe=python_exe, hf_cache_dir=hf_cache_dir)
    else:
      _download_url(entry['url'], path)
    actual = sha256_of(path)
    if actual != entry['sha256']:
      os.replace(path, path + '.unexpected')
      raise AssetError(
          f"{name} is not the pinned file: {_source_label(entry)} now has SHA-256 "
          f"{actual}, expected {entry['sha256']}. The file changed upstream. "
          f"It was kept as {path}.unexpected for review.")
  return path
