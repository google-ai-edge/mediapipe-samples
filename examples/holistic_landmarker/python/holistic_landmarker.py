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
"""Detects pose, face and hand landmarks with the Holistic Landmarker task.

This is the plain Python version of
[MediaPipe_Python_Tasks]_Holistic_Landmarker.ipynb.

  pip install mediapipe
  python holistic_landmarker.py

The model and the sample image are downloaded on first use. The image with the
landmarks drawn on it is written to --output, a bar graph of the face
blendshapes to --blendshapes_output, and the segmentation mask of the person to
--mask_output.
"""

import argparse
import os
import urllib.request
from typing import Optional, Sequence

import cv2
import matplotlib

matplotlib.use('Agg')  # Render to files; no display is needed.
import matplotlib.pyplot as plt
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from mediapipe.tasks.python.vision import drawing_styles
from mediapipe.tasks.python.vision import drawing_utils

MODEL_URL = 'https://huggingface.co/litert-community/MediaPipe-HolisticLandmarker/resolve/main/holistic_landmarker.task'
# The image is from Unsplash: https://unsplash.com/photos/mt2fyrdXxzk
IMAGE_URL = 'https://storage.googleapis.com/mediapipe-assets/business-person.png'

DELEGATES = {
    'cpu': python.BaseOptions.Delegate.CPU,
    'gpu': python.BaseOptions.Delegate.GPU,
}

# The notebook scales the image to this height before it runs the task.
DESIRED_HEIGHT = 480


def download(url: str, path: str) -> None:
  """Downloads `url` to `path` unless the file is already there."""
  if os.path.exists(path):
    return
  print(f'Downloading {url}')
  urllib.request.urlretrieve(url, path + '.download')
  os.replace(path + '.download', path)


def load_resized_image(image_file: str) -> mp.Image:
  """Loads an image, scaled to DESIRED_HEIGHT with a width divisible by 16."""
  img = cv2.imread(image_file)
  h, w, _ = img.shape
  new_h = DESIRED_HEIGHT
  new_w = int(round(w * (DESIRED_HEIGHT / h) / 16.0)) * 16
  img = cv2.resize(img, (new_w, new_h))
  # OpenCV reads BGR. MediaPipe gets RGBA, which is also what
  # mp.Image.create_from_file() returns: the GPU delegate does not accept
  # three-channel images.
  rgba_image = np.ascontiguousarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGBA))
  return mp.Image(image_format=mp.ImageFormat.SRGBA, data=rgba_image)


def draw_landmarks_on_image(rgb_image, detection_result):
  pose_landmarks = detection_result.pose_landmarks
  face_landmarks = detection_result.face_landmarks
  left_hand_landmarks = detection_result.left_hand_landmarks
  right_hand_landmarks = detection_result.right_hand_landmarks
  annotated_image = np.copy(
      rgb_image[:, :, :3] if rgb_image.shape[2] == 4 else rgb_image)

  # Draw pose landmarks.
  if pose_landmarks:
    pose_landmark_style = drawing_styles.get_default_pose_landmarks_style()
    pose_connection_style = drawing_utils.DrawingSpec(color=(0, 255, 0),
                                                      thickness=2)
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=pose_landmarks,
        connections=vision.PoseLandmarksConnections.POSE_LANDMARKS,
        landmark_drawing_spec=pose_landmark_style,
        connection_drawing_spec=pose_connection_style)

  # Draw face landmarks.
  if face_landmarks:
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

  # Draw hand landmarks.
  if left_hand_landmarks:
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=left_hand_landmarks,
        connections=vision.HandLandmarksConnections.HAND_CONNECTIONS,
        landmark_drawing_spec=drawing_styles.get_default_hand_landmarks_style(),
        connection_drawing_spec=drawing_styles
        .get_default_hand_connections_style())

  if right_hand_landmarks:
    drawing_utils.draw_landmarks(
        image=annotated_image,
        landmark_list=right_hand_landmarks,
        connections=vision.HandLandmarksConnections.HAND_CONNECTIONS,
        landmark_drawing_spec=drawing_styles.get_default_hand_landmarks_style(),
        connection_drawing_spec=drawing_styles
        .get_default_hand_connections_style())

  return annotated_image


def plot_face_blendshapes_bar_graph(face_blendshapes, output: str) -> None:
  """Writes a bar graph of the blendshape scores of the face to `output`."""
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
        mask_output: Optional[str] = None) -> vision.HolisticLandmarkerResult:
  """Detects the pose, face and hand landmarks of the person in an image.

  Args:
    model: Path of the holistic landmarker model bundle.
    image_file: Path of the image to run holistic landmark detection on.
    delegate: The hardware to run the model on.
    output: Where to write the image with the landmarks drawn on it.
    blendshapes_output: Where to write the bar graph of the face blendshapes.
    mask_output: Where to write the segmentation mask of the person.

  Returns:
    The detection result.
  """
  # STEP 2: Create a HolisticLandmarker object.
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = vision.HolisticLandmarkerOptions(
      base_options=base_options,
      output_face_blendshapes=True,
      output_segmentation_mask=True)
  detector = vision.HolisticLandmarker.create_from_options(options)

  # STEP 3: Load the input image.
  image = load_resized_image(image_file)

  # STEP 4: Detect holistic landmarks from the input image.
  detection_result = detector.detect(image)
  detector.close()

  # STEP 5: Process the detection result. In this case, visualize it.
  print(f'Landmarks detected in {image_file}:')
  print(f'  pose: {len(detection_result.pose_landmarks or [])}')
  print(f'  face: {len(detection_result.face_landmarks or [])}')
  print(f'  left hand: {len(detection_result.left_hand_landmarks or [])}')
  print(f'  right hand: {len(detection_result.right_hand_landmarks or [])}')

  if output:
    annotated_image = draw_landmarks_on_image(image.numpy_view(),
                                              detection_result)
    cv2.imwrite(output, cv2.cvtColor(annotated_image, cv2.COLOR_RGB2BGR))
    print(f'Wrote {output}')

  # Visualize the face blendshapes categories using a bar graph.
  if blendshapes_output and detection_result.face_blendshapes:
    plot_face_blendshapes_bar_graph(detection_result.face_blendshapes,
                                    blendshapes_output)
    print(f'Wrote {blendshapes_output}')

  # Visualize the pose segmentation mask.
  if mask_output and detection_result.segmentation_mask is not None:
    segmentation_mask = detection_result.segmentation_mask.numpy_view()
    segmentation_mask = np.squeeze(segmentation_mask)

    # Convert to 3-channel uint8 image for visualization.
    visualized_mask = (segmentation_mask * 255).astype(np.uint8)
    visualized_mask = np.stack([visualized_mask] * 3, axis=-1)
    cv2.imwrite(mask_output, visualized_mask)
    print(f'Wrote {mask_output}')

  return detection_result


def main(argv: Optional[Sequence[str]] = None
         ) -> vision.HolisticLandmarkerResult:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the holistic landmarker model. Downloaded if it is '
      'missing.',
      default='holistic_landmarker.task')
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
      default='holistic_landmarker_result.png')
  parser.add_argument(
      '--blendshapes_output',
      help='Where to write the bar graph of the face blendshapes.',
      default='holistic_face_blendshapes.png')
  parser.add_argument(
      '--mask_output',
      help='Where to write the segmentation mask.',
      default='holistic_segmentation_mask.png')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  image_file = args.image
  if image_file is None:
    image_file = 'image.png'
    download(IMAGE_URL, image_file)

  return run(args.model, image_file, DELEGATES[args.delegate], args.output,
             args.blendshapes_output, args.mask_output)


if __name__ == '__main__':
  main()
