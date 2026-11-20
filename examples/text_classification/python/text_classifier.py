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
"""Classifies the sentiment of a text with the MediaPipe Text Classifier task.

This is the plain Python version of text_classifier.ipynb.

  pip install mediapipe huggingface_hub
  python text_classifier.py --text "I'm looking forward to what will come next."

The model is downloaded on first use. The most likely category is printed.
"""

import argparse
from typing import Optional, Sequence

from huggingface_hub import hf_hub_download
from mediapipe.tasks import python
from mediapipe.tasks.python import text

MODEL_REPO = 'litert-community/MediaPipe-TextClassifier-MobileBERT'
MODEL_FILENAME = 'bert_classifier.tflite'

# The input text that the model classifies by default.
INPUT_TEXT = "I'm looking forward to what will come next."

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}


def run(model: str,
        input_text: str,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> text.TextClassifierResult:
  """Classifies a text.

  Args:
    model: Path of the text classification model.
    input_text: The text to classify.
    delegate: The hardware to run the model on.

  Returns:
    The classification result.
  """
  # STEP 2: Create an TextClassifier object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = text.TextClassifierOptions(base_options=base_options)
  classifier = text.TextClassifier.create_from_options(options)

  # STEP 3: Classify the input text.
  classification_result = classifier.classify(input_text)
  classifier.close()

  # STEP 4: Process the classification result. In this case, print out the most
  # likely category.
  top_category = classification_result.classifications[0].categories[0]
  print(f'{top_category.category_name} ({top_category.score:.2f})')

  return classification_result


def main(argv: Optional[Sequence[str]] = None) -> text.TextClassifierResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the text classification model. Downloaded from Hugging '
      'Face if omitted.',
      default=None)
  parser.add_argument(
      '--text', help='The text to classify.', default=INPUT_TEXT)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  model_path = args.model or hf_hub_download(
      repo_id=MODEL_REPO, filename=MODEL_FILENAME)

  return run(model_path, args.text, DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
