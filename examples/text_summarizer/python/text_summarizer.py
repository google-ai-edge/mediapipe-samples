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
"""Summarizes a text with the MediaPipe Text Summarizer task.

This is the plain Python version of text_summarizer.ipynb.

  pip install mediapipe
  python text_summarizer.py --mode keypoints

The model is downloaded on first use. The summary is printed.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

from mediapipe.tasks import python
from mediapipe.tasks.python import text

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-TextSummarizer-200M/resolve/main/summarization_quant_200m_2modes.litertlm'

# The text that is summarized by default.
INPUT_TEXT = (
    'This station is in the centre of the European quarter of Brussels, being '
    'adjacent to the Berlaymont building (headquarters of the European '
    'Commission), the Justus Lipsius building (Council of the European Union) '
    'and numerous other EU offices. The station is named after the area around '
    'the Schuman roundabout, which was named after Robert Schuman. The station '
    'sits on the Rue de la Loi/Wetstraat, a major city thoroughfare, and is '
    'close to Parc du Cinquantenaire/Jubelpark.')

MODES = {
    'keypoints': text.TextSummarizerMode.KEYPOINTS,
    'tldr': text.TextSummarizerMode.TLDR,
}

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
        mode: text.TextSummarizerMode = text.TextSummarizerMode.KEYPOINTS,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> text.TextSummarizerResult:
  """Summarizes a text.

  Args:
    model: Path of the text summarizer model.
    input_text: The text to summarize.
    mode: The kind of summary: a list of key points, or a short paragraph.
    delegate: The hardware to run the model on.

  Returns:
    The summarization result.
  """
  # Create your base options with the model that was downloaded earlier
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)

  # Create the final set of options for the Summarizer
  options = text.TextSummarizerOptions(base_options=base_options, mode=mode)

  with text.TextSummarizer.create_from_options(options) as summarizer:
    # Run summarization on the text
    summarizer_result = summarizer.summarize(input_text)

  # Print the summary
  print(summarizer_result.summary)

  return summarizer_result


def main(argv: Optional[Sequence[str]] = None) -> text.TextSummarizerResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the text summarizer model. Downloaded if it is missing.',
      default='summarization_quant_200m_2modes.litertlm')
  parser.add_argument(
      '--text', help='The text to summarize.', default=INPUT_TEXT)
  parser.add_argument(
      '--mode',
      help='The kind of summary: a list of key points, or a short paragraph.',
      choices=sorted(MODES),
      default='keypoints')
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)

  return run(args.model, args.text, MODES[args.mode], DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
