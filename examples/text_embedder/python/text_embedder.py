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
"""Compares two texts with the MediaPipe Text Embedder task.

This is the plain Python version of text_embedder.ipynb.

  pip install mediapipe
  python text_embedder.py --texts "I'm feeling so good" "I'm okay I guess"

The model is downloaded on first use. The cosine similarity of the two texts is
printed.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

from mediapipe.tasks import python
from mediapipe.tasks.python import text

MODEL_URL = 'https://storage.googleapis.com/mediapipe-models/text_embedder/bert_embedder/float32/1/bert_embedder.tflite'

# The two texts that are compared by default.
FIRST_TEXT = "I'm feeling so good"
SECOND_TEXT = "I'm okay I guess"

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
        first_text: str,
        second_text: str,
        l2_normalize: bool = True,
        quantize: bool = False,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> float:
  """Computes how similar two texts are.

  Args:
    model: Path of the text embedding model.
    first_text: The first text.
    second_text: The second text.
    l2_normalize: Whether to normalize the embeddings to length 1.
    quantize: Whether to quantize the embeddings to bytes.
    delegate: The hardware to run the model on.

  Returns:
    The cosine similarity of the embeddings of the two texts.
  """
  # Create your base options with the model that was downloaded earlier
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)

  # Create the final set of options for the Embedder
  options = text.TextEmbedderOptions(
      base_options=base_options, l2_normalize=l2_normalize, quantize=quantize)

  with text.TextEmbedder.create_from_options(options) as embedder:
    # Convert both sets of text to embeddings
    first_embedding_result = embedder.embed(first_text)
    second_embedding_result = embedder.embed(second_text)

    # Calculate and print similarity
    similarity = text.TextEmbedder.cosine_similarity(
        first_embedding_result.embeddings[0],
        second_embedding_result.embeddings[0])
    print(similarity)

  return similarity


def main(argv: Optional[Sequence[str]] = None) -> float:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the text embedding model. Downloaded if it is missing.',
      default='embedder.tflite')
  parser.add_argument(
      '--texts',
      help='The two texts to compare.',
      nargs=2,
      metavar=('FIRST', 'SECOND'),
      default=[FIRST_TEXT, SECOND_TEXT])
  parser.add_argument(
      '--l2_normalize',
      help='Normalize the embeddings to length 1.',
      action=argparse.BooleanOptionalAction,
      default=True)
  parser.add_argument(
      '--quantize',
      help='Quantize the embeddings to bytes.',
      action=argparse.BooleanOptionalAction,
      default=False)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)

  return run(args.model, args.texts[0], args.texts[1], args.l2_normalize,
             args.quantize, DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
