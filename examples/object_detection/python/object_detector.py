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
"""Detects objects in an image with the MediaPipe Object Detector task.

This is the plain Python version of object_detector.ipynb.

  pip install mediapipe
  python object_detector.py

The model and the sample image are downloaded on first use. The image with the
detected objects drawn on it is written to --output.
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

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-ObjectDetector-EfficientDet-Lite0/resolve/main/efficientdet_lite0_int8.tflite'
# This image comes from Pixabay:
# https://pixabay.com/photos/pet-cute-animal-domestic-mammal-3157961/
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-tasks/object_detector/cat_and_dog.jpg'

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


def visualize(image, detection_result) -> np.ndarray:
  """Draws bounding boxes on the input image and return it.

  Args:
    image: The input RGB image.
    detection_result: The list of all "Detection" entities to be visualize.

  Returns:
    Image with bounding boxes.
  """
  for detection in detection_result.detections:
    # Draw bounding_box
    bbox = detection.bounding_box
    start_point = bbox.origin_x, bbox.origin_y
    end_point = bbox.origin_x + bbox.width, bbox.origin_y + bbox.height
    cv2.rectangle(image, start_point, end_point, TEXT_COLOR, 3)

    # Draw label and score
    category = detection.categories[0]
    category_name = category.category_name
    probability = round(category.score, 2)
    result_text = category_name + ' (' + str(probability) + ')'
    text_location = (MARGIN + bbox.origin_x,
                     MARGIN + ROW_SIZE + bbox.origin_y)
    cv2.putText(image, result_text, text_location, cv2.FONT_HERSHEY_PLAIN,
                FONT_SIZE, TEXT_COLOR, FONT_THICKNESS)

  return image


def run(model: str,
        image_file: str,
        score_threshold: float = 0.5,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None) -> vision.ObjectDetectorResult:
  """Detects the objects in an image.

  Args:
    model: Path of the object detection model.
    image_file: Path of the image to run object detection on.
    score_threshold: Detections with a lower score are dropped.
    delegate: The hardware to run the model on.
    output: Where to write the image with the detections drawn on it.

  Returns:
    The detection result.
  """
  # STEP 2: Create an ObjectDetector object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.ObjectDetectorOptions(base_options=base_options,
                                         score_threshold=score_threshold)
  detector = vision.ObjectDetector.create_from_options(options)

  # STEP 3: Load the input image.
  image = mp.Image.create_from_file(image_file)

  # STEP 4: Detect objects in the input image.
  detection_result = detector.detect(image)
  detector.close()

  # STEP 5: Process the detection result. In this case, visualize it.
  print(f'Detected {len(detection_result.detections)} object(s) in '
        f'{image_file}')
  for detection in detection_result.detections:
    category = detection.categories[0]
    bbox = detection.bounding_box
    print(f'  {category.category_name} ({category.score:.2f}), box at '
          f'({bbox.origin_x}, {bbox.origin_y}), {bbox.width}x{bbox.height}')

  if output:
    image_copy = np.copy(image.numpy_view())
    annotated_image = visualize(image_copy, detection_result)
    # MediaPipe images are RGB, OpenCV writes BGR.
    cv2.imwrite(output, cv2.cvtColor(annotated_image, cv2.COLOR_RGB2BGR))
    print(f'Wrote {output}')

  return detection_result


def main(argv: Optional[Sequence[str]] = None) -> vision.ObjectDetectorResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the object detection model. Downloaded if it is missing.',
      default='efficientdet.tflite')
  parser.add_argument(
      '--image',
      help='Path of the input image. Without it, a sample image is downloaded.',
      default=None)
  parser.add_argument(
      '--score_threshold',
      help='Detections with a lower score are dropped.',
      type=float,
      default=0.5)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  parser.add_argument(
      '--output',
      help='Where to write the annotated image.',
      default='object_detector_result.png')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_file = args.image
  if image_file is None:
    image_file = 'image.jpg'
    download(IMAGE_URL, image_file)

  return run(args.model, image_file, args.score_threshold,
             DELEGATES[args.delegate], args.output)


if __name__ == '__main__':
  main()
