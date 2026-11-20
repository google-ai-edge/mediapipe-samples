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
"""Recognizes hand gestures in images with the Gesture Recognizer task.

This is the plain Python version of gesture_recognizer.ipynb.

  pip install mediapipe
  python gesture_recognizer.py

The model and the sample images are downloaded on first use. A figure with the
recognized gesture and the hand landmarks of every image is written to
--output.
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
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-GestureRecognizer/resolve/main/gesture_recognizer.task'
# The images are from Pixabay.
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-tasks/gesture_recognizer/'
IMAGE_FILENAMES = [
    'thumbs_down.jpg', 'victory.jpg', 'thumbs_up.jpg', 'pointing_up.jpg'
]

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}
RUNNING_MODES = {
    'image': vision.RunningMode.IMAGE,
    'video': vision.RunningMode.VIDEO,
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

mp_hands = mp.tasks.vision.HandLandmarksConnections
mp_drawing = mp.tasks.vision.drawing_utils
mp_drawing_styles = mp.tasks.vision.drawing_styles


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


def display_batch_of_images_with_gestures_and_hand_landmarks(
    images, results, output: str) -> None:
  """Writes the images with their gesture and hand landmarks to a file."""
  # Images and labels.
  images = [image.numpy_view() for image in images]
  gestures = [top_gesture for (top_gesture, _) in results]
  multi_hand_landmarks_list = [
      multi_hand_landmarks for (_, multi_hand_landmarks) in results
  ]

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

  # Display gestures and hand landmarks.
  for i, (image, gesture) in enumerate(
      zip(images[:rows * cols], gestures[:rows * cols])):
    title = f'{gesture.category_name} ({gesture.score:.2f})'
    dynamic_titlesize = FIGSIZE * SPACING / max(rows, cols) * 40 + 3
    # mp.Image.create_from_file() returns RGBA data; the drawing utilities
    # need three channels.
    annotated_image = np.copy(image[:, :, :3] if image.shape[2] == 4 else image)

    for hand_landmarks in multi_hand_landmarks_list[i]:
      mp_drawing.draw_landmarks(
          annotated_image,
          hand_landmarks,
          mp_hands.HAND_CONNECTIONS,
          mp_drawing_styles.get_default_hand_landmarks_style(),
          mp_drawing_styles.get_default_hand_connections_style())

    subplot = display_one_image(annotated_image, title, subplot,
                                titlesize=dynamic_titlesize)

  # Layout.
  plt.tight_layout()
  plt.subplots_adjust(wspace=SPACING, hspace=SPACING)
  plt.savefig(output)
  plt.close()


def run(model: str,
        image_files: Sequence[str],
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None,
        running_mode: vision.RunningMode = vision.RunningMode.IMAGE
        ) -> List[vision.GestureRecognizerResult]:
  """Recognizes the hand gesture in each image.

  Args:
    model: Path of the gesture recognizer model bundle.
    image_files: Paths of the images to recognize gestures in.
    delegate: The hardware to run the model on.
    output: Where to write the figure with the gestures and hand landmarks.
    running_mode: IMAGE for single images, or VIDEO to feed video frames.

  Returns:
    The recognition result of each image, in the order of `image_files`.
  """
  # STEP 2: Create an GestureRecognizer object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.GestureRecognizerOptions(base_options=base_options,
                                            running_mode=running_mode)
  recognizer = vision.GestureRecognizer.create_from_options(options)

  recognition_results = []
  images = []
  results = []
  timestamp_ms = 0
  for image_file_name in image_files:
    # STEP 3: Load the input image.
    image = mp.Image.create_from_file(image_file_name)

    # STEP 4: Recognize gestures in the input image.
    if running_mode == vision.RunningMode.VIDEO:
      for _ in range(3):
        recognition_result = recognizer.recognize_for_video(image, timestamp_ms)
        timestamp_ms += 33
    else:
      recognition_result = recognizer.recognize(image)
    recognition_results.append(recognition_result)

    # STEP 5: Process the result. In this case, visualize it.
    if not recognition_result.gestures:
      print(f'{image_file_name}: no hand detected')
      continue
    top_gesture = recognition_result.gestures[0][0]
    print(f'{image_file_name}: {top_gesture.category_name} '
          f'({top_gesture.score:.2f})')
    images.append(image)
    hand_landmarks = recognition_result.hand_landmarks
    results.append((top_gesture, hand_landmarks))

  recognizer.close()

  if output and images:
    display_batch_of_images_with_gestures_and_hand_landmarks(
        images, results, output)
    print(f'Wrote {output}')

  return recognition_results


def main(argv: Optional[Sequence[str]] = None
         ) -> List[vision.GestureRecognizerResult]:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the gesture recognizer model. Downloaded if it is missing.',
      default='gesture_recognizer.task')
  parser.add_argument(
      '--images',
      help='Paths of the input images. Without it, sample images are '
      'downloaded.',
      nargs='+',
      default=None)
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
  parser.add_argument(
      '--output',
      help='Where to write the figure with the results.',
      default='gesture_recognizer_result.png')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_files = args.images
  if image_files is None:
    image_files = IMAGE_FILENAMES
    for name in image_files:
      download(IMAGE_URL + name, name)

  return run(args.model, image_files, DELEGATES[args.delegate], args.output,
             RUNNING_MODES[args.running_mode])


if __name__ == '__main__':
  main()
