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
"""Detects the language of a text with the MediaPipe Language Detector task.

This is the plain Python version of
[MediaPipe_Python_Tasks]_Language_Detector.ipynb.

  pip install mediapipe
  python language_detector.py --text "To be, or not to be, that is the question"

The model is downloaded on first use. The detected languages and their
probabilities are printed.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

from mediapipe.tasks import python
from mediapipe.tasks.python import text

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-LanguageDetector/resolve/main/language_detector.tflite'

# The input text that the model classifies by default: a Chinese proverb.
INPUT_TEXT = '\u5206\u4E45\u5FC5\u5408\u5408\u4E45\u5FC5\u5206'

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
        ) -> text.LanguageDetectorResult:
  """Detects the language(s) of a text.

  Args:
    model: Path of the language detection model.
    input_text: The text to detect the language of.
    delegate: The hardware to run the model on.

  Returns:
    The detection result.
  """
  # STEP 2: Create a LanguageDetector object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = text.LanguageDetectorOptions(base_options=base_options)
  detector = text.LanguageDetector.create_from_options(options)

  # STEP 3: Get the language detection result for the input text.
  detection_result = detector.detect(input_text)
  detector.close()

  # STEP 4: Process the detection result and print the languages detected and
  # their scores.
  for detection in detection_result.detections:
    print(f'{detection.language_code}: ({detection.probability:.2f})')

  return detection_result


def main(argv: Optional[Sequence[str]] = None) -> text.LanguageDetectorResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the language detection model. Downloaded if it is missing.',
      default='detector.tflite')
  parser.add_argument(
      '--text', help='The text to detect the language of.', default=INPUT_TEXT)
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
