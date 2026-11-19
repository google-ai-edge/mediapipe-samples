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
"""Corrects the grammar and spelling of a text with the Text Proofreader task.

This is the plain Python version of text_proofreader.ipynb.

  pip install mediapipe
  python text_proofreader.py --text "I am a studnt."

The model is downloaded on first use. The corrected text is printed, followed
by the segments of the text that were kept, inserted and deleted.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

from mediapipe.tasks import python
from mediapipe.tasks.python import text

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-TextProofreader-200M/resolve/main/proofread_quant_200m.litertlm'

# The text that is proofread by default.
INPUT_TEXT = 'I am a studnt.'

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def run(model: str,
        input_text: str,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> text.TextProofreaderResult:
  """Proofreads a text.

  Args:
    model: Path of the text proofreader model.
    input_text: The text to proofread.
    delegate: The hardware to run the model on.

  Returns:
    The proofreading result.
  """
  # Create your base options with the model that was downloaded earlier
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)

  # Create the final set of options for the Proofreader
  options = text.TextProofreaderOptions(base_options=base_options)

  with text.TextProofreader.create_from_options(options) as proofreader:
    # Run proofreading on the text
    proofreader_result = proofreader.proofread(input_text)

  # Print proofread text
  print(proofreader_result.proofread_text)

  # Print each correction segment and its type
  for correction in proofreader_result.corrections:
    print(f'[{correction.type.name}]: {correction.text}')

  return proofreader_result


def main(argv: Optional[Sequence[str]] = None) -> text.TextProofreaderResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the text proofreader model. Downloaded if it is missing.',
      default='proofread_quant_200m.litertlm')
  parser.add_argument(
      '--text', help='The text to proofread.', default=INPUT_TEXT)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)

  return run(args.model, args.text, DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
