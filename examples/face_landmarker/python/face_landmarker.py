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
"""Detects face landmarks in an image with the MediaPipe Face Landmarker task.

This is the plain Python version of
[MediaPipe_Python_Tasks]_Face_Landmarker.ipynb.

  pip install mediapipe huggingface_hub
  python face_landmarker.py

The model and the sample image are downloaded on first use. The image with the
face mesh drawn on it is written to --output, and a bar graph of the face
blendshapes to --blendshapes_output.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

import cv2
import matplotlib

matplotlib.use('Agg')  # Render to files; no display is needed.
import matplotlib.pyplot as plt
from huggingface_hub import hf_hub_download
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.vision import drawing_styles
from mediapipe.tasks.python.vision import drawing_utils

MODEL_REPO = 'litert-community/MediaPipe-FaceLandmarker'
MODEL_FILENAME = 'face_landmarker.task'
# The image is from Unsplash: https://unsplash.com/photos/mt2fyrdXxzk
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-assets/business-person.png'

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


def draw_landmarks_on_image(rgb_image, detection_result):
  face_landmarks_list = detection_result.face_landmarks
  annotated_image = np.copy(rgb_image)

  # Loop through the detected faces to visualize.
  for idx in range(len(face_landmarks_list)):
    face_landmarks = face_landmarks_list[idx]

    # Draw the face landmarks.
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=face_landmarks,
        connections=vision.FaceLandmarksConnections.FACE_LANDMARKS_TESSELATION,
        landmark_drawing_spec=None,
        connection_drawing_spec=drawing_styles
        .get_default_face_mesh_tesselation_style())
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=face_landmarks,
        connections=vision.FaceLandmarksConnections.FACE_LANDMARKS_CONTOURS,
        landmark_drawing_spec=None,
        connection_drawing_spec=drawing_styles
        .get_default_face_mesh_contours_style())
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=face_landmarks,
        connections=vision.FaceLandmarksConnections.FACE_LANDMARKS_LEFT_IRIS,
        landmark_drawing_spec=None,
        connection_drawing_spec=drawing_styles
        .get_default_face_mesh_iris_connections_style())
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=face_landmarks,
        connections=vision.FaceLandmarksConnections.FACE_LANDMARKS_RIGHT_IRIS,
        landmark_drawing_spec=None,
        connection_drawing_spec=drawing_styles
        .get_default_face_mesh_iris_connections_style())

  return annotated_image


def plot_face_blendshapes_bar_graph(face_blendshapes, output: str) -> None:
  """Writes a bar graph of the blendshape scores of one face to `output`."""
  # Extract the face blendshapes category names and scores.
  face_blendshapes_names = [
      face_blendshapes_category.category_name
      for face_blendshapes_category in face_blendshapes
  ]
  face_blendshapes_scores = [
      face_blendshapes_category.score
      for face_blendshapes_category in face_blendshapes
  ]
  # The blendshapes are ordered in decreasing score value.
  face_blendshapes_ranks = range(len(face_blendshapes_names))

  fig, ax = plt.subplots(figsize=(12, 12))
  bar = ax.barh(face_blendshapes_ranks, face_blendshapes_scores,
                label=[str(x) for x in face_blendshapes_ranks])
  ax.set_yticks(face_blendshapes_ranks, face_blendshapes_names)
  ax.invert_yaxis()

  # Label each bar with values
  for score, patch in zip(face_blendshapes_scores, bar.patches):
    plt.text(patch.get_x() + patch.get_width(), patch.get_y(), f'{score:.4f}',
             va='top')

  ax.set_xlabel('Score')
  ax.set_title('Face Blendshapes')
  plt.tight_layout()
  fig.savefig(output)
  plt.close(fig)


def run(model: str,
        image_file: str,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU,
        output: Optional[str] = None,
        blendshapes_output: Optional[str] = None,
        running_mode: vision.RunningMode = vision.RunningMode.IMAGE
        ) -> vision.FaceLandmarkerResult:
  """Detects the face landmarks in an image.

  Args:
    model: Path of the face landmarker model bundle.
    image_file: Path of the image to run face landmark detection on.
    delegate: The hardware to run the model on.
    output: Where to write the image with the face mesh drawn on it.
    blendshapes_output: Where to write the bar graph of the face blendshapes.
    running_mode: IMAGE for a single image, or VIDEO to feed video frames.

  Returns:
    The detection result.
  """
  # STEP 2: Create an FaceLandmarker object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.FaceLandmarkerOptions(
      base_options=base_options,
      running_mode=running_mode,
      output_face_blendshapes=True,
      output_facial_transformation_matrixes=True,
      num_faces=1)
  detector = vision.FaceLandmarker.create_from_options(options)

  # STEP 3: Load the input image.
  image = mp.Image.create_from_file(image_file)

  # STEP 4: Detect face landmarks from the input image.
  if running_mode == vision.RunningMode.VIDEO:
    for timestamp_ms in (0, 33, 66):
      detection_result = detector.detect_for_video(image, timestamp_ms)
  else:
    detection_result = detector.detect(image)
  detector.close()

  # STEP 5: Process the detection result. In this case, visualize it.
  print(f'Detected {len(detection_result.face_landmarks)} face(s) in '
        f'{image_file}')
  if output:
    annotated_image = draw_landmarks_on_image(image.numpy_view(),
                                              detection_result)
    cv2.imwrite(output, cv2.cvtColor(annotated_image, cv2.COLOR_RGB2BGR))
    print(f'Wrote {output}')

  # Visualize the face blendshapes categories using a bar graph.
  if blendshapes_output and detection_result.face_blendshapes:
    plot_face_blendshapes_bar_graph(detection_result.face_blendshapes[0],
                                    blendshapes_output)
    print(f'Wrote {blendshapes_output}')

  # And print the transformation matrix.
  print(detection_result.facial_transformation_matrixes)

  return detection_result


def main(argv: Optional[Sequence[str]] = None) -> vision.FaceLandmarkerResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the face landmarker model. Downloaded from Hugging Face if '
      'omitted.',
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
      '--running_mode',
      help='Task running mode.',
      choices=sorted(RUNNING_MODES),
      default='image')
  parser.add_argument(
      '--output',
      help='Where to write the annotated image.',
      default='face_landmarker_result.png')
  parser.add_argument(
      '--blendshapes_output',
      help='Where to write the bar graph of the face blendshapes.',
      default='face_blendshapes.png')
  args = parser.parse_args(argv)

  model_path = args.model or hf_hub_download(
      repo_id=MODEL_REPO, filename=MODEL_FILENAME)
  image_file = args.image
  if image_file is None:
    image_file = 'image.png'
    download(IMAGE_URL, image_file)

  return run(model_path, image_file, DELEGATES[args.delegate], args.output,
             args.blendshapes_output, RUNNING_MODES[args.running_mode])


if __name__ == '__main__':
  main()
