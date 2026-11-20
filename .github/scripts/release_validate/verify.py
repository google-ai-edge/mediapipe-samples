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
"""Output verification.

Pure functions that compare what a sample returned and wrote to disk (as
observed by worker.py) with an expectation from expectations.py. Nothing here
imports MediaPipe, so every verifier is unit-tested by self_test.py with both a
correct and an incorrect output.
"""

import itertools
import math
import re

VERIFIED = 'VERIFIED'
FAILED = 'FAILED'
UNVERIFIED = 'UNVERIFIED'


def _fmt(value):
  if isinstance(value, bool):
    return 'yes' if value else 'no'
  if isinstance(value, float):
    return f'{value:.4f}'
  if isinstance(value, (list, tuple, dict)):
    return repr(value)
  return str(value)


def _check(name, passed, expected, actual):
  return {'name': name, 'pass': bool(passed), 'expected': _fmt(expected), 'actual': _fmt(actual)}


def _is_number(value):
  return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)


def _in_range(value, low, high):
  return _is_number(value) and low <= value <= high


def box_iou(a, b):
  """Intersection over union of two `{x, y, width, height}` boxes."""
  if not a or not b:
    return 0.0
  x1, y1 = max(a['x'], b['x']), max(a['y'], b['y'])
  x2 = min(a['x'] + a['width'], b['x'] + b['width'])
  y2 = min(a['y'] + a['height'], b['y'] + b['height'])
  inter = max(0, x2 - x1) * max(0, y2 - y1)
  union = a['width'] * a['height'] + b['width'] * b['height'] - inter
  return inter / union if union > 0 else 0.0


def _distance(point, target):
  return math.hypot(point[0] - target['x'], point[1] - target['y'])


def _check_near(name, point, target):
  """Checks that a point lies within `target.tolerance` of where it really is."""
  ok = (isinstance(point, (list, tuple)) and len(point) >= 2 and _is_number(point[0]) and _is_number(point[1])
        and _distance(point, target) <= target['tolerance'])
  actual = f'({point[0]:.3f}, {point[1]:.3f})' if isinstance(point, (list, tuple)) and len(point) >= 2 else 'none'
  return _check(name, ok, f"within {target['tolerance']} of ({target['x']:.3f}, {target['y']:.3f})", actual)


def _check_within(name, point, box):
  ok = (isinstance(point, dict) and _in_range(point.get('x'), box['minX'], box['maxX'])
        and _in_range(point.get('y'), box['minY'], box['maxY']))
  actual = f"x {point['x']:.3f}, y {point['y']:.3f}" if isinstance(point, dict) and _is_number(point.get('x')) else 'none'
  return _check(name, ok, f"x {box['minX']}-{box['maxX']}, y {box['minY']}-{box['maxY']}", actual)


def _top(categories):
  return max(categories, key=lambda c: c['score']) if categories else None


def _runs_by_key(runs):
  return {run['key']: run.get('data') or {} for run in runs or []}


def _prefix(checks, tag):
  return [dict(c, name=f"{tag}: {c['name']}") for c in checks]


def normalize_words(text):
  """Lower-cased words of a text, without punctuation."""
  return [w for w in re.sub(r"[^\w'\s]", ' ', str(text or '').lower()).split() if w]


def _edit_distance(reference, hypothesis):
  """The number of insertions, deletions and substitutions between two sequences."""
  previous = list(range(len(hypothesis) + 1))
  for i, ref_item in enumerate(reference, 1):
    current = [i]
    for j, hyp_item in enumerate(hypothesis, 1):
      current.append(min(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + (ref_item != hyp_item)))
    previous = current
  return previous[-1]


def word_error_rate(reference, hypothesis):
  """Word-level edit distance from the reference, relative to its length."""
  ref, hyp = normalize_words(reference), normalize_words(hypothesis)
  if not ref:
    return 0.0 if not hyp else 1.0
  return _edit_distance(ref, hyp) / len(ref)


def char_error_rate(reference, hypothesis):
  """Like word_error_rate, over the letters and digits only: spaces are ignored."""
  ref, hyp = ''.join(normalize_words(reference)), ''.join(normalize_words(hypothesis))
  ref, hyp = ref.replace("'", ''), hyp.replace("'", '')
  if not ref:
    return 0.0 if not hyp else 1.0
  return _edit_distance(ref, hyp) / len(ref)


def _verify_annotated_file(info, name='annotated.png'):
  """Checks an image file on which boxes or landmarks were drawn."""
  info = info or {}
  size_ok = (info.get('exists') is True and info.get('channels') == 3
             and bool(info.get('expectedWidth'))
             and info.get('width') == info.get('expectedWidth')
             and info.get('height') == info.get('expectedHeight'))
  actual_size = (f"{info.get('width')}x{info.get('height')}x{info.get('channels')}"
                 if info.get('exists') else 'missing')
  expected_size = f"{info.get('expectedWidth')}x{info.get('expectedHeight')}x3"
  changed = info.get('changedFraction')
  return [
      _check(f'{name} was written with the size of the image', size_ok, expected_size, actual_size),
      _check(f'{name} has annotations drawn on the image', _in_range(changed, 0.0005, 0.5),
             '0.0005 to 0.5 of pixels changed', 'none' if changed is None else changed),
  ]


def _verify_figure_file(info, name):
  """Checks a rendered figure (bar chart or grid of images) written to disk."""
  info = info or {}
  ok = (info.get('exists') is True and (info.get('width') or 0) >= 400
        and (info.get('height') or 0) >= 400 and info.get('channels') in (3, 4))
  actual = f"{info.get('width')}x{info.get('height')}x{info.get('channels')}" if info.get('exists') else 'missing'
  ink = info.get('inkFraction')
  return [
      _check(f'{name} was written as an image', ok, '>= 400x400, 3 or 4 channels', actual),
      _check(f'{name} is not blank', _in_range(ink, 0.02, 0.95), '0.02 to 0.95 non-white pixels',
             'none' if ink is None else ink),
  ]


def _verify_grayscale_mask_file(info, width, height, name='mask.png'):
  """Checks a 3-channel grayscale PNG written from a [0, 1] confidence mask."""
  info = info or {}
  ok = (info.get('exists') is True and width > 0 and info.get('width') == width and info.get('height') == height
        and info.get('channels') == 3 and info.get('grayscale') is True)
  actual = (f"{info.get('width')}x{info.get('height')}x{info.get('channels')}, grayscale={info.get('grayscale')}"
            if info.get('exists') else 'missing')
  diff = info.get('maxDiff')
  return [
      _check(f'{name} was written as a grayscale mask of {width}x{height}', ok, f'{width}x{height}x3, grayscale=True',
             actual),
      _check(f'{name} matches the returned mask', _in_range(diff, 0, 0.005), '<= 0.005 max diff',
             'none' if diff is None else diff),
  ]


def _verify_mask(mask, expected):
  """A confidence mask: valid values, the share and place of what it covers, and its PNG file."""
  name = expected.get('name', 'mask')
  if not mask:
    return [_check(f'{name} is returned', False, 'a mask', 'none')]
  width, height = mask.get('width') or 0, mask.get('height') or 0
  if 'imageAspect' in mask:
    aspect = width / height if height else float('nan')
    size_check = _check(f'{name} has the proportions of the image',
                        _is_number(aspect) and abs(aspect / mask['imageAspect'] - 1) <= 0.05,
                        f"width / height = {mask['imageAspect']:.3f}", aspect)
  else:
    size_check = _check(f'{name} has the size of the image',
                        width > 0 and width == mask.get('imageWidth') and height == mask.get('imageHeight'),
                        f"{mask.get('imageWidth')}x{mask.get('imageHeight')}", f'{width}x{height}')
  checks = [
      size_check,
      _check(f'{name} has one channel', mask.get('channels') == 1, 1, mask.get('channels', 'none')),
      _check(f'{name} values are finite', mask.get('nonFinite', 0) == 0, 0, mask.get('nonFinite', 'none')),
      _check(f'{name} holds confidences from 0 to 1',
             _in_range(mask.get('min'), 0, 1) and _in_range(mask.get('max'), 0, 1), '0 to 1',
             f"{_fmt(mask.get('min'))} to {_fmt(mask.get('max'))}"),
  ]
  low, high = expected['fraction']
  checks.append(_check(f'{name}: share of the image', _in_range(mask.get('fraction'), low, high),
                       f'{low} to {high}', mask.get('fraction')))
  if 'centroidWithin' in expected:
    checks.append(_check_within(f'{name}: centre', mask.get('centroid'), expected['centroidWithin']))
  if expected.get('fileName'):
    checks += _verify_grayscale_mask_file(mask.get('file'), width, height, expected['fileName'])
  return checks


def _match_instances(expected_instances, observed):
  """Pairs each expected instance with one observed instance.

  Results list their instances (hands, poses) in no particular order, so the
  pairing that puts the landmarks closest to where they are expected is used.

  Returns a list parallel to `expected_instances`, holding the observed
  instance of each, or None where there are fewer observed than expected.
  """
  observed = observed[:6]  # More than that is a wrong count, reported as such.
  count = min(len(expected_instances), len(observed))

  def cost(expected, instance):
    landmarks = instance.get('landmarks') or []
    total = 0.0
    for target in expected.get('points', {}).values():
      index = target['index']
      total += _distance(landmarks[index], target) if index < len(landmarks) else 10.0
    return total

  best, best_cost = {}, None
  for expected_ids in itertools.permutations(range(len(expected_instances)), count):
    for observed_ids in itertools.combinations(range(len(observed)), count):
      total = sum(cost(expected_instances[e], observed[o]) for e, o in zip(expected_ids, observed_ids))
      if best_cost is None or total < best_cost:
        best, best_cost = dict(zip(expected_ids, observed_ids)), total
  return [observed[best[i]] if i in best else None for i in range(len(expected_instances))]


def _verify_landmark_run(data, expected):
  checks = []
  for group in expected.get('groups', []):
    name = group['name']
    observed = [i for i in (data.get(group['key']) or []) if i and i.get('landmarks')]
    wanted = group['instances']
    checks.append(_check(f'{name}s found', len(observed) == len(wanted), len(wanted), len(observed)))
    paired = _match_instances(wanted, observed)
    for index, expected_instance in enumerate(wanted):
      instance = paired[index]
      if instance is None:
        continue
      tag = expected_instance.get('name', name)
      landmarks = instance['landmarks']
      checks.append(_check(f'{tag}: landmark count', len(landmarks) == group['landmarkCount'],
                           group['landmarkCount'], len(landmarks)))
      bad = [p for p in landmarks if not (len(p) >= 2 and _is_number(p[0]) and _is_number(p[1]))]
      checks.append(_check(f'{tag}: landmarks are finite numbers', not bad, 0, len(bad)))
      # Landmarks of what the photo shows may fall slightly outside the frame,
      # but not far. Where the photo shows only part of a body, `inFrame` names
      # the landmarks of that part: the model places the others, as it should,
      # where the rest of the body would be.
      shown = group.get('inFrame')
      good = [p for i, p in enumerate(landmarks) if p not in bad and (shown is None or i in shown)]
      if good:
        xs, ys = [p[0] for p in good], [p[1] for p in good]
        in_frame = min(xs) >= -0.25 and min(ys) >= -0.25 and max(xs) <= 1.25 and max(ys) <= 1.25
        which = 'landmarks' if shown is None else group.get('inFrameName', 'landmarks of the visible part')
        checks.append(_check(f'{tag}: {which} lie in the image', in_frame, 'within the frame',
                             f'x {min(xs):.3f}-{max(xs):.3f}, y {min(ys):.3f}-{max(ys):.3f}'))
      for point_name, target in expected_instance.get('points', {}).items():
        point = landmarks[target['index']] if target['index'] < len(landmarks) else None
        checks.append(_check_near(f'{tag}: {point_name} position', point, target))
      for field, value in expected_instance.get('fields', {}).items():
        checks.append(_check(f'{tag}: {field}', instance.get(field) == value, value, instance.get(field, 'none')))
      if 'minScore' in expected_instance:
        checks.append(_check(f'{tag}: score', _in_range(instance.get('score'), expected_instance['minScore'], 1),
                             f">= {expected_instance['minScore']}", instance.get('score', 'none')))
  for item in expected.get('equals', []):
    checks.append(_check(item.get('name', item['key']), data.get(item['key']) == item['value'], item['value'],
                         data.get(item['key'], 'none')))
  for item in expected.get('ranges', []):
    value = (data.get('values') or {}).get(item['key'])
    low, high = item['range']
    checks.append(_check(item.get('name', item['key']), _in_range(value, low, high), f'{low} to {high}',
                         'none' if value is None else value))
  if 'mask' in expected:
    checks += _verify_mask(data.get('mask'), expected['mask'])
  if expected.get('annotatedFile'):
    checks += _verify_annotated_file(data.get('annotatedFile'), expected['annotatedFile'])
  if expected.get('figureFile'):
    checks += _verify_figure_file(data.get('figureFile'), expected['figureFile'])
  return checks


def _verify_detections(runs, expectation):
  """Object detection: each object is found, confidently, where it really is."""
  data = (runs[0].get('data') or {}) if runs else {}
  detections = data.get('detections') or []
  width, height = data.get('imageWidth') or 0, data.get('imageHeight') or 0
  bad = [d for d in detections
         if not (d.get('box') and _in_range(d['box']['width'], 1, width) and _in_range(d['box']['height'], 1, height)
                 and _in_range(d.get('score'), 0, 1))]
  checks = [_check('every detection has a valid box and score', not bad, 0, len(bad))]
  for required in expectation['objects']:
    label = required['label']
    candidates = [d for d in detections if d.get('label') == label and d not in bad]
    found = ', '.join(str(d.get('label')) for d in detections) or 'nothing'
    checks.append(_check(f'detects "{label}"', bool(candidates), label, found))
    if not candidates:
      continue
    best = max(candidates, key=lambda d: d['score'])
    checks.append(_check(f'"{label}" score', best['score'] >= required['minScore'], f">= {required['minScore']}",
                         best['score']))
    # The true box is relative to the image, so it applies to any image size.
    truth = {'x': required['box']['x'] * width, 'y': required['box']['y'] * height,
             'width': required['box']['width'] * width, 'height': required['box']['height'] * height}
    iou = box_iou(best['box'], truth)
    checks.append(_check(f'"{label}" box overlaps the object', iou >= required['minIoU'],
                         f"IoU >= {required['minIoU']}", iou))
  if 'onlyLabels' in expectation:
    others = sorted({str(d.get('label')) for d in detections if d.get('label') not in expectation['onlyLabels']})
    checks.append(_check('nothing else is detected', not others, 'only ' + ', '.join(expectation['onlyLabels']),
                         ', '.join(others) or 'nothing else'))
  checks += _verify_annotated_file(data.get('annotatedFile'))
  return checks


def _verify_faces(runs, expectation):
  """Face detection: the right number of faces, with eyes and nose in place."""
  data = (runs[0].get('data') or {}) if runs else {}
  detections = data.get('detections') or []
  checks = [_check('faces found', len(detections) == expectation['count'], expectation['count'], len(detections))]
  if not detections:
    return checks
  face = max(detections, key=lambda d: d.get('score') or 0)
  checks.append(_check('face score', _in_range(face.get('score'), expectation['minScore'], 1),
                       f">= {expectation['minScore']}", face.get('score', 'none')))
  keypoints = face.get('keypoints') or []
  for keypoint in expectation['keypoints']:
    point = keypoints[keypoint['index']] if keypoint['index'] < len(keypoints) else None
    checks.append(_check_near(f"{keypoint['name']} position", point, keypoint))
  box, width, height = face.get('box'), data.get('imageWidth') or 0, data.get('imageHeight') or 0
  share = box['width'] / width if box and width else float('nan')
  low, high = expectation['boxWidth']
  checks.append(_check('face box width as a share of the image', _in_range(share, low, high), f'{low} to {high}',
                       share))
  px, py = expectation['boxContains']['x'] * width, expectation['boxContains']['y'] * height
  contains = bool(box) and box['x'] <= px <= box['x'] + box['width'] and box['y'] <= py <= box['y'] + box['height']
  checks.append(_check('face box contains the nose', contains, True, contains))
  checks += _verify_annotated_file(data.get('annotatedFile'))
  return checks


def _verify_landmarks(runs, expectation):
  """Landmark tasks: the right instances, each with its landmarks in place."""
  by_key = _runs_by_key(runs)
  checks = []
  multiple = len(expectation['runs']) > 1
  for expected in expectation['runs']:
    tag = expected['key']
    present = tag in by_key
    if multiple:
      checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      if not multiple:
        checks.append(_check('has a result', False, True, False))
      continue
    run_checks = _verify_landmark_run(by_key[tag], expected)
    checks += _prefix(run_checks, tag) if multiple else run_checks
  if expectation.get('figureFile'):
    first_data = (runs[0].get('data') or {}) if runs else {}
    checks += _verify_figure_file(first_data.get('figureFile'), expectation['figureFile'])
  return checks


def _verify_classification(runs, expectation):
  """Image classification: each photo gets one of the labels of what it shows."""
  by_key = _runs_by_key(runs)
  checks = []
  for expected in expectation['runs']:
    tag = expected['key']
    present = tag in by_key
    checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      continue
    categories = by_key[tag].get('categories') or []
    checks.append(_check(f'{tag}: category count', 1 <= len(categories) <= expectation['maxResults'],
                         f"1 to {expectation['maxResults']}", len(categories)))
    top = _top(categories)
    wanted = expected['labels']
    checks.append(_check(f'{tag}: top label', bool(top) and top['label'] in wanted, ' or '.join(wanted),
                         top['label'] if top else 'none'))
    checks.append(_check(f'{tag}: top score', bool(top) and _in_range(top['score'], expected['minScore'], 1),
                         f">= {expected['minScore']}", top['score'] if top else 'none'))
    if 'minGroupScore' in expected:
      group_score = sum(c['score'] for c in categories if c['label'] in wanted and _is_number(c['score']))
      checks.append(_check(f'{tag}: combined score of matching classes',
                           _in_range(group_score, expected['minGroupScore'], 1.01),
                           f">= {expected['minGroupScore']}", group_score))
    valid = all(_in_range(c['score'], 0, 1) for c in categories)
    checks.append(_check(f'{tag}: scores are probabilities', valid, True, valid))
  first_data = (runs[0].get('data') or {}) if runs else {}
  checks += _verify_figure_file(first_data.get('figureFile'), 'annotated.png')
  return checks


def _verify_similarity(runs, expectation):
  """Embedding tasks: cosine similarities lie where they should, in order."""
  by_key = _runs_by_key(runs)
  checks, values = [], {}
  for expected in expectation['runs']:
    tag = expected['key']
    present = tag in by_key
    checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      continue
    value = by_key[tag].get('similarity')
    values[tag] = value
    low, high = expected['range']
    checks.append(_check(f'{tag}: cosine similarity', _in_range(value, low, high), f'{low} to {high}',
                         'none' if value is None else value))
    if 'baseline' in expected:
      baseline = expected['baseline']
      ok = _is_number(value) and abs(value - baseline['value']) <= baseline['tolerance']
      checks.append(_check(f"{tag}: baseline of release {baseline['release']}", ok,
                           f"{baseline['value']} +/- {baseline['tolerance']}", 'none' if value is None else value))
  for order in expectation.get('ordered', []):
    higher, lower = values.get(order['higher']), values.get(order['lower'])
    ok = _is_number(higher) and _is_number(lower) and higher >= lower + order['by']
    actual = f'{higher:.4f} vs {lower:.4f}' if _is_number(higher) and _is_number(lower) else 'none'
    checks.append(_check(f"\"{order['higher']}\" scores higher than \"{order['lower']}\"", ok,
                         f"at least {order['by']} higher", actual))
  return checks


def _verify_text_labels(runs, expectation):
  """Text classification and language detection: each input gets its label."""
  by_key = _runs_by_key(runs)
  checks = []
  for expected in expectation['runs']:
    tag = f"{expected['key']} ({expected['label']})"
    present = expected['key'] in by_key
    checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      continue
    data = by_key[expected['key']]
    categories = data.get('categories') or []
    if expected.get('match', 'top') == 'present':
      # The label only has to be among the results, with at least this score.
      found = next((c for c in categories if c['label'] == expected['label']), None)
      listed = ', '.join(f"{c['label']} {c['score']:.2f}" for c in categories) or 'none'
      checks.append(_check(f'{tag}: label is among the results',
                           bool(found) and _in_range(found['score'], expected['minScore'], 1),
                           f"{expected['label']} >= {expected['minScore']}", listed))
    else:
      top = _top(categories)
      checks.append(_check(f'{tag}: top label', bool(top) and top['label'] == expected['label'], expected['label'],
                           top['label'] if top else 'none'))
      checks.append(_check(f'{tag}: top score', bool(top) and _in_range(top['score'], expected['minScore'], 1),
                           f">= {expected['minScore']}", top['score'] if top else 'none'))
    valid = all(_in_range(c['score'], 0, 1) for c in categories)
    checks.append(_check(f'{tag}: scores are probabilities', valid, True, valid))
  return checks


def _verify_audio_classification(runs, expectation):
  """Audio classification: every full window of the clip gets its label."""
  data = (runs[0].get('data') or {}) if runs else {}
  results = data.get('results') or []
  checks = [_check('number of results', len(results) == expectation['results'], expectation['results'], len(results))]
  # In AUDIO_STREAM mode, close() flushes the partial trailing chunk at Timestamp::Max().
  checked = expectation['fullWindows'] if expectation.get('stream') else len(results)
  timestamps = [r.get('timestampMs') for r in results[:checked]]
  wanted = [i * expectation['windowMs'] for i in range(checked)]
  checks.append(_check('timestamps of the windows', timestamps == wanted, wanted, timestamps))
  full = results[:expectation['fullWindows']]
  checks.append(_check('full windows classified', len(full) == expectation['fullWindows'],
                       expectation['fullWindows'], len(full)))
  for result in full:
    top = _top(result.get('categories') or [])
    tag = f"window at {result.get('timestampMs')} ms"
    checks.append(_check(f'{tag}: top label', bool(top) and top['label'] == expectation['label'],
                         expectation['label'], top['label'] if top else 'none'))
    checks.append(_check(f'{tag}: top score', bool(top) and _in_range(top['score'], expectation['minScore'], 1),
                         f">= {expectation['minScore']}", top['score'] if top else 'none'))
  counts = [len(r.get('categories') or []) for r in results]
  ok = all(1 <= c <= expectation['maxResults'] for c in counts)
  checks.append(_check('categories per window', ok, f"1 to {expectation['maxResults']}", counts))
  return checks


def _verify_transcript(runs, expectation):
  """Speech recognition: the transcript says what is said in the clip."""
  data = (runs[0].get('data') or {}) if runs else {}
  segments = data.get('segments') or []
  clip_ms = data.get('clipMs') or 0
  transcript = ' '.join(str(s.get('transcript') or '') for s in segments).strip()
  checks = [_check('segments returned', len(segments) >= 1, '>= 1', len(segments))]
  rate = word_error_rate(expectation['reference'], transcript)
  checks.append(_check('transcript (word error rate)', bool(transcript) and rate <= expectation['maxWordErrorRate'],
                       f"\"{expectation['reference']}\" with a word error rate <= {expectation['maxWordErrorRate']}",
                       f'"{transcript}" (word error rate {rate:.2f})'))
  final = [bool(s.get('isFinal')) for s in segments]
  checks.append(_check('every segment is final', bool(final) and all(final), True, bool(final) and all(final)))
  # Timestamps lie in the clip (a quarter of a second of slack), and are in order.
  times = [(s.get('fromMs'), s.get('toMs')) for s in segments]
  valid = bool(times) and all(_is_number(a) and _is_number(b) and 0 <= a <= b <= clip_ms + 250 for a, b in times)
  checks.append(_check('segment times lie in the clip', valid, f'0 to {clip_ms} ms',
                       ', '.join(f'{a}-{b}' for a, b in times) or 'none'))
  starts = [a for a, _ in times]
  ordered = all(_is_number(a) for a in starts) and starts == sorted(starts)
  checks.append(_check('segments are in order', bool(starts) and ordered, True, bool(starts) and ordered))
  return checks


def _verify_speech(runs, expectation):
  """Speech synthesis: valid audio and speech.wav file that say the text."""
  data = (runs[0].get('data') or {}) if runs else {}
  pieces = data.get('pieces') or []
  if expectation['stream']:
    checks = [_check('pieces of audio', len(pieces) >= 1, '>= 1', len(pieces))]
  else:
    checks = [_check('pieces of audio', len(pieces) == 1, 1, len(pieces))]
  indices = [p.get('chunkIndex') for p in pieces]
  checks.append(_check('pieces are numbered in order', bool(pieces) and indices == list(range(len(pieces))),
                       list(range(len(pieces))) or 'at least one', indices))
  checks.append(_check('last piece is marked final', bool(pieces) and pieces[-1].get('isFinal') is True, True,
                       pieces[-1].get('isFinal') if pieces else 'none'))
  formats = sorted({(p.get('dtype'), p.get('ndim')) for p in pieces})
  checks.append(_check('samples are a flat float32 array', formats == [('float32', 1)], 'float32, 1 dimension',
                       ', '.join(f'{d}, {n} dimension(s)' for d, n in formats) or 'none'))
  rates = sorted({p.get('sampleRateHz') for p in pieces})
  low, high = expectation['sampleRateHz']
  checks.append(_check('sample rate', len(rates) == 1 and _in_range(rates[0], low, high), f'{low} to {high} Hz',
                       ', '.join(str(r) for r in rates) or 'none'))
  checks.append(_check('samples are finite numbers', bool(pieces) and data.get('nonFinite') == 0, 0,
                       data.get('nonFinite', 'none')))
  low, high = expectation['durationSec']
  checks.append(_check('duration', _in_range(data.get('durationSec'), low, high), f'{low} to {high} s',
                       data.get('durationSec', 'none')))
  checks.append(_check('audible (RMS level)', _in_range(data.get('rms'), expectation['minRms'], 1),
                       f">= {expectation['minRms']}", data.get('rms', 'none')))
  checks.append(_check('within -1 to 1 (peak level)', _in_range(data.get('peak'), 0, expectation['maxPeak'] + 1e-6),
                       f"<= {expectation['maxPeak']}", data.get('peak', 'none')))

  # On-disk WAV file verification.
  wav = data.get('wavFile') or {}
  total_samples = sum(p.get('samples', 0) for p in pieces)
  pcm_ok = wav.get('exists') is True and wav.get('channels') == 1 and wav.get('sampleWidth') == 2
  actual_pcm = (f"{wav.get('channels')} ch, {wav.get('sampleWidth')} B/sample" if wav.get('exists') else 'missing')
  checks.append(_check('speech.wav is mono 16-bit PCM', pcm_ok, '1 ch, 2 B/sample', actual_pcm))
  header_ok = (bool(pieces) and wav.get('exists') is True and wav.get('sampleRateHz') == data.get('sampleRateHz')
               and wav.get('samples') == total_samples and total_samples > 0)
  actual_header = (f"{wav.get('samples')} samples at {wav.get('sampleRateHz')} Hz" if wav.get('exists') else 'missing')
  checks.append(_check('speech.wav has the sample rate and length of the audio', header_ok,
                       f"{total_samples} samples at {data.get('sampleRateHz')} Hz", actual_header))
  checks.append(_check('speech.wav matches the returned samples', _in_range(wav.get('maxDiff'), 0, 0.0002),
                       '<= 0.0002 max diff', 'none' if wav.get('maxDiff') is None else wav.get('maxDiff')))

  if not expectation['stream']:
    # One timestamp per word of the text, in order, inside the clip.
    stamps = data.get('wordTimestamps') or []
    words = expectation['text'].split()
    checks.append(_check('word timestamps name the words of the text', [s.get('word') for s in stamps] == words,
                         ' '.join(words), ' '.join(str(s.get('word')) for s in stamps) or 'none'))
    duration = data.get('durationSec') or 0
    times = [(s.get('startSec'), s.get('endSec')) for s in stamps]
    valid = bool(times) and all(_is_number(a) and _is_number(b) and 0 <= a <= b <= duration + 0.1 for a, b in times)
    starts = [a for a, _ in times]
    valid = valid and starts == sorted(starts)
    checks.append(_check('word timestamps are in order and inside the clip', valid, f'0 to {duration} s, ascending',
                         f'{times[0][0]} to {times[-1][1]} s' if times else 'none'))
  # Round trip of speech.wav through the Audio Transcriber.
  heard = data.get('heard')
  if data.get('heardError') or heard is None:
    checks.append(_check('speech recognition of speech.wav ran', False, 'a transcript',
                         data.get('heardError') or 'none'))
  else:
    rate = char_error_rate(expectation['text'], heard)
    checks.append(_check('speech recognition hears the text in speech.wav (letter error rate)',
                         bool(heard.strip()) and rate <= expectation['maxCharErrorRate'],
                         f"\"{expectation['text']}\" with a letter error rate <= {expectation['maxCharErrorRate']}",
                         f'"{heard}" (letter error rate {rate:.2f})'))
  return checks


def _verify_retrieval(runs, expectation):
  """Semantic retrieval: the best match is the record the query describes."""
  by_key = _runs_by_key(runs)
  checks = []
  for expected in expectation['runs']:
    tag = f"\"{expected['key']}\""
    present = expected['key'] in by_key
    checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      continue
    results = by_key[expected['key']].get('results') or []
    ids = [r.get('id') for r in results]
    checks.append(_check(f'{tag}: best match', bool(ids) and ids[0] == expected['top'], expected['top'],
                         ids[0] if ids else 'none'))
    checks.append(_check(f'{tag}: number of matches', 1 <= len(results) <= expectation['limit'],
                         f"1 to {expectation['limit']}", len(results)))
    known = len(set(ids)) == len(ids) and all(i in expectation['records'] for i in ids)
    checks.append(_check(f'{tag}: matches are distinct records of the index', bool(ids) and known, True,
                         ', '.join(str(i) for i in ids) or 'none'))
    scores = [r.get('score') for r in results]
    valid = bool(scores) and all(_in_range(s, -1, 1.0001) for s in scores) and scores == sorted(scores, reverse=True)
    checks.append(_check(f'{tag}: scores are similarities, best first', valid, '-1 to 1, descending', scores))
  return checks


def _is_distribution(values):
  return bool(values) and all(_in_range(v, 0, 1) for v in values) and abs(sum(values) - 1) <= 0.02


def _verify_decisions(runs, expectation):
  """Decisions: each text gets the answers that follow from what it says."""
  by_key = _runs_by_key(runs)
  checks, severities = [], {}
  top_level = expectation['levels'] - 1
  for expected in expectation['runs']:
    tag = expected['key']
    present = tag in by_key
    checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      continue
    data = by_key[tag]
    urgent, department, severity = data.get('urgent') or {}, data.get('department') or {}, data.get('severity') or {}
    checks.append(_check(f'{tag}: urgent outage', urgent.get('value') is expected['urgent'], expected['urgent'],
                         urgent.get('value', 'none')))
    probability = urgent.get('probabilityTrue')
    # The answer is "yes" from a probability of 0.5 (the default threshold).
    consistent = _in_range(probability, 0, 1) and (probability >= 0.5) == bool(urgent.get('value'))
    checks.append(_check(f'{tag}: probability of "urgent" agrees with the answer', consistent,
                         '>= 0.5' if expected['urgent'] else '< 0.5', 'none' if probability is None else probability))
    checks.append(_check(f'{tag}: department', department.get('selected') == expected['department'],
                         expected['department'], department.get('selected', 'none')))
    probabilities = department.get('probabilities') or {}
    checks.append(_check(f'{tag}: department probabilities cover the choices and add up to 1',
                         sorted(probabilities) == sorted(expectation['departments'])
                         and _is_distribution(list(probabilities.values())),
                         ', '.join(sorted(expectation['departments'])),
                         ', '.join(f'{k} {v:.3f}' for k, v in sorted(probabilities.items())) or 'none'))
    levels = severity.get('probabilities') or []
    checks.append(_check(f'{tag}: severity probabilities cover the scale and add up to 1',
                         len(levels) == expectation['levels'] and _is_distribution(levels),
                         f"{expectation['levels']} levels", levels or 'none'))
    score = severity.get('expectedScore')
    severities[tag] = score
    checks.append(_check(f'{tag}: severity lies on the scale', _in_range(score, 0, top_level), f'0 to {top_level}',
                         'none' if score is None else score))
  for order in expectation.get('severityOrder', []):
    higher, lower = severities.get(order['higher']), severities.get(order['lower'])
    ok = _is_number(higher) and _is_number(lower) and higher >= lower + order['by']
    actual = f'{higher:.2f} vs {lower:.2f}' if _is_number(higher) and _is_number(lower) else 'none'
    checks.append(_check(f"{order['higher']} is more severe than {order['lower']}", ok,
                         f"at least {order['by']} higher", actual))
  return checks


def _verify_segmentation(runs, expectation):
  """Image segmentation: category mask, optional confidence mask, and output PNGs."""
  data = (runs[0].get('data') or {}) if runs else {}
  img_w, img_h = data.get('imageWidth') or 0, data.get('imageHeight') or 0
  checks = [
      _check('category mask has the size of the image',
             bool(data.get('maskWidth')) and data.get('maskWidth') == img_w and data.get('maskHeight') == img_h,
             f'{img_w}x{img_h}', f"{data.get('maskWidth')}x{data.get('maskHeight')}"),
      _check('category names of the model', len(data.get('labels') or []) == expectation['labelCount'],
             expectation['labelCount'], len(data.get('labels') or [])),
  ]
  classes = {c['label']: c for c in data.get('classes') or []}
  for expected in expectation['classes']:
    label = expected['label']
    observed = classes.get(label)
    low, high = expected['fraction']
    share = observed['fraction'] if observed else 0.0
    checks.append(_check(f'"{label}" share of the image', _in_range(share, low, high), f'{low} to {high}', share))
    if 'centroidWithin' in expected:
      checks.append(_check_within(f'"{label}" centre', observed.get('centroid') if observed else None,
                                  expected['centroidWithin']))
  wanted = {c['label'] for c in expectation['classes']}
  other = sum(c['fraction'] for label, c in classes.items() if label not in wanted)
  names = ', '.join(sorted(label for label in classes if label not in wanted)) or 'none'
  checks.append(_check('share of any other category', other <= expectation['maxOther'],
                       f"<= {expectation['maxOther']}", f'{other:.4f} ({names})'))

  conf_exp = expectation.get('confidence')
  if conf_exp:
    conf = data.get('confidence') or {}
    checks.append(_check('confidence masks returned for all categories',
                         conf.get('count') == expectation['labelCount'] and conf.get('category') == conf_exp['label'],
                         f"{expectation['labelCount']} masks ({conf_exp['label']})",
                         f"{conf.get('count', 'none')} ({conf.get('category', 'none')})"))
    conf_mask = dict(conf, imageWidth=img_w, imageHeight=img_h) if conf else None
    checks += _verify_mask(conf_mask, {'name': f'confidence mask of "{conf_exp["label"]}"',
                                       'fraction': conf_exp['fraction'],
                                       'centroidWithin': conf_exp['centroidWithin']})
    checks.append(_check(f'confidence mask of "{conf_exp["label"]}": peak confidence',
                         _in_range(conf.get('max'), conf_exp['minPeak'], 1),
                         f">= {conf_exp['minPeak']}", conf.get('max', 'none')))
    checks += _verify_grayscale_mask_file(data.get('maskFile'), img_w, img_h, 'mask.png')
  else:
    mask_file = data.get('maskFile') or {}
    mask_ok = (mask_file.get('exists') is True and img_w > 0 and mask_file.get('width') == img_w
               and mask_file.get('height') == img_h and mask_file.get('channels') == 3
               and mask_file.get('grayscale') is True)
    actual_mask = (f"{mask_file.get('width')}x{mask_file.get('height')}x{mask_file.get('channels')}, "
                   f"grayscale={mask_file.get('grayscale')}" if mask_file.get('exists') else 'missing')
    checks.append(_check(f'mask.png was written as a grayscale mask of {img_w}x{img_h}', mask_ok,
                         f'{img_w}x{img_h}x3, grayscale=True', actual_mask))
    checks.append(_check('mask.png foreground matches the category mask',
                         _in_range(mask_file.get('fgDiff'), 0, 0.005), '<= 0.005 foreground diff',
                         'none' if mask_file.get('fgDiff') is None else mask_file.get('fgDiff')))

  blur_file = data.get('blurFile') or {}
  blur_ok = (blur_file.get('exists') is True and img_w > 0 and blur_file.get('width') == img_w
             and blur_file.get('height') == img_h and blur_file.get('channels') == 3)
  actual_blur = (f"{blur_file.get('width')}x{blur_file.get('height')}x{blur_file.get('channels')}"
                 if blur_file.get('exists') else 'missing')
  checks.append(_check(f'blurred_background.png was written with size {img_w}x{img_h}', blur_ok,
                       f'{img_w}x{img_h}x3', actual_blur))
  checks.append(_check('blurred_background.png keeps the foreground sharp',
                       _in_range(blur_file.get('fgMeanDiff'), 0, 0.5), '<= 0.5 mean pixel diff',
                       'none' if blur_file.get('fgMeanDiff') is None else blur_file.get('fgMeanDiff')))
  checks.append(_check('blurred_background.png blurs the background',
                       _in_range(blur_file.get('bgMeanDiff'), 1.5, 255), '>= 1.5 mean pixel diff',
                       'none' if blur_file.get('bgMeanDiff') is None else blur_file.get('bgMeanDiff')))
  return checks


def _verify_selection(runs, expectation):
  """Interactive segmentation: the mask and overlay PNG select the intended region."""
  by_key = _runs_by_key(runs)
  checks = []
  for expected in expectation['runs']:
    tag = f"selection of {expected['key']}"
    present = expected['key'] in by_key
    checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      continue
    data = by_key[expected['key']]
    img_w, img_h = data.get('imageWidth') or 0, data.get('imageHeight') or 0
    mask = data.get('mask') or {}
    checks.append(_check(f'{tag}: mask has one channel of confidences', mask.get('channels') == 1, 1,
                         mask.get('channels', 'none')))
    checks.append(_check(f'{tag}: mask values are finite', mask.get('nonFinite', 0) == 0, 0,
                         mask.get('nonFinite', 'none')))
    checks.append(_check(f'{tag}: mask has the size of the image',
                         bool(mask.get('width')) and mask.get('width') == img_w and mask.get('height') == img_h,
                         f'{img_w}x{img_h}', f"{mask.get('width')}x{mask.get('height')}"))
    checks.append(_check(f'{tag}: confidences are from 0 to 1',
                         _in_range(mask.get('min'), 0, 1) and _in_range(mask.get('max'), 0, 1), '0 to 1',
                         f"{_fmt(mask.get('min'))} to {_fmt(mask.get('max'))}"))
    low, high = expected['fraction']
    checks.append(_check(f'{tag}: selected share of the image', _in_range(data.get('fraction'), low, high),
                         f'{low} to {high}', data.get('fraction', 'none')))
    checks.append(_check_within(f'{tag}: centre of the selection', data.get('centroid'), expected['centroidWithin']))
    bounds, limit = data.get('bounds'), expected['boundsWithin']
    inside = (bool(bounds) and bounds['minX'] >= limit['minX'] and bounds['maxX'] <= limit['maxX']
              and bounds['minY'] >= limit['minY'] and bounds['maxY'] <= limit['maxY'])
    actual = (f"x {bounds['minX']:.3f}-{bounds['maxX']:.3f}, y {bounds['minY']:.3f}-{bounds['maxY']:.3f}"
              if bounds else 'none')
    checks.append(_check(f'{tag}: selection stays within bounds', inside,
                         f"x {limit['minX']}-{limit['maxX']}, y {limit['minY']}-{limit['maxY']}", actual))
    probes = data.get('probes') or {}
    for name in expected['selected']:
      checks.append(_check(f'{tag}: {name} is selected', probes.get(name) is True, True, probes.get(name, 'none')))
    for name in expected['notSelected']:
      checks.append(_check(f'{tag}: {name} is not selected', probes.get(name) is False, False,
                           probes.get(name, 'none')))

    overlay = data.get('overlayFile') or {}
    overlay_ok = (overlay.get('exists') is True and img_w > 0 and overlay.get('width') == img_w
                  and overlay.get('height') == img_h and overlay.get('channels') == 3)
    actual_overlay = (f"{overlay.get('width')}x{overlay.get('height')}x{overlay.get('channels')}"
                      if overlay.get('exists') else 'missing')
    checks.append(_check(f'{tag}: overlay PNG was written with size {img_w}x{img_h}', overlay_ok,
                         f'{img_w}x{img_h}x3', actual_overlay))
    checks.append(_check(f'{tag}: overlay PNG tints the selected pixels green',
                         _in_range(overlay.get('selectedGreenGain'), 65, 88), '65 to 88 green gain',
                         'none' if overlay.get('selectedGreenGain') is None else overlay.get('selectedGreenGain')))
    checks.append(_check(f'{tag}: overlay PNG leaves unselected pixels unchanged',
                         _in_range(overlay.get('outsideUnchangedFraction'), 0.95, 1.0), '>= 0.95 unchanged',
                         'none' if overlay.get('outsideUnchangedFraction') is None
                         else overlay.get('outsideUnchangedFraction')))
  return checks


def apply_corrections(corrections, keep):
  """The text made of the unchanged segments plus those of type `keep`."""
  return ''.join(c['text'] for c in corrections if c['type'] in ('SAME', keep))


def _verify_proofreading(runs, expectation):
  """Proofreading: the text is fixed, and the corrections explain the fix."""
  by_key = _runs_by_key(runs)
  checks = []
  for expected in expectation['runs']:
    tag = expected['key']
    present = tag in by_key
    checks.append(_check(f'{tag}: has a result', present, True, present))
    if not present:
      continue
    data = by_key[tag]
    proofread = data.get('proofreadText') or ''
    corrections = data.get('corrections') or []
    if 'exact' in expected:
      checks.append(_check(f'{tag}: proofread text', proofread == expected['exact'], expected['exact'], proofread))
    else:
      checks.append(_check(f'{tag}: text was changed', bool(proofread) and proofread != expected['text'],
                           'a corrected text', proofread or 'none'))
    if 'startsWith' in expected:
      checks.append(_check(f'{tag}: proofread text starts with', proofread.startswith(expected['startsWith']),
                           expected['startsWith'], proofread))
    if 'containsAny' in expected:
      checks.append(_check(f'{tag}: proofread text contains', any(s in proofread for s in expected['containsAny']),
                           ' or '.join(expected['containsAny']), proofread))
    types = {c['type'] for c in corrections}
    checks.append(_check(f'{tag}: correction types', bool(types) and types <= {'SAME', 'INSERTION', 'DELETION'},
                         'SAME, INSERTION, DELETION', ', '.join(sorted(types)) or 'none'))
    checks.append(_check(f'{tag}: corrections change something', bool(types - {'SAME'}), 'an insertion or deletion',
                         ', '.join(sorted(types)) or 'none'))
    # Kept and inserted segments make up the proofread text; kept and deleted
    # segments make up the input.
    checks.append(_check(f'{tag}: kept + inserted segments give the proofread text',
                         apply_corrections(corrections, 'INSERTION') == proofread, proofread,
                         apply_corrections(corrections, 'INSERTION')))
    checks.append(_check(f'{tag}: kept + deleted segments give the input',
                         apply_corrections(corrections, 'DELETION') == expected['text'], expected['text'],
                         apply_corrections(corrections, 'DELETION')))
  return checks


def _verify_summary(runs, expectation):
  """Summarization: shorter than the text, in the right form, about the text."""
  data = (runs[0].get('data') or {}) if runs else {}
  summary = (data.get('summary') or '').strip()
  source = expectation['text']
  checks = [_check('summary is not empty', bool(summary), 'a summary', summary[:80] or 'none')]
  ratio = len(summary) / len(source)
  checks.append(_check('length relative to the text', 0 < ratio <= expectation['maxLengthRatio'],
                       f"<= {expectation['maxLengthRatio']}", ratio))
  lines = [line.strip() for line in summary.splitlines() if line.strip()]
  bullets = [line for line in lines if re.match(r'^([*\-\u2022]|\d+[.)])\s+\S', line)]
  if expectation['format'] == 'bullets':
    checks.append(_check('every line is a bullet point', bool(lines) and len(bullets) == len(lines), 'bullets only',
                         f'{len(bullets)} of {len(lines)} lines'))
    checks.append(_check('number of bullet points', len(bullets) >= expectation['minBullets'],
                         f">= {expectation['minBullets']}", len(bullets)))
  else:
    checks.append(_check('summary is a paragraph, not a list', not bullets, 'no bullet points', len(bullets)))
  lowered = summary.lower()
  for term in expectation.get('mentionsAll', []):
    checks.append(_check(f'mentions "{term}"', term.lower() in lowered, term, summary[:120]))
  if expectation.get('mentionsAny'):
    terms = expectation['mentionsAny']
    words = set(normalize_words(summary))
    ok = any(term.lower() in words or (len(term) > 3 and term.lower() in lowered) for term in terms)
    checks.append(_check('mentions ' + ' or '.join(f'"{t}"' for t in terms), ok, ' or '.join(terms), summary[:120]))
  return checks


VERIFIERS = {
    'detections': _verify_detections,
    'faces': _verify_faces,
    'landmarks': _verify_landmarks,
    'classification': _verify_classification,
    'similarity': _verify_similarity,
    'textLabels': _verify_text_labels,
    'audioClassification': _verify_audio_classification,
    'transcript': _verify_transcript,
    'retrieval': _verify_retrieval,
    'decisions': _verify_decisions,
    'speech': _verify_speech,
    'segmentation': _verify_segmentation,
    'selection': _verify_selection,
    'proofreading': _verify_proofreading,
    'summary': _verify_summary,
}


def verify_case(expectation, runs):
  """Verifies one case.

  `expectation` is None when no ground truth is defined for the case, which
  yields UNVERIFIED so that it is never reported as a pass.
  """
  if not expectation:
    return {'status': UNVERIFIED, 'reason': 'no output expectation is defined for this case', 'checks': []}
  verifier = VERIFIERS.get(expectation['type'])
  if not verifier:
    raise ValueError(f"Unknown expectation type: {expectation['type']}")
  checks = verifier(runs or [], expectation)
  if not checks:
    return {'status': UNVERIFIED, 'reason': 'the expectation made no check', 'checks': []}
  failed = [c for c in checks if not c['pass']]
  return {
      'status': FAILED if failed else VERIFIED,
      'reason': '; '.join(f"{c['name']}: expected {c['expected']}, got {c['actual']}" for c in failed),
      'checks': checks,
  }
