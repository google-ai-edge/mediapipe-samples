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
"""The models and inputs of the validation, pinned by SHA-256.

Every file is downloaded once into the asset directory and checked against its
checksum before each run, so that two runs validate exactly the same inputs. A
file whose content changed upstream is an error: review the new file, then
update its entry here (and the expectations that depend on it).
"""

import hashlib
import os
import urllib.request

_MODELS = 'https://storage.googleapis.com/mediapipe-models/'
_ASSETS = 'https://storage.googleapis.com/mediapipe-assets/'
_TASKS = 'https://storage.googleapis.com/mediapipe-tasks/'
# Hugging Face repositories are mutable, so these URLs name a commit.
_PARAKEET = 'https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3/resolve/cd95f7bb97fd0757ab1bd6d5eff513cfcd7a2a10/'
_KOKORO = 'https://huggingface.co/litert-community/Kokoro-82M/resolve/bf1c2405642aa310bd210205c22d3ae1abc253fb/'
_POCKET_TTS = 'https://huggingface.co/litert-community/pocket-tts/resolve/03f949ec418ccd4d153008f7ef9c58c754cc0616/'
_PHONON = 'https://huggingface.co/litert-community/phonon-31m-en/resolve/63a6170895a34600560f80dce4c70f7c90e32956/'
_EG2_270M = 'https://huggingface.co/litert-community/embeddinggemma-2-text-270m-litert-lm/resolve/9be6e8b90982095dc05c2bd162e4b954ee4dbac7/'
_EG2_440M = 'https://huggingface.co/litert-community/embeddinggemma-2-text-vision-440m-litert-lm/resolve/538e6574db281b4fe17993a3212f10c73bd678c6/'

ASSETS = {
    # --- Models: the ones the notebooks download, unless noted. ---
    'yamnet.tflite': {
        'url': _MODELS + 'audio_classifier/yamnet/float32/1/yamnet.tflite',
        'sha256': '4d8b4a53282dc83ef04e3e7dbc4fbc98082e34e44ed798e16c3a0cdd4c584faf',
        'bytes': 4126810,
    },
    'blaze_face_short_range.tflite': {
        'url': _MODELS + 'face_detector/blaze_face_short_range/float16/1/blaze_face_short_range.tflite',
        'sha256': 'b4578f35940bf5a1a655214a1cce5cab13eba73c1297cd78e1a04c2380b0152f',
        'bytes': 229746,
    },
    'face_landmarker.task': {
        'url': _MODELS + 'face_landmarker/face_landmarker/float16/1/face_landmarker.task',
        'sha256': '64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff',
        'bytes': 3758596,
    },
    'gesture_recognizer.task': {
        'url': _MODELS + 'gesture_recognizer/gesture_recognizer/float16/1/gesture_recognizer.task',
        'sha256': '97952348cf6a6a4915c2ea1496b4b37ebabc50cbbf80571435643c455f2b0482',
        'bytes': 8373440,
    },
    'hand_landmarker.task': {
        'url': _MODELS + 'hand_landmarker/hand_landmarker/float16/1/hand_landmarker.task',
        'sha256': 'fbc2a30080c3c557093b5ddfc334698132eb341044ccee322ccf8bcf3607cde1',
        'bytes': 7819105,
    },
    'holistic_landmarker.task': {
        'url': _MODELS + 'holistic_landmarker/holistic_landmarker/float16/1/holistic_landmarker.task',
        'sha256': 'e2dab61191e2dcd0a15f943d8e3ed1dce13c82dfa597b9dd39f562975a50c3f8',
        'bytes': 13683609,
    },
    'efficientnet_lite0.tflite': {
        'url': _MODELS + 'image_classifier/efficientnet_lite0/float32/1/efficientnet_lite0.tflite',
        'sha256': '6c7ab0a6e5dcbf38a8c33b960996a55a3b4300b36a018c4545801de3a3c8bde0',
        'bytes': 18582189,
    },
    'mobilenet_v3_small.tflite': {
        'url': _MODELS + 'image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite',
        'sha256': 'bbbb4c51a55a53905af1daec995ca1aae355046f8839bb8c9f5ce9271394bc40',
        'bytes': 4117670,
    },
    'deeplab_v3.tflite': {
        'url': _MODELS + 'image_segmenter/deeplab_v3/float32/1/deeplab_v3.tflite',
        'sha256': 'ff36e24d40547fe9e645e2f4e8745d1876d6e38b332d39a82f0bf0f5d1d561b3',
        'bytes': 2780176,
    },
    'interactive_segmentation.task': {
        'url': _MODELS + 'interactive_segmenter_v2/magic_touch/int8/1/interactive_segmentation.task',
        'sha256': '38431bc66b883404e8397f74c3579404315b9b52b04a46c6346fe906a7309b03',
        'bytes': 30525312,
    },
    # The URL of this model is not versioned ("latest").
    'language_detector.tflite': {
        'url': _MODELS + 'language_detector/language_detector/float32/latest/language_detector.tflite',
        'sha256': '7db4f23dfe1ad8966b050b419a865da451143fd43eb6b606a256aadeeb1e5417',
        'bytes': 315294,
    },
    'efficientdet_lite0_int8.tflite': {
        'url': _MODELS + 'object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite',
        'sha256': '0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb',
        'bytes': 4602795,
    },
    # Not used by the notebook; see the object_detector entry in cases.py.
    'efficientdet_lite0_float32.tflite': {
        'url': _MODELS + 'object_detector/efficientdet_lite0/float32/1/efficientdet_lite0.tflite',
        'sha256': '40338edf5ec70d43e318b0a716a84d4564cd1802759a7a07170c7e43796dbf58',
        'bytes': 13836895,
    },
    'pose_landmarker_heavy.task': {
        'url': _MODELS + 'pose_landmarker/pose_landmarker_heavy/float16/1/pose_landmarker_heavy.task',
        'sha256': '64437af838a65d18e5ba7a0d39b465540069bc8aae8308de3e318aad31fcbc7b',
        'bytes': 30664242,
    },
    'bert_classifier.tflite': {
        'url': _MODELS + 'text_classifier/bert_classifier/float32/1/bert_classifier.tflite',
        'sha256': '9b45012ab143d88d61e10ea501d6c8763f7202b86fa987711519d89bfa2a88b1',
        'bytes': 25707538,
    },
    'bert_embedder.tflite': {
        'url': _MODELS + 'text_embedder/bert_embedder/float32/1/bert_embedder.tflite',
        'sha256': '02ae6279faf86c2cd4ff18f61876c878bcc0b572b472f0678897a184c4ac7ef6',
        'bytes': 26142232,
    },
    # The URL of this model is not versioned ("latest").
    'proofread_quant_200m.litertlm': {
        'url': _MODELS + 'text_proofreader/gemma_200m/latest/proofread_quant_200m.litertlm',
        'sha256': '2caa317d5a6f951af6e437edce3bb3a9fdedc85a7a8c2a8fcaec96318d7708cc',
        'bytes': 117587968,
    },
    # The URL of this model is not versioned ("latest").
    'summarization_quant_200m_2modes.litertlm': {
        'url': _MODELS + 'text_summarizer/gemma_200m/latest/summarization_quant_200m_2modes.litertlm',
        'sha256': '8b2d4ef09236adb9ead3127325526ba1aa5a59feb7c5de2d3f5958f27479de59',
        'bytes': 117587968,
    },

    # --- Speech models (large). The sample of each task downloads the first. ---
    'parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm': {
        'url': _PARAKEET + 'parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm',
        'sha256': '66c5a74cf6c2c1d2b990901f6cece5bb53e3214dbceafa0989900a6f0eeb3504',
        'bytes': 614588752,
    },
    'kokoro_int8_fp16.litertlm': {
        'url': _KOKORO + 'kokoro_int8_fp16.litertlm',
        'sha256': 'e7b931ee5d4b7104012a838c9eee240d19ea69cb3ca269041a3274f83dbb7fa1',
        'bytes': 294783298,
    },
    'kokoro_int8_gpu_fp16.litertlm': {
        'url': _KOKORO + 'kokoro_int8_gpu_fp16.litertlm',
        'sha256': '1ef178b127ec48c737daf17eabb4cea5f6b3ffbc510aced270fe5c267bbeac52',
        'bytes': 294750530,
    },
    'pocket_tts_en_int8.litertlm': {
        'url': _POCKET_TTS + 'pocket_tts_en_int8.litertlm',
        'sha256': '465e0083cd61188772aed76160b07af0dcf59057497d7659f43f3f2b13a174a1',
        'bytes': 162766856,
    },
    'phonon_int8.litertlm': {
        'url': _PHONON + 'phonon_int8.litertlm',
        'sha256': 'b33a9a0d65acec36762db38a2bff6d899f4aab34b196bb56d89fab5665eabebe',
        'bytes': 87205888,
    },

    # --- Embedding models (large), for the retrieval and decision tasks. ---
    'embeddinggemma-2-text-270m.litertlm': {
        'url': _EG2_270M + 'embeddinggemma-2-text-270m.litertlm',
        'sha256': '2d079ee2f6f066b1f368e8d7c819f55214eaef1d0513b312321901f30ab286fb',
        'bytes': 164626432,
    },
    'embeddinggemma-2-text-vision-440m.litertlm': {
        'url': _EG2_440M + 'embeddinggemma-2-text-vision-440m.litertlm',
        'sha256': '92dcbea108899e5d6e30d919b0744f90d9967e80c67a4ab5503ac16d54f62eb0',
        'bytes': 387710976,
    },

    # --- Inputs: the ones the notebooks download, unless noted. ---
    'speech_16000_hz_mono.wav': {
        'url': _ASSETS + 'speech_16000_hz_mono.wav',
        'sha256': '71caf50b8757d6ab9cad5eae4d36669d3c20c225a51660afd7fe0dc44cdb74f6',
        'bytes': 136798,
    },
    # The face detector notebook downloads its photo from i.imgur.com, and the
    # pose landmarker notebook from cdn.pixabay.com. Neither host serves
    # scripts, so the samples and the validation use these MediaPipe test
    # images instead.
    'portrait.jpg': {
        'url': _ASSETS + 'portrait.jpg',
        'sha256': 'a6f11efaa834706db23f275b6115058fa87fc7f14362681e6abe14e82749de3e',
        'bytes': 176561,
    },
    'pose.jpg': {
        'url': _ASSETS + 'pose.jpg',
        'sha256': 'c8a830ed683c0276d713dd5aeda28f415f10cd6291972084a40d0d8b934ed62b',
        'bytes': 44100,
    },
    'business-person.png': {
        'url': _ASSETS + 'business-person.png',
        'sha256': '1f61cf0603cef77ffca4e24848ddf8290b5651d03b957e93b742c9ef963b5c11',
        'bytes': 1094717,
    },
    'thumbs_down.jpg': {
        'url': _TASKS + 'gesture_recognizer/thumbs_down.jpg',
        'sha256': '080b589bf3b91ba10cc6c03645be3b5b491a8ca8c8f7d65b5f32c563ae266af9',
        'bytes': 46651,
    },
    'victory.jpg': {
        'url': _TASKS + 'gesture_recognizer/victory.jpg',
        'sha256': '6ac265f3ace6a6c4ac4a9079b63fcce4ab6517272afb1e430857f55ef324fde6',
        'bytes': 19125,
    },
    'thumbs_up.jpg': {
        'url': _TASKS + 'gesture_recognizer/thumbs_up.jpg',
        'sha256': '2aee0e3a69ba5f0d3287597e61d265f4f3ac2a44ccec198dddd2639b0c8ef7ba',
        'bytes': 28894,
    },
    'pointing_up.jpg': {
        'url': _TASKS + 'gesture_recognizer/pointing_up.jpg',
        'sha256': 'f4a701316b63dd8fa56e622f2b3042766369ccc189f0d89513f803cd985b993b',
        'bytes': 36757,
    },
    'woman_hands.jpg': {
        'url': _TASKS + 'hand_landmarker/woman_hands.jpg',
        'sha256': '70cbeb38e198c9862202e0979c21a99b40ca980d3e7b250176c85b1636a40f12',
        'bytes': 67719,
    },
    # Two different photos are published as "burger.jpg"; this is the one of
    # the image classifier notebook.
    'classifier_burger.jpg': {
        'url': _TASKS + 'image_classifier/burger.jpg',
        'sha256': '08151ebb48f30a6cfbea02168ec0f3c0f1694d64c8d0f75ca08a63a89302853f',
        'bytes': 92220,
    },
    'classifier_cat.jpg': {
        'url': _TASKS + 'image_classifier/cat.jpg',
        'sha256': 'a83aa74a3d1d9bbc8bf92065e6e4d1ba217438a9f4a95f35287b2e8316e83859',
        'bytes': 69569,
    },
    'burger.jpg': {
        'url': _ASSETS + 'burger.jpg',
        'sha256': '97c15bbbf3cf3615063b1031c85d669de55839f59262bbe145d15ca75b36ecbf',
        'bytes': 30300,
    },
    'burger_crop.jpg': {
        'url': _ASSETS + 'burger_crop.jpg',
        'sha256': '8f58de573f0bf59a49c3d86cfabb9ad4061481f574aa049177e8da3963dddc50',
        'bytes': 26500,
    },
    'segmentation_input_rotation0.jpg': {
        'url': _ASSETS + 'segmentation_input_rotation0.jpg',
        'sha256': '5bf58d8af1f1c33224f3f3bc0ce451c8daf0739cc15a86d59d8c3bf2879afb97',
        'bytes': 16741,
    },
    'cats_and_dogs.jpg': {
        'url': _ASSETS + 'cats_and_dogs.jpg',
        'sha256': 'a2eaa7ad3a1aae4e623dd362a5f737e8a88d122597ecd1a02b3e1444db56df9c',
        'bytes': 54472,
    },
    'cat_and_dog.jpg': {
        'url': _TASKS + 'object_detector/cat_and_dog.jpg',
        'sha256': 'cfa90c34bb93021165e48bd22cfc20dbbb0440ff638a54878939bf30d362e824',
        'bytes': 69041,
    },
}


class AssetError(Exception):
  """An asset could not be downloaded, or is not the pinned file."""


def sha256_of(path):
  digest = hashlib.sha256()
  with open(path, 'rb') as f:
    for chunk in iter(lambda: f.read(1 << 20), b''):
      digest.update(chunk)
  return digest.hexdigest()


def _download(url, path):
  partial = path + '.download'
  try:
    urllib.request.urlretrieve(url, partial)
  except Exception as error:  # pylint: disable=broad-except
    if os.path.exists(partial):
      os.remove(partial)
    raise AssetError(f'could not download {url}: {error}') from error
  os.replace(partial, path)


def remove_derived_files(directory):
  """Deletes what tasks wrote next to the pinned files, and returns the names.

  Some tasks compile a model on first use and store the result next to it (for
  example `<model>.xnnpack_cache`). Such a file must not carry over to another
  case, or to a run with another MediaPipe version. Files kept for review
  (`.unexpected`) stay.
  """
  removed = []
  for name in sorted(os.listdir(directory)):
    path = os.path.join(directory, name)
    if name in ASSETS or name.endswith('.unexpected') or not os.path.isfile(path):
      continue
    os.remove(path)
    removed.append(name)
  return removed


def ensure_asset(name, directory, log=None):
  """Returns the path of a pinned asset, downloading it if it is missing.

  Raises:
    AssetError: if the file cannot be downloaded, or its content is not the
      pinned content.
  """
  if name not in ASSETS:
    raise AssetError(f'{name} is not listed in assets.ASSETS')
  spec = ASSETS[name]
  path = os.path.join(directory, name)
  os.makedirs(directory, exist_ok=True)
  if os.path.exists(path) and sha256_of(path) != spec['sha256']:
    # A file from an earlier state of the manifest, or a damaged one.
    os.remove(path)
  if not os.path.exists(path):
    if log:
      log(f"Downloading {name} ({spec['bytes'] / 1e6:.1f} MB)")
    _download(spec['url'], path)
    actual = sha256_of(path)
    if actual != spec['sha256']:
      os.replace(path, path + '.unexpected')
      raise AssetError(
          f"{name} is not the pinned file: {spec['url']} now has SHA-256 "
          f"{actual}, expected {spec['sha256']}. The file changed upstream. "
          f"It was kept as {path}.unexpected for review.")
  return path
