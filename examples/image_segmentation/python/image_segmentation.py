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
"""Segments an image with the MediaPipe Image Segmenter task.

This is the plain Python version of image_segmentation.ipynb.

  pip install mediapipe huggingface_hub
  python image_segmentation.py

The model and the sample image are downloaded on first use. Two images are
written: the segmentation mask (--output) and the input image with a blurred
background (--blur_output).
"""

import argparse
import os
import urllib.request
from typing import List, Optional, Sequence, Tuple

import cv2
from huggingface_hub import hf_hub_download
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.vision import image_segmenter

MODEL_REPO = 'litert-community/MediaPipe-ImageSegmenter-DeepLab-V3'
MODEL_FILENAME = 'deeplab_v3.tflite'
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-assets/segmentation_input_rotation0.jpg'

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}
RUNNING_MODES = {
    'image': vision.RunningMode.IMAGE,
    'video': vision.RunningMode.VIDEO,
}

BG_COLOR = (192, 192, 192)  # gray
MASK_COLOR = (255, 255, 255)  # white


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def run(model: str,
        image_file: str,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None,
        blur_output: Optional[str] = None,
        confidence_category: Optional[str] = None,
        running_mode: vision.RunningMode = vision.RunningMode.IMAGE
        ) -> Tuple[image_segmenter.ImageSegmenterResult, List[str]]:
  """Segments an image into the categories of the model.

  Args:
    model: Path of the image segmentation model.
    image_file: Path of the image to segment.
    delegate: The hardware to run the model on.
    output: Where to write the segmentation mask.
    blur_output: Where to write the image with a blurred background.
    confidence_category: If set, also request confidence masks and write the
      confidence mask of this category name.
    running_mode: IMAGE for a single image, or VIDEO to feed video frames.

  Returns:
    The segmentation result, and the category names of the model: the value of
    a pixel of the category mask is an index into that list.
  """
  # Create the options that will be used for ImageSegmenter
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.ImageSegmenterOptions(
      base_options=base_options,
      running_mode=running_mode,
      output_category_mask=True,
      output_confidence_masks=confidence_category is not None)

  # Create the image segmenter
  with vision.ImageSegmenter.create_from_options(options) as segmenter:
    labels = list(segmenter.labels)
    if confidence_category is not None and confidence_category not in labels:
      raise ValueError(f'Unknown category {confidence_category!r}; model has '
                       f'{labels}')

    # Create the MediaPipe image file that will be segmented
    image = mp.Image.create_from_file(image_file)

    # Retrieve the masks for the segmented image
    if running_mode == vision.RunningMode.VIDEO:
      for timestamp_ms in (0, 33, 66):
        segmentation_result = segmenter.segment_for_video(image, timestamp_ms)
    else:
      segmentation_result = segmenter.segment(image)
    category_mask = segmentation_result.category_mask

  # Print the share of the image that each category covers.
  mask = category_mask.numpy_view()
  mask = mask.squeeze(-1) if mask.ndim == 3 else mask
  # mp.Image.create_from_file() returns RGBA data; keep the three color
  # channels.
  rgb_image = image.numpy_view()[:, :, :3]
  # Single-class selfie models put the person in category 0 and the background
  # in 255; multi-class models put the background in category 0.
  foreground = (mask == 0) if len(labels) == 1 else (mask > 0)
  print(f'Categories found in {image_file}:')
  for index in np.unique(mask):
    name = labels[index] if index < len(labels) else 'background'
    print(f'  {name}: {np.mean(mask == index):.1%}')

  if confidence_category is not None:
    category_index = labels.index(confidence_category)
    confidence_view = segmentation_result.confidence_masks[
        category_index].numpy_view()
    confidence = (
        confidence_view.squeeze(-1)
        if confidence_view.ndim == 3 else confidence_view)
    foreground = confidence > 0.5
    if output:
      visualized = (np.clip(confidence, 0.0, 1.0) * 255).astype(np.uint8)
      cv2.imwrite(output, np.stack([visualized] * 3, axis=-1))
      print(f'Wrote {output}')
  elif output:
    # Generate solid color images for showing the output segmentation mask.
    image_data = rgb_image
    fg_image = np.zeros(image_data.shape, dtype=np.uint8)
    fg_image[:] = MASK_COLOR
    bg_image = np.zeros(image_data.shape, dtype=np.uint8)
    bg_image[:] = BG_COLOR

    output_image = np.where(foreground[..., None], fg_image, bg_image)
    cv2.imwrite(output, output_image)
    print(f'Wrote {output}')

  if blur_output:
    # Blur the image background based on the segmentation mask.
    # MediaPipe images are RGB, OpenCV writes BGR.
    image_data = cv2.cvtColor(rgb_image, cv2.COLOR_RGB2BGR)

    # Apply effects
    blurred_image = cv2.GaussianBlur(image_data, (55, 55), 0)

    output_image = np.where(foreground[..., None], image_data, blurred_image)
    cv2.imwrite(blur_output, output_image)
    print(f'Wrote {blur_output}')

  return segmentation_result, labels


def main(argv: Optional[Sequence[str]] = None
         ) -> Tuple[image_segmenter.ImageSegmenterResult, List[str]]:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the image segmentation model. Downloaded from Hugging Face '
      'if omitted.',
      default=None)
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
      '--confidence_category',
      help='Category name whose confidence mask to write instead of the '
      'category mask.',
      default=None)
  parser.add_argument(
      '--running_mode',
      help='Task running mode.',
      choices=sorted(RUNNING_MODES),
      default='image')
  parser.add_argument(
      '--output',
      help='Where to write the segmentation mask.',
      default='image_segmentation_mask.png')
  parser.add_argument(
      '--blur_output',
      help='Where to write the image with a blurred background.',
      default='image_segmentation_blurred_background.png')
  args = parser.parse_args(argv)

  model_path = args.model or hf_hub_download(
      repo_id=MODEL_REPO, filename=MODEL_FILENAME)
  image_file = args.image
  if image_file is None:
    image_file = 'segmentation_input_rotation0.jpg'
    download(IMAGE_URL, image_file)

  return run(model_path, image_file, DELEGATES[args.delegate], args.output,
             args.blur_output, args.confidence_category,
             RUNNING_MODES[args.running_mode])


if __name__ == '__main__':
  main()
