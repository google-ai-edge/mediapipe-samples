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
"""Synthesizes speech to a .wav file with the MediaPipe TextToSpeech task."""

import argparse
import os
import tempfile
import time
import urllib.request
import wave

import numpy as np

from mediapipe.tasks.python.audio import text_to_speech
from mediapipe.tasks.python.core import base_options

BaseOptions = base_options.BaseOptions
Delegate = BaseOptions.Delegate

_MODEL_URL = (
    'https://huggingface.co/litert-community/Kokoro-82M/resolve/main/'
    'kokoro_int8_gpu_fp16.litertlm'
)
_DEFAULT_TEXT = 'Hello from MediaPipe Text to Speech, running entirely on device!'


def download_if_missing(url: str, path: str) -> str:
  """Downloads `url` to `path` unless the file already exists."""
  if not os.path.exists(path):
    print(f'Downloading {url}...')
    urllib.request.urlretrieve(url, path)
  return path


def save_wav(path: str, samples: np.ndarray, sample_rate: int) -> None:
  """Saves float32 samples in [-1.0, 1.0] as a 16-bit mono PCM .wav file."""
  pcm = (np.clip(samples, -1.0, 1.0) * 32767).astype(np.int16)
  with wave.open(path, 'wb') as wav_file:
    wav_file.setnchannels(1)
    wav_file.setsampwidth(2)  # 16 bits per sample.
    wav_file.setframerate(sample_rate)
    wav_file.writeframes(pcm.tobytes())


def create_tts(
    model_path: str, delegate: str, speaking_rate: float
) -> tuple[text_to_speech.TextToSpeech, str]:
  """Creates a TextToSpeech task, falling back to CPU if GPU is unavailable."""

  def create(delegate_name: str) -> text_to_speech.TextToSpeech:
    options = text_to_speech.TextToSpeechOptions(
        base_options=BaseOptions(
            model_asset_path=model_path, delegate=Delegate[delegate_name]
        ),
        default_voice_id='af_heart',
        default_locale='en-US',
        speaking_rate=speaking_rate,
        # Compiled kernels and weights are cached here for faster startup.
        cache_directory=os.path.join(
            tempfile.gettempdir(), 'mediapipe_tts_cache'
        ),
    )
    return text_to_speech.TextToSpeech.create_from_options(options)

  try:
    return create(delegate), delegate
  except RuntimeError as e:
    if delegate == 'CPU':
      raise
    print(f'GPU is not available ({e}), falling back to CPU.')
    return create('CPU'), 'CPU'


def main() -> None:
  parser = argparse.ArgumentParser(description=__doc__)
  parser.add_argument('text', nargs='?', default=_DEFAULT_TEXT)
  parser.add_argument(
      '--model',
      default=os.path.basename(_MODEL_URL),
      help='Path to the .litertlm model. Downloaded if missing.',
  )
  parser.add_argument(
      '--voice', default='af_heart', help='Voice ID, see --list_voices.'
  )
  parser.add_argument(
      '--locale',
      help="Locale of the text. Defaults to the selected voice's locale.",
  )
  parser.add_argument(
      '--speaking_rate', type=float, default=1.0, help='1.0 is normal speed.'
  )
  parser.add_argument(
      '--delegate', choices=['GPU', 'CPU'], type=str.upper, default='GPU'
  )
  parser.add_argument(
      '--stream',
      action='store_true',
      help='Synthesize clause by clause and report the time to first audio.',
  )
  parser.add_argument('--output', default='tts_output.wav')
  parser.add_argument(
      '--list_voices', action='store_true', help='List voices and exit.'
  )
  args = parser.parse_args()

  if args.model == os.path.basename(_MODEL_URL):
    download_if_missing(_MODEL_URL, args.model)

  tts, delegate = create_tts(args.model, args.delegate, args.speaking_rate)
  with tts:
    print(f'Delegate: {delegate}')
    voices = {voice.voice_id: voice for voice in tts.list_voices()}
    if args.list_voices:
      for voice in voices.values():
        print(f'{voice.voice_id:15s} {voice.locale:6s} {voice.display_name}')
      return
    if args.voice not in voices:
      parser.error(f'Unknown voice {args.voice!r}, see --list_voices.')
    locale = args.locale or voices[args.voice].locale

    start = time.perf_counter()
    if args.stream:
      chunks = []
      for chunk in tts.synthesize_stream(
          args.text, voice_id=args.voice, locale=locale
      ):
        elapsed = time.perf_counter() - start
        print(
            f'[{elapsed * 1000:5.0f} ms] chunk {chunk.chunk_index}:'
            f' {chunk.duration_seconds:.2f} s of audio'
        )
        chunks.append(chunk)
      samples = np.concatenate([chunk.samples for chunk in chunks])
      sample_rate = chunks[0].sample_rate_hz
      word_timestamps = [w for chunk in chunks for w in chunk.word_timestamps]
    else:
      result = tts.synthesize(args.text, voice_id=args.voice, locale=locale)
      samples, sample_rate = result.samples, result.sample_rate_hz
      word_timestamps = result.word_timestamps
    latency = time.perf_counter() - start

  duration = len(samples) / sample_rate
  print()
  for word in word_timestamps:
    print(
        f'{word.start_sec:6.2f}s - {word.end_sec:6.2f}s '
        f' {args.text[word.start_char:word.end_char]}'
    )
  save_wav(args.output, samples, sample_rate)
  print(
      f'\nSaved {duration:.2f} s of audio to {args.output}.'
      f' Latency: {latency * 1000:.0f} ms (RTF: {latency / duration:.3f})'
  )


if __name__ == '__main__':
  main()
