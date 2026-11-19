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
"""Classifies the sounds in an audio file with the Audio Classifier task.

This is the plain Python version of audio_classification.ipynb.

  pip install mediapipe scipy
  python audio_classification.py

The model and the sample audio clip are downloaded on first use. The clip is
classified in chunks of 0.975 seconds; the top category of each chunk is
printed.
"""

import argparse
import os
import urllib.request
from typing import List, Optional, Sequence

import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import audio
from mediapipe.tasks.python.components import containers
from scipy.io import wavfile

MODEL_URL = 'https://storage.googleapis.com/mediapipe-models/audio_classifier/yamnet/float32/1/yamnet.tflite'
AUDIO_URL = 'https://storage.googleapis.com/mediapipe-assets/speech_16000_hz_mono.wav'

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


def run(model: str,
        audio_file: str,
        max_results: int = 4,
        delegate: python.BaseOptions.Delegate = python.BaseOptions.Delegate.CPU
        ) -> List[audio.AudioClassifierResult]:
  """Classifies the sounds in a 16-bit PCM WAV file.

  Args:
    model: Path of the audio classification model.
    audio_file: Path of the WAV file to classify.
    max_results: The maximum number of categories to return per chunk.
    delegate: The hardware to run the model on.

  Returns:
    The classification result of each chunk of the clip, in order. The last
    chunk may be shorter than the others.
  """
  # Customize and associate model for Classifier
  base_options = python.BaseOptions(model_asset_path=model, delegate=delegate)
  options = audio.AudioClassifierOptions(
      base_options=base_options, max_results=max_results)

  # Create classifier, segment audio clips, and classify
  with audio.AudioClassifier.create_from_options(options) as classifier:
    sample_rate, wav_data = wavfile.read(audio_file)
    audio_clip = containers.AudioData.create_from_array(
        wav_data.astype(float) / np.iinfo(np.int16).max, sample_rate)
    classification_result_list = classifier.classify(audio_clip)

  # Iterate through clips to display classifications
  for classification_result in classification_result_list:
    top_category = classification_result.classifications[0].categories[0]
    print(f'Timestamp {classification_result.timestamp_ms}: '
          f'{top_category.category_name} ({top_category.score:.2f})')

  return classification_result_list


def main(argv: Optional[Sequence[str]] = None
         ) -> List[audio.AudioClassifierResult]:
  parser = argparse.ArgumentParser(
      description=__doc__,
      formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument(
      '--model',
      help='Path of the audio classification model. Downloaded if it is '
      'missing.',
      default='classifier.tflite')
  parser.add_argument(
      '--audio',
      help='Path of the WAV file to classify. Without it, a sample clip is '
      'downloaded.',
      default=None)
  parser.add_argument(
      '--max_results',
      help='The maximum number of categories to return per chunk.',
      type=int,
      default=4)
  parser.add_argument(
      '--delegate',
      help='Hardware to run the model on.',
      choices=sorted(DELEGATES),
      default='cpu')
  args = parser.parse_args(argv)

  download(MODEL_URL, args.model)
  audio_file = args.audio
  if audio_file is None:
    audio_file = 'speech_16000_hz_mono.wav'
    download(AUDIO_URL, audio_file)

  return run(args.model, audio_file, args.max_results, DELEGATES[args.delegate])


if __name__ == '__main__':
  main()
