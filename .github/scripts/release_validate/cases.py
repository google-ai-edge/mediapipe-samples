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
"""The validation inventory: every Python sample, model, mode and delegate.

A case is one (task, model, mode, delegate) combination. The samples are the
plain Python versions of the notebooks in examples/*/python; each case runs one
of them through its command line entry point.

Models marked `large` are a few hundred megabytes each; --small-models-only
leaves their cases out.
"""

import platform

DELEGATES = ('CPU', 'GPU')

# `model.asset` names an entry of assets.ASSETS. The first model of a task is
# the one its notebook downloads.
TASK_DEFINITIONS = [
    # --- Vision (11 tasks) ---
    {
        'domain': 'vision',
        'task': 'object_detector',
        'heading': 'Object Detection',
        'sample': 'examples/object_detection/python/object_detector.py',
        'modes': ['image', 'video'],
        'models': [
            {'id': 'efficientdet_lite0_int8', 'asset': 'efficientdet_lite0_int8.tflite'},
            # The int8 model cannot run on the macOS GPU delegate (see
            # KNOWN_UNSUPPORTED); the float models validate GPU execution too.
            {'id': 'efficientdet_lite0_float32', 'asset': 'efficientdet_lite0_float32.tflite'},
            {'id': 'efficientdet_lite2_float32', 'asset': 'efficientdet_lite2_float32.tflite'},
            {'id': 'ssd_mobilenet_v2_float32', 'asset': 'ssd_mobilenet_v2_float32.tflite'},
            {'id': 'ssd_mobilenet_v2_float16', 'asset': 'ssd_mobilenet_v2_float16.tflite'},
        ],
    },
    {
        'domain': 'vision',
        'task': 'face_detector',
        'heading': 'Face Detection',
        'sample': 'examples/face_detector/python/face_detector.py',
        'modes': ['image', 'video'],
        'models': [
            {'id': 'blaze_face_short_range', 'asset': 'blaze_face_short_range.tflite'},
            {'id': 'blaze_face_full_range', 'asset': 'blaze_face_full_range.tflite'},
        ],
    },
    {
        'domain': 'vision',
        'task': 'face_landmarker',
        'heading': 'Face Landmarker',
        'sample': 'examples/face_landmarker/python/face_landmarker.py',
        'modes': ['image', 'video'],
        'models': [{'id': 'face_landmarker', 'asset': 'face_landmarker.task'}],
    },
    {
        'domain': 'vision',
        'task': 'hand_landmarker',
        'heading': 'Hand Landmarker',
        'sample': 'examples/hand_landmarker/python/hand_landmarker.py',
        'modes': ['image', 'video'],
        'models': [{'id': 'hand_landmarker', 'asset': 'hand_landmarker.task'}],
    },
    {
        'domain': 'vision',
        'task': 'pose_landmarker',
        'heading': 'Pose Landmarker',
        'sample': 'examples/pose_landmarker/python/pose_landmarker.py',
        'modes': ['image', 'video'],
        'models': [
            {'id': 'pose_landmarker_heavy', 'asset': 'pose_landmarker_heavy.task'},
            {'id': 'pose_landmarker_full', 'asset': 'pose_landmarker_full.task'},
            {'id': 'pose_landmarker_lite', 'asset': 'pose_landmarker_lite.task'},
        ],
    },
    {
        'domain': 'vision',
        'task': 'holistic_landmarker',
        'heading': 'Holistic Landmarker',
        'sample': 'examples/holistic_landmarker/python/holistic_landmarker.py',
        'modes': ['image', 'video'],
        'models': [{'id': 'holistic_landmarker', 'asset': 'holistic_landmarker.task'}],
    },
    {
        'domain': 'vision',
        'task': 'image_classifier',
        'heading': 'Image Classifier',
        'sample': 'examples/image_classification/python/image_classifier.py',
        'modes': ['image', 'video'],
        'models': [
            {'id': 'efficientnet_lite0', 'asset': 'efficientnet_lite0.tflite'},
            {'id': 'efficientnet_lite2', 'asset': 'efficientnet_lite2.tflite'},
        ],
    },
    {
        'domain': 'vision',
        'task': 'gesture_recognizer',
        'heading': 'Gesture Recognizer',
        'sample': 'examples/gesture_recognizer/python/gesture_recognizer.py',
        'modes': ['image', 'video'],
        'models': [{'id': 'gesture_recognizer', 'asset': 'gesture_recognizer.task'}],
    },
    {
        'domain': 'vision',
        'task': 'interactive_segmenter',
        'heading': 'Interactive Segmenter',
        'sample': 'examples/interactive_segmentation/python/interactive_segmenter.py',
        'modes': ['point', 'negative', 'lasso'],
        'models': [{'id': 'magic_touch', 'asset': 'interactive_segmentation.task'}],
    },
    {
        'domain': 'vision',
        'task': 'image_segmenter',
        'heading': 'Image Segmentation',
        'sample': 'examples/image_segmentation/python/image_segmentation.py',
        'modes': ['image-category', 'image-confidence', 'video'],
        'models': [
            {'id': 'deeplab_v3', 'asset': 'deeplab_v3.tflite'},
            {'id': 'hair_segmenter', 'asset': 'hair_segmenter.tflite'},
            {'id': 'selfie_segmenter', 'asset': 'selfie_segmenter.tflite'},
            {'id': 'selfie_segmenter_landscape', 'asset': 'selfie_segmenter_landscape.tflite'},
            {'id': 'selfie_multiclass', 'asset': 'selfie_multiclass_256x256.tflite'},
        ],
    },
    {
        'domain': 'vision',
        'task': 'image_embedder',
        'heading': 'Image Embedding',
        'sample': 'examples/image_embedder/python/image_embedder.py',
        'modes': ['image-pair', 'video-pair'],
        'models': [
            {'id': 'mobilenet_v3_small', 'asset': 'mobilenet_v3_small.tflite'},
            {'id': 'mobilenet_v3_large', 'asset': 'mobilenet_v3_large.tflite'},
        ],
    },

    # --- Audio (3 tasks) ---
    {
        'domain': 'audio',
        'task': 'audio_classifier',
        'heading': 'Audio Classifier',
        'sample': 'examples/audio_classifier/python/audio_classification.py',
        'modes': ['file', 'stream'],
        'models': [{'id': 'yamnet', 'asset': 'yamnet.tflite'}],
    },
    {
        'domain': 'audio',
        'task': 'audio_transcriber',
        'heading': 'Audio Transcriber',
        'sample': 'examples/audio_transcriber/python/audio_transcriber.py',
        'modes': ['file', 'stream'],
        'models': [
            {'id': 'parakeet_tdt_v3', 'asset': 'parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm', 'large': True},
        ],
    },
    {
        'domain': 'audio',
        'task': 'text_to_speech',
        'heading': 'Text to Speech',
        'sample': 'examples/text_to_speech/python/text_to_speech.py',
        'modes': ['speak', 'stream'],
        # The first is the model the sample downloads. Kokoro is published in a
        # variant for the CPU and one for the GPU; both are run on both
        # delegates. PocketTTS and Phonon are other model families.
        'models': [
            {'id': 'kokoro_82m_gpu', 'asset': 'kokoro_int8_gpu_fp16.litertlm', 'large': True},
            {'id': 'kokoro_82m_cpu', 'asset': 'kokoro_int8_fp16.litertlm', 'large': True},
            {'id': 'pocket_tts_en', 'asset': 'pocket_tts_en_int8.litertlm', 'large': True},
            {'id': 'phonon_31m_en', 'asset': 'phonon_int8.litertlm', 'large': True},
        ],
    },

    # --- Text (5 tasks) ---
    {
        'domain': 'text',
        'task': 'text_classifier',
        'heading': 'Text Classification',
        'sample': 'examples/text_classification/python/text_classifier.py',
        'modes': ['classify'],
        'models': [
            {'id': 'bert_classifier', 'asset': 'bert_classifier.tflite'},
            {'id': 'average_word_classifier', 'asset': 'average_word_classifier.tflite'},
        ],
    },
    {
        'domain': 'text',
        'task': 'language_detector',
        'heading': 'Language Detection',
        'sample': 'examples/language_detector/python/language_detector.py',
        'modes': ['detect'],
        'models': [{'id': 'language_detector', 'asset': 'language_detector.tflite'}],
    },
    {
        'domain': 'text',
        'task': 'text_embedder',
        'heading': 'Text Embedding',
        'sample': 'examples/text_embedder/python/text_embedder.py',
        'modes': ['embed'],
        'models': [
            {'id': 'bert_embedder', 'asset': 'bert_embedder.tflite'},
            {'id': 'universal_sentence_encoder', 'asset': 'universal_sentence_encoder.tflite'},
            {'id': 'embedding_gemma', 'asset': 'embedding_gemma.task', 'large': True},
        ],
    },
    {
        'domain': 'text',
        'task': 'text_proofreader',
        'heading': 'Text Proofreading',
        'sample': 'examples/text_proofreader/python/text_proofreader.py',
        'modes': ['proofread'],
        'models': [{'id': 'gemma_200m', 'asset': 'proofread_quant_200m.litertlm'}],
    },
    {
        'domain': 'text',
        'task': 'text_summarizer',
        'heading': 'Text Summarization',
        'sample': 'examples/text_summarizer/python/text_summarizer.py',
        'modes': ['keypoints', 'tldr'],
        'models': [{'id': 'gemma_200m', 'asset': 'summarization_quant_200m_2modes.litertlm'}],
    },

    # --- Retrieval (2 tasks) ---
    {
        'domain': 'retrieval',
        'task': 'universal_embedder',
        'heading': 'Universal Embedder',
        'sample': 'examples/universal_embedder/python/universal_embedder.py',
        'modes': ['text-text', 'text-image', 'image-image'],
        'models': [
            {'id': 'embeddinggemma2_text_vision_440m', 'asset': 'embeddinggemma-2-text-vision-440m.litertlm',
             'large': True},
        ],
    },
    {
        'domain': 'retrieval',
        'task': 'semantic_retriever',
        'heading': 'Semantic Retriever',
        'sample': 'examples/semantic_retriever/python/semantic_retriever.py',
        'modes': ['index-and-search'],
        'models': [
            {'id': 'embeddinggemma2_text_vision_440m', 'asset': 'embeddinggemma-2-text-vision-440m.litertlm',
             'large': True},
        ],
    },

    # --- Decision (1 task) ---
    {
        'domain': 'decision',
        'task': 'decision_maker',
        'heading': 'Decision Maker',
        'sample': 'examples/decision_maker/python/decision_maker.py',
        'modes': ['triage', 'individual'],
        'models': [
            {'id': 'embeddinggemma2_text_270m', 'asset': 'embeddinggemma-2-text-270m.litertlm', 'large': True},
        ],
    },
]

# Combinations that MediaPipe itself declares unsupported: it rejects them with
# the error text given here. Such a case is reported as UNSUPPORTED (never as a
# pass) as long as it fails with exactly that error; any other failure is a
# FAIL. Only add an entry when the library states the limitation in its error
# message. A crash or a wrong result is a bug, not a known limitation.
KNOWN_UNSUPPORTED = [
    {
        'task': 'text_proofreader',
        'delegate': 'GPU',
        'error': 'Only CPU delegate is supported.',
        'reason': 'The Text Proofreader rejects the GPU delegate.',
    },
    {
        'task': 'text_summarizer',
        'delegate': 'GPU',
        'error': 'Only CPU delegate is supported.',
        'reason': 'The Text Summarizer rejects the GPU delegate.',
    },
    {
        'task': 'text_to_speech',
        'model': 'pocket_tts_en',
        'delegate': 'GPU',
        'error': 'Only CPU delegate is supported.',
        'reason': 'PocketTTS rejects the GPU delegate.',
    },
    {
        'task': 'text_to_speech',
        'model': 'phonon_31m_en',
        'delegate': 'GPU',
        'error': 'Only CPU delegate is supported.',
        'reason': 'Phonon rejects the GPU delegate.',
    },
    {
        'task': 'object_detector',
        'model': 'efficientdet_lite0_int8',
        'delegate': 'GPU',
        'system': 'Darwin',
        'error': 'Quantized tensors are not supported by the macOS/iOS GPU delegate.',
        'reason': 'The macOS GPU delegate rejects quantized (int8) models. Object '
                  'detection on the GPU is validated with the float models.',
    },
    {
        'task': 'text_classifier',
        'delegate': 'GPU',
        'system': 'Darwin',
        'error': 'not supported by the macOS/iOS GPU delegate (only float32 input/output tensors are supported).',
        'reason': 'The macOS GPU delegate rejects integer token input tensors.',
    },
    {
        'task': 'language_detector',
        'delegate': 'GPU',
        'system': 'Darwin',
        'error': 'not supported by the macOS/iOS GPU delegate (only float32 input/output tensors are supported).',
        'reason': 'The macOS GPU delegate rejects string input tensors.',
    },
    {
        'task': 'text_embedder',
        'delegate': 'GPU',
        'system': 'Darwin',
        'error': 'not supported by the macOS/iOS GPU delegate (only float32 input/output tensors are supported).',
        'reason': 'The macOS GPU delegate rejects string and integer token input tensors.',
    },
]


def _parse_task_filter(task_filter):
  return [s.strip().lower() for s in task_filter.split(',') if s.strip()]


def _filter_selects(entry, definition):
  """Whether one filter entry selects a task.

  An entry is a domain ("text"), a task ("text_embedder") or both
  ("text/text_embedder"), matched exactly.
  """
  return entry in (definition['domain'], definition['task'],
                   f"{definition['domain']}/{definition['task']}")


def unknown_task_filters(task_filter=''):
  """The entries of a task filter that select nothing (typos, removed tasks)."""
  return [
      entry for entry in _parse_task_filter(task_filter)
      if not any(_filter_selects(entry, d) for d in TASK_DEFINITIONS)
  ]


def known_unsupported(case, system=None):
  """The KNOWN_UNSUPPORTED entry that applies to a case, or None."""
  system = system or platform.system()
  for entry in KNOWN_UNSUPPORTED:
    if entry['task'] != case['task'] or entry['delegate'] != case['delegate']:
      continue
    if 'model' in entry and entry['model'] != case['modelId']:
      continue
    if 'system' in entry and entry['system'] != system:
      continue
    return entry
  return None


def build_validation_matrix(task_filter='', delegates=DELEGATES, include_large_models=True):
  """Expands TASK_DEFINITIONS into the individual cases to run."""
  filters = _parse_task_filter(task_filter)
  cases = []
  for definition in TASK_DEFINITIONS:
    if filters and not any(_filter_selects(f, definition) for f in filters):
      continue
    for model in definition['models']:
      if model.get('large') and not include_large_models:
        continue
      for mode in definition['modes']:
        for delegate in DELEGATES:
          if delegate not in delegates:
            continue
          cases.append({
              'id': '__'.join([definition['domain'], definition['task'],
                               model['id'], mode, delegate]),
              'domain': definition['domain'],
              'task': definition['task'],
              'heading': definition['heading'],
              'sample': definition['sample'],
              'modelId': model['id'],
              'modelAsset': model['asset'],
              'largeModel': bool(model.get('large')),
              'mode': mode,
              'delegate': delegate,
          })
  return cases
