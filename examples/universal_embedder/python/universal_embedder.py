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
"""Compares texts and images with the MediaPipe Universal Embedder task.

  pip install mediapipe
  python universal_embedder.py --inputs "a photo of a cat" cat.jpg

The Universal Embedder maps texts and images into one embedding space, so a
text can be compared with an image. Each of the two inputs is a text, or the
path of an image file. The model (about 390 MB) and a sample image are
downloaded on first use. The cosine similarity of the two inputs is printed.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

from mediapipe.tasks import python
from mediapipe.tasks.python import retrieval
from mediapipe.tasks.python.components.containers import embedding_result

MODEL_URL = 'https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/main/embeddinggemma-2-text-vision-440m.litertlm'
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-tasks/image_classifier/cat.jpg'

# The text that is compared with the sample image by default.
INPUT_TEXT = 'a photo of a cat'

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


def embed(embedder: retrieval.UniversalEmbedder,
          text_or_image_file: str) -> embedding_result.Embedding:
  """Embeds an input: the image if it names a file, the text otherwise."""
  if os.path.isfile(text_or_image_file):
    with open(text_or_image_file, 'rb') as f:
      return embedder.embed_image(f.read()).embeddings[0]
  return embedder.embed_text(text_or_image_file).embeddings[0]


def run(model: str,
        first: str,
        second: str,
        l2_normalize: bool = True,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> float:
  """Computes how similar two inputs are.

  Args:
    model: Path of the embedding model.
    first: The first input: a text, or the path of an image file.
    second: The second input, like `first`.
    l2_normalize: Whether to normalize the embeddings to length 1.
    delegate: The hardware to run the model on.

  Returns:
    The cosine similarity of the embeddings of the two inputs.
  """
  # Create the options for the Universal Embedder
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = retrieval.UniversalEmbedderOptions(
      base_options=base_options, l2_normalize=l2_normalize)

  with retrieval.UniversalEmbedder.create_from_options(options) as embedder:
    # Convert both inputs to embeddings
    first_embedding = embed(embedder, first)
    second_embedding = embed(embedder, second)

    # Calculate and print similarity
    similarity = retrieval.UniversalEmbedder.cosine_similarity(
        first_embedding, second_embedding)
    print(similarity)

  return similarity


def main(argv: Optional[Sequence[str]] = None) -> float:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the embedding model. Downloaded if it is missing.',
      default='embeddinggemma-2-text-vision-440m.litertlm')
  parser.add_argument(
      '--inputs',
      help='The two inputs to compare. Each is a text, or the path of an '
      'image file. Without it, a text is compared with a sample image.',
      nargs=2,
      metavar=('FIRST', 'SECOND'),
      default=None)
  parser.add_argument(
      '--l2_normalize',
      help='Normalize the embeddings to length 1.',
      action=argparse.BooleanOptionalAction,
      default=True)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  inputs = args.inputs
  if inputs is None:
    download(IMAGE_URL, 'cat.jpg')
    inputs = [INPUT_TEXT, 'cat.jpg']

  return run(args.model, inputs[0], inputs[1], args.l2_normalize,
             DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
