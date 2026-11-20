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
"""Detects hand landmarks in an image with the MediaPipe Hand Landmarker task.

This is the plain Python version of hand_landmarker.ipynb.

  pip install mediapipe huggingface_hub
  python hand_landmarker.py

The model and the sample image are downloaded on first use. The image with the
hand landmarks drawn on it is written to --output.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

import cv2
from huggingface_hub import hf_hub_download
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_REPO = 'litert-community/MediaPipe-HandLandmarker-Full'
MODEL_FILENAME = 'hand_landmarker.task'
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-tasks/hand_landmarker/woman_hands.jpg'

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}
RUNNING_MODES = {
    'image': vision.RunningMode.IMAGE,
    'video': vision.RunningMode.VIDEO,
}

mp_hands = mp.tasks.vision.HandLandmarksConnections
mp_drawing = mp.tasks.vision.drawing_utils
mp_drawing_styles = mp.tasks.vision.drawing_styles

MARGIN = 10  # pixels
FONT_SIZE = 1
FONT_THICKNESS = 1
HANDEDNESS_TEXT_COLOR = (88, 205, 54)  # vibrant green


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def draw_landmarks_on_image(rgb_image, detection_result):
  hand_landmarks_list = detection_result.hand_landmarks
  handedness_list = detection_result.handedness
  # mp.Image.create_from_file() returns RGBA data; the drawing utilities need
  # three channels.
  annotated_image = np.copy(
      rgb_image[:, :, :3] if rgb_image.shape[2] == 4 else rgb_image)

  # Loop through the detected hands to visualize.
  for idx in range(len(hand_landmarks_list)):
    hand_landmarks = hand_landmarks_list[idx]
    handedness = handedness_list[idx]

    # Draw the hand landmarks.
    mp_drawing.draw_landmarks(
        annotated_image,
        hand_landmarks,
        mp_hands.HAND_CONNECTIONS,
        mp_drawing_styles.get_default_hand_landmarks_style(),
        mp_drawing_styles.get_default_hand_connections_style())

    # Get the top left corner of the detected hand's bounding box.
    height, width, _ = annotated_image.shape
    x_coordinates = [landmark.x for landmark in hand_landmarks]
    y_coordinates = [landmark.y for landmark in hand_landmarks]
    text_x = int(min(x_coordinates) * width)
    text_y = int(min(y_coordinates) * height) - MARGIN

    # Draw handedness (left or right hand) on the image.
    cv2.putText(annotated_image, f'{handedness[0].category_name}',
                (text_x, text_y), cv2.FONT_HERSHEY_DUPLEX,
                FONT_SIZE, HANDEDNESS_TEXT_COLOR, FONT_THICKNESS, cv2.LINE_AA)

  return annotated_image


def run(model: str,
        image_file: str,
        num_hands: int = 2,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None,
        running_mode: vision.RunningMode = vision.RunningMode.IMAGE
        ) -> vision.HandLandmarkerResult:
  """Detects the hand landmarks in an image.

  Args:
    model: Path of the hand landmarker model bundle.
    image_file: Path of the image to run hand landmark detection on.
    num_hands: The maximum number of hands to detect.
    delegate: The hardware to run the model on.
    output: Where to write the image with the hand landmarks drawn on it.
    running_mode: IMAGE for a single image, or VIDEO to feed video frames.

  Returns:
    The detection result.
  """
  # STEP 2: Create an HandLandmarker object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.HandLandmarkerOptions(base_options=base_options,
                                         running_mode=running_mode,
                                         num_hands=num_hands)
  detector = vision.HandLandmarker.create_from_options(options)

  # STEP 3: Load the input image.
  image = mp.Image.create_from_file(image_file)

  # STEP 4: Detect hand landmarks from the input image.
  if running_mode == vision.RunningMode.VIDEO:
    for timestamp_ms in (0, 33, 66):
      detection_result = detector.detect_for_video(image, timestamp_ms)
  else:
    detection_result = detector.detect(image)
  detector.close()

  # STEP 5: Process the classification result. In this case, visualize it.
  print(f'Detected {len(detection_result.hand_landmarks)} hand(s) in '
        f'{image_file}')
  for handedness in detection_result.handedness:
    print(f'  {handedness[0].category_name} ({handedness[0].score:.2f})')

  if output:
    annotated_image = draw_landmarks_on_image(image.numpy_view(),
                                              detection_result)
    cv2.imwrite(output, cv2.cvtColor(annotated_image, cv2.COLOR_RGB2BGR))
    print(f'Wrote {output}')

  return detection_result


def main(argv: Optional[Sequence[str]] = None) -> vision.HandLandmarkerResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the hand landmarker model. Downloaded from Hugging Face if '
      'omitted.',
      default=None)
  parser.add_argument(
      '--image',
      help='Path of the input image. Without it, a sample image is downloaded.',
      default=None)
  parser.add_argument(
      '--num_hands',
      help='The maximum number of hands to detect.',
      type=int,
      default=2)
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
      help='Where to write the annotated image.',
      default='hand_landmarker_result.png')
  args = parser.parse_args(argv)

  model_path = args.model or hf_hub_download(
      repo_id=MODEL_REPO, filename=MODEL_FILENAME)
  image_file = args.image
  if image_file is None:
    image_file = 'image.jpg'
    download(IMAGE_URL, image_file)

  return run(model_path, image_file, args.num_hands, DELEGATES[args.delegate],
             args.output, RUNNING_MODES[args.running_mode])


if __name__ == '__main__':
  main()
