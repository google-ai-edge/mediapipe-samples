# Copyright 2023 The MediaPipe Authors. All Rights Reserved.
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
"""Main scripts to run image classification."""

import argparse
import sys
import time

import cv2
import mediapipe as mp

from mediapipe.tasks import python
from mediapipe.tasks.python import vision

# Global variables to calculate FPS
COUNTER, FPS = 0, 0
START_TIME = time.time()


def run(model: str, max_results: int, score_threshold: float, camera_id: int,
        width: int, height: int) -> None:
  """Continuously run inference on images acquired from the camera.

  Args:
      model: Name of the TFLite image classification model.
      max_results: Max of classification results.
      score_threshold: The score threshold of classification results.
      camera_id: The camera id to be passed to OpenCV.
      width: The width of the frame captured from the camera.
      height: The height of the frame captured from the camera.
  """

  # Start capturing video input from the camera
  cap = cv2.VideoCapture(camera_id)
  cap.set(cv2.CAP_PROP_FRAME_WIDTH, width)
  cap.set(cv2.CAP_PROP_FRAME_HEIGHT, height)

  # Visualization parameters
  row_size = 50  # pixels
  left_margin = 24  # pixels
  text_color = (0, 0, 0)  # black
  font_size = 1
  font_thickness = 1
  fps_avg_frame_count = 10

  # Label box parameters
  label_text_color = (0, 0, 0)  # red
  label_background_color = (255, 255, 255)  # white
  label_font_size = 1
  label_thickness = 2
  label_width = 50  # pixels
  label_rect_size = 16  # pixels
  label_margin = 40
  label_padding_width = 600  # pixels

  classification_frame = None
  classification_result_list = []

  def save_result(result: vision.ImageClassifierResult, unused_output_image: mp.Image, timestamp_ms: int):
      global FPS, COUNTER, START_TIME

      # Calculate the FPS
      if COUNTER % fps_avg_frame_count == 0:
          FPS = fps_avg_frame_count / (time.time() - START_TIME)
          START_TIME = time.time()

      classification_result_list.append(result)
      COUNTER += 1

  # Initialize the image classification model
  base_options = python.BaseOptions(model_asset_path=model)
  options = vision.ImageClassifierOptions(base_options=base_options,
                                          running_mode=vision.RunningMode.LIVE_STREAM,
                                          max_results=max_results,
                                          score_threshold=score_threshold,
                                          result_callback=save_result)
  classifier = vision.ImageClassifier.create_from_options(options)

  # Continuously capture images from the camera and run inference
  while cap.isOpened():
    success, image = cap.read()
    if not success:
      sys.exit(
          'ERROR: Unable to read from webcam. Please verify your webcam settings.'
      )

    image = cv2.flip(image, 1)

    # Convert the image from BGR to RGB as required by the TFLite model.
    rgb_image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_image)

    # Run image classifier using the model.
    classifier.classify_async(mp_image, time.time_ns() // 1_000_000)

    # Show the FPS
    fps_text = 'FPS = {:.1f}'.format(FPS)
    text_location = (left_margin, row_size)
    current_frame = image
    cv2.putText(current_frame, fps_text, text_location, cv2.FONT_HERSHEY_DUPLEX,
                font_size, text_color, font_thickness, cv2.LINE_AA)

    # Initialize the origin coordinates of the label.
    legend_x = current_frame.shape[1] + label_margin
    legend_y = current_frame.shape[0] // label_width + label_margin

    # Expand the frame to show the labels.
    current_frame = cv2.copyMakeBorder(current_frame, 0, 0, 0, label_padding_width,
                                       cv2.BORDER_CONSTANT, None,
                                       label_background_color)

    # Show the labels on right-side frame.
    if classification_result_list:
      # Show classification results.
      for idx, category in enumerate(classification_result_list[0].classifications[0].categories):
        category_name = category.category_name
        score = round(category.score, 2)
        result_text = category_name + ' (' + str(score) + ')'

        label_location = legend_x + label_rect_size + label_margin, legend_y + label_margin
        cv2.putText(current_frame, result_text, label_location,
                    cv2.FONT_HERSHEY_DUPLEX, label_font_size, label_text_color,
                    label_thickness, cv2.LINE_AA)
        legend_y += (label_rect_size + label_margin)

      classification_frame = current_frame
      classification_result_list.clear()

    if classification_frame is not None:
        cv2.imshow('image_classification', classification_frame)

    # Stop the program if the ESC key is pressed.
    if cv2.waitKey(1) == 27:
        break

  classifier.close()
  cap.release()
  cv2.destroyAllWindows()


def main():
  parser = argparse.ArgumentParser(
      formatter_class=argparse.ArgumentDefaultsHelpFormatter)
  parser.add_argument(
      '--model',
      help='Name of image classification model.',
      required=False,
      default='classifier.tflite')
  parser.add_argument(
      '--maxResults',
      help='Max number of classification results.',
      required=False,
      default=5)
  parser.add_argument(
      '--scoreThreshold',
      help='The score threshold of classification results.',
      required=False,
      type=float,
      default=0.0)
  # Finding the camera ID can be very reliant on platform-dependent methods. 
  # One common approach is to use the fact that camera IDs are usually indexed sequentially by the OS, starting from 0. 
  # Here, we use OpenCV and create a VideoCapture object for each potential ID with 'cap = cv2.VideoCapture(i)'.
  # If 'cap' is None or not 'cap.isOpened()', it indicates the camera ID is not available.
  parser.add_argument(
      '--cameraId', help='Id of camera.', required=False, default=0)
  parser.add_argument(
      '--frameWidth',
      help='Width of frame to capture from camera.',
      required=False,
      default=640)
  parser.add_argument(
      '--frameHeight',
      help='Height of frame to capture from camera.',
      required=False,
      default=480)
  args = parser.parse_args()

  run(args.model, int(args.maxResults),
      args.scoreThreshold, int(args.cameraId), args.frameWidth, args.frameHeight)


if __name__ == '__main__':
  main()
