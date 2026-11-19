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
"""Classifies images with the MediaPipe Image Classifier task.

This is the plain Python version of image_classifier.ipynb.

  pip install mediapipe
  python image_classifier.py

The model and the sample images are downloaded on first use. A figure with the
top category of every image is written to --output.
"""

import argparse
import math
import os
import urllib.request
from typing import List, Optional, Sequence

import matplotlib

matplotlib.use('Agg')  # Render to files; no display is needed.
from matplotlib import pyplot as plt
import mediapipe as mp
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_URL = 'https://storage.googleapis.com/mediapipe-models/image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite'
# The images are from Pixabay.
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-tasks/image_classifier/'
IMAGE_FILENAMES = ['burger.jpg', 'cat.jpg']

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}

plt.rcParams.update({
    'axes.spines.top': False,
    'axes.spines.right': False,
    'axes.spines.left': False,
    'axes.spines.bottom': False,
    'xtick.labelbottom': False,
    'xtick.bottom': False,
    'ytick.labelleft': False,
    'ytick.left': False,
    'xtick.labeltop': False,
    'xtick.top': False,
    'ytick.labelright': False,
    'ytick.right': False
})


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def display_one_image(image, title, subplot, titlesize=16):
  """Displays one image along with the predicted category name and score."""
  plt.subplot(*subplot)
  plt.imshow(image)
  if len(title) > 0:
    plt.title(title, fontsize=int(titlesize), color='black',
              fontdict={'verticalalignment': 'center'},
              pad=int(titlesize / 1.5))
  return (subplot[0], subplot[1], subplot[2] + 1)


def display_batch_of_images(images, predictions, output: str) -> None:
  """Writes a batch of images with their classifications to a file."""
  # Images and predictions.
  images = [image.numpy_view() for image in images]

  # Auto-squaring: this will drop data that does not fit into square or
  # square-ish rectangle.
  rows = int(math.sqrt(len(images)))
  cols = len(images) // rows

  # Size and spacing.
  FIGSIZE = 13.0
  SPACING = 0.1
  subplot = (rows, cols, 1)
  if rows < cols:
    plt.figure(figsize=(FIGSIZE, FIGSIZE / cols * rows))
  else:
    plt.figure(figsize=(FIGSIZE / rows * cols, FIGSIZE))

  # Display.
  for i, (image, prediction) in enumerate(
      zip(images[:rows * cols], predictions[:rows * cols])):
    dynamic_titlesize = FIGSIZE * SPACING / max(rows, cols) * 40 + 3
    subplot = display_one_image(image, prediction, subplot,
                                titlesize=dynamic_titlesize)

  # Layout.
  plt.tight_layout()
  plt.subplots_adjust(wspace=SPACING, hspace=SPACING)
  plt.savefig(output)
  plt.close()


def run(model: str,
        image_files: Sequence[str],
        max_results: int = 4,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None) -> List[vision.ImageClassifierResult]:
  """Classifies each image.

  Args:
    model: Path of the image classification model.
    image_files: Paths of the images to classify.
    max_results: The maximum number of categories to return per image.
    delegate: The hardware to run the model on.
    output: Where to write the figure with the top category of every image.

  Returns:
    The classification result of each image, in the order of `image_files`.
  """
  # STEP 2: Create an ImageClassifier object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.ImageClassifierOptions(
      base_options=base_options, max_results=max_results)
  classifier = vision.ImageClassifier.create_from_options(options)

  classification_results = []
  images = []
  predictions = []
  for image_name in image_files:
    # STEP 3: Load the input image.
    image = mp.Image.create_from_file(image_name)

    # STEP 4: Classify the input image.
    classification_result = classifier.classify(image)
    classification_results.append(classification_result)

    # STEP 5: Process the classification result. In this case, visualize it.
    images.append(image)
    top_category = classification_result.classifications[0].categories[0]
    predictions.append(
        f'{top_category.category_name} ({top_category.score:.2f})')
    print(f'{image_name}: {predictions[-1]}')

  classifier.close()

  if output and images:
    display_batch_of_images(images, predictions, output)
    print(f'Wrote {output}')

  return classification_results


def main(argv: Optional[Sequence[str]] = None
         ) -> List[vision.ImageClassifierResult]:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the image classification model. Downloaded if it is '
      'missing.',
      default='classifier.tflite')
  parser.add_argument(
      '--images',
      help='Paths of the input images. Without it, sample images are '
      'downloaded.',
      nargs='+',
      default=None)
  parser.add_argument(
      '--max_results',
      help='The maximum number of categories to return per image.',
      type=int,
      default=4)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  parser.add_argument(
      '--output',
      help='Where to write the figure with the results.',
      default='image_classifier_result.png')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_files = args.images
  if image_files is None:
    image_files = IMAGE_FILENAMES
    for name in image_files:
      download(IMAGE_URL + name, name)

  return run(args.model, image_files, args.max_results,
             DELEGATES[args.delegate], args.output)


if __name__ == '__main__':
  main()
