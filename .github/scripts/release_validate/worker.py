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
"""Runs the sample of one validation case and records what it returned.

run_local.py starts this script once per case, with the Python of the
validation environment (the one that has the MediaPipe under test), so that
every case gets a fresh process and a crash cannot take other cases down.

  worker.py --job job.json --out result.json

The sample is called through its command line entry point, `main(argv)`, which
returns what its `run()` returned. An observer turns that MediaPipe result (and
any output file the sample wrote to disk) into plain JSON; verify.py then checks
it. Nothing is judged here.
"""

import argparse
import faulthandler
import gc
import importlib.util
import json
import os
import platform
import sys
import time
import traceback
import wave

import cv2
import numpy as np

MASK_THRESHOLD = 0.5


def _arg(invocation, flag, default=None):
  """The value that follows `flag` in the arguments of an invocation."""
  args = invocation['args']
  if flag not in args:
    return default
  return args[args.index(flag) + 1]


def _read_bgr(path):
  image = cv2.imread(path)
  if image is None:
    raise ValueError(f'cannot read {path}')
  return image


def _image_size(path):
  image = _read_bgr(path)
  height, width = image.shape[:2]
  return width, height


def _observe_annotated_file(output_path, source_bgr):
  """Describes an image file on which boxes or landmarks were drawn."""
  if not output_path or not os.path.isfile(output_path):
    return {'exists': False}
  written = cv2.imread(output_path, cv2.IMREAD_UNCHANGED)
  if written is None:
    return {'exists': False}
  channels = 1 if written.ndim == 2 else int(written.shape[2])
  changed = (round(float(np.any(written != source_bgr, axis=-1).mean()), 6)
             if written.shape == source_bgr.shape else None)
  return {
      'exists': True,
      'width': int(written.shape[1]),
      'height': int(written.shape[0]),
      'channels': channels,
      'expectedWidth': int(source_bgr.shape[1]),
      'expectedHeight': int(source_bgr.shape[0]),
      'changedFraction': changed,
  }


def _observe_figure_file(output_path):
  """Describes a rendered figure (bar chart or grid of images) on disk."""
  if not output_path or not os.path.isfile(output_path):
    return {'exists': False}
  written = cv2.imread(output_path, cv2.IMREAD_UNCHANGED)
  if written is None:
    return {'exists': False}
  channels = 1 if written.ndim == 2 else int(written.shape[2])
  rgb = written[..., :3] if written.ndim == 3 else written[..., None]
  ink = round(float(np.any(rgb < 250, axis=-1).mean()), 6)
  return {
      'exists': True,
      'width': int(written.shape[1]),
      'height': int(written.shape[0]),
      'channels': channels,
      'inkFraction': ink,
  }


def _observe_grayscale_mask_file(output_path, mask_plane):
  """Describes a 3-channel grayscale PNG written from a [0, 1] float mask."""
  if not output_path or not os.path.isfile(output_path):
    return {'exists': False}
  written = cv2.imread(output_path, cv2.IMREAD_UNCHANGED)
  if written is None:
    return {'exists': False}
  channels = 1 if written.ndim == 2 else int(written.shape[2])
  grayscale = bool(channels == 3 and np.array_equal(written[..., 0], written[..., 1])
                   and np.array_equal(written[..., 0], written[..., 2]))
  plane = written if written.ndim == 2 else written[..., 0]
  expected_u8 = (np.clip(mask_plane, 0.0, 1.0) * 255).astype(np.uint8)
  max_diff = (round(float(np.abs(plane.astype(np.float32) - expected_u8.astype(np.float32)).max() / 255.0), 6)
              if plane.shape == expected_u8.shape else None)
  return {
      'exists': True,
      'width': int(written.shape[1]),
      'height': int(written.shape[0]),
      'channels': channels,
      'grayscale': grayscale,
      'maxDiff': max_diff,
  }


def _categories(categories):
  return [{'label': c.category_name, 'score': round(float(c.score), 6)} for c in categories]


def _points(landmarks):
  return [[round(float(p.x), 5), round(float(p.y), 5)] for p in landmarks or []]


def _box(bounding_box):
  return {'x': int(bounding_box.origin_x), 'y': int(bounding_box.origin_y),
          'width': int(bounding_box.width), 'height': int(bounding_box.height)}


def _region(selected):
  """Share, centre and bounds of the selected pixels, as fractions of the mask."""
  height, width = selected.shape
  ys, xs = np.nonzero(selected)
  if len(xs) == 0:
    return {'fraction': 0.0, 'centroid': None, 'bounds': None}
  return {
      'fraction': round(float(len(xs)) / selected.size, 6),
      'centroid': {'x': round(float(xs.mean() + 0.5) / width, 5), 'y': round(float(ys.mean() + 0.5) / height, 5)},
      'bounds': {'minX': round(float(xs.min()) / width, 5), 'maxX': round(float(xs.max() + 1) / width, 5),
                 'minY': round(float(ys.min()) / height, 5), 'maxY': round(float(ys.max() + 1) / height, 5)},
  }


def _confidence_mask(mask_image):
  """Describes a confidence mask: its format, and what it covers above 0.5."""
  view = np.array(mask_image.numpy_view(), copy=True)
  channels = 1 if view.ndim == 2 else int(view.shape[2])
  finite = np.isfinite(view)
  clean = np.where(finite, view, 0.0)
  stats = {'width': int(view.shape[1]), 'height': int(view.shape[0]), 'channels': channels, 'dtype': str(view.dtype),
           'min': round(float(clean.min()), 6), 'max': round(float(clean.max()), 6),
           'mean': round(float(clean.mean()), 6), 'nonFinite': int((~finite).sum())}
  plane = clean if clean.ndim == 2 else clean[..., 0]
  stats.update(_region(plane > MASK_THRESHOLD))
  return stats, plane


def observe_detections(result, invocation):
  source = _read_bgr(_arg(invocation, '--image'))
  height, width = source.shape[:2]
  return {
      'imageWidth': width,
      'imageHeight': height,
      'detections': [{'label': d.categories[0].category_name, 'score': round(float(d.categories[0].score), 6),
                      'box': _box(d.bounding_box)} for d in result.detections],
      'annotatedFile': _observe_annotated_file(_arg(invocation, '--output'), source),
  }


def observe_faces(result, invocation):
  source = _read_bgr(_arg(invocation, '--image'))
  height, width = source.shape[:2]
  return {
      'imageWidth': width,
      'imageHeight': height,
      'detections': [{'score': round(float(d.categories[0].score), 6), 'box': _box(d.bounding_box),
                      'keypoints': _points(d.keypoints)} for d in result.detections],
      'annotatedFile': _observe_annotated_file(_arg(invocation, '--output'), source),
  }


def observe_face_landmarks(result, invocation):
  source = _read_bgr(_arg(invocation, '--image'))
  blendshapes = result.face_blendshapes or []
  return {
      'faces': [{'landmarks': _points(face)} for face in result.face_landmarks],
      'blendshapeCounts': [len(b) for b in blendshapes],
      'matrixShapes': [list(np.asarray(m).shape) for m in result.facial_transformation_matrixes or []],
      # The blendshape scores of the first face, by name.
      'values': {c.category_name: round(float(c.score), 6) for c in blendshapes[0]} if blendshapes else {},
      'annotatedFile': _observe_annotated_file(_arg(invocation, '--output'), source),
      'figureFile': _observe_figure_file(_arg(invocation, '--blendshapes_output')),
  }


def observe_hand_landmarks(result, invocation):
  source = _read_bgr(_arg(invocation, '--image'))
  return {
      'hands': [{'landmarks': _points(landmarks), 'handedness': handedness[0].category_name,
                 'handednessScore': round(float(handedness[0].score), 6)}
                for landmarks, handedness in zip(result.hand_landmarks, result.handedness)],
      'annotatedFile': _observe_annotated_file(_arg(invocation, '--output'), source),
  }


def observe_pose_landmarks(result, invocation):
  source = _read_bgr(_arg(invocation, '--image'))
  height, width = source.shape[:2]
  data = {
      'poses': [{'landmarks': _points(pose)} for pose in result.pose_landmarks],
      'mask': None,
      'annotatedFile': _observe_annotated_file(_arg(invocation, '--output'), source),
  }
  if result.segmentation_masks:
    mask_stats, plane = _confidence_mask(result.segmentation_masks[0])
    mask_stats.update({'imageWidth': width, 'imageHeight': height,
                       'file': _observe_grayscale_mask_file(_arg(invocation, '--mask_output'), plane)})
    data['mask'] = mask_stats
  return data


def observe_holistic_landmarks(result, invocation):
  raw = _read_bgr(_arg(invocation, '--image'))
  height, width = raw.shape[:2]
  scaled_w = int(round(width * (480 / height) / 16.0)) * 16
  scaled_bgr = cv2.resize(raw, (scaled_w, 480))

  def group(landmarks):
    return [{'landmarks': _points(landmarks)}] if landmarks else []

  data = {
      'pose': group(result.pose_landmarks),
      'face': group(result.face_landmarks),
      'leftHand': group(result.left_hand_landmarks),
      'rightHand': group(result.right_hand_landmarks),
      'blendshapeCount': len(result.face_blendshapes or []),
      'mask': None,
      'annotatedFile': _observe_annotated_file(_arg(invocation, '--output'), scaled_bgr),
      'figureFile': _observe_figure_file(_arg(invocation, '--blendshapes_output')),
  }
  if result.segmentation_mask is not None:
    mask_stats, plane = _confidence_mask(result.segmentation_mask)
    # The sample scales the photo before it runs the task, so the mask has the
    # proportions of the photo, not its size.
    mask_stats.update({'imageAspect': round(width / height, 5),
                       'file': _observe_grayscale_mask_file(_arg(invocation, '--mask_output'), plane)})
    data['mask'] = mask_stats
  return data


def observe_image_classifications(results, invocation):
  runs = [{'key': key, 'data': {'categories': _categories(result.classifications[0].categories)}}
          for key, result in zip(invocation['params']['keys'], results)]
  if runs:
    runs[0]['data']['figureFile'] = _observe_figure_file(_arg(invocation, '--output'))
  return runs


def observe_gestures(results, invocation):
  runs = []
  for key, result in zip(invocation['params']['keys'], results):
    hands = []
    for index, landmarks in enumerate(result.hand_landmarks):
      gesture = result.gestures[index][0]
      hands.append({'landmarks': _points(landmarks), 'gesture': gesture.category_name,
                    'score': round(float(gesture.score), 6),
                    'handedness': result.handedness[index][0].category_name})
    runs.append({'key': key, 'data': {'hands': hands}})
  if runs:
    runs[0]['data']['figureFile'] = _observe_figure_file(_arg(invocation, '--output'))
  return runs


def observe_selection_mask(mask_image, invocation):
  source = _read_bgr(_arg(invocation, '--image'))
  height, width = source.shape[:2]
  stats, plane = _confidence_mask(mask_image)
  region = {name: stats.pop(name) for name in ('fraction', 'centroid', 'bounds')}
  probes = {}
  for name, (x, y) in invocation['params']['probes'].items():
    row = min(int(y * plane.shape[0]), plane.shape[0] - 1)
    column = min(int(x * plane.shape[1]), plane.shape[1] - 1)
    probes[name] = bool(plane[row, column] > MASK_THRESHOLD)

  output_path = _arg(invocation, '--output')
  overlay = {'exists': False}
  if output_path and os.path.isfile(output_path):
    written = cv2.imread(output_path, cv2.IMREAD_UNCHANGED)
    if written is not None:
      selected = plane > MASK_THRESHOLD
      green_gain = (round(float(np.median(written[selected, 1].astype(float) - 0.7 * source[selected, 1].astype(float))), 4)
                    if written.shape == source.shape and np.any(selected) else None)
      unchanged = (round(float(np.all(np.abs(written[~selected].astype(float) - 0.7 * source[~selected].astype(float)) <= 2.0,
                                      axis=-1).mean()), 6)
                   if written.shape == source.shape and np.any(~selected) else None)
      overlay = {
          'exists': True,
          'width': int(written.shape[1]),
          'height': int(written.shape[0]),
          'channels': 1 if written.ndim == 2 else int(written.shape[2]),
          'selectedGreenGain': green_gain,
          'outsideUnchangedFraction': unchanged,
      }
  return dict(region, imageWidth=width, imageHeight=height, mask=stats, probes=probes, overlayFile=overlay)


def observe_category_mask(returned, invocation):
  result, labels = returned
  source = _read_bgr(_arg(invocation, '--image'))
  height, width = source.shape[:2]
  mask = np.array(result.category_mask.numpy_view(), copy=True)
  mask = mask if mask.ndim == 2 else mask[..., 0]
  classes = []
  has_background_label = 'background' in labels
  for index in np.unique(mask):
    region = _region(mask == index)
    if index < len(labels):
      label = labels[index]
    elif not has_background_label:
      # Single-class selfie models put background pixels at index 255.
      label = 'background'
    else:
      label = str(int(index))
    classes.append({'index': int(index), 'label': label,
                    'fraction': region['fraction'], 'centroid': region['centroid']})

  confidence_category = _arg(invocation, '--confidence_category')
  confidence_data = None
  output_path = _arg(invocation, '--output')
  if confidence_category is not None and result.confidence_masks and confidence_category in labels:
    cat_index = labels.index(confidence_category)
    conf_stats, conf_plane = _confidence_mask(result.confidence_masks[cat_index])
    confidence_data = dict(conf_stats, category=confidence_category, index=cat_index,
                           count=len(result.confidence_masks))
    foreground = conf_plane > MASK_THRESHOLD
    mask_file = _observe_grayscale_mask_file(output_path, conf_plane)
  else:
    foreground = (mask == 0) if len(labels) == 1 else (mask > 0)
    mask_file = {'exists': False}
    if output_path and os.path.isfile(output_path):
      written = cv2.imread(output_path, cv2.IMREAD_UNCHANGED)
      if written is not None:
        channels = 1 if written.ndim == 2 else int(written.shape[2])
        grayscale = bool(channels == 3 and np.array_equal(written[..., 0], written[..., 1])
                         and np.array_equal(written[..., 0], written[..., 2]))
        fg_diff = (round(abs(float((written[..., 0] > 220).mean()) - float(foreground.mean())), 6)
                   if written.shape[:2] == foreground.shape else None)
        mask_file = {'exists': True, 'width': int(written.shape[1]), 'height': int(written.shape[0]),
                     'channels': channels, 'grayscale': grayscale, 'fgDiff': fg_diff}

  blur_path = _arg(invocation, '--blur_output')
  blur_file = {'exists': False}
  if blur_path and os.path.isfile(blur_path):
    written_blur = cv2.imread(blur_path, cv2.IMREAD_UNCHANGED)
    if written_blur is not None:
      fg_mean_diff = (round(float(np.abs(written_blur[foreground].astype(float) - source[foreground].astype(float)).mean()), 6)
                      if written_blur.shape == source.shape and np.any(foreground) else None)
      bg_mean_diff = (round(float(np.abs(written_blur[~foreground].astype(float) - source[~foreground].astype(float)).mean()), 6)
                      if written_blur.shape == source.shape and np.any(~foreground) else None)
      blur_file = {'exists': True, 'width': int(written_blur.shape[1]), 'height': int(written_blur.shape[0]),
                   'channels': 1 if written_blur.ndim == 2 else int(written_blur.shape[2]),
                   'fgMeanDiff': fg_mean_diff, 'bgMeanDiff': bg_mean_diff}

  return {
      'imageWidth': width,
      'imageHeight': height,
      'maskWidth': int(mask.shape[1]),
      'maskHeight': int(mask.shape[0]),
      'dtype': str(mask.dtype),
      'labels': list(labels),
      'classes': classes,
      'confidence': confidence_data,
      'maskFile': mask_file,
      'blurFile': blur_file,
  }


def observe_similarity(similarity, invocation):
  return {'similarity': round(float(similarity), 6)}


def observe_audio_classifications(results, invocation):
  return {'results': [
      {'timestampMs': int(r.timestamp_ms), 'categories': _categories(r.classifications[0].categories)}
      for r in results]}


def observe_transcription(result, invocation):
  from scipy.io import wavfile  # pylint: disable=g-import-not-at-top
  rate, wav = wavfile.read(_arg(invocation, '--audio'))
  return {
      'clipMs': int(round(len(wav) * 1000 / rate)),
      'segments': [{'transcript': s.transcript, 'isFinal': bool(s.is_final), 'fromMs': int(s.from_timestamp_ms),
                    'toMs': int(s.to_timestamp_ms)} for s in result.segments],
  }


def observe_speech(results, invocation):
  """Describes synthesized speech and its .wav file, and transcribes the file."""
  pieces = [{'chunkIndex': int(r.chunk_index), 'isFinal': bool(r.is_final), 'samples': int(np.asarray(r.samples).size),
             'sampleRateHz': int(r.sample_rate_hz), 'dtype': str(np.asarray(r.samples).dtype),
             'ndim': int(np.asarray(r.samples).ndim)} for r in results]
  samples = (np.concatenate([np.asarray(r.samples).reshape(-1) for r in results]) if results
             else np.zeros(0, np.float32))
  rate = pieces[0]['sampleRateHz'] if pieces else 0
  finite = np.isfinite(samples)
  clean = np.where(finite, samples, 0).astype(np.float64)

  wav_path = _arg(invocation, '--output')
  wav_info = {'exists': False}
  transcribe_samples = clean.astype(np.float32)
  transcribe_rate = rate
  if wav_path and os.path.isfile(wav_path):
    with wave.open(wav_path, 'rb') as wf:
      channels = wf.getnchannels()
      sample_width = wf.getsampwidth()
      wav_rate = wf.getframerate()
      nframes = wf.getnframes()
      raw_frames = wf.readframes(nframes)
    max_diff = None
    if channels == 1 and sample_width == 2:
      wav_float = np.frombuffer(raw_frames, dtype=np.int16).astype(np.float32) / 32767.0
      if len(wav_float) == len(samples) and len(samples) > 0 and int((~finite).sum()) == 0:
        max_diff = round(float(np.abs(wav_float - np.clip(samples, -1.0, 1.0)).max()), 6)
      transcribe_samples = wav_float
      transcribe_rate = wav_rate
    wav_info = {
        'exists': True,
        'channels': channels,
        'sampleWidth': sample_width,
        'sampleRateHz': wav_rate,
        'samples': nframes,
        'maxDiff': max_diff,
    }

  data = {
      'pieces': pieces,
      'sampleRateHz': rate,
      'durationSec': round(len(samples) / rate, 4) if rate > 0 else 0.0,
      'rms': round(float(np.sqrt(np.mean(clean ** 2))), 6) if len(clean) else 0.0,
      'peak': round(float(np.abs(clean).max()), 6) if len(clean) else 0.0,
      'nonFinite': int((~finite).sum()),
      'wordTimestamps': [{'word': w.word, 'startSec': round(float(w.start_sec), 4), 'endSec': round(float(w.end_sec), 4)}
                         for w in results[0].word_timestamps] if results else [],
      'wavFile': wav_info,
      'heard': None,
      'heardError': None,
  }
  # Round trip: what does speech recognition (on the CPU) hear in speech.wav? A
  # failure here is recorded, not raised: it is not a failure of the sample.
  try:
    from mediapipe.tasks import python  # pylint: disable=g-import-not-at-top
    from mediapipe.tasks.python import audio  # pylint: disable=g-import-not-at-top
    options = audio.AudioTranscriberOptions(
        base_options=python.BaseOptions(model_asset_path=invocation['params']['transcriber']),
        sample_rate_hz=transcribe_rate)
    with audio.AudioTranscriber.create_from_options(options) as transcriber:
      heard = transcriber.transcribe(transcribe_samples)
    data['heard'] = ' '.join(s.transcript for s in heard.segments)
  except Exception as error:  # pylint: disable=broad-except
    data['heardError'] = f'{type(error).__name__}: {error}'
  return data


def observe_retrieval_results(results, invocation):
  return {'results': [{'id': r.id, 'score': round(float(r.score), 6)} for r in results]}


def observe_decisions(result, invocation):
  urgent, department, severity = result['urgent'], result['department'], result['severity']
  return {
      'urgent': {'value': bool(urgent.value), 'probabilityTrue': round(float(urgent.probability_true), 6)},
      'department': {'selected': department.selected_key,
                     'probabilities': {k: round(float(v), 6) for k, v in department.probabilities.items()}},
      'severity': {'expectedScore': round(float(severity.expected_score), 6),
                   'probabilities': [round(float(p), 6) for p in severity.probabilities]},
  }


def observe_text_classification(result, invocation):
  return {'categories': _categories(result.classifications[0].categories)}


def observe_language_detection(result, invocation):
  return {'categories': [{'label': d.language_code, 'score': round(float(d.probability), 6)}
                         for d in result.detections]}


def observe_proofreading(result, invocation):
  return {'proofreadText': result.proofread_text,
          'corrections': [{'type': c.type.name, 'text': c.text} for c in result.corrections]}


def observe_summary(result, invocation):
  return {'summary': result.summary}


OBSERVERS = {
    'detections': observe_detections,
    'faces': observe_faces,
    'face_landmarks': observe_face_landmarks,
    'hand_landmarks': observe_hand_landmarks,
    'pose_landmarks': observe_pose_landmarks,
    'holistic_landmarks': observe_holistic_landmarks,
    'image_classifications': observe_image_classifications,
    'gestures': observe_gestures,
    'selection_mask': observe_selection_mask,
    'category_mask': observe_category_mask,
    'similarity': observe_similarity,
    'audio_classifications': observe_audio_classifications,
    'transcription': observe_transcription,
    'retrieval_results': observe_retrieval_results,
    'decisions': observe_decisions,
    'speech': observe_speech,
    'text_classification': observe_text_classification,
    'language_detection': observe_language_detection,
    'proofreading': observe_proofreading,
    'summary': observe_summary,
}


def _load_sample(path):
  """Imports a sample script as a module, without running its `main()`."""
  name = 'sample_' + os.path.splitext(os.path.basename(path))[0]
  spec = importlib.util.spec_from_file_location(name, path)
  module = importlib.util.module_from_spec(spec)
  sys.modules[name] = module
  spec.loader.exec_module(module)
  return module


def _write(path, state):
  with open(path + '.tmp', 'w', encoding='utf-8') as f:
    json.dump(state, f, indent=1, ensure_ascii=False)
  os.replace(path + '.tmp', path)


def main():
  if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8', errors='replace')
  if hasattr(sys.stderr, 'reconfigure'):
    sys.stderr.reconfigure(encoding='utf-8', errors='replace')
  parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument('--job', required=True)
  parser.add_argument('--out', required=True)
  args = parser.parse_args()
  # A crash in native code then leaves a Python traceback in the log.
  faulthandler.enable()

  with open(args.job, encoding='utf-8') as f:
    job = json.load(f)
  # The result file is rewritten after every step: if this process dies, what
  # it had finished is still on disk, and `finished` is still false.
  state = {'finished': False, 'pythonVersion': platform.python_version(), 'mediapipeVersion': None, 'runs': [],
           'error': None, 'traceback': None, 'failedInvocation': None}
  _write(args.out, state)

  current = None
  try:
    import mediapipe  # pylint: disable=g-import-not-at-top
    state['mediapipeVersion'] = mediapipe.__version__
    sample = _load_sample(job['sample'])
    observe = OBSERVERS[job['observer']]
    for invocation in job['invocations']:
      current = invocation['key']
      print(f"--- {os.path.basename(job['sample'])} {' '.join(invocation['args'])}", flush=True)
      started = time.perf_counter()
      returned = sample.main(invocation['args'])
      seconds = round(time.perf_counter() - started, 3)
      observed = observe(returned, invocation)
      if isinstance(observed, dict):
        observed = [{'key': invocation['key'], 'data': observed}]
      for run in observed:
        state['runs'].append(dict(run, seconds=seconds))
      _write(args.out, state)
  except BaseException as error:  # pylint: disable=broad-except
    # The case has failed; all that is left is to record why. A MediaPipe task
    # whose call failed cannot be closed, and the process can hang when the
    # garbage collector frees it. Keep the collector off from here on, so that
    # the error is reported instead of a timeout.
    gc.disable()
    # SystemExit is included: argparse raises it when it rejects the arguments.
    state['error'] = f'{type(error).__name__}: {error}'
    state['traceback'] = traceback.format_exc()
    state['failedInvocation'] = current
    traceback.print_exc()
  state['finished'] = True
  _write(args.out, state)
  sys.stdout.flush()
  sys.stderr.flush()


if __name__ == '__main__':
  main()
