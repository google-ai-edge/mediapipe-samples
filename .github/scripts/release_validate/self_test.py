#!/usr/bin/env python3
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
"""Tests of the validation itself. No MediaPipe is needed.

run_local.py runs these before every validation: a verifier that accepts a wrong
output would make the whole report worthless. For every case of the matrix, a
correct output is built from its expectation and must be VERIFIED; then that
output is damaged in the ways a broken release would damage it, and every
damaged output must be FAILED.

  python3 .github/scripts/release_validate/self_test.py
"""

import ast
import copy
import os
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO_DIR = os.path.abspath(os.path.join(SCRIPT_DIR, '..', '..', '..'))
sys.path.insert(0, SCRIPT_DIR)

import assets  # pylint: disable=g-import-not-at-top,wrong-import-position
import cases  # pylint: disable=wrong-import-position
import expectations  # pylint: disable=wrong-import-position
import policy  # pylint: disable=wrong-import-position
import verify  # pylint: disable=wrong-import-position

NAN = float('nan')


def _middle(low, high):
  return (low + high) / 2


def _centre(box):
  return {'x': _middle(box['minX'], box['maxX']), 'y': _middle(box['minY'], box['maxY'])}


def _landmarks(count, points):
  landmarks = [[0.5, 0.5] for _ in range(count)]
  for target in points.values():
    landmarks[target['index']] = [target['x'], target['y']]
  return landmarks


def _annotated_file(width=640, height=480):
  return {'exists': True, 'width': width, 'height': height, 'channels': 3,
          'expectedWidth': width, 'expectedHeight': height, 'changedFraction': 0.03}


def _figure_file():
  return {'exists': True, 'width': 1200, 'height': 900, 'channels': 4, 'inkFraction': 0.25}


def _grayscale_mask_file(width=640, height=480):
  return {'exists': True, 'width': width, 'height': height, 'channels': 3, 'grayscale': True, 'maxDiff': 0.0}


def _mask(expected, **extra):
  mask = {'width': 640, 'height': 480, 'imageWidth': 640, 'imageHeight': 480, 'channels': 1, 'dtype': 'float32',
          'min': 0.0, 'max': 1.0, 'mean': 0.2, 'nonFinite': 0, 'fraction': _middle(*expected['fraction']),
          'centroid': _centre(expected['centroidWithin']) if 'centroidWithin' in expected else {'x': 0.5, 'y': 0.5}}
  if expected.get('fileName'):
    mask['file'] = _grayscale_mask_file(640, 480)
  mask.update(extra)
  return mask


def correct_runs(expectation):
  """An output that fulfils an expectation, in the form worker.py reports it."""
  kind = expectation['type']
  if kind == 'detections':
    width, height = 640, 416
    return [{'key': 'image', 'data': {
        'imageWidth': width, 'imageHeight': height,
        'detections': [{'label': o['label'], 'score': 0.9,
                        'box': {'x': o['box']['x'] * width, 'y': o['box']['y'] * height,
                                'width': o['box']['width'] * width, 'height': o['box']['height'] * height}}
                       for o in expectation['objects']],
        'annotatedFile': _annotated_file(width, height)}}]
  if kind == 'faces':
    width, height = 820, 1024
    keypoints = [[0.5, 0.5] for _ in range(6)]
    for k in expectation['keypoints']:
      keypoints[k['index']] = [k['x'], k['y']]
    share = _middle(*expectation['boxWidth'])
    nose = expectation['boxContains']
    box = {'x': (nose['x'] - share / 2) * width, 'y': nose['y'] * height - share * width / 2,
           'width': share * width, 'height': share * width}
    return [{'key': 'image', 'data': {
        'imageWidth': width, 'imageHeight': height,
        'detections': [{'score': 0.9, 'box': box, 'keypoints': keypoints}],
        'annotatedFile': _annotated_file(width, height)}}]
  if kind == 'landmarks':
    runs = []
    for run in expectation['runs']:
      data = {}
      for group in run['groups']:
        data[group['key']] = []
        for instance in group['instances']:
          observed = {'landmarks': _landmarks(group['landmarkCount'], instance.get('points', {})), 'score': 0.9}
          observed.update(instance.get('fields', {}))
          data[group['key']].append(observed)
      for item in run.get('equals', []):
        data[item['key']] = copy.deepcopy(item['value'])
      data['values'] = {item['key']: _middle(*item['range']) for item in run.get('ranges', [])}
      if 'mask' in run:
        data['mask'] = _mask(run['mask'])
      if run.get('annotatedFile'):
        data['annotatedFile'] = _annotated_file()
      if run.get('figureFile'):
        data['figureFile'] = _figure_file()
      runs.append({'key': run['key'], 'data': data})
    if expectation.get('figureFile') and runs:
      runs[0]['data']['figureFile'] = _figure_file()
    return runs
  if kind == 'classification':
    runs = [{'key': run['key'], 'data': {'categories': [
        {'label': run['labels'][0], 'score': 0.9}, {'label': 'something else', 'score': 0.05}]}}
            for run in expectation['runs']]
    if runs:
      runs[0]['data']['figureFile'] = _figure_file()
    return runs
  if kind == 'similarity':
    values = {run['key']: run['baseline']['value'] if 'baseline' in run else min(max(_middle(*run['range']), -1), 1)
              for run in expectation['runs']}
    # Pairs that must be told apart are, by a clear margin.
    for order in expectation.get('ordered', []):
      values[order['higher']] = max(values[order['higher']], values[order['lower']] + order['by'] + 0.1)
    return [{'key': key, 'data': {'similarity': value}} for key, value in values.items()]
  if kind == 'textLabels':
    return [{'key': run['key'], 'data': {'categories': [
        {'label': run['label'], 'score': 0.95}, {'label': 'zz', 'score': 0.05}]}} for run in expectation['runs']]
  if kind == 'audioClassification':
    results = [{'timestampMs': i * expectation['windowMs'],
                'categories': [{'label': expectation['label'], 'score': 0.9}, {'label': 'Silence', 'score': 0.05}]}
               for i in range(expectation['fullWindows'])]
    results += [{'timestampMs': i * expectation['windowMs'], 'categories': [{'label': 'Tick', 'score': 0.3}]}
                for i in range(expectation['fullWindows'], expectation['results'])]
    return [{'key': 'clip', 'data': {'results': results}}]
  if kind == 'retrieval':
    runs = []
    for run in expectation['runs']:
      others = [r for r in expectation['records'] if r != run['top']][:expectation['limit'] - 1]
      runs.append({'key': run['key'], 'data': {'results': [
          {'id': record, 'score': round(0.75 - 0.1 * i, 2)} for i, record in enumerate([run['top']] + others)]}})
    return runs
  if kind == 'decisions':
    runs = []
    for run in expectation['runs']:
      departments = {d: 0.02 for d in expectation['departments']}
      departments[run['department']] = 1 - 0.02 * (len(departments) - 1)
      level = expectation['levels'] - 1 if run['urgent'] else 0
      levels = [0.05] * expectation['levels']
      levels[level] = 1 - 0.05 * (expectation['levels'] - 1)
      runs.append({'key': run['key'], 'data': {
          'urgent': {'value': run['urgent'], 'probabilityTrue': 0.85 if run['urgent'] else 0.15},
          'department': {'selected': run['department'], 'probabilities': departments},
          'severity': {'expectedScore': sum(i * p for i, p in enumerate(levels)), 'probabilities': levels}}})
    return runs
  if kind == 'transcript':
    return [{'key': 'clip', 'data': {'clipMs': 4273, 'segments': [
        {'transcript': expectation['reference'], 'isFinal': True, 'fromMs': 0, 'toMs': 4272}]}}]
  if kind == 'speech':
    count = 3 if expectation['stream'] else 1
    pieces = [{'chunkIndex': i, 'isFinal': i == count - 1, 'samples': 96000 // count, 'sampleRateHz': 24000,
               'dtype': 'float32', 'ndim': 1} for i in range(count)]
    stamps = [{'word': word, 'startSec': round(0.2 + 0.25 * i, 2), 'endSec': round(0.4 + 0.25 * i, 2)}
              for i, word in enumerate(expectation['text'].split())]
    return [{'key': 'speech', 'data': {
        'pieces': pieces, 'sampleRateHz': 24000, 'durationSec': 4.0, 'rms': 0.08, 'peak': 0.6, 'nonFinite': 0,
        'wordTimestamps': stamps,
        'wavFile': {'exists': True, 'channels': 1, 'sampleWidth': 2, 'sampleRateHz': 24000, 'samples': 96000,
                    'maxDiff': 0.00003},
        'heard': expectation['text'], 'heardError': None}}]
  if kind == 'segmentation':
    width, height = 257, 257
    classes = [{'index': i, 'label': c['label'], 'fraction': _middle(*c['fraction']),
                'centroid': _centre(c['centroidWithin']) if 'centroidWithin' in c else {'x': 0.5, 'y': 0.5}}
               for i, c in enumerate(expectation['classes'])]
    # Single-class selfie models have 1 model label ('selfie') and background at 255.
    model_labels = [c['label'] for c in expectation['classes']
                    if expectation['labelCount'] > 1 or c['label'] != 'background']
    model_labels += [f'category {i}' for i in range(len(model_labels), expectation['labelCount'])]
    data = {
        'imageWidth': width, 'imageHeight': height, 'maskWidth': width, 'maskHeight': height,
        'dtype': 'uint8', 'labels': model_labels, 'classes': classes,
        'blurFile': {'exists': True, 'width': width, 'height': height, 'channels': 3,
                     'fgMeanDiff': 0.0, 'bgMeanDiff': 8.0},
    }
    conf = expectation.get('confidence')
    if conf:
      data['confidence'] = {
          'category': conf['label'], 'index': 0, 'count': expectation['labelCount'],
          'width': width, 'height': height, 'channels': 1, 'dtype': 'float32',
          'min': 0.0, 'max': 0.98, 'mean': _middle(*conf['fraction']), 'nonFinite': 0,
          'fraction': _middle(*conf['fraction']), 'centroid': _centre(conf['centroidWithin']),
      }
      data['maskFile'] = _grayscale_mask_file(width, height)
    else:
      data['confidence'] = None
      data['maskFile'] = {'exists': True, 'width': width, 'height': height, 'channels': 3,
                          'grayscale': True, 'fgDiff': 0.0}
    return [{'key': 'image', 'data': data}]
  if kind == 'selection':
    runs = []
    for run in expectation['runs']:
      limit = run['boundsWithin']
      probes = {name: True for name in run['selected']}
      probes.update({name: False for name in run['notSelected']})
      runs.append({'key': run['key'], 'data': {
          'imageWidth': 1200, 'imageHeight': 600,
          'mask': {'width': 1200, 'height': 600, 'channels': 1, 'dtype': 'float32', 'min': 0.0, 'max': 1.0,
                   'nonFinite': 0},
          'fraction': _middle(*run['fraction']), 'centroid': _centre(run['centroidWithin']),
          'bounds': {'minX': limit['minX'] + 0.01, 'maxX': limit['maxX'] - 0.01, 'minY': limit['minY'] + 0.01,
                     'maxY': limit['maxY'] - 0.01},
          'probes': probes,
          'overlayFile': {'exists': True, 'width': 1200, 'height': 600, 'channels': 3,
                          'selectedGreenGain': 76.5, 'outsideUnchangedFraction': 0.995}}})
    return runs
  if kind == 'proofreading':
    runs = []
    for run in expectation['runs']:
      fixed = run.get('exact') or (run['startsWith'] + ' did. ' + run['containsAny'][0] + ' fine.')
      runs.append({'key': run['key'], 'data': {'proofreadText': fixed, 'corrections': [
          {'type': 'DELETION', 'text': run['text']}, {'type': 'INSERTION', 'text': fixed}, {'type': 'SAME', 'text': ''}]}})
    return runs
  if kind == 'summary':
    sentence = 'The station is in Brussels, next to EU offices.'
    summary = f'* {sentence}\n* It is named after the Schuman roundabout.' if expectation['format'] == 'bullets' else sentence
    return [{'key': 'text', 'data': {'summary': summary}}]
  raise ValueError(kind)


def _first(runs):
  return runs[0]['data']


def damaged_runs(expectation):
  """Yields (what was damaged, output) pairs: outputs a broken release could produce."""

  def damage(name, change):
    runs = copy.deepcopy(correct_runs(expectation))
    change(runs)
    return name, runs

  kind = expectation['type']
  yield damage('no result at all', lambda runs: runs.clear())
  if kind == 'detections':
    yield damage('one object is missed', lambda r: _first(r)['detections'].pop())
    yield damage('nothing is detected', lambda r: _first(r)['detections'].clear())
    yield damage('a box is in the wrong place', lambda r: _first(r)['detections'][0]['box'].update(x=0, y=0))
    yield damage('a label is wrong', lambda r: _first(r)['detections'][0].update(label='teddy bear'))
    yield damage('a score is low', lambda r: _first(r)['detections'][0].update(score=0.2))
    yield damage('a score is not a number', lambda r: _first(r)['detections'][0].update(score=NAN))
    yield damage('something that is not there is detected', lambda r: _first(r)['detections'].append(
        {'label': 'person', 'score': 0.8, 'box': {'x': 10, 'y': 10, 'width': 50, 'height': 50}}))
    yield damage('annotated.png is missing', lambda r: _first(r).update(annotatedFile={'exists': False}))
    yield damage('annotated.png has no annotations', lambda r: _first(r)['annotatedFile'].update(changedFraction=0.0))
  elif kind == 'faces':
    yield damage('no face is found', lambda r: _first(r)['detections'].clear())
    yield damage('a second face is found', lambda r: _first(r)['detections'].append(
        copy.deepcopy(_first(r)['detections'][0])))
    yield damage('an eye is in the wrong place', lambda r: _first(r)['detections'][0]['keypoints'].__setitem__(
        0, [0.9, 0.9]))
    yield damage('the keypoints are missing', lambda r: _first(r)['detections'][0].update(keypoints=[]))
    yield damage('the box is elsewhere', lambda r: _first(r)['detections'][0]['box'].update(x=0, y=900))
    yield damage('the box spans the image', lambda r: _first(r)['detections'][0]['box'].update(x=0, width=820))
    yield damage('the score is low', lambda r: _first(r)['detections'][0].update(score=0.1))
    yield damage('annotated.png is missing', lambda r: _first(r).update(annotatedFile={'exists': False}))
    yield damage('annotated.png is blanked', lambda r: _first(r)['annotatedFile'].update(changedFraction=0.99))
  elif kind == 'landmarks':
    run_exp = expectation['runs'][0]
    group = run_exp['groups'][0]
    key = group['key']
    yield damage('nothing is found', lambda r: _first(r)[key].clear())
    yield damage('one instance too many', lambda r: _first(r)[key].append(copy.deepcopy(_first(r)[key][0])))
    yield damage('landmarks are missing', lambda r: _first(r)[key][0]['landmarks'].pop())
    yield damage('a landmark is not a number', lambda r: _first(r)[key][0]['landmarks'].__setitem__(3, [NAN, 0.5]))
    yield damage('a landmark is far outside the image', lambda r: _first(r)[key][0]['landmarks'].__setitem__(
        3, [4.0, 0.5]))
    points = group['instances'][0].get('points', {})
    if points:
      index = next(iter(points.values()))['index']
      yield damage('a landmark is in the wrong place', lambda r: _first(r)[key][0]['landmarks'].__setitem__(
          index, [0.02, 0.98]))
      yield damage('the result is mirrored', lambda r: _first(r)[key][0].update(
          landmarks=[[1 - x, y] for x, y in _first(r)[key][0]['landmarks']]))
    if len(group['instances']) > 1:
      yield damage('only one of the instances is found', lambda r: _first(r)[key].pop())
    for field in group['instances'][0].get('fields', {}):
      yield damage(f'{field} is wrong', lambda r, field=field: _first(r)[key][0].update({field: 'None'}))
    if 'minScore' in group['instances'][0]:
      yield damage('the score is low', lambda r: _first(r)[key][0].update(score=0.1))
    for item in run_exp.get('equals', []):
      yield damage(f"{item['key']} is wrong", lambda r, item=item: _first(r).update({item['key']: 0}))
    for item in run_exp.get('ranges', []):
      yield damage(f"{item['key']} is out of range", lambda r, item=item: _first(r)['values'].update(
          {item['key']: item['range'][1] + 0.3}))
      yield damage(f"{item['key']} is missing", lambda r, item=item: _first(r)['values'].pop(item['key']))
    if 'mask' in run_exp:
      yield damage('the mask is missing', lambda r: _first(r).update(mask=None))
      yield damage('the mask covers everything', lambda r: _first(r)['mask'].update(fraction=1.0))
      yield damage('the mask is empty', lambda r: _first(r)['mask'].update(fraction=0.0, centroid=None))
      yield damage('the mask is elsewhere', lambda r: _first(r)['mask'].update(centroid={'x': 0.02, 'y': 0.02}))
      yield damage('the mask has another size', lambda r: _first(r)['mask'].update(width=64, height=640))
      yield damage('the mask has four channels', lambda r: _first(r)['mask'].update(channels=4))
      yield damage('the mask has NaN values', lambda r: _first(r)['mask'].update(nonFinite=5))
      yield damage('the mask holds bytes, not confidences', lambda r: _first(r)['mask'].update(max=255.0))
      if run_exp['mask'].get('fileName'):
        yield damage('mask.png is missing', lambda r: _first(r)['mask'].update(file={'exists': False}))
        yield damage('mask.png differs from the returned mask', lambda r: _first(r)['mask']['file'].update(maxDiff=0.2))
    if run_exp.get('annotatedFile'):
      yield damage('annotated.png is missing', lambda r: _first(r).update(annotatedFile={'exists': False}))
      yield damage('annotated.png was not modified', lambda r: _first(r)['annotatedFile'].update(changedFraction=0.0))
    if run_exp.get('figureFile') or expectation.get('figureFile'):
      yield damage('figure PNG is missing', lambda r: _first(r).update(figureFile={'exists': False}))
      yield damage('figure PNG is blank', lambda r: _first(r)['figureFile'].update(inkFraction=0.0))
    if len(expectation['runs']) > 1:
      yield damage('the result of one input is missing', lambda r: r.pop())
  elif kind == 'classification':
    yield damage('the top label is wrong', lambda r: _first(r)['categories'][0].update(label='bagel'))
    yield damage('the top score is low', lambda r: _first(r)['categories'][0].update(score=0.06))
    yield damage('no category is returned', lambda r: _first(r)['categories'].clear())
    yield damage('too many categories are returned', lambda r: _first(r)['categories'].extend(
        [{'label': f'c{i}', 'score': 0.001} for i in range(5)]))
    yield damage('a score is not a probability', lambda r: _first(r)['categories'][0].update(score=7.5))
    yield damage('the result of one photo is missing', lambda r: r.pop())
    yield damage('annotated.png is missing', lambda r: _first(r).update(figureFile={'exists': False}))
    yield damage('annotated.png is blank', lambda r: _first(r)['figureFile'].update(inkFraction=0.0))
  elif kind == 'similarity':
    low, high = expectation['runs'][0]['range']
    yield damage('a similarity is too high', lambda r: _first(r).update(similarity=high + 0.05))
    yield damage('a similarity is not a number', lambda r: _first(r).update(similarity=NAN))
    yield damage('a similarity is missing', lambda r: _first(r).pop('similarity'))
    identical = [i for i, run in enumerate(expectation['runs']) if run['range'][0] >= 0.99]
    for index in identical:
      yield damage('identical inputs do not score 1', lambda r, index=index: r[index]['data'].update(similarity=0.5))
    if 'baseline' in expectation['runs'][0]:
      yield damage('the similarity moved away from the baseline', lambda r: _first(r).update(
          similarity=expectation['runs'][0]['baseline']['value'] - 0.2))
    for order in expectation.get('ordered', []):
      def swap(runs, order=order):
        by_key = {run['key']: run['data'] for run in runs}
        by_key[order['higher']]['similarity'] = by_key[order['lower']]['similarity']
      yield damage('the pairs are not told apart', swap)
  elif kind == 'textLabels':
    yield damage('a label is wrong', lambda r: _first(r)['categories'][0].update(label='xx'))
    yield damage('a score is low', lambda r: _first(r)['categories'][0].update(score=0.1))
    yield damage('no label is returned', lambda r: _first(r)['categories'].clear())
    yield damage('the result of one sentence is missing', lambda r: r.pop())
    yield damage('the last sentence gets the wrong label', lambda r: r[-1]['data']['categories'][0].update(label='xx'))
  elif kind == 'audioClassification':
    yield damage('a window is mislabelled', lambda r: _first(r)['results'][1]['categories'][0].update(label='Music'))
    yield damage('a window has a low score', lambda r: _first(r)['results'][0]['categories'][0].update(score=0.2))
    yield damage('the last window is missing', lambda r: _first(r)['results'].pop())
    yield damage('only one result for the whole clip', lambda r: _first(r).update(results=_first(r)['results'][:1]))
    yield damage('the timestamps are wrong', lambda r: _first(r)['results'][2].update(timestampMs=2000))
    yield damage('a window has no category', lambda r: _first(r)['results'][0].update(categories=[]))
  elif kind == 'retrieval':
    results = lambda r: _first(r)['results']
    yield damage('another record is the best match', lambda r: results(r).reverse())
    yield damage('nothing is found', lambda r: results(r).clear())
    yield damage('more matches than the limit', lambda r: results(r).extend(
        [{'id': expectation['records'][-1], 'score': 0.1}, {'id': expectation['records'][-2], 'score': 0.05}]))
    yield damage('a record is returned twice', lambda r: results(r).__setitem__(1, dict(results(r)[0])))
    yield damage('a record that was never indexed', lambda r: results(r)[1].update(id='ghost.jpg'))
    yield damage('a score is not a number', lambda r: results(r)[0].update(score=NAN))
    yield damage('the matches are not sorted', lambda r: results(r)[1].update(score=0.99))
    yield damage('the result of one query is missing', lambda r: r.pop())
    yield damage('every query returns the same record', lambda r: [run.update(data=copy.deepcopy(r[0]['data']))
                                                                    for run in r[1:]])
  elif kind == 'decisions':
    first, last = (lambda r: r[0]['data']), (lambda r: r[-1]['data'])
    yield damage('the outage is not judged urgent', lambda r: first(r)['urgent'].update(value=False, probabilityTrue=0.2))
    yield damage('a routine ticket is judged urgent', lambda r: last(r)['urgent'].update(value=True, probabilityTrue=0.9))
    yield damage('the answer contradicts its probability', lambda r: first(r)['urgent'].update(probabilityTrue=0.1))
    yield damage('a probability is not a number', lambda r: first(r)['urgent'].update(probabilityTrue=NAN))
    yield damage('the wrong department', lambda r: first(r)['department'].update(selected='billing'))
    yield damage('no department', lambda r: first(r)['department'].update(selected=''))
    yield damage('a department is missing from the probabilities', lambda r: first(r)['department'][
        'probabilities'].pop('billing'))
    yield damage('department probabilities do not add up', lambda r: first(r)['department'][
        'probabilities'].update(bug=0.3))
    yield damage('the severity scale has too few levels', lambda r: first(r)['severity']['probabilities'].pop())
    yield damage('the severity is off the scale', lambda r: first(r)['severity'].update(expectedScore=7.5))
    yield damage('every ticket is equally severe', lambda r: [run['data']['severity'].update(expectedScore=2.0)
                                                             for run in r])
    yield damage('the outage is the least severe', lambda r: first(r)['severity'].update(expectedScore=0.1))
    yield damage('the result of one ticket is missing', lambda r: r.pop())
  elif kind == 'transcript':
    segment = lambda r: _first(r)['segments'][0]
    yield damage('no segment is returned', lambda r: _first(r)['segments'].clear())
    yield damage('the transcript is empty', lambda r: segment(r).update(transcript=''))
    yield damage('other words are transcribed', lambda r: segment(r).update(
        transcript='Play some music in the kitchen, please.'))
    yield damage('half of the sentence is missing', lambda r: segment(r).update(transcript='Send a text to Mike'))
    yield damage('the segment is not final', lambda r: segment(r).update(isFinal=False))
    yield damage('the segment ends after the clip', lambda r: segment(r).update(toMs=9000))
    yield damage('the segment ends before it starts', lambda r: segment(r).update(fromMs=3000, toMs=1000))
    yield damage('the segments are out of order', lambda r: _first(r)['segments'].insert(
        0, {'transcript': '', 'isFinal': True, 'fromMs': 2000, 'toMs': 3000}))
  elif kind == 'speech':
    yield damage('no audio at all', lambda r: _first(r).update(pieces=[], durationSec=0.0, rms=0.0, peak=0.0))
    yield damage('silence', lambda r: _first(r).update(rms=0.0, peak=0.0))
    yield damage('far too short', lambda r: _first(r).update(durationSec=0.4))
    yield damage('far too long', lambda r: _first(r).update(durationSec=40.0))
    yield damage('samples outside of -1 to 1', lambda r: _first(r).update(peak=3.2))
    yield damage('invalid samples', lambda r: _first(r).update(nonFinite=12))
    yield damage('samples are not float32', lambda r: _first(r)['pieces'][0].update(dtype='int16'))
    yield damage('samples are not a flat array', lambda r: _first(r)['pieces'][0].update(ndim=2))
    yield damage('a sample rate of zero', lambda r: [p.update(sampleRateHz=0) for p in _first(r)['pieces']])
    yield damage('pieces are numbered wrongly', lambda r: _first(r)['pieces'][0].update(chunkIndex=5))
    yield damage('the last piece is not marked final', lambda r: _first(r)['pieces'][-1].update(isFinal=False))
    yield damage('speech.wav is missing', lambda r: _first(r).update(wavFile={'exists': False}))
    yield damage('speech.wav is stereo', lambda r: _first(r)['wavFile'].update(channels=2))
    yield damage('speech.wav is 8-bit PCM', lambda r: _first(r)['wavFile'].update(sampleWidth=1))
    yield damage('speech.wav has another sample rate', lambda r: _first(r)['wavFile'].update(sampleRateHz=16000))
    yield damage('speech.wav is truncated', lambda r: _first(r)['wavFile'].update(samples=48000))
    yield damage('speech.wav differs from the returned samples', lambda r: _first(r)['wavFile'].update(maxDiff=0.05))
    yield damage('noise instead of speech', lambda r: _first(r).update(heard='Ye'))
    yield damage('other words are spoken', lambda r: _first(r).update(heard='Play some music in the kitchen please'))
    yield damage('only half of the text is spoken', lambda r: _first(r).update(heard='The quick brown fox jumps'))
    yield damage('nothing is heard', lambda r: _first(r).update(heard=''))
    yield damage('speech recognition failed', lambda r: _first(r).update(heard=None, heardError='RuntimeError: x'))
    if not expectation['stream']:
      stamps = lambda r: _first(r)['wordTimestamps']
      yield damage('two pieces instead of one', lambda r: _first(r)['pieces'].append(
          dict(_first(r)['pieces'][0], chunkIndex=1)))
      yield damage('no word timestamps', lambda r: stamps(r).clear())
      yield damage('a word timestamp is missing', lambda r: stamps(r).pop(3))
      yield damage('word timestamps of other words', lambda r: stamps(r)[0].update(word='A'))
      yield damage('word timestamps run backwards', lambda r: stamps(r)[5].update(startSec=0.0, endSec=0.1))
      yield damage('word timestamps beyond the clip', lambda r: stamps(r)[-1].update(endSec=99.0))
  elif kind == 'segmentation':
    yield damage('a category is missing', lambda r: _first(r)['classes'].pop(0))
    yield damage('a category covers too much', lambda r: _first(r)['classes'][0].update(fraction=0.999))
    if any('centroidWithin' in c for c in expectation['classes']):
      idx = next(i for i, c in enumerate(expectation['classes']) if 'centroidWithin' in c)
      yield damage('a category is in the wrong place', lambda r, idx=idx: _first(r)['classes'][idx].update(
          centroid={'x': 0.99, 'y': 0.99}))
    yield damage('another category appears', lambda r: _first(r)['classes'].append(
        {'index': 9, 'label': 'chair', 'fraction': 0.2, 'centroid': {'x': 0.5, 'y': 0.5}}))
    yield damage('the mask has another size', lambda r: _first(r).update(maskWidth=128))
    yield damage('the category names are missing', lambda r: _first(r).update(labels=[]))
    yield damage('mask.png is missing', lambda r: _first(r).update(maskFile={'exists': False}))
    yield damage('blurred_background.png is missing', lambda r: _first(r).update(blurFile={'exists': False}))
    yield damage('blurred_background.png blurs the foreground', lambda r: _first(r)['blurFile'].update(fgMeanDiff=9.0))
    yield damage('blurred_background.png does not blur the background', lambda r: _first(r)['blurFile'].update(
        bgMeanDiff=0.0))
    if expectation.get('confidence'):
      yield damage('confidence masks are missing', lambda r: _first(r).update(confidence=None))
      yield damage('confidence mask has low peak', lambda r: _first(r)['confidence'].update(max=0.2))
      yield damage('confidence mask has NaNs', lambda r: _first(r)['confidence'].update(nonFinite=3))
      yield damage('mask.png differs from the confidence mask', lambda r: _first(r)['maskFile'].update(maxDiff=0.2))
    else:
      yield damage('mask.png foreground disagrees with category mask', lambda r: _first(r)['maskFile'].update(
          fgDiff=0.2))
  elif kind == 'selection':
    selected = expectation['runs'][0]['selected'][-1]
    other = expectation['runs'][0]['notSelected'][0]
    yield damage('part of the object is not selected', lambda r: _first(r)['probes'].update({selected: False}))
    yield damage('an excluded point is selected', lambda r: _first(r)['probes'].update({other: True}))
    yield damage('nothing is selected', lambda r: _first(r).update(fraction=0.0, centroid=None, bounds=None))
    yield damage('everything is selected', lambda r: _first(r).update(
        fraction=1.0, bounds={'minX': 0, 'maxX': 1, 'minY': 0, 'maxY': 1}))
    yield damage('the selection is elsewhere', lambda r: _first(r).update(centroid={'x': 0.05, 'y': 0.05}))
    yield damage('the mask has four channels of bytes', lambda r: _first(r)['mask'].update(
        channels=4, dtype='uint8', max=255.0))
    yield damage('the mask has NaN values', lambda r: _first(r)['mask'].update(nonFinite=2))
    yield damage('the mask has another size', lambda r: _first(r)['mask'].update(width=256, height=256))
    yield damage('the overlay PNG is missing', lambda r: _first(r).update(overlayFile={'exists': False}))
    yield damage('the overlay PNG was not tinted green', lambda r: _first(r)['overlayFile'].update(
        selectedGreenGain=0.0))
    yield damage('the overlay PNG tinted unselected pixels', lambda r: _first(r)['overlayFile'].update(
        outsideUnchangedFraction=0.2))
    if len(expectation['runs']) > 1:
      yield damage('both selections select the same object', lambda r: r[1].update(data=copy.deepcopy(r[0]['data'])))
      yield damage('the result of one selection is missing', lambda r: r.pop())
  elif kind == 'proofreading':
    yield damage('the text is not corrected', lambda r: _first(r).update(
        proofreadText=expectation['runs'][0]['text'],
        corrections=[{'type': 'SAME', 'text': expectation['runs'][0]['text']}]))
    yield damage('the text is corrected wrongly', lambda r: _first(r).update(proofreadText='I am a stud.'))
    yield damage('the corrections are missing', lambda r: _first(r).update(corrections=[]))
    yield damage('the corrections do not lead to the proofread text', lambda r: _first(r)['corrections'].append(
        {'type': 'INSERTION', 'text': '!!'}))
    yield damage('the corrections do not start from the input', lambda r: _first(r)['corrections'].append(
        {'type': 'DELETION', 'text': 'x'}))
    yield damage('a correction has an unknown type', lambda r: _first(r)['corrections'][0].update(type='REPLACE'))
    yield damage('the second text is left unchanged', lambda r: r[1]['data'].update(
        proofreadText=expectation['runs'][1]['text'],
        corrections=[{'type': 'SAME', 'text': expectation['runs'][1]['text']}]))
    yield damage('the result of one text is missing', lambda r: r.pop())
  elif kind == 'summary':
    yield damage('the summary is empty', lambda r: _first(r).update(summary=''))
    yield damage('the summary is the text itself', lambda r: _first(r).update(summary=expectation['text']))
    yield damage('the summary is about something else', lambda r: _first(r).update(
        summary='* Cats sleep a lot.\n* Dogs like walks.' if expectation['format'] == 'bullets'
        else 'Cats sleep a lot.'))
    yield damage('the city is not mentioned', lambda r: _first(r).update(summary=_first(r)['summary'].replace(
        'Brussels', 'a city')))
    if expectation['format'] == 'bullets':
      yield damage('a paragraph instead of key points', lambda r: _first(r).update(
          summary='The station is in Brussels, next to EU offices.'))
      yield damage('a single key point', lambda r: _first(r).update(
          summary='* The station is in Brussels, next to EU offices.'))
    else:
      yield damage('key points instead of a paragraph', lambda r: _first(r).update(
          summary='* The station is in Brussels.\n* It is next to EU offices.'))


class VerifierTest(unittest.TestCase):
  """Every case of the matrix: a correct output passes, damaged ones fail."""

  def test_correct_and_damaged_outputs(self):
    seen = set()
    for case in cases.build_validation_matrix():
      expectation = expectations.get_expectation(case)
      self.assertIsNotNone(expectation, f"{case['id']} has no expectation")
      seen.add(expectation['type'])
      with self.subTest(case=case['id'], output='correct'):
        verdict = verify.verify_case(expectation, correct_runs(expectation))
        self.assertEqual(verdict['status'], verify.VERIFIED, verdict['reason'])
        self.assertGreaterEqual(len(verdict['checks']), 3)
      damages = list(damaged_runs(expectation))
      self.assertGreaterEqual(len(damages), 5, f"{case['id']} has too few damaged outputs")
      for name, runs in damages:
        with self.subTest(case=case['id'], output=name):
          verdict = verify.verify_case(expectation, runs)
          self.assertEqual(verdict['status'], verify.FAILED, f'"{name}" was accepted as correct')
          self.assertTrue(verdict['reason'])
    self.assertEqual(seen, set(verify.VERIFIERS), 'a verifier is not used by any case')

  def test_no_expectation_is_never_a_pass(self):
    self.assertEqual(verify.verify_case(None, [{'key': 'k', 'data': {}}])['status'], verify.UNVERIFIED)

  def test_instances_are_matched_by_position(self):
    expectation = expectations.get_expectation({'task': 'hand_landmarker'})
    runs = correct_runs(expectation)
    runs[0]['data']['hands'].reverse()
    self.assertEqual(verify.verify_case(expectation, runs)['status'], verify.VERIFIED)

  def test_only_what_the_photo_shows_must_lie_in_the_frame(self):
    expectation = expectations.get_expectation({'task': 'holistic_landmarker'})
    runs = correct_runs(expectation)
    pose = runs[0]['data']['pose'][0]['landmarks']
    for index in range(25, 33):
      pose[index] = [0.5, 1.8]
    self.assertEqual(verify.verify_case(expectation, runs)['status'], verify.VERIFIED)
    pose[13] = [0.5, 1.8]  # An elbow is in the photo.
    self.assertEqual(verify.verify_case(expectation, runs)['status'], verify.FAILED)
    expectation = expectations.get_expectation({'task': 'pose_landmarker'})
    runs = correct_runs(expectation)
    runs[0]['data']['poses'][0]['landmarks'][30] = [0.5, 1.8]
    verdict = verify.verify_case(expectation, runs)
    self.assertEqual(verdict['status'], verify.FAILED)
    self.assertIn('lie in the image', verdict['reason'])

  def test_language_that_shares_its_script_may_rank_second(self):
    expectation = expectations.get_expectation({'task': 'language_detector'})
    runs = correct_runs(expectation)
    runs[0]['data']['categories'] = [{'label': 'ja', 'score': 0.52}, {'label': 'zh', 'score': 0.46}]
    self.assertEqual(verify.verify_case(expectation, runs)['status'], verify.VERIFIED)
    runs[0]['data']['categories'] = [{'label': 'ja', 'score': 0.9}, {'label': 'zh', 'score': 0.1}]
    self.assertEqual(verify.verify_case(expectation, runs)['status'], verify.FAILED)
    runs = correct_runs(expectation)
    runs[1]['data']['categories'] = [{'label': 'de', 'score': 0.52}, {'label': 'en', 'score': 0.46}]
    self.assertEqual(verify.verify_case(expectation, runs)['status'], verify.FAILED)

  def test_any_striped_cat_class_is_accepted(self):
    expectation = expectations.get_expectation({'task': 'image_classifier'})
    for label in expectations.TABBY_CAT_LABELS:
      runs = correct_runs(expectation)
      runs[1]['data']['categories'] = [{'label': label, 'score': 0.3}, {'label': 'tabby', 'score': 0.2}]
      self.assertEqual(verify.verify_case(expectation, runs)['status'], verify.VERIFIED, label)

  def test_helpers(self):
    box = {'x': 0, 'y': 0, 'width': 10, 'height': 10}
    self.assertEqual(verify.box_iou(box, box), 1.0)
    self.assertEqual(verify.box_iou(box, {'x': 20, 'y': 20, 'width': 5, 'height': 5}), 0.0)
    self.assertAlmostEqual(verify.box_iou(box, {'x': 5, 'y': 0, 'width': 10, 'height': 10}), 1 / 3)
    corrections = [{'type': 'SAME', 'text': 'I am a stud'}, {'type': 'INSERTION', 'text': 'e'},
                   {'type': 'DELETION', 'text': 'x'}, {'type': 'SAME', 'text': 'nt.'}]
    self.assertEqual(verify.apply_corrections(corrections, 'INSERTION'), 'I am a student.')
    self.assertEqual(verify.apply_corrections(corrections, 'DELETION'), 'I am a studxnt.')

  def test_error_rates(self):
    self.assertEqual(verify.word_error_rate("Send a text to Mike, I'll be home.", "send a text to mike i'll be home"), 0)
    self.assertAlmostEqual(verify.word_error_rate('a b c d', 'a x c'), 0.5)
    self.assertEqual(verify.word_error_rate('a b', ''), 1.0)
    self.assertEqual(verify.char_error_rate('the quiet river bank.', 'The quiet riverbank'), 0)
    sentence = expectations.TTS_SENTENCE
    for heard in ('The quick brown fox jumps over the lazy dog, near the quiet riverbank.',
                  'The quick brown fox jumps over the lazy dog near the quiet riverbank, and',
                  'The quick brown fox jumps over the lazy dog near the quiet riverbank. The'):
      self.assertLess(verify.char_error_rate(sentence, heard), 0.1, heard)
    self.assertGreater(verify.char_error_rate(sentence, 'Ye'), 0.9)
    self.assertGreater(verify.char_error_rate(sentence, ''), 0.9)


class PolicyTest(unittest.TestCase):
  """How the outcome of a case becomes its status."""

  VERIFIED = {'status': verify.VERIFIED, 'reason': '', 'summary': '9/9 checks'}
  FAILED = {'status': verify.FAILED, 'reason': 'faces found: expected 1, got 0', 'summary': '1/9 checks'}
  UNSUPPORTED = {'error': 'Only CPU delegate is supported.', 'reason': 'Rejected by design.'}
  METAL = ['Created TensorFlow Lite delegate for Metal.']

  def _status(self, delegate, worker, verification, evidence=(), unsupported=None):
    return policy.decide({'delegate': delegate}, worker, verification, list(evidence), unsupported)[0]

  def test_statuses(self):
    self.assertEqual(self._status('CPU', {}, self.VERIFIED), 'PASS')
    self.assertEqual(self._status('GPU', {}, self.VERIFIED, self.METAL), 'PASS')
    self.assertEqual(self._status('CPU', {}, self.FAILED), 'FAIL')
    self.assertEqual(self._status('GPU', {}, self.FAILED, self.METAL), 'FAIL')
    self.assertEqual(self._status('CPU', {'error': 'IndexError: list index out of range'}, self.VERIFIED), 'FAIL')
    self.assertEqual(self._status('CPU', {}, {'status': verify.UNVERIFIED, 'reason': 'none', 'summary': '-'}),
                     'UNVERIFIED')

  def test_gpu_request_needs_evidence_of_the_gpu(self):
    self.assertEqual(self._status('GPU', {}, self.VERIFIED, []), 'GPU_UNCONFIRMED')
    self.assertEqual(self._status('GPU', {}, self.FAILED, []), 'FAIL')
    self.assertEqual(self._status('CPU', {}, self.VERIFIED, self.METAL), 'FAIL')

  def test_known_unsupported(self):
    rejected = {'error': 'ValueError: Only CPU delegate is supported.'}
    self.assertEqual(self._status('GPU', rejected, self.VERIFIED, [], self.UNSUPPORTED), 'UNSUPPORTED')
    crashed = {'error': 'the sample process crashed (signal 11); see the native log'}
    self.assertEqual(self._status('GPU', crashed, self.VERIFIED, [], self.UNSUPPORTED), 'FAIL')
    status, _, note = policy.decide({'delegate': 'GPU'}, {}, self.VERIFIED, self.METAL, self.UNSUPPORTED)
    self.assertEqual(status, 'PASS')
    self.assertIn('KNOWN_UNSUPPORTED', note)
    self.assertEqual(self._status('GPU', {}, self.FAILED, self.METAL, self.UNSUPPORTED), 'FAIL')
    self.assertEqual(self._status('GPU', rejected, self.VERIFIED, []), 'FAIL')

  def test_only_pass_and_unsupported_are_accepted(self):
    self.assertEqual(set(policy.ACCEPTED), {'PASS', 'UNSUPPORTED'})

  def test_gpu_evidence(self):
    gpu_log = '\n'.join([
        'I0000 00:00:1795104000.123456  259 gl_context.cc:408] GL version: 2.1 (2.1 Metal - 91.7), renderer: Apple M4 Pro',
        'INFO: Created TensorFlow Lite delegate for Metal.',
        'INFO: Created TensorFlow Lite XNNPACK delegate for CPU.',
    ])
    self.assertEqual(policy.find_gpu_evidence(gpu_log), ['INFO: Created TensorFlow Lite delegate for Metal.'])
    litert_log = '\n'.join([
        'INFO: [gpu_environment.cc:516] Failed to create OpenCL context.',
        'I0000 00:00:1795104000.223456  259 delegate_metal.mm:89] Created a Metal device.',
        'I0000 00:00:1795104000.323456  259 delegate_kernel.cc:1022] Initializing Metal-based API from graph.',
    ])
    self.assertEqual(len(policy.find_gpu_evidence(litert_log)), 2)
    cpu_log = '\n'.join([
        'I0000 00:00:1795104000.123456  259 gl_context.cc:408] GL version: 2.1 (2.1 Metal - 91.7), renderer: Apple M4 Pro',
        'INFO: [gpu_environment.cc:516] Failed to create OpenCL context.',
        'INFO: Created TensorFlow Lite XNNPACK delegate for CPU.',
        'INFO: [cpu_registry.cc:75] XNNPACK CPU accelerator registered.',
        'INFO: [gpu_registry.cc:121] Statically linked GPU accelerator registered.',
    ])
    self.assertEqual(policy.find_gpu_evidence(cpu_log), [])
    self.assertEqual(policy.find_gpu_evidence(''), [])

  def test_digest_ignores_timing_and_rounding_noise(self):
    runs = [{'key': 'a', 'seconds': 1.5, 'data': {'similarity': 0.91912345, 'labels': ['x', 'y']}}]
    same = [{'data': {'labels': ['x', 'y'], 'similarity': 0.91912399}, 'seconds': 9.9, 'key': 'a'}]
    other = [{'key': 'a', 'seconds': 1.5, 'data': {'similarity': 0.9201, 'labels': ['x', 'y']}}]
    self.assertEqual(policy.observation_digest(runs), policy.observation_digest(same))
    self.assertNotEqual(policy.observation_digest(runs), policy.observation_digest(other))


class MatrixTest(unittest.TestCase):
  """The inventory is complete and consistent."""

  def test_matrix(self):
    matrix = cases.build_validation_matrix()
    ids = [case['id'] for case in matrix]
    self.assertEqual(len(ids), len(set(ids)))
    small = cases.build_validation_matrix(include_large_models=False)
    self.assertEqual(len(small), 128)
    self.assertFalse(any(case['largeModel'] for case in small))
    self.assertEqual(len(cases.TASK_DEFINITIONS), 22)
    # Large models: speech recognition (2 modes), speech synthesis (4 models, 2 modes), embedding_gemma,
    # universal embedder (3 modes), semantic retriever and decision maker (2 modes); 2 delegates each = 34.
    self.assertEqual(len(matrix), 128 + 34)
    self.assertEqual(len(cases.build_validation_matrix(delegates=['CPU'])), 81)
    self.assertEqual({case['domain'] for case in matrix}, {'vision', 'audio', 'text', 'retrieval', 'decision'})

  def test_filters(self):
    tasks = lambda f: {case['task'] for case in cases.build_validation_matrix(f)}
    self.assertEqual(tasks('audio'), {'audio_classifier', 'audio_transcriber', 'text_to_speech'})
    self.assertEqual(tasks('text/text_embedder, face_detector'), {'text_embedder', 'face_detector'})
    self.assertEqual(tasks('text'), {'text_classifier', 'language_detector', 'text_embedder', 'text_proofreader',
                                     'text_summarizer'})
    self.assertEqual(cases.unknown_task_filters('vision, face_detectr,audio/nope'), ['face_detectr', 'audio/nope'])
    self.assertEqual(cases.unknown_task_filters(''), [])

  def test_every_case_can_run(self):
    with open(os.path.join(SCRIPT_DIR, 'worker.py')) as f:
      tree = ast.parse(f.read())
    observers = next(
        {key.value for key in node.value.keys} for node in tree.body
        if isinstance(node, ast.Assign) and getattr(node.targets[0], 'id', '') == 'OBSERVERS')
    for case in cases.build_validation_matrix():
      with self.subTest(case=case['id']):
        self.assertTrue(os.path.isfile(os.path.join(REPO_DIR, case['sample'])), case['sample'])
        self.assertIn(case['modelAsset'], assets.ASSETS)
        plan = expectations.get_plan(case)
        self.assertIn(plan['observer'], observers)
        self.assertTrue(plan['invocations'])
        keys = [invocation['key'] for invocation in plan['invocations']]
        self.assertEqual(len(keys), len(set(keys)))
        for invocation in plan['invocations']:
          self.assertIn('@model', invocation['args'])
          self.assertIn('@delegate', invocation['args'])
          for value in invocation['args']:
            if value.startswith('@asset:'):
              self.assertIn(value[len('@asset:'):], assets.ASSETS)
        description = expectations.describe_case(case)
        self.assertTrue(description['inputs'])
        for item in description['inputs']:
          if 'asset' in item:
            self.assertIn(item['asset'], assets.ASSETS)

  def test_assets_are_pinned(self):
    for name, spec in assets.ASSETS.items():
      with self.subTest(asset=name):
        self.assertRegex(spec['sha256'], r'^[0-9a-f]{64}$')
        self.assertRegex(spec['url'], r'^https://(storage\.googleapis\.com/mediapipe-|'
                                      r'huggingface\.co/litert-community/[\w.-]+/resolve/[0-9a-f]{40}/)')
        self.assertGreater(spec['bytes'], 0)
    used = {case['modelAsset'] for case in cases.build_validation_matrix()}
    for case in cases.build_validation_matrix():
      for invocation in expectations.get_plan(case)['invocations']:
        used.update(v[len('@asset:'):] for v in invocation['args'] if v.startswith('@asset:'))
    self.assertEqual(used, set(assets.ASSETS), 'an asset is pinned but not used, or the reverse')

  def test_derived_files_are_removed(self):
    with tempfile.TemporaryDirectory() as directory:
      names = ['yamnet.tflite', 'kokoro_int8_fp16.litertlm_1795107260_294783298.xnnpack_cache', 'pose.jpg.unexpected',
               'notes.txt']
      for name in names:
        with open(os.path.join(directory, name), 'w') as f:
          f.write('x')
      os.mkdir(os.path.join(directory, 'subdirectory'))
      removed = assets.remove_derived_files(directory)
      self.assertEqual(removed, ['kokoro_int8_fp16.litertlm_1795107260_294783298.xnnpack_cache', 'notes.txt'])
      self.assertEqual(sorted(os.listdir(directory)), ['pose.jpg.unexpected', 'subdirectory', 'yamnet.tflite'])

  def test_known_unsupported(self):
    case = lambda task, model, delegate: {'task': task, 'modelId': model, 'delegate': delegate}
    self.assertIsNotNone(cases.known_unsupported(case('text_summarizer', 'gemma_200m', 'GPU')))
    self.assertIsNone(cases.known_unsupported(case('text_summarizer', 'gemma_200m', 'CPU')))
    self.assertIsNone(cases.known_unsupported(case('face_detector', 'blaze_face_short_range', 'GPU')))
    self.assertIsNotNone(cases.known_unsupported(case('text_to_speech', 'pocket_tts_en', 'GPU')))
    self.assertIsNone(cases.known_unsupported(case('text_to_speech', 'kokoro_82m_gpu', 'GPU')))
    self.assertIsNotNone(cases.known_unsupported(case('text_classifier', 'bert_classifier', 'GPU'), system='Darwin'))
    self.assertIsNone(cases.known_unsupported(case('text_classifier', 'bert_classifier', 'GPU'), system='Linux'))
    int8 = case('object_detector', 'efficientdet_lite0_int8', 'GPU')
    self.assertIsNotNone(cases.known_unsupported(int8, system='Darwin'))
    self.assertIsNone(cases.known_unsupported(int8, system='Linux'))
    self.assertIsNone(cases.known_unsupported(case('object_detector', 'efficientdet_lite0_float32', 'GPU'), 'Darwin'))
    for entry in cases.KNOWN_UNSUPPORTED:
      self.assertTrue(entry['error'] and entry['reason'])
      self.assertTrue(any(d['task'] == entry['task'] for d in cases.TASK_DEFINITIONS))


def run(verbosity=0):
  """Runs the self-test. Returns whether it passed."""
  suite = unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
  result = unittest.TextTestRunner(verbosity=verbosity, stream=sys.stdout).run(suite)
  return result.wasSuccessful()


if __name__ == '__main__':
  sys.exit(0 if run(verbosity=2) else 1)
