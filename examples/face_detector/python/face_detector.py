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
"""Detects faces in an image with the MediaPipe Face Detector task.

This is the plain Python version of face_detector.ipynb.

  pip install mediapipe
  python face_detector.py

The model and the sample image are downloaded on first use. The image with the
detected faces drawn on it is written to --output.
"""

import argparse
import math
import os
import urllib.request
from typing import Optional, Sequence, Tuple, Union

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-FaceDetector-BlazeFace-Short-Range/resolve/main/blaze_face_short_range.tflite'
# The notebook uses https://i.imgur.com/Vu2Nqwb.jpeg, which that host does not
# serve to scripts. This is one of MediaPipe's own test images instead.
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-assets/portrait.jpg'

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}

MARGIN = 10  # pixels
ROW_SIZE = 10  # pixels
FONT_SIZE = 1
FONT_THICKNESS = 1
TEXT_COLOR = (255, 0, 0)  # red


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def _normalized_to_pixel_coordinates(
    normalized_x: float, normalized_y: float, image_width: int,
    image_height: int) -> Union[None, Tuple[int, int]]:
  """Converts normalized value pair to pixel coordinates."""

  # Checks if the float value is between 0 and 1.
  def is_valid_normalized_value(value: float) -> bool:
    return (value > 0 or math.isclose(0, value)) and (value < 1 or
                                                      math.isclose(1, value))

  if not (is_valid_normalized_value(normalized_x) and
          is_valid_normalized_value(normalized_y)):
    return None
  x_px = min(math.floor(normalized_x * image_width), image_width - 1)
  y_px = min(math.floor(normalized_y * image_height), image_height - 1)
  return x_px, y_px


def visualize(image, detection_result) -> np.ndarray:
  """Draws bounding boxes and keypoints on the input image and return it.

  Args:
    image: The input RGB image.
    detection_result: The list of all "Detection" entities to be visualize.

  Returns:
    Image with bounding boxes.
  """
  annotated_image = image.copy()
  height, width, _ = image.shape

  for detection in detection_result.detections:
    # Draw bounding_box
    bbox = detection.bounding_box
    start_point = bbox.origin_x, bbox.origin_y
    end_point = bbox.origin_x + bbox.width, bbox.origin_y + bbox.height
    cv2.rectangle(annotated_image, start_point, end_point, TEXT_COLOR, 3)

    # Draw keypoints
    for keypoint in detection.keypoints:
      keypoint_px = _normalized_to_pixel_coordinates(keypoint.x, keypoint.y,
                                                     width, height)
      if keypoint_px is None:
        # The keypoint lies outside of the image.
        continue
      color, thickness, radius = (0, 255, 0), 2, 2
      cv2.circle(annotated_image, keypoint_px, radius, color, thickness)

    # Draw label and score
    category = detection.categories[0]
    category_name = category.category_name
    category_name = '' if category_name is None else category_name
    probability = round(category.score, 2)
    result_text = category_name + ' (' + str(probability) + ')'
    text_location = (MARGIN + bbox.origin_x,
                     MARGIN + ROW_SIZE + bbox.origin_y)
    cv2.putText(annotated_image, result_text, text_location,
                cv2.FONT_HERSHEY_PLAIN, FONT_SIZE, TEXT_COLOR, FONT_THICKNESS)

  return annotated_image


def run(model: str,
        image_file: str,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None) -> vision.FaceDetectorResult:
  """Detects the faces in an image.

  Args:
    model: Path of the face detection model.
    image_file: Path of the image to run face detection on.
    delegate: The hardware to run the model on.
    output: Where to write the image with the detections drawn on it.

  Returns:
    The detection result.
  """
  # STEP 2: Create an FaceDetector object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.FaceDetectorOptions(base_options=base_options)
  detector = vision.FaceDetector.create_from_options(options)

  # STEP 3: Load the input image.
  image = mp.Image.create_from_file(image_file)

  # STEP 4: Detect faces in the input image.
  detection_result = detector.detect(image)
  detector.close()

  # STEP 5: Process the detection result. In this case, visualize it.
  print(f'Detected {len(detection_result.detections)} face(s) in {image_file}')
  for detection in detection_result.detections:
    bbox = detection.bounding_box
    print(f'  score {detection.categories[0].score:.2f}, box at '
          f'({bbox.origin_x}, {bbox.origin_y}), {bbox.width}x{bbox.height}')

  if output:
    image_copy = np.copy(image.numpy_view())
    annotated_image = visualize(image_copy, detection_result)
    # MediaPipe images are RGB, OpenCV writes BGR.
    cv2.imwrite(output, cv2.cvtColor(annotated_image, cv2.COLOR_RGB2BGR))
    print(f'Wrote {output}')

  return detection_result


def main(argv: Optional[Sequence[str]] = None) -> vision.FaceDetectorResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the face detection model. Downloaded if it is missing.',
      default='detector.tflite')
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
      default='face_detector_result.png')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_file = args.image
  if image_file is None:
    image_file = 'image.jpg'
    download(IMAGE_URL, image_file)

  return run(args.model, image_file, DELEGATES[args.delegate], args.output)


if __name__ == '__main__':
  main()
