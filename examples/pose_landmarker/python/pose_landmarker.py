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
"""Detects pose landmarks in an image with the MediaPipe Pose Landmarker task.

This is the plain Python version of
[MediaPipe_Python_Tasks]_Pose_Landmarker.ipynb.

  pip install mediapipe
  python pose_landmarker.py

The model and the sample image are downloaded on first use. The image with the
pose landmarks drawn on it is written to --output, and the segmentation mask of
the person to --mask_output.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.vision import drawing_styles
from mediapipe.tasks.python.vision import drawing_utils

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-PoseLandmarker-Heavy/resolve/main/pose_landmarker_heavy.task'
# The notebook uses
# https://cdn.pixabay.com/photo/2019/03/12/20/39/girl-4051811_960_720.jpg, which
# that host does not serve to scripts. This is one of MediaPipe's own test
# images instead.
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-assets/pose.jpg'

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


def draw_landmarks_on_image(rgb_image, detection_result):
  pose_landmarks_list = detection_result.pose_landmarks
  # mp.Image.create_from_file() returns RGBA data; the drawing utilities need
  # three channels.
  annotated_image = np.copy(
      rgb_image[:, :, :3] if rgb_image.shape[2] == 4 else rgb_image)

  pose_landmark_style = drawing_styles.get_default_pose_landmarks_style()
  pose_connection_style = drawing_utils.DrawingSpec(color=(0, 255, 0),
                                                    thickness=2)

  for pose_landmarks in pose_landmarks_list:
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=pose_landmarks,
        connections=vision.PoseLandmarksConnections.POSE_LANDMARKS,
        landmark_drawing_spec=pose_landmark_style,
        connection_drawing_spec=pose_connection_style)

  return annotated_image


def run(model: str,
        image_file: str,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None,
        mask_output: Optional[str] = None) -> vision.PoseLandmarkerResult:
  """Detects the pose landmarks in an image.

  Args:
    model: Path of the pose landmarker model bundle.
    image_file: Path of the image to run pose landmark detection on.
    delegate: The hardware to run the model on.
    output: Where to write the image with the pose landmarks drawn on it.
    mask_output: Where to write the segmentation mask of the first person.

  Returns:
    The detection result.
  """
  # STEP 2: Create an PoseLandmarker object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.PoseLandmarkerOptions(
      base_options=base_options,
      output_segmentation_masks=True)
  detector = vision.PoseLandmarker.create_from_options(options)

  # STEP 3: Load the input image.
  image = mp.Image.create_from_file(image_file)

  # STEP 4: Detect pose landmarks from the input image.
  detection_result = detector.detect(image)
  detector.close()

  # STEP 5: Process the detection result. In this case, visualize it.
  print(f'Detected {len(detection_result.pose_landmarks)} pose(s) in '
        f'{image_file}')
  if output:
    annotated_image = draw_landmarks_on_image(image.numpy_view(),
                                              detection_result)
    cv2.imwrite(output, cv2.cvtColor(annotated_image, cv2.COLOR_RGB2BGR))
    print(f'Wrote {output}')

  # Visualize the pose segmentation mask.
  if mask_output and detection_result.segmentation_masks:
    segmentation_mask = detection_result.segmentation_masks[0].numpy_view()
    segmentation_mask = np.squeeze(segmentation_mask)

    # Convert to 3-channel uint8 image for visualization.
    visualized_mask = (segmentation_mask * 255).astype(np.uint8)
    visualized_mask = np.stack([visualized_mask] * 3, axis=-1)
    cv2.imwrite(mask_output, visualized_mask)
    print(f'Wrote {mask_output}')

  return detection_result


def main(argv: Optional[Sequence[str]] = None) -> vision.PoseLandmarkerResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the pose landmarker model. Downloaded if it is missing.',
      default='pose_landmarker.task')
  parser.add_argument(
      '--image',
      help='Path of the input image. Without it, a sample image is downloaded.',
      default=None)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  parser.add_argument(
      '--output',
      help='Where to write the annotated image.',
      default='pose_landmarker_result.png')
  parser.add_argument(
      '--mask_output',
      help='Where to write the segmentation mask.',
      default='pose_segmentation_mask.png')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_file = args.image
  if image_file is None:
    image_file = 'image.jpg'
    download(IMAGE_URL, image_file)

  return run(args.model, image_file, DELEGATES[args.delegate], args.output,
             args.mask_output)


if __name__ == '__main__':
  main()
