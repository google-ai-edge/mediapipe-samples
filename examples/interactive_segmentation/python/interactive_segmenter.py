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
"""Selects an object in an image with the Interactive Segmenter task.

This is the plain Python version of interactive_segmenter.ipynb.

  pip install mediapipe
  python interactive_segmenter.py --x 0.68 --y 0.68

The model and the sample image are downloaded on first use. The object at the
point (--x, --y) is selected, and the image with the selection drawn over it is
written to --output.
"""

import argparse
import math
import os
import urllib.request
from typing import Optional, Sequence

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.vision import interactive_segmenter

MODEL_URL = 'https://storage.googleapis.com/mediapipe-models/interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task'
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-assets/cats_and_dogs.jpg'

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}

CONFIDENCE_THRESHOLD = 0.5

Stroke = interactive_segmenter.Stroke
StrokePoint = interactive_segmenter.StrokePoint
BrushMode = interactive_segmenter.BrushMode


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def _normalized_to_pixel_coordinates(
    normalized_x: float, normalized_y: float, image_width: int,
    image_height: int):
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


def run(model: str,
        image_file: str,
        x: float = 0.68,
        y: float = 0.68,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None) -> mp.Image:
  """Selects the object at a point of an image.

  Args:
    model: Path of the interactive segmentation model.
    image_file: Path of the image to segment.
    x: Horizontal position of the point of interest: a fraction of the image
      width (0 to 1), or a pixel position if larger than 1.
    y: Vertical position of the point of interest, like `x`.
    delegate: The hardware to run the model on.
    output: Where to write the image with the selection drawn over it.

  Returns:
    The confidence mask of the selected object.
  """
  # Create the options that will be used for InteractiveSegmenter
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.InteractiveSegmenterOptions(base_options=base_options)

  # Create the interactive segmenter
  with vision.InteractiveSegmenter.create_from_options(options) as segmenter:
    # Create the MediaPipe image file that will be segmented
    image = mp.Image.create_from_file(image_file)

    # Detect if coordinates are absolute pixels and normalize them for the
    # Stroke API
    norm_x = x / image.width if x > 1.0 else x
    norm_y = y / image.height if y > 1.0 else y

    # Set the image (calculates and caches the image embeddings)
    segmenter.set_image(image)

    # Define the stroke (using normalized coordinates)
    strokes = [
        Stroke(
            brush_mode=BrushMode.POSITIVE,
            points=[StrokePoint(x=norm_x, y=norm_y)],
            is_completed=True,
        )
    ]

    # Retrieve the masks for the segmented image using the strokes
    category_mask = segmenter.segment(strokes)

  mask_array = category_mask.numpy_view()

  # Apply tuned threshold
  condition = mask_array.squeeze(-1) > CONFIDENCE_THRESHOLD
  matching_pixels = np.sum(condition)
  print(f'Selected {matching_pixels} of {condition.size} pixels '
        f'({matching_pixels / condition.size:.1%}) of {image_file}')

  if output:
    # Generate dynamic transparent overlay on top of the original image
    image_data = image.numpy_view()

    # Convert original image from RGB (MediaPipe) to BGR (OpenCV) for correct
    # color output
    output_image = cv2.cvtColor(image_data, cv2.COLOR_RGB2BGR)

    # Create a solid green canvas for the mask
    colored_overlay = np.zeros_like(output_image)
    colored_overlay[condition] = [0, 255, 0]  # Green in BGR

    # Blend original image and the green overlay (70% original, 30% green mask)
    output_image = cv2.addWeighted(output_image, 0.7, colored_overlay, 0.3, 0)

    # Draw a white dot with black border to denote the point of interest
    thickness, radius = 6, -1
    keypoint_px = _normalized_to_pixel_coordinates(norm_x, norm_y, image.width,
                                                   image.height)

    if keypoint_px is not None:
      cv2.circle(output_image, keypoint_px, thickness + 5, (0, 0, 0), radius)
      cv2.circle(output_image, keypoint_px, thickness, (255, 255, 255), radius)

    cv2.imwrite(output, output_image)
    print(f'Wrote {output}')

  return category_mask


def main(argv: Optional[Sequence[str]] = None) -> mp.Image:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the interactive segmentation model. Downloaded if it is '
      'missing.',
      default='model.tflite')
  parser.add_argument(
      '--image',
      help='Path of the input image. Without it, a sample image is downloaded.',
      default=None)
  parser.add_argument(
      '--x',
      help='Horizontal position of the point of interest, as a fraction of '
      'the image width or in pixels.',
      type=float,
      default=0.68)
  parser.add_argument(
      '--y',
      help='Vertical position of the point of interest, as a fraction of the '
      'image height or in pixels.',
      type=float,
      default=0.68)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  parser.add_argument(
      '--output',
      help='Where to write the image with the selection.',
      default='interactive_segmenter_result.png')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_file = args.image
  if image_file is None:
    image_file = 'cats_and_dogs.jpg'
    download(IMAGE_URL, image_file)

  return run(args.model, image_file, args.x, args.y, DELEGATES[args.delegate],
             args.output)


if __name__ == '__main__':
  main()
