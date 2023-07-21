# Copyright 2023 The MediaPipe Authors. All Rights Reserved.
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
"""Main scripts to run text classification."""

import argparse

from mediapipe.tasks import python
from mediapipe.tasks.python import text


def run(model: str, input_text: str) -> None:
  """Classify input text using a Text Classifier TFLite model.

  Args:
    model: Name of the TFLite text classifier model.
    input_text: The input text to be classified.
  """
  # Initialize the text classifier model.
  base_options = python.BaseOptions(model_asset_path=model)
  options = text.TextClassifierOptions(base_options=base_options)
  classifier = text.TextClassifier.create_from_options(options)

  # Classify the input text.
  classification_result = classifier.classify(input_text)

  # Process the classification result. In this case, print out the most likely category.
  top_category = classification_result.classifications[0].categories[0]
  print(f'{top_category.category_name} ({top_category.score:.2f})')
                                         

def main():
  parser = argparse.ArgumentParser(
      formatter_class=argparse.ArgumentDefaultsHelpFormatter)
  parser.add_argument(
      '--model',
      help='Name of text classifier model.',
      required=False,
      default='classifier.tflite')
  parser.add_argument(
      '--inputText',
      help='Enter the text to classify.',
      required=True)
  args = parser.parse_args()

  run(args.model, args.inputText)


if __name__ == '__main__':
  main()
