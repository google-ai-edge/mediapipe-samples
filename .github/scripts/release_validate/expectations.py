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
"""Ground truths for output verification, and how each sample is invoked.

Each expectation states what is true about the input (a cat and a dog are in the
photo, the sentence is negative, the clip is speech), with tolerances wide
enough to hold for any correct release rather than for one package version.

Positions are fractions of the image width and height. They were marked by eye
on the input images and then checked by drawing each mark, with its tolerance,
back onto the image; they were not copied from the output of a release. Where a
value is a baseline measured from a release instead of an independent truth, the
comment says so, and the name of the check says "baseline".

The same expectation applies to the CPU and the GPU delegate.
"""

# Arguments starting with "@" are filled in by run_local.py:
#   @model          path of the model of the case
#   @asset:NAME     path of an entry of assets.ASSETS
#   @out:NAME       path of a file the sample writes, inside the report
#   @delegate       "cpu" or "gpu"


def _near(x, y, tolerance, index):
  return {'x': x, 'y': y, 'tolerance': tolerance, 'index': index}


# --- Text inputs -----------------------------------------------------------

# Sentences given to the MobileBERT Text Classifier, with their true sentiment.
# The first is the sentence of the notebook.
TEXT_CLASSIFIER_INPUTS = [
    {'text': "I'm looking forward to what will come next.", 'label': 'positive'},
    {'text': 'This MediaPipe release validation test is working wonderfully!', 'label': 'positive'},
    {'text': 'This is a terrible, broken and disappointing release. I hate it.', 'label': 'negative'},
]

# The AverageWord classifier uses "1" for positive and "0" for negative, and
# classifies bag-of-words movie-review vocabulary without sentence context.
AVERAGE_WORD_INPUTS = [
    {'text': 'This film is a wonderful, uplifting masterpiece with great acting!', 'label': '1'},
    {'text': 'This MediaPipe release validation test is working wonderfully!', 'label': '1'},
    {'text': 'This is a terrible, broken and disappointing release. I hate it.', 'label': '0'},
]

# Sentences given to the Language Detector, with the language they are written
# in. The first is the default of the notebook: a Chinese proverb. It consists
# only of characters that are also used in Japanese, so a detector can
# legitimately be unsure between the two (release 1.2.3 reports zh 0.51 and ja
# 0.48); for it, Chinese only has to be among the detected languages.
LANGUAGE_DETECTOR_INPUTS = [
    {'text': '\u5206\u4e45\u5fc5\u5408\u5408\u4e45\u5fc5\u5206', 'label': 'zh', 'match': 'present', 'minScore': 0.3},
    {'text': 'To be, or not to be, that is the question', 'label': 'en'},
    {'text': 'Bonjour, ceci est un test de validation automatique.', 'label': 'fr'},
    {'text': 'Hola, esta es una prueba de validaci\u00f3n autom\u00e1tica para la nueva versi\u00f3n.', 'label': 'es'},
    {'text': 'Hallo, dies ist ein automatischer Validierungstest f\u00fcr die neue Version.', 'label': 'de'},
    {'text': '\u3053\u308c\u306f\u65b0\u3057\u3044\u30ea\u30ea\u30fc\u30b9\u306e\u81ea\u52d5\u691c\u8a3c\u30c6\u30b9\u30c8\u3067\u3059\u3002', 'label': 'ja'},
]

# The two sentences of the Text Embedder notebook (used for bert_embedder).
TEXT_EMBEDDER_FIRST = "I'm feeling so good"
TEXT_EMBEDDER_SECOND = "I'm okay I guess"

# Semantic sentence pairs for Universal Sentence Encoder and EmbeddingGemma.
_BEACH_TEXT = 'I love watching sunny sunsets at the beach.'
TEXT_EMBEDDER_SEMANTIC_PAIRS = [
    ('identical sentences', _BEACH_TEXT, _BEACH_TEXT, [0.99, 1.0001]),
    ('related sentences', _BEACH_TEXT, 'Warm ocean evenings are wonderful.', [-1, 1]),
    ('unrelated sentences', _BEACH_TEXT, 'The quarterly tax filing deadline is next Tuesday.', [-1, 1]),
]

# Texts given to the Text Proofreader: the two of the notebook. The first has
# one typo and exactly one correct fix. The second can be fixed in several
# ways, so only what every fix has in common is checked.
PROOFREADER_INPUTS = [
    {'text': 'I am a studnt.', 'exact': 'I am a student.'},
    {
        'text': 'i hope you doing . theyre a great addition on any families.',
        'startsWith': 'I hope you',
        'containsAny': ["They're", 'They are'],
    },
]

# The text of the Text Summarizer notebook: a station in the European quarter
# of Brussels, named after the Schuman roundabout.
SUMMARIZER_TEXT = (
    'This station is in the centre of the European quarter of Brussels, being '
    'adjacent to the Berlaymont building (headquarters of the European '
    'Commission), the Justus Lipsius building (Council of the European Union) '
    'and numerous other EU offices. The station is named after the area around '
    'the Schuman roundabout, which was named after Robert Schuman. The station '
    'sits on the Rue de la Loi/Wetstraat, a major city thoroughfare, and is '
    'close to Parc du Cinquantenaire/Jubelpark.')

# --- Speech ----------------------------------------------------------------

# What is said in speech_16000_hz_mono.wav, a MediaPipe test clip of 4.27 s.
# There is no published transcript of it. This is the reference the release
# validation of mediapipe-samples-web uses; according to its fixtures/README.md
# it was taken from the Audio Transcriber of release 1.2.2 and then confirmed
# by a person listening to the clip. It was not confirmed again for this suite.
SPEECH_TRANSCRIPT = "Send a text to Mike, I'll be home late tomorrow."

# The sentence given to Text to Speech (the default of the sample). At a normal
# speaking rate its 14 words take about 4 to 7 seconds.
TTS_SENTENCE = 'The quick brown fox jumps over the lazy dog near the quiet river bank.'

# The model that listens to the synthesized speech (see the text_to_speech
# expectation).
ROUND_TRIP_TRANSCRIBER = 'parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm'

# --- Retrieval and decisions -----------------------------------------------

_CAT_TEXT = 'a photo of a cat'
_BURGER_TEXT = 'a photo of a cheeseburger'
_CAT_PHOTO = '@asset:classifier_cat.jpg'
_BURGER_PHOTO = '@asset:classifier_burger.jpg'

# Pairs given to the Universal Embedder, per mode. Identical inputs must score
# about 1. A sentence must be closer to a sentence about the same subject than
# to an unrelated one, and a description closer to the photo it describes than
# to a photo of something else. The photos are the cat and the cheeseburger of
# the image classifier notebook.
UNIVERSAL_EMBEDDER_PLANS = {
    'text-text': {
        'runs': [
            {'key': 'the same sentence twice', 'inputs': [_CAT_TEXT, _CAT_TEXT], 'range': [0.99, 1.0001]},
            {'key': 'related sentences', 'inputs': [_CAT_TEXT, 'an adorable fluffy kitten'], 'range': [-1, 1]},
            {'key': 'unrelated sentences', 'inputs': [_CAT_TEXT, 'the quarterly tax filing deadline'],
             'range': [-1, 1]},
        ],
        'ordered': [{'higher': 'related sentences', 'lower': 'unrelated sentences', 'by': 0.1}],
    },
    'text-image': {
        'runs': [
            {'key': 'cat description and cat photo', 'inputs': [_CAT_TEXT, _CAT_PHOTO], 'range': [-1, 1]},
            {'key': 'cat description and burger photo', 'inputs': [_CAT_TEXT, _BURGER_PHOTO], 'range': [-1, 1]},
            {'key': 'burger description and burger photo', 'inputs': [_BURGER_TEXT, _BURGER_PHOTO], 'range': [-1, 1]},
            {'key': 'burger description and cat photo', 'inputs': [_BURGER_TEXT, _CAT_PHOTO], 'range': [-1, 1]},
        ],
        'ordered': [
            {'higher': 'cat description and cat photo', 'lower': 'cat description and burger photo', 'by': 0.05},
            {'higher': 'burger description and burger photo', 'lower': 'burger description and cat photo', 'by': 0.05},
        ],
    },
    'image-image': {
        'runs': [
            {'key': 'the same photo twice', 'inputs': [_CAT_PHOTO, _CAT_PHOTO], 'range': [0.99, 1.0001]},
            {'key': 'a cat photo and a burger photo', 'inputs': [_CAT_PHOTO, _BURGER_PHOTO], 'range': [-1, 0.9]},
        ],
        'ordered': [{'higher': 'the same photo twice', 'lower': 'a cat photo and a burger photo', 'by': 0.1}],
    },
}

# What the Semantic Retriever indexes: five photos of clearly different things
# (a cheeseburger, a cat, a man doing yoga on a beach, two girls on a horse, a
# man in a suit) and two short texts. The sample names an image record after
# its file, and the texts "document 1" and "document 2".
RETRIEVER_PHOTOS = ['classifier_burger.jpg', 'classifier_cat.jpg', 'pose.jpg', 'segmentation_input_rotation0.jpg',
                    'portrait.jpg']
RETRIEVER_DOCUMENTS = [
    'The quarterly tax filing deadline is next Tuesday.',
    'Whisk the eggs, then fold in the flour and the melted butter.',
]
RETRIEVER_LIMIT = 3

# Queries, each with the one record it describes.
RETRIEVER_QUERIES = [
    {'query': 'a cheeseburger', 'top': 'classifier_burger.jpg'},
    {'query': 'a striped cat', 'top': 'classifier_cat.jpg'},
    {'query': 'a man doing yoga on the beach', 'top': 'pose.jpg'},
    {'query': 'children riding a horse', 'top': 'segmentation_input_rotation0.jpg'},
    {'query': 'when are my taxes due?', 'top': 'document 1'},
    {'query': 'how do I bake a cake?', 'top': 'document 2'},
]

# Support tickets given to the Decision Maker, which triages them with the
# three questions of the sample: is it an outage that stops customers, which
# department owns it, and how severe is it. The answers follow from the text:
# the first reports a crash that locks customers out, the second asks for a
# refund, the third suggests a feature. The outage is plainly the most severe.
DECISION_TICKETS = [
    {'key': 'outage ticket', 'urgent': True, 'department': 'bug',
     'text': 'Urgent: our production database pipeline crashes with a fatal segfault and customers cannot sign in!'},
    {'key': 'billing ticket', 'urgent': False, 'department': 'billing',
     'text': 'We were charged twice on invoice #4821 this month, can you refund the duplicate?'},
    {'key': 'feature request', 'urgent': False, 'department': 'feature_request',
     'text': 'It would be nice if the analytics dashboard had a dark mode toggle.'},
]

# --- Images ----------------------------------------------------------------

# cats_and_dogs.jpg (1200x600): four pets in a row in front of a pink wall. From
# the left: a cat lying down, a grey poodle, a corgi, and a second cat. The
# points below name what lies at that position.
PET_PROBES = {
    'corgi chest': [0.68, 0.68],
    'corgi face': [0.65, 0.45],
    'corgi ear': [0.59, 0.33],
    'poodle face': [0.42, 0.50],
    'poodle chest': [0.44, 0.75],
    'left cat face': [0.30, 0.80],
    'right cat face': [0.84, 0.80],
    'wall, upper left': [0.10, 0.20],
    'wall, upper right': [0.90, 0.30],
}

# Selections given to the Interactive Segmenter, per mode.
INTERACTIVE_SEGMENTER_CLICKS = [
    {
        'key': 'corgi',
        'point': PET_PROBES['corgi chest'],
        'lasso': [0.50, 0.24, 0.77, 0.98],
        'fraction': [0.07, 0.2],
        'centroidWithin': {'minX': 0.57, 'maxX': 0.70, 'minY': 0.5, 'maxY': 0.8},
        'boundsWithin': {'minX': 0.46, 'maxX': 0.81, 'minY': 0.18, 'maxY': 1.0},
        'selected': ['corgi chest', 'corgi face', 'corgi ear'],
        'notSelected': ['poodle face', 'poodle chest', 'left cat face', 'right cat face',
                        'wall, upper left', 'wall, upper right'],
    },
    {
        'key': 'poodle',
        'point': PET_PROBES['poodle chest'],
        'lasso': [0.32, 0.33, 0.54, 0.98],
        'fraction': [0.04, 0.16],
        'centroidWithin': {'minX': 0.36, 'maxX': 0.50, 'minY': 0.5, 'maxY': 0.85},
        'boundsWithin': {'minX': 0.28, 'maxX': 0.58, 'minY': 0.3, 'maxY': 1.0},
        'selected': ['poodle chest', 'poodle face'],
        'notSelected': ['corgi chest', 'corgi face', 'corgi ear', 'left cat face', 'right cat face',
                        'wall, upper left', 'wall, upper right'],
    },
]

# Click on the corgi's chest plus a negative stroke across its head and face:
# the chest remains selected while the ear and face are excluded.
INTERACTIVE_NEGATIVE_RUNS = [
    {
        'key': 'corgi body without head',
        'point': PET_PROBES['corgi chest'],
        'negativeStroke': [0.57, 0.30, 0.68, 0.50],
        'fraction': [0.04, 0.11],
        'centroidWithin': {'minX': 0.55, 'maxX': 0.70, 'minY': 0.60, 'maxY': 0.90},
        'boundsWithin': {'minX': 0.46, 'maxX': 0.81, 'minY': 0.30, 'maxY': 1.0},
        'selected': ['corgi chest'],
        'notSelected': ['corgi face', 'corgi ear', 'poodle face', 'poodle chest',
                        'left cat face', 'right cat face', 'wall, upper left', 'wall, upper right'],
    },
]

# The four photos of the Gesture Recognizer notebook. Each shows one hand making
# the gesture its file is named after.
GESTURE_IMAGES = [
    # The thumb points down; its tip is near the bottom edge.
    {'asset': 'thumbs_down.jpg', 'gesture': 'Thumb_Down', 'points': {'thumb tip': _near(0.635, 0.93, 0.06, 4)}},
    {'asset': 'victory.jpg', 'gesture': 'Victory', 'points': {}},
    {'asset': 'thumbs_up.jpg', 'gesture': 'Thumb_Up', 'points': {'thumb tip': _near(0.648, 0.535, 0.06, 4)}},
    # The index finger points up; its tip is near the top edge.
    {'asset': 'pointing_up.jpg', 'gesture': 'Pointing_Up', 'points': {'index finger tip': _near(0.713, 0.07, 0.06, 8)}},
]

# The ImageNet classes for a striped domestic cat. cat.jpg shows a silver tabby
# Scottish Fold; ImageNet has no class for that breed, and a correct classifier
# spreads its score over these three.
TABBY_CAT_LABELS = ['tabby', 'tiger cat', 'Egyptian cat']

# Per-model ground truths for Image Segmenter. DeepLab runs on the riders/horse
# photo of the notebook; the hair and selfie models run on business-person.png
# (a woman from the waist up, hair tied at the top of her head).
IMAGE_SEGMENTER_PLANS = {
    'deeplab_v3': {
        'asset': 'segmentation_input_rotation0.jpg',
        'photoLabel': 'Two girls on a horse, and a woman',
        'labelCount': 21,
        'classes': [
            {'label': 'person', 'fraction': [0.2, 0.45],
             'centroidWithin': {'minX': 0.15, 'maxX': 0.45, 'minY': 0.4, 'maxY': 0.7}},
            {'label': 'horse', 'fraction': [0.15, 0.4],
             'centroidWithin': {'minX': 0.45, 'maxX': 0.8, 'minY': 0.6, 'maxY': 0.9}},
            {'label': 'background', 'fraction': [0.25, 0.5]},
        ],
        'maxOther': 0.03,
        'confidence': {
            'label': 'person',
            'fraction': [0.2, 0.45],
            'minPeak': 0.8,
            'centroidWithin': {'minX': 0.15, 'maxX': 0.45, 'minY': 0.4, 'maxY': 0.7},
        },
    },
    'hair_segmenter': {
        'asset': 'business-person.png',
        'photoLabel': 'A woman facing the camera',
        'labelCount': 2,
        'classes': [
            {'label': 'hair', 'fraction': [0.01, 0.08],
             'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.02, 'maxY': 0.22}},
            {'label': 'background', 'fraction': [0.92, 0.99]},
        ],
        'maxOther': 0.01,
        'confidence': {
            'label': 'hair',
            'fraction': [0.01, 0.08],
            'minPeak': 0.8,
            'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.02, 'maxY': 0.22},
        },
    },
    'selfie_segmenter': {
        'asset': 'business-person.png',
        'photoLabel': 'A woman facing the camera',
        'labelCount': 1,
        'classes': [
            {'label': 'selfie', 'fraction': [0.4, 0.7],
             'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.45, 'maxY': 0.8}},
            {'label': 'background', 'fraction': [0.3, 0.6]},
        ],
        'maxOther': 0.01,
        'confidence': {
            'label': 'selfie',
            'fraction': [0.4, 0.7],
            'minPeak': 0.8,
            'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.45, 'maxY': 0.8},
        },
    },
    'selfie_segmenter_landscape': {
        'asset': 'business-person.png',
        'photoLabel': 'A woman facing the camera',
        'labelCount': 1,
        'classes': [
            {'label': 'selfie', 'fraction': [0.4, 0.7],
             'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.45, 'maxY': 0.8}},
            {'label': 'background', 'fraction': [0.3, 0.6]},
        ],
        'maxOther': 0.01,
        'confidence': {
            'label': 'selfie',
            'fraction': [0.4, 0.7],
            'minPeak': 0.8,
            'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.45, 'maxY': 0.8},
        },
    },
    'selfie_multiclass': {
        'asset': 'business-person.png',
        'photoLabel': 'A woman facing the camera',
        'labelCount': 6,
        'classes': [
            {'label': 'background', 'fraction': [0.3, 0.6]},
            {'label': 'hair', 'fraction': [0.01, 0.08],
             'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.02, 'maxY': 0.22}},
            {'label': 'body-skin', 'fraction': [0.02, 0.12],
             'centroidWithin': {'minX': 0.30, 'maxX': 0.65, 'minY': 0.40, 'maxY': 0.75}},
            {'label': 'face-skin', 'fraction': [0.03, 0.12],
             'centroidWithin': {'minX': 0.40, 'maxX': 0.62, 'minY': 0.12, 'maxY': 0.35}},
            {'label': 'clothes', 'fraction': [0.25, 0.55],
             'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.55, 'maxY': 0.85}},
        ],
        'maxOther': 0.03,
        'confidence': {
            'label': 'clothes',
            'fraction': [0.25, 0.55],
            'minPeak': 0.8,
            'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.55, 'maxY': 0.85},
        },
    },
}


def _vision_args(image_asset, outputs=('annotated.png',), output_flags=('--output',), mode='image'):
  args = ['--model', '@model', '--image', f'@asset:{image_asset}', '--delegate', '@delegate']
  if mode == 'video':
    args += ['--running_mode', 'video']
  for flag, name in zip(output_flags, outputs):
    args += [flag, f'@out:{name}']
  return args


def get_plan(case):
  """How the sample of a case is invoked, and how its results are observed.

  Returns a dict with the name of the observer (see worker.py) and the list of
  invocations. Each invocation runs the sample's `main()` once with `args`;
  `params` is passed to the observer.
  """
  task = case['task']
  mode = case['mode']
  text_args = ['--model', '@model', '--delegate', '@delegate']

  if task == 'object_detector':
    return {'observer': 'detections', 'invocations': [
        {'key': 'cat_and_dog.jpg', 'args': _vision_args('cat_and_dog.jpg', mode=mode)}]}
  if task == 'face_detector':
    return {'observer': 'faces', 'invocations': [
        {'key': 'portrait.jpg', 'args': _vision_args('portrait.jpg', mode=mode)}]}
  if task == 'face_landmarker':
    return {'observer': 'face_landmarks', 'invocations': [
        {'key': 'business-person.png',
         'args': _vision_args('business-person.png', ('annotated.png', 'blendshapes.png'),
                              ('--output', '--blendshapes_output'), mode=mode)}]}
  if task == 'hand_landmarker':
    return {'observer': 'hand_landmarks', 'invocations': [
        {'key': 'woman_hands.jpg', 'args': _vision_args('woman_hands.jpg', mode=mode)}]}
  if task == 'pose_landmarker':
    return {'observer': 'pose_landmarks', 'invocations': [
        {'key': 'pose.jpg',
         'args': _vision_args('pose.jpg', ('annotated.png', 'mask.png'),
                              ('--output', '--mask_output'), mode=mode)}]}
  if task == 'holistic_landmarker':
    return {'observer': 'holistic_landmarks', 'invocations': [
        {'key': 'business-person.png',
         'args': _vision_args('business-person.png', ('annotated.png', 'blendshapes.png', 'mask.png'),
                              ('--output', '--blendshapes_output', '--mask_output'), mode=mode)}]}
  if task == 'image_classifier':
    args = ['--model', '@model', '--images', '@asset:classifier_burger.jpg', '@asset:classifier_cat.jpg',
            '--delegate', '@delegate', '--output', '@out:annotated.png']
    if mode == 'video':
      args += ['--running_mode', 'video']
    return {'observer': 'image_classifications', 'invocations': [
        {'key': 'burger and cat', 'args': args,
         'params': {'keys': ['classifier_burger.jpg', 'classifier_cat.jpg']}}]}
  if task == 'gesture_recognizer':
    args = (['--model', '@model', '--images'] + [f"@asset:{g['asset']}" for g in GESTURE_IMAGES] +
            ['--delegate', '@delegate', '--output', '@out:annotated.png'])
    if mode == 'video':
      args += ['--running_mode', 'video']
    return {'observer': 'gestures', 'invocations': [
        {'key': 'four gestures', 'args': args,
         'params': {'keys': [g['asset'] for g in GESTURE_IMAGES]}}]}
  if task == 'interactive_segmenter':
    if mode == 'negative':
      invocations = []
      for item in INTERACTIVE_NEGATIVE_RUNS:
        invocations.append({
            'key': item['key'],
            'args': ['--model', '@model', '--image', '@asset:cats_and_dogs.jpg',
                     '--x', str(item['point'][0]), '--y', str(item['point'][1]),
                     '--negative_stroke', *[str(v) for v in item['negativeStroke']],
                     '--delegate', '@delegate', '--output', '@out:selected_negative.png'],
            'params': {'probes': PET_PROBES},
        })
      return {'observer': 'selection_mask', 'invocations': invocations}
    if mode == 'lasso':
      return {'observer': 'selection_mask', 'invocations': [
          {'key': click['key'],
           'args': ['--model', '@model', '--image', '@asset:cats_and_dogs.jpg',
                    '--lasso', *[str(v) for v in click['lasso']],
                    '--delegate', '@delegate', '--output', f"@out:selected_{click['key']}.png"],
           'params': {'probes': PET_PROBES}}
          for click in INTERACTIVE_SEGMENTER_CLICKS]}
    return {'observer': 'selection_mask', 'invocations': [
        {'key': click['key'],
         'args': ['--model', '@model', '--image', '@asset:cats_and_dogs.jpg', '--x', str(click['point'][0]),
                  '--y', str(click['point'][1]), '--delegate', '@delegate',
                  '--output', f"@out:selected_{click['key']}.png"],
         'params': {'probes': PET_PROBES}}
        for click in INTERACTIVE_SEGMENTER_CLICKS]}
  if task == 'image_segmenter':
    plan = IMAGE_SEGMENTER_PLANS[case['modelId']]
    args = _vision_args(plan['asset'], ('mask.png', 'blurred_background.png'),
                        ('--output', '--blur_output'), mode='video' if mode == 'video' else 'image')
    if mode == 'image-confidence':
      args += ['--confidence_category', plan['confidence']['label']]
    return {'observer': 'category_mask', 'invocations': [{'key': plan['asset'], 'args': args}]}
  if task == 'image_embedder':
    pairs = [('same photo, cropped', 'burger.jpg', 'burger_crop.jpg'),
             ('the same photo twice', 'burger.jpg', 'burger.jpg'),
             ('a burger and a cat', 'burger.jpg', 'classifier_cat.jpg')]
    extra = ['--running_mode', 'video'] if mode == 'video-pair' else []
    return {'observer': 'similarity', 'invocations': [
        {'key': key, 'args': ['--model', '@model', '--images', f'@asset:{first}', f'@asset:{second}',
                              '--delegate', '@delegate'] + extra}
        for key, first, second in pairs]}
  if task == 'audio_classifier':
    args = ['--model', '@model', '--audio', '@asset:speech_16000_hz_mono.wav', '--delegate', '@delegate']
    if mode == 'stream':
      args.append('--stream')
    return {'observer': 'audio_classifications', 'invocations': [{'key': 'speech_16000_hz_mono.wav', 'args': args}]}
  if task == 'audio_transcriber':
    args = ['--model', '@model', '--audio', '@asset:speech_16000_hz_mono.wav', '--delegate', '@delegate']
    if mode == 'stream':
      args.append('--stream')
    return {'observer': 'transcription', 'invocations': [{'key': 'speech_16000_hz_mono.wav', 'args': args}]}
  if task == 'text_to_speech':
    args = [TTS_SENTENCE] + text_args + ['--output', '@out:speech.wav']
    if mode == 'stream':
      args.append('--stream')
    return {'observer': 'speech', 'invocations': [
        {'key': mode, 'args': args, 'params': {'transcriber': f'@asset:{ROUND_TRIP_TRANSCRIBER}'}}]}
  if task == 'universal_embedder':
    return {'observer': 'similarity', 'invocations': [
        {'key': run['key'], 'args': ['--model', '@model', '--inputs'] + run['inputs'] + ['--delegate', '@delegate']}
        for run in UNIVERSAL_EMBEDDER_PLANS[mode]['runs']]}
  if task == 'semantic_retriever':
    return {'observer': 'retrieval_results', 'invocations': [
        {'key': item['query'],
         'args': ['--model', '@model', '--images'] + [f'@asset:{name}' for name in RETRIEVER_PHOTOS] +
                 ['--documents'] + RETRIEVER_DOCUMENTS +
                 ['--query', item['query'], '--limit', str(RETRIEVER_LIMIT), '--delegate', '@delegate']}
        for item in RETRIEVER_QUERIES]}
  if task == 'decision_maker':
    extra = ['--mode', 'individual'] if mode == 'individual' else []
    return {'observer': 'decisions', 'invocations': [
        {'key': ticket['key'], 'args': text_args + ['--text', ticket['text']] + extra}
        for ticket in DECISION_TICKETS]}
  if task == 'text_classifier':
    inputs = AVERAGE_WORD_INPUTS if case['modelId'] == 'average_word_classifier' else TEXT_CLASSIFIER_INPUTS
    return {'observer': 'text_classification', 'invocations': [
        {'key': f'sentence {i + 1}', 'args': text_args + ['--text', item['text']]}
        for i, item in enumerate(inputs)]}
  if task == 'language_detector':
    return {'observer': 'language_detection', 'invocations': [
        {'key': f'sentence {i + 1}', 'args': text_args + ['--text', item['text']]}
        for i, item in enumerate(LANGUAGE_DETECTOR_INPUTS)]}
  if task == 'text_embedder':
    if case['modelId'] == 'bert_embedder':
      pairs = [('the two sentences of the notebook', TEXT_EMBEDDER_FIRST, TEXT_EMBEDDER_SECOND),
               ('the same sentence twice', TEXT_EMBEDDER_FIRST, TEXT_EMBEDDER_FIRST)]
    else:
      pairs = [(key, first, second) for key, first, second, _ in TEXT_EMBEDDER_SEMANTIC_PAIRS]
    return {'observer': 'similarity', 'invocations': [
        {'key': key, 'args': text_args + ['--texts', first, second]} for key, first, second in pairs]}
  if task == 'text_proofreader':
    return {'observer': 'proofreading', 'invocations': [
        {'key': f'text {i + 1}', 'args': text_args + ['--text', item['text']]}
        for i, item in enumerate(PROOFREADER_INPUTS)]}
  if task == 'text_summarizer':
    return {'observer': 'summary', 'invocations': [
        {'key': mode, 'args': text_args + ['--text', SUMMARIZER_TEXT, '--mode', mode]}]}
  return None


# cat_and_dog.jpg (640x416): a cat crouching on the left and a puppy sitting on
# the right, on a concrete floor. The boxes were marked by eye (cat: ear tips to
# paws; dog: top of the head to the paws); 0.5 IoU is the usual detection
# criterion. Nothing else is in the photo.
_CAT_AND_DOG = {
    'type': 'detections',
    'objects': [
        {'label': 'cat', 'minScore': 0.5, 'box': {'x': 0.12, 'y': 0.40, 'width': 0.38, 'height': 0.45}, 'minIoU': 0.5},
        {'label': 'dog', 'minScore': 0.5, 'box': {'x': 0.49, 'y': 0.07, 'width': 0.36, 'height': 0.82}, 'minIoU': 0.5},
    ],
    'onlyLabels': ['cat', 'dog'],
}

# portrait.jpg (820x1024): one man in a suit facing the camera, arms crossed.
# His right eye is on the left of the image. BlazeFace keypoints: 0 right eye,
# 1 left eye, 2 nose tip. The face is a little under a third of the image wide.
_PORTRAIT_FACE = {
    'type': 'faces',
    'count': 1,
    'minScore': 0.5,
    'keypoints': [
        {'index': 0, 'name': 'right eye', 'x': 0.452, 'y': 0.17, 'tolerance': 0.05},
        {'index': 1, 'name': 'left eye', 'x': 0.548, 'y': 0.17, 'tolerance': 0.05},
        {'index': 2, 'name': 'nose tip', 'x': 0.513, 'y': 0.232, 'tolerance': 0.05},
    ],
    'boxWidth': [0.18, 0.4],
    'boxContains': {'x': 0.513, 'y': 0.232},
}

# business-person.png (958x1358): one woman facing the camera, from the waist
# up. Face mesh landmarks: 1 nose tip, 152 chin, 33 outer corner of her right
# eye (left in the image), 263 outer corner of her left eye. Her eyes are open
# and her mouth is closed. The model outputs 478 landmarks, 52 blendshapes and
# one 4x4 transformation matrix per face.
_BUSINESS_PERSON_NOSE = (0.535, 0.252)
_BUSINESS_PERSON_FACE = {
    'type': 'landmarks',
    'runs': [{
        'key': 'business-person.png',
        'groups': [{
            'key': 'faces',
            'name': 'face',
            'landmarkCount': 478,
            'instances': [{'points': {
                'nose tip': _near(*_BUSINESS_PERSON_NOSE, 0.05, 1),
                'chin': _near(0.535, 0.342, 0.05, 152),
                'right eye outer corner': _near(0.437, 0.200, 0.05, 33),
                'left eye outer corner': _near(0.612, 0.193, 0.05, 263),
            }}],
        }],
        'equals': [
            {'key': 'blendshapeCounts', 'name': 'blendshapes per face', 'value': [52]},
            {'key': 'matrixShapes', 'name': 'transformation matrices', 'value': [[4, 4]]},
        ],
        'ranges': [
            {'key': 'eyeBlinkLeft', 'name': 'left eye is open (eyeBlinkLeft)', 'range': [0, 0.5]},
            {'key': 'eyeBlinkRight', 'name': 'right eye is open (eyeBlinkRight)', 'range': [0, 0.5]},
            {'key': 'jawOpen', 'name': 'mouth is closed (jawOpen)', 'range': [0, 0.3]},
        ],
        'annotatedFile': 'annotated.png',
        'figureFile': 'blendshapes.png',
    }],
}

# woman_hands.jpg (640x960): a woman holds one hand over her forehead, fingers
# pointing right, and stretches the other towards the camera, fingers pointing
# left (blurred, at the left edge). Hand landmarks: 0 wrist, 12 middle fingertip.
# Which hand is the left one is not asserted.
_TWO_HANDS = {
    'type': 'landmarks',
    'runs': [{
        'key': 'woman_hands.jpg',
        'groups': [{
            'key': 'hands',
            'name': 'hand',
            'landmarkCount': 21,
            'instances': [
                {'name': 'hand over the forehead', 'points': {
                    'wrist': _near(0.45, 0.41, 0.08, 0), 'middle fingertip': _near(0.735, 0.39, 0.06, 12)}},
                {'name': 'hand stretched out', 'points': {
                    'wrist': _near(0.41, 0.70, 0.08, 0), 'middle fingertip': _near(0.02, 0.735, 0.06, 12)}},
            ],
        }],
        'annotatedFile': 'annotated.png',
    }],
}

# pose.jpg (1000x667): one man on a beach in a wide lunge (warrior pose), chest
# towards the camera and head turned to the left of the image, arms stretched
# out sideways at shoulder height. His left side is on the right of the image.
# Pose landmarks: 0 nose, 11/12 left/right shoulder, 15/16 left/right wrist,
# 27/28 left/right ankle. He is small in the frame: roughly 7% of it.
_WARRIOR_POSE = {
    'type': 'landmarks',
    'runs': [{
        'key': 'pose.jpg',
        'groups': [{
            'key': 'poses',
            'name': 'pose',
            'landmarkCount': 33,
            'instances': [{'points': {
                'nose': _near(0.458, 0.43, 0.05, 0),
                'left shoulder': _near(0.545, 0.48, 0.06, 11),
                'right shoulder': _near(0.455, 0.49, 0.06, 12),
                'left wrist': _near(0.70, 0.47, 0.06, 15),
                'right wrist': _near(0.30, 0.465, 0.06, 16),
                'left ankle': _near(0.705, 0.92, 0.06, 27),
                'right ankle': _near(0.35, 0.915, 0.06, 28),
            }}],
        }],
        'mask': {'name': 'segmentation mask of the person', 'fraction': [0.03, 0.12],
                 'centroidWithin': {'minX': 0.4, 'maxX': 0.6, 'minY': 0.5, 'maxY': 0.8},
                 'fileName': 'mask.png'},
        'annotatedFile': 'annotated.png',
    }],
}

# The same photo as the Face Landmarker. Her right hand is raised in front of
# her chest and holds a pair of glasses; her left arm lies across her waist with
# the hand tucked behind her right elbow, mostly hidden, so the left hand is not
# asserted. She fills a little over half of the frame.
#
# The photo ends at her hips. Pose landmarks 0 to 24 are the head, arms and
# hips; 25 to 32 are the knees, ankles and feet, which are not in the photo. A
# correct model places those below the bottom edge, where her legs would be, so
# only landmarks 0 to 24 have to lie in the frame.
_BUSINESS_PERSON_HOLISTIC = {
    'type': 'landmarks',
    'runs': [{
        'key': 'business-person.png',
        'groups': [
            {'key': 'pose', 'name': 'pose', 'landmarkCount': 33, 'inFrame': list(range(25)),
             'inFrameName': 'landmarks from the head to the hips', 'instances': [{'points': {
                'nose': _near(*_BUSINESS_PERSON_NOSE, 0.06, 0),
                'left shoulder': _near(0.76, 0.48, 0.08, 11),
                'right shoulder': _near(0.20, 0.47, 0.08, 12),
            }}]},
            {'key': 'face', 'name': 'face', 'landmarkCount': 478, 'instances': [{'points': {
                'nose tip': _near(*_BUSINESS_PERSON_NOSE, 0.05, 1)}}]},
            {'key': 'rightHand', 'name': 'right hand', 'landmarkCount': 21, 'instances': [{'points': {
                'wrist': _near(0.54, 0.59, 0.08, 0)}}]},
        ],
        'equals': [{'key': 'blendshapeCount', 'name': 'face blendshapes', 'value': 52}],
        'mask': {'name': 'segmentation mask of the person', 'fraction': [0.4, 0.7],
                 'centroidWithin': {'minX': 0.35, 'maxX': 0.65, 'minY': 0.45, 'maxY': 0.8},
                 'fileName': 'mask.png'},
        'annotatedFile': 'annotated.png',
        'figureFile': 'blendshapes.png',
    }],
}


def get_expectation(case):
  """The expectation for a case, or None when none is defined yet."""
  task = case['task']
  mode = case.get('mode', 'image')
  model_id = case.get('modelId', '')

  if task == 'object_detector':
    return _CAT_AND_DOG
  if task == 'face_detector':
    return _PORTRAIT_FACE
  if task == 'face_landmarker':
    return _BUSINESS_PERSON_FACE
  if task == 'hand_landmarker':
    return _TWO_HANDS
  if task == 'pose_landmarker':
    return _WARRIOR_POSE
  if task == 'holistic_landmarker':
    return _BUSINESS_PERSON_HOLISTIC

  if task == 'image_segmenter':
    plan = IMAGE_SEGMENTER_PLANS[model_id]
    return {
        'type': 'segmentation',
        'labelCount': plan['labelCount'],
        'classes': plan['classes'],
        'maxOther': plan['maxOther'],
        'confidence': plan['confidence'] if mode == 'image-confidence' else None,
    }

  if task == 'image_classifier':
    return {'type': 'classification', 'maxResults': 4, 'runs': [
        {'key': 'classifier_burger.jpg', 'labels': ['cheeseburger'], 'minScore': 0.3},
        {'key': 'classifier_cat.jpg', 'labels': TABBY_CAT_LABELS, 'minScore': 0.15, 'minGroupScore': 0.4},
    ]}

  if task == 'gesture_recognizer':
    return {'type': 'landmarks', 'figureFile': 'annotated.png', 'runs': [
        {'key': g['asset'], 'groups': [{
            'key': 'hands', 'name': 'hand', 'landmarkCount': 21,
            'instances': [{'points': g['points'], 'fields': {'gesture': g['gesture']}, 'minScore': 0.5}],
        }]} for g in GESTURE_IMAGES]}

  if task == 'interactive_segmenter':
    if mode == 'negative':
      return {'type': 'selection', 'runs': INTERACTIVE_NEGATIVE_RUNS}
    return {'type': 'selection', 'runs': INTERACTIVE_SEGMENTER_CLICKS}

  if task == 'image_embedder':
    return {'type': 'similarity', 'runs': [
        {'key': 'same photo, cropped', 'range': [0.8, 1.0001]},
        {'key': 'the same photo twice', 'range': [0.99, 1.0001]},
        {'key': 'a burger and a cat', 'range': [-1, 0.5]},
    ], 'ordered': [{'higher': 'same photo, cropped', 'lower': 'a burger and a cat', 'by': 0.3}]}

  if task == 'audio_classifier':
    return {'type': 'audioClassification', 'label': 'Speech', 'minScore': 0.5, 'fullWindows': 4,
            'results': 5, 'windowMs': 975, 'maxResults': 4, 'stream': mode == 'stream'}

  if task == 'audio_transcriber':
    return {'type': 'transcript', 'reference': SPEECH_TRANSCRIPT, 'maxWordErrorRate': 0.2}

  if task == 'text_to_speech':
    return {'type': 'speech', 'text': TTS_SENTENCE, 'stream': mode == 'stream',
            'durationSec': [2.5, 12], 'sampleRateHz': [8000, 48000], 'minRms': 0.005, 'maxPeak': 1.0,
            'maxCharErrorRate': 0.2}

  if task == 'universal_embedder':
    plan = UNIVERSAL_EMBEDDER_PLANS[mode]
    return {'type': 'similarity', 'runs': [{'key': run['key'], 'range': run['range']} for run in plan['runs']],
            'ordered': plan['ordered']}

  if task == 'semantic_retriever':
    return {'type': 'retrieval', 'limit': RETRIEVER_LIMIT,
            'records': RETRIEVER_PHOTOS + [f'document {i + 1}' for i in range(len(RETRIEVER_DOCUMENTS))],
            'runs': [{'key': item['query'], 'top': item['top']} for item in RETRIEVER_QUERIES]}

  if task == 'decision_maker':
    return {'type': 'decisions', 'departments': ['bug', 'billing', 'feature_request'], 'levels': 5,
            'runs': [{'key': t['key'], 'urgent': t['urgent'], 'department': t['department']}
                     for t in DECISION_TICKETS],
            'severityOrder': [{'higher': 'outage ticket', 'lower': 'billing ticket', 'by': 1.0},
                              {'higher': 'outage ticket', 'lower': 'feature request', 'by': 1.0}]}

  if task == 'text_classifier':
    if model_id == 'average_word_classifier':
      return {'type': 'textLabels', 'runs': [
          {'key': f'sentence {i + 1}', 'text': item['text'], 'label': item['label'], 'minScore': 0.6}
          for i, item in enumerate(AVERAGE_WORD_INPUTS)]}
    return {'type': 'textLabels', 'runs': [
        {'key': f'sentence {i + 1}', 'text': item['text'], 'label': item['label'], 'minScore': 0.7}
        for i, item in enumerate(TEXT_CLASSIFIER_INPUTS)]}

  if task == 'language_detector':
    return {'type': 'textLabels', 'runs': [
        {'key': f'sentence {i + 1}', 'text': item['text'], 'label': item['label'],
         'match': item.get('match', 'top'), 'minScore': item.get('minScore', 0.5)}
        for i, item in enumerate(LANGUAGE_DETECTOR_INPUTS)]}

  if task == 'text_embedder':
    if model_id == 'bert_embedder':
      # The BERT embedder of the notebook does not order sentences by meaning: on
      # release 1.2.3 an unrelated pair of sentences scored higher (0.99) than a
      # related one (0.97). So no ordering by meaning is asserted, and the
      # similarity is compared with a BASELINE measured on release 1.2.3 (CPU).
      return {'type': 'similarity', 'runs': [
          {'key': 'the two sentences of the notebook', 'range': [-1, 0.99],
           'baseline': {'value': 0.9191, 'tolerance': 0.03, 'release': '1.2.3'}},
          {'key': 'the same sentence twice', 'range': [0.99, 1.0001]},
      ]}
    return {
        'type': 'similarity',
        'runs': [{'key': key, 'range': rng} for key, _, _, rng in TEXT_EMBEDDER_SEMANTIC_PAIRS],
        'ordered': [{'higher': 'related sentences', 'lower': 'unrelated sentences', 'by': 0.15}],
    }

  if task == 'text_proofreader':
    return {'type': 'proofreading', 'runs': [
        dict(item, key=f'text {i + 1}') for i, item in enumerate(PROOFREADER_INPUTS)]}

  if task == 'text_summarizer':
    common = {'type': 'summary', 'text': SUMMARIZER_TEXT, 'mentionsAll': ['Brussels'],
              'mentionsAny': ['Schuman', 'European', 'EU']}
    if mode == 'keypoints':
      return dict(common, format='bullets', minBullets=2, maxLengthRatio=1.0)
    return dict(common, format='prose', maxLengthRatio=0.5)

  return None


def _image(asset, label):
  return {'kind': 'image', 'asset': asset, 'label': label}


def _text(value, label):
  return {'kind': 'text', 'value': value, 'label': label}


def describe_case(case):
  """A plain-language description of a case and its inputs, for the report."""
  setup = (f"Runs {case['sample']} with model \"{case['modelId']}\" in mode "
           f"\"{case['mode']}\" on the {case['delegate']} delegate.")
  task = case['task']
  mode = case['mode']
  if task == 'object_detector':
    return {'description': f'{setup} The photo shows a cat and a puppy. Both must be detected, each '
                           'with a box around it, and nothing else, and annotated.png must be written.',
            'inputs': [_image('cat_and_dog.jpg', 'A cat (left) and a puppy (right)')]}
  if task == 'face_detector':
    return {'description': f'{setup} The photo shows one man facing the camera. His face must be found, '
                           'with the eyes and the nose where they are, and annotated.png must be written.',
            'inputs': [_image('portrait.jpg', 'A man facing the camera')]}
  if task == 'face_landmarker':
    return {'description': f'{setup} The photo shows one woman facing the camera. The 478-point face mesh '
                           'must lie on her face, with 52 blendshapes that say her eyes are open and her '
                           'mouth is closed, one transformation matrix, and valid annotated/chart PNGs.',
            'inputs': [_image('business-person.png', 'A woman facing the camera')]}
  if task == 'hand_landmarker':
    return {'description': f'{setup} The photo shows a woman with one hand over her forehead and the other '
                           'stretched towards the camera. Both hands must be found, each with 21 landmarks '
                           'on the hand, and annotated.png must be written.',
            'inputs': [_image('woman_hands.jpg', 'A woman showing both hands')]}
  if task == 'pose_landmarker':
    return {'description': f'{setup} The photo shows a man in a wide lunge with his arms stretched out. '
                           'His 33 pose landmarks must lie on his body, the segmentation mask must cover '
                           'him and nothing else, and both annotated.png and mask.png must match.',
            'inputs': [_image('pose.jpg', 'A man in a warrior pose')]}
  if task == 'holistic_landmarker':
    return {'description': f'{setup} The photo shows a woman from the waist up, her right hand raised and '
                           'holding glasses. Her pose, her face mesh and her right hand must be found where '
                           'they are, with 52 face blendshapes, a segmentation mask, and three valid PNGs.',
            'inputs': [_image('business-person.png', 'A woman holding glasses in her right hand')]}
  if task == 'image_classifier':
    return {'description': f'{setup} Two photos are classified. The burger must be labelled a cheeseburger, '
                           'the cat one of the ImageNet classes for a striped cat, and annotated.png written.',
            'inputs': [_image('classifier_burger.jpg', 'A cheeseburger'), _image('classifier_cat.jpg', 'A tabby cat')]}
  if task == 'gesture_recognizer':
    return {'description': f'{setup} Four photos, each of one hand making a gesture. Every gesture must be '
                           'recognized, with the 21 hand landmarks on the hand, and annotated.png written.',
            'inputs': [_image(g['asset'], f"Expected: {g['gesture']}") for g in GESTURE_IMAGES]}
  if task == 'interactive_segmenter':
    if mode == 'negative':
      detail = ('A positive click on the corgi chest is paired with a negative stroke across its head: '
                'the body must stay selected while the ear and face are excluded.')
    elif mode == 'lasso':
      detail = ('A lasso box is drawn around the corgi and around the poodle in turn; each time the mask '
                'and overlay PNG must select that animal and none of the others.')
    else:
      detail = ('A positive click is placed on the corgi and on the poodle in turn; each time the mask '
                'and overlay PNG must select that animal and none of the others.')
    return {'description': f'{setup} {detail}',
            'inputs': [_image('cats_and_dogs.jpg', 'From the left: a cat, a poodle, a corgi, a cat')]}
  if task == 'image_segmenter':
    plan = IMAGE_SEGMENTER_PLANS[case['modelId']]
    classes = ', '.join(c['label'] for c in plan['classes'])
    extra = (f" Confidence mask \"{plan['confidence']['label']}\" is also verified."
             if mode == 'image-confidence' else '')
    return {'description': f'{setup} The category mask must contain {classes} in the share of the photo '
                           f'each occupies, and both mask.png and blurred_background.png must match.{extra}',
            'inputs': [_image(plan['asset'], plan['photoLabel'])]}
  if task == 'image_embedder':
    return {'description': f'{setup} Three pairs of photos are compared. A photo and a crop of it must be '
                           'very similar, a photo and itself identical, and a burger and a cat dissimilar.',
            'inputs': [_image('burger.jpg', 'A burger'), _image('burger_crop.jpg', 'The same photo, cropped'),
                       _image('classifier_cat.jpg', 'A cat')]}
  if task == 'audio_classifier':
    how = 'asynchronously in AUDIO_STREAM mode (--stream)' if mode == 'stream' else 'in one call'
    return {'description': f'{setup} The 4.27 s speech clip is classified {how}. Each of its four full '
                           'windows must be labelled Speech.',
            'inputs': [{'kind': 'audio', 'asset': 'speech_16000_hz_mono.wav', 'label': 'Speech clip (4.27 s)'}]}
  if task == 'audio_transcriber':
    how = 'chunk by chunk (--stream)' if mode == 'stream' else 'in one call'
    return {'description': f'{setup} The 4.27 s speech clip is transcribed {how}. The transcript must '
                           'match what is said.',
            'inputs': [{'kind': 'audio', 'asset': 'speech_16000_hz_mono.wav', 'label': 'Speech clip (4.27 s)'},
                       _text(SPEECH_TRANSCRIPT, 'Reference transcript')]}
  if task == 'text_to_speech':
    how = 'piece by piece (--stream)' if mode == 'stream' else 'in one piece'
    return {'description': f'{setup} The sentence is synthesized {how}. Both the returned samples and '
                           'speech.wav on disk must be valid mono 16-bit PCM audio, and the Audio '
                           'Transcriber must hear the sentence in speech.wav.',
            'inputs': [_text(TTS_SENTENCE, 'Sentence to speak')]}
  if task == 'universal_embedder':
    plan = UNIVERSAL_EMBEDDER_PLANS[mode]
    shown = lambda value: 'photo ' + value[len('@asset:'):] if value.startswith('@asset:') else f'"{value}"'
    photos = sorted({v[len('@asset:'):] for run in plan['runs'] for v in run['inputs'] if v.startswith('@asset:')})
    return {'description': f'{setup} Each pair of inputs is compared. Identical inputs must score about 1, and '
                           'inputs about the same subject must score higher than inputs about different ones.',
            'inputs': [_text(f"{shown(run['inputs'][0])}  |  {shown(run['inputs'][1])}", run['key'])
                       for run in plan['runs']] + [_image(name, name) for name in photos]}
  if task == 'semantic_retriever':
    return {'description': f'{setup} Five photos and two short texts are indexed, then each query is searched. '
                           'The best match must be the record the query describes.',
            'inputs': [_text(item['query'], f"Expected best match: {item['top']}") for item in RETRIEVER_QUERIES] +
                      [_image(name, name) for name in RETRIEVER_PHOTOS] +
                      [_text(document, f'document {i + 1}') for i, document in enumerate(RETRIEVER_DOCUMENTS)]}
  if task == 'decision_maker':
    how = 'via evaluate_boolean/choice/score' if mode == 'individual' else 'via evaluate_schema'
    return {'description': f'{setup} Each support ticket is triaged {how}. The outage must be judged an '
                           'urgent outage, a bug and the most severe; the others not urgent, and assigned '
                           'to their department.',
            'inputs': [_text(t['text'], f"{t['key']}: urgent={t['urgent']}, department={t['department']}")
                       for t in DECISION_TICKETS]}
  if task == 'text_classifier':
    inputs = AVERAGE_WORD_INPUTS if case['modelId'] == 'average_word_classifier' else TEXT_CLASSIFIER_INPUTS
    return {'description': f'{setup} Each sentence is classified. Its sentiment must be correct.',
            'inputs': [_text(i['text'], f"Expected: {i['label']}") for i in inputs]}
  if task == 'language_detector':
    return {'description': f'{setup} The language of each sentence is detected. It must be correct.',
            'inputs': [_text(i['text'], f"Expected: {i['label']}") for i in LANGUAGE_DETECTOR_INPUTS]}
  if task == 'text_embedder':
    if case['modelId'] == 'bert_embedder':
      return {'description': f'{setup} The two sentences of the notebook are compared, and one of them with '
                             'itself. Identical sentences must score 1 and different ones less, within the '
                             'baseline measured on release 1.2.3.',
              'inputs': [_text(TEXT_EMBEDDER_FIRST, 'First sentence'), _text(TEXT_EMBEDDER_SECOND, 'Second sentence')]}
    return {'description': f'{setup} Identical, related and unrelated sentence pairs are compared. Identical '
                           'sentences must score about 1, and related sentences clearly higher than unrelated ones.',
            'inputs': [_text(f'{a}  |  {b}', name) for name, a, b, _ in TEXT_EMBEDDER_SEMANTIC_PAIRS]}
  if task == 'text_proofreader':
    return {'description': f'{setup} Each text is proofread. The typo of the first must be fixed, the '
                           'second must become a correct sentence, and the list of corrections must lead '
                           'from the input to the proofread text.',
            'inputs': [_text(i['text'], f"Expected: {i['exact']}" if 'exact' in i else 'Several fixes are correct')
                       for i in PROOFREADER_INPUTS]}
  if task == 'text_summarizer':
    kind = 'a list of key points' if mode == 'keypoints' else 'a short paragraph'
    return {'description': f'{setup} The text is summarized as {kind}. The summary must be shorter than the '
                           'text and say where the station is.',
            'inputs': [_text(SUMMARIZER_TEXT, 'Text to summarize')]}
  return {'description': setup, 'inputs': []}
