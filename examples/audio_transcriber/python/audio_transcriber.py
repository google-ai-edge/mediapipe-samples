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
"""Transcribes a .wav file with the MediaPipe AudioTranscriber task."""

import argparse
import os
import time
import urllib.request
import wave

import numpy as np

from mediapipe.tasks.python.audio import audio_transcriber
from mediapipe.tasks.python.core import base_options

BaseOptions = base_options.BaseOptions
Delegate = BaseOptions.Delegate

_MODEL_URL = (
    'https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/'
    'main/parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm'
)
_AUDIO_URL = (
    'https://storage.googleapis.com/mediapipe-assets/speech_16000_hz_mono.wav'
)


def download_if_missing(url: str, path: str) -> str:
  """Downloads `url` to `path` unless the file already exists."""
  if not os.path.exists(path):
    print(f'Downloading {url}...')
    urllib.request.urlretrieve(url, path)
  return path


def load_wav(path: str) -> tuple[np.ndarray, int]:
  """Loads a PCM .wav file as mono float32 samples in [-1.0, 1.0]."""
  with wave.open(path, 'rb') as wav_file:
    sample_rate = wav_file.getframerate()
    num_channels = wav_file.getnchannels()
    sample_width = wav_file.getsampwidth()
    frames = wav_file.readframes(wav_file.getnframes())

  if sample_width == 1:  # 8-bit PCM is unsigned.
    samples = (np.frombuffer(frames, np.uint8).astype(np.float32) - 128) / 128
  elif sample_width == 2:
    samples = np.frombuffer(frames, np.int16).astype(np.float32) / 32768
  elif sample_width == 4:
    samples = np.frombuffer(frames, np.int32).astype(np.float32) / 2**31
  else:
    raise ValueError(f'Unsupported sample width: {sample_width} bytes')

  # Average all channels to get mono audio.
  samples = samples.reshape(-1, num_channels).mean(axis=1)
  return samples, sample_rate


def create_transcriber(
    model_path: str, delegate: str, sample_rate: int
) -> tuple[audio_transcriber.AudioTranscriber, str]:
  """Creates an AudioTranscriber, falling back to CPU if GPU is unavailable."""

  def create(delegate_name: str) -> audio_transcriber.AudioTranscriber:
    options = audio_transcriber.AudioTranscriberOptions(
        base_options=BaseOptions(
            model_asset_path=model_path, delegate=Delegate[delegate_name]
        ),
        sample_rate_hz=sample_rate,
        enable_interim_results=True,
    )
    return audio_transcriber.AudioTranscriber.create_from_options(options)

  try:
    return create(delegate), delegate
  except RuntimeError as e:
    if delegate == 'CPU':
      raise
    print(f'GPU is not available ({e}), falling back to CPU.')
    return create('CPU'), 'CPU'


def main() -> None:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument(
      '--model',
      default=os.path.basename(_MODEL_URL),
      help='Path to the .litertlm model. Downloaded if missing.',
  )
  parser.add_argument(
      '--audio',
      help='Path to a PCM .wav file. Defaults to a downloaded sample clip.',
  )
  parser.add_argument(
      '--delegate', choices=['GPU', 'CPU'], type=str.upper, default='GPU'
  )
  parser.add_argument(
      '--stream',
      action='store_true',
      help='Feed the audio in chunks instead of transcribing it in one call.',
  )
  parser.add_argument(
      '--chunk_ms',
      type=int,
      default=1000,
      help='Chunk length in milliseconds for --stream.',
  )
  args = parser.parse_args()

  if args.model == os.path.basename(_MODEL_URL):
    download_if_missing(_MODEL_URL, args.model)
  audio_path = args.audio or download_if_missing(
      _AUDIO_URL, os.path.basename(_AUDIO_URL)
  )

  samples, sample_rate = load_wav(audio_path)
  duration = len(samples) / sample_rate
  print(f'Audio: {audio_path} ({duration:.2f} s at {sample_rate} Hz)')

  transcriber, delegate = create_transcriber(
      args.model, args.delegate, sample_rate
  )
  with transcriber:
    transcriber.prewarm()
    print(f'Delegate: {delegate}')

    start = time.perf_counter()
    if args.stream:
      chunk_size = max(1, sample_rate * args.chunk_ms // 1000)
      final_segments = []
      for chunk_start in range(0, len(samples), chunk_size):
        chunk = samples[chunk_start : chunk_start + chunk_size]
        result = transcriber.transcribe(chunk, end_of_stream=False)
        stream_time = (chunk_start + len(chunk)) / sample_rate
        for segment in result.segments:
          kind = 'final  ' if segment.is_final else 'interim'
          print(f'[{stream_time:5.1f}s audio] {kind}: {segment.transcript}')
        final_segments += [s for s in result.segments if s.is_final]
      final_segments += transcriber.finish_stream().segments
    else:
      final_segments = transcriber.transcribe(
          samples, end_of_stream=True
      ).segments
    latency = time.perf_counter() - start

  print()
  for segment in final_segments:
    print(
        f'[{segment.from_timestamp_ms / 1000:6.2f}s -'
        f' {segment.to_timestamp_ms / 1000:6.2f}s] {segment.transcript}'
    )
  print(
      f'\nLatency: {latency * 1000:.0f} ms (RTF: {latency / duration:.3f})'
  )


if __name__ == '__main__':
  main()
