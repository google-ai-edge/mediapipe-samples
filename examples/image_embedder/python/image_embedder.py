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
"""Compares two images with the MediaPipe Image Embedder task.

This is the plain Python version of image_embedder.ipynb.

  pip install mediapipe huggingface_hub
  python image_embedder.py

The model and the two sample images are downloaded on first use. The cosine
similarity of the two images is printed.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

from huggingface_hub import hf_hub_download
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_REPO = 'litert-community/MediaPipe-ImageEmbedder-MobileNet-V3-Small'
MODEL_FILENAME = 'mobilenet_v3_small.tflite'
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-assets/'
IMAGE_FILENAMES = ['burger.jpg', 'burger_crop.jpg']

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}
RUNNING_MODES = {
    'image': vision.RunningMode.IMAGE,
    'video': vision.RunningMode.VIDEO,
}


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def run(model: str,
        first_image_file: str,
        second_image_file: str,
        l2_normalize: bool = True,
        quantize: bool = True,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        running_mode: vision.RunningMode = vision.RunningMode.IMAGE
        ) -> float:
  """Computes how similar two images are.

  Args:
    model: Path of the image embedding model.
    first_image_file: Path of the first image.
    second_image_file: Path of the second image.
    l2_normalize: Whether to normalize the embeddings to length 1.
    quantize: Whether to quantize the embeddings to bytes.
    delegate: The hardware to run the model on.
    running_mode: IMAGE for single images, or VIDEO to feed video frames.

  Returns:
    The cosine similarity of the embeddings of the two images.
  """
  # Create options for Image Embedder
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.ImageEmbedderOptions(
      base_options=base_options,
      running_mode=running_mode,
      l2_normalize=l2_normalize,
      quantize=quantize)

  # Create Image Embedder
  with vision.ImageEmbedder.create_from_options(options) as embedder:

    # Format images for MediaPipe
    first_image = mp.Image.create_from_file(first_image_file)
    second_image = mp.Image.create_from_file(second_image_file)
    if running_mode == vision.RunningMode.VIDEO:
      first_embedding_result = embedder.embed_for_video(first_image, 0)
      second_embedding_result = embedder.embed_for_video(second_image, 33)
    else:
      first_embedding_result = embedder.embed(first_image)
      second_embedding_result = embedder.embed(second_image)

    # Calculate and print similarity
    similarity = vision.ImageEmbedder.cosine_similarity(
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
      help='Path of the image embedding model. Downloaded from Hugging Face if '
      'omitted.',
      default=None)
  parser.add_argument(
      '--images',
      help='Paths of the two images to compare. Without it, two sample images '
      'are downloaded.',
      nargs=2,
      metavar=('FIRST', 'SECOND'),
      default=None)
  parser.add_argument(
      '--l2_normalize',
      help='Normalize the embeddings to length 1.',
      action=argparse.BooleanOptionalAction,
      default=True)
  parser.add_argument(
      '--quantize',
      help='Quantize the embeddings to bytes.',
      action=argparse.BooleanOptionalAction,
      default=True)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  parser.add_argument(
      '--running_mode',
      help='Task running mode.',
      choices=sorted(RUNNING_MODES),
      default='image')
  args = parser.parse_args(argv)

  model_path = args.model or hf_hub_download(
      repo_id=MODEL_REPO, filename=MODEL_FILENAME)
  image_files = args.images
  if image_files is None:
    image_files = IMAGE_FILENAMES
    for name in image_files:
      download(IMAGE_URL + name, name)

  return run(model_path, image_files[0], image_files[1], args.l2_normalize,
             args.quantize, DELEGATES[args.delegate],
             RUNNING_MODES[args.running_mode])


if __name__ == '__main__':
  main()
