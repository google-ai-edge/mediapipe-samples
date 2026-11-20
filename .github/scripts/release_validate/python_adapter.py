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
"""How a case of the spec is run with the Python samples.

The spec (spec/, read through spec.py) says what a case feeds to its task and
with which options; it knows nothing about the samples. This module is the
Python side of it: it turns the steps and options of a case into the command
line arguments of the sample in examples/*/python, and names the observer of
worker.py that reads the result. A runner for another platform has its own
counterpart of this file, and shares everything else.

Arguments starting with "@" are filled in by run_local.py:
  @model          path of the model of the case
  @asset:NAME     path of a pinned file of spec/assets.json
  @out:NAME       path of a file the sample writes, inside the report
  @delegate       "cpu" or "gpu"
"""

import ast
import os

import spec

# The observer of worker.py that turns what the sample of a task returns into
# the observation of the spec.
OBSERVERS = {
    'object_detector': 'detections',
    'face_detector': 'faces',
    'face_landmarker': 'face_landmarks',
    'hand_landmarker': 'hand_landmarks',
    'pose_landmarker': 'pose_landmarks',
    'holistic_landmarker': 'holistic_landmarks',
    'image_classifier': 'image_classifications',
    'gesture_recognizer': 'gestures',
    'interactive_segmenter': 'selection_mask',
    'image_segmenter': 'category_mask',
    'image_embedder': 'similarity',
    'audio_classifier': 'audio_classifications',
    'audio_transcriber': 'transcription',
    'text_to_speech': 'speech',
    'text_classifier': 'text_classification',
    'language_detector': 'language_detection',
    'text_embedder': 'similarity',
    'text_proofreader': 'proofreading',
    'text_summarizer': 'summary',
    'universal_embedder': 'similarity',
    'semantic_retriever': 'retrieval_results',
    'decision_maker': 'decisions',
}


def _value(flag):
  return lambda value: [flag, str(value)]


def _switch(flag):
  return lambda value: [flag] if value else []


def _toggle(name):
  return lambda value: [f'--{name}' if value else f'--no-{name}']


def _choice(flag, names):
  return lambda value: [flag, names[value]]


_RUNNING_MODE = _choice('--running_mode', {'IMAGE': 'image', 'VIDEO': 'video'})

# How each option of the spec reaches the sample of a task: the command line
# arguments it becomes.
FLAGS = {
    'object_detector': {'runningMode': _RUNNING_MODE, 'scoreThreshold': _value('--score_threshold')},
    'face_detector': {'runningMode': _RUNNING_MODE},
    'face_landmarker': {'runningMode': _RUNNING_MODE},
    'hand_landmarker': {'runningMode': _RUNNING_MODE, 'numHands': _value('--num_hands')},
    'pose_landmarker': {'runningMode': _RUNNING_MODE},
    'holistic_landmarker': {'runningMode': _RUNNING_MODE},
    'image_classifier': {'runningMode': _RUNNING_MODE, 'maxResults': _value('--max_results')},
    'gesture_recognizer': {'runningMode': _RUNNING_MODE},
    # "select" chooses the arguments of the step, see _selection().
    'interactive_segmenter': {'select': lambda value: []},
    'image_segmenter': {'runningMode': _RUNNING_MODE, 'confidenceCategory': _value('--confidence_category')},
    'image_embedder': {'runningMode': _RUNNING_MODE, 'l2Normalize': _toggle('l2_normalize'),
                       'quantize': _toggle('quantize')},
    'audio_classifier': {'runningMode': lambda value: {'AUDIO_CLIPS': [], 'AUDIO_STREAM': ['--stream']}[value],
                         'maxResults': _value('--max_results')},
    'audio_transcriber': {'stream': _switch('--stream')},
    'text_to_speech': {'stream': _switch('--stream')},
    'text_embedder': {'l2Normalize': _toggle('l2_normalize'), 'quantize': _toggle('quantize')},
    'text_summarizer': {'summaryMode': _choice('--mode', {'KEYPOINTS': 'keypoints', 'TLDR': 'tldr'})},
    'universal_embedder': {'l2Normalize': _toggle('l2_normalize')},
    'semantic_retriever': {'limit': _value('--limit')},
    'decision_maker': {'evaluate': _choice('--mode', {'schema': 'schema', 'individual': 'individual'})},
}

# Options a sample always uses and has no command line argument for, with the
# code of the sample that sets each. problems() checks that the spec asks for
# exactly this value, and that the sample still contains that code.
FIXED = {
    'face_landmarker': {
        'numFaces': (1, 'num_faces=1'),
        'outputFaceBlendshapes': (True, 'output_face_blendshapes=True'),
        'outputFacialTransformationMatrixes': (True, 'output_facial_transformation_matrixes=True'),
    },
    'pose_landmarker': {'outputSegmentationMasks': (True, 'output_segmentation_masks=True')},
    'holistic_landmarker': {
        'outputFaceBlendshapes': (True, 'output_face_blendshapes=True'),
        'outputSegmentationMask': (True, 'output_segmentation_mask=True'),
    },
    'semantic_retriever': {'minSimilarity': (0.0, 'min_similarity=0.0')},
}

# The command line argument of a sample for each file it writes, by the role
# the spec gives the file.
OUTPUTS = {
    'object_detector': {'annotated': '--output'},
    'face_detector': {'annotated': '--output'},
    'face_landmarker': {'annotated': '--output', 'figure': '--blendshapes_output'},
    'hand_landmarker': {'annotated': '--output'},
    'pose_landmarker': {'annotated': '--output', 'mask': '--mask_output'},
    'holistic_landmarker': {'annotated': '--output', 'figure': '--blendshapes_output', 'mask': '--mask_output'},
    'image_classifier': {'figure': '--output'},
    'gesture_recognizer': {'figure': '--output'},
    'interactive_segmenter': {'overlay': '--output'},
    'image_segmenter': {'mask': '--output', 'blurred': '--blur_output'},
    'text_to_speech': {'audio': '--output'},
}

_MODEL = ['--model', '@model']
_DELEGATE = ['--delegate', '@delegate']


def _asset(name):
  return f'@asset:{name}'


def _options(task, options):
  args = []
  for name, value in options.items():
    if name in FLAGS.get(task, {}):
      args += FLAGS[task][name](value)
    elif name not in FIXED.get(task, {}):
      raise spec.SpecError(f'the Python sample of {task} has no argument for the option "{name}"')
  return args


def _outputs(task, files):
  args = []
  for role, name in files.items():
    if role not in OUTPUTS.get(task, {}):
      raise spec.SpecError(f'the Python sample of {task} does not write a "{role}" file')
    args += [OUTPUTS[task][role], f'@out:{name}']
  return args


def _pair(step):
  return [_asset(item['image']) if 'image' in item else item['text'] for item in step['pair']]


def _selection(step, options):
  """The object to select: a click, or a lasso around it; and what to leave out."""
  if options.get('select') == 'lasso':
    args = ['--lasso'] + [str(v) for v in step['lasso']]
  else:
    args = ['--x', str(step['point'][0]), '--y', str(step['point'][1])]
  if 'negativeStroke' in step:
    args += ['--negative_stroke'] + [str(v) for v in step['negativeStroke']]
  return args


def _inputs(task, step, resolved):
  """The arguments that give one step to the sample of a task."""
  if task == 'interactive_segmenter':
    return ['--image', _asset(step['image'])] + _selection(step, resolved['options'])
  if task == 'image_embedder':
    return ['--images'] + _pair(step)
  if task == 'text_embedder':
    return ['--texts'] + _pair(step)
  if task == 'universal_embedder':
    return ['--inputs'] + _pair(step)
  if task == 'semantic_retriever':
    index = resolved['index']
    return (['--images'] + [_asset(name) for name in index['images']] + ['--documents'] + index['documents'] +
            ['--query', step['query']])
  if 'image' in step:
    return ['--image', _asset(step['image'])]
  if 'audio' in step:
    return ['--audio', _asset(step['audio'])]
  if task == 'text_to_speech':
    return [step['text']]  # The text to speak is the positional argument.
  return ['--text', step['text']]


def plan(case):
  """How the sample of a case is invoked, and how its results are observed.

  Returns a dict with the name of the observer (see worker.py) and the list of
  invocations. Each invocation runs the sample's `main()` once with `args`;
  `params` is passed to the observer. The observations are keyed by the keys of
  the steps of the spec.
  """
  task = case['task']
  resolved = spec.resolve(case)
  options = _options(task, resolved['options'])
  invocations = []
  if task in ('image_classifier', 'gesture_recognizer'):
    # These samples take all images in one call, and return one result each.
    steps = resolved['steps']
    args = (_MODEL + ['--images'] + [_asset(step['image']) for step in steps] + _DELEGATE + options +
            _outputs(task, resolved['files']))
    invocations.append({'key': resolved['name'] or task, 'args': args,
                        'params': {'keys': [step['key'] for step in steps]}})
  else:
    for step in resolved['steps']:
      files = {**resolved['files'], **step.get('files', {})}
      text_first = task == 'text_to_speech'
      inputs = _inputs(task, step, resolved)
      args = (inputs + _MODEL if text_first else _MODEL + inputs) + _DELEGATE + options + _outputs(task, files)
      invocation = {'key': step['key'], 'args': args}
      if 'probes' in resolved:
        invocation['params'] = {'probes': resolved['probes']}
      if 'listenWith' in resolved:
        invocation['params'] = {'transcriber': _asset(resolved['listenWith'])}
      invocations.append(invocation)
  return {'observer': OBSERVERS[task], 'invocations': invocations}


def _strings(value):
  if isinstance(value, str):
    yield value
  elif isinstance(value, dict):
    for item in value.values():
      yield from _strings(item)
  elif isinstance(value, list):
    for item in value:
      yield from _strings(item)


def problems():
  """Where the Python samples cannot do what the spec asks: empty when they can."""
  found = []
  sources = {}
  for case in spec.build_matrix(platform='python'):
    task = case['task']
    if task not in OBSERVERS:
      found.append(f'{task}: python_adapter.OBSERVERS names no observer')
      continue
    if task not in sources:
      with open(os.path.join(spec.REPO_DIR, case['sample']), encoding='utf-8') as f:
        sources[task] = f.read()
    resolved = spec.resolve(case)
    for name, value in resolved['options'].items():
      if name in FLAGS.get(task, {}):
        continue
      if name not in FIXED.get(task, {}):
        found.append(f"{case['id']}: the Python sample has no argument for the option \"{name}\"")
      elif FIXED[task][name][0] != value:
        found.append(f"{case['id']}: the spec asks for {name}={value!r}, the Python sample always uses "
                     f'{FIXED[task][name][0]!r}')
    try:
      plan(case)
    except (spec.SpecError, KeyError, IndexError) as error:
      found.append(f"{case['id']}: cannot be run with the Python sample: {type(error).__name__}: {error}")
    if 'questions' in resolved:
      # The sample defines the questions itself; they must be those of the spec.
      constants = {node.value for node in ast.walk(ast.parse(sources[task]))
                   if isinstance(node, ast.Constant) and isinstance(node.value, str)}
      for text in _strings(resolved['questions']):
        if text not in constants and text not in ('boolean', 'choice', 'score'):
          found.append(f"{case['id']}: the Python sample does not ask \"{text}\"")
  for task, options in FIXED.items():
    for name, (_, code) in options.items():
      if task in sources and code not in sources[task]:
        found.append(f'{task}: the Python sample no longer sets {code}, which python_adapter.FIXED lists for '
                     f'"{name}"')
  return sorted(set(found))
