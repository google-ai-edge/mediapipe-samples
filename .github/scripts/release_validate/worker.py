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
returns what its `run()` returned. An observer turns that MediaPipe result into
plain JSON; verify.py then checks it. Nothing is judged here.
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

import cv2
import numpy as np

MASK_THRESHOLD = 0.5


def _arg(invocation, flag):
  """The value that follows `flag` in the arguments of an invocation."""
  args = invocation['args']
  return args[args.index(flag) + 1]


def _image_size(path):
  image = cv2.imread(path)
  if image is None:
    raise ValueError(f'cannot read {path}')
  height, width = image.shape[:2]
  return width, height


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
  view = np.asarray(mask_image.numpy_view())
  channels = 1 if view.ndim == 2 else int(view.shape[2])
  stats = {'width': int(view.shape[1]), 'height': int(view.shape[0]), 'channels': channels, 'dtype': str(view.dtype),
           'min': round(float(view.min()), 6), 'max': round(float(view.max()), 6)}
  plane = view if view.ndim == 2 else view[..., 0]
  stats.update(_region(plane > MASK_THRESHOLD))
  return stats, plane


def observe_detections(result, invocation):
  width, height = _image_size(_arg(invocation, '--image'))
  return {'imageWidth': width, 'imageHeight': height, 'detections': [
      {'label': d.categories[0].category_name, 'score': round(float(d.categories[0].score), 6),
       'box': _box(d.bounding_box)} for d in result.detections]}


def observe_faces(result, invocation):
  width, height = _image_size(_arg(invocation, '--image'))
  return {'imageWidth': width, 'imageHeight': height, 'detections': [
      {'score': round(float(d.categories[0].score), 6), 'box': _box(d.bounding_box),
       'keypoints': _points(d.keypoints)} for d in result.detections]}


def observe_face_landmarks(result, invocation):
  blendshapes = result.face_blendshapes or []
  return {
      'faces': [{'landmarks': _points(face)} for face in result.face_landmarks],
      'blendshapeCounts': [len(b) for b in blendshapes],
      'matrixShapes': [list(np.asarray(m).shape) for m in result.facial_transformation_matrixes or []],
      # The blendshape scores of the first face, by name.
      'values': {c.category_name: round(float(c.score), 6) for c in blendshapes[0]} if blendshapes else {},
  }


def observe_hand_landmarks(result, invocation):
  return {'hands': [
      {'landmarks': _points(landmarks), 'handedness': handedness[0].category_name,
       'handednessScore': round(float(handedness[0].score), 6)}
      for landmarks, handedness in zip(result.hand_landmarks, result.handedness)]}


def observe_pose_landmarks(result, invocation):
  width, height = _image_size(_arg(invocation, '--image'))
  data = {'poses': [{'landmarks': _points(pose)} for pose in result.pose_landmarks], 'mask': None}
  if result.segmentation_masks:
    data['mask'], _ = _confidence_mask(result.segmentation_masks[0])
    data['mask'].update({'imageWidth': width, 'imageHeight': height})
  return data


def observe_holistic_landmarks(result, invocation):
  width, height = _image_size(_arg(invocation, '--image'))

  def group(landmarks):
    return [{'landmarks': _points(landmarks)}] if landmarks else []

  data = {
      'pose': group(result.pose_landmarks),
      'face': group(result.face_landmarks),
      'leftHand': group(result.left_hand_landmarks),
      'rightHand': group(result.right_hand_landmarks),
      'blendshapeCount': len(result.face_blendshapes or []),
      'mask': None,
  }
  if result.segmentation_mask is not None:
    data['mask'], _ = _confidence_mask(result.segmentation_mask)
    # The sample scales the photo before it runs the task, so the mask has the
    # proportions of the photo, not its size.
    data['mask']['imageAspect'] = round(width / height, 5)
  return data


def observe_image_classifications(results, invocation):
  return [{'key': key, 'data': {'categories': _categories(result.classifications[0].categories)}}
          for key, result in zip(invocation['params']['keys'], results)]


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
  return runs


def observe_selection_mask(mask_image, invocation):
  width, height = _image_size(_arg(invocation, '--image'))
  stats, plane = _confidence_mask(mask_image)
  region = {name: stats.pop(name) for name in ('fraction', 'centroid', 'bounds')}
  probes = {}
  for name, (x, y) in invocation['params']['probes'].items():
    row = min(int(y * plane.shape[0]), plane.shape[0] - 1)
    column = min(int(x * plane.shape[1]), plane.shape[1] - 1)
    probes[name] = bool(plane[row, column] > MASK_THRESHOLD)
  return dict(region, imageWidth=width, imageHeight=height, mask=stats, probes=probes)


def observe_category_mask(returned, invocation):
  result, labels = returned
  width, height = _image_size(_arg(invocation, '--image'))
  mask = np.asarray(result.category_mask.numpy_view())
  mask = mask if mask.ndim == 2 else mask[..., 0]
  classes = []
  for index in np.unique(mask):
    region = _region(mask == index)
    classes.append({'index': int(index), 'label': labels[index] if index < len(labels) else str(int(index)),
                    'fraction': region['fraction'], 'centroid': region['centroid']})
  return {'imageWidth': width, 'imageHeight': height, 'maskWidth': int(mask.shape[1]),
          'maskHeight': int(mask.shape[0]), 'dtype': str(mask.dtype), 'labels': list(labels), 'classes': classes,
          'confidenceMaskCount': len(result.confidence_masks or [])}


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
  """Describes synthesized speech, and has the Audio Transcriber listen to it."""
  pieces = [{'chunkIndex': int(r.chunk_index), 'isFinal': bool(r.is_final), 'samples': int(np.asarray(r.samples).size),
             'sampleRateHz': int(r.sample_rate_hz), 'dtype': str(np.asarray(r.samples).dtype),
             'ndim': int(np.asarray(r.samples).ndim)} for r in results]
  samples = (np.concatenate([np.asarray(r.samples).reshape(-1) for r in results]) if results
             else np.zeros(0, np.float32))
  rate = pieces[0]['sampleRateHz'] if pieces else 0
  finite = np.isfinite(samples)
  clean = np.where(finite, samples, 0).astype(np.float64)
  data = {
      'pieces': pieces,
      'sampleRateHz': rate,
      'durationSec': round(len(samples) / rate, 4) if rate > 0 else 0.0,
      'rms': round(float(np.sqrt(np.mean(clean ** 2))), 6) if len(clean) else 0.0,
      'peak': round(float(np.abs(clean).max()), 6) if len(clean) else 0.0,
      'nonFinite': int((~finite).sum()),
      'wordTimestamps': [{'word': w.word, 'startSec': round(float(w.start_sec), 4), 'endSec': round(float(w.end_sec), 4)}
                         for w in results[0].word_timestamps] if results else [],
      'heard': None,
      'heardError': None,
  }
  # Round trip: what does speech recognition (on the CPU) hear in the audio? A
  # failure here is recorded, not raised: it is not a failure of the sample.
  try:
    from mediapipe.tasks import python  # pylint: disable=g-import-not-at-top
    from mediapipe.tasks.python import audio  # pylint: disable=g-import-not-at-top
    options = audio.AudioTranscriberOptions(
        base_options=python.BaseOptions(model_asset_path=invocation['params']['transcriber']), sample_rate_hz=rate)
    with audio.AudioTranscriber.create_from_options(options) as transcriber:
      heard = transcriber.transcribe(clean.astype(np.float32))
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
  with open(path + '.tmp', 'w') as f:
    json.dump(state, f, indent=1, ensure_ascii=False)
  os.replace(path + '.tmp', path)


def main():
  parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
  parser.add_argument('--job', required=True)
  parser.add_argument('--out', required=True)
  args = parser.parse_args()
  # A crash in native code then leaves a Python traceback in the log.
  faulthandler.enable()

  with open(args.job) as f:
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
