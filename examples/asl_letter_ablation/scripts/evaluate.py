#!/usr/bin/env python3
"""Run reproducible baseline or MediaPipe crop experiment; save JSON + per-image CSV.

Example:
  python scripts/evaluate.py --split val --method full --output results/baseline_val.json
  python scripts/evaluate.py --split val --method crop --output results/crop_val.json
"""
import argparse
import csv
import json
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from asl_project import LETTERS, MODEL_ID
from asl_project.metrics import summarize


def select_rows(manifest, split, max_per_letter):
    with Path(manifest).open(newline='') as f:
        rows = [r for r in csv.DictReader(f) if r['split'] == split and r['label'] in LETTERS]
    rows.sort(key=lambda r: (r['label'], r['image']))
    if max_per_letter:
        keep = []
        counts = {}
        for r in rows:
            counts.setdefault(r['label'], 0)
            if counts[r['label']] < max_per_letter:
                keep.append(r)
                counts[r['label']] += 1
        rows = keep
    if not rows:
        raise RuntimeError('Zero matching rows. Verify manifest and split or change --split.')
    return rows


def evaluate(manifest, split, method, output, max_per_letter=None, batch_size=16, task=None, margin=0.25):
    from PIL import Image
    from asl_project.inference import Classifier
    from asl_project.crop import HandCropper
    rows = select_rows(manifest, split, max_per_letter)
    cropper = HandCropper(task or 'data/raw/hand_landmarker.task', margin) if method == 'crop' else None
    classifier = Classifier()
    inference_photos = []
    inference_indices = []
    records = []
    try:
        for row in rows:
            with Image.open(row['image']) as i:
                photo = i.convert('RGB')
            if cropper:
                photo = cropper.crop(photo)
            rec = {'image': row['image'], 'label': row['label'],
                   'background': row.get('background', 'unknown'),
                   'signer_id': row.get('signer_id',''),
                   'predicted': None, 'detection_failed': photo is None}
            idx = len(records)
            records.append(rec)
            if photo is not None:
                inference_indices.append(idx)
                inference_photos.append(photo)
        predictions = classifier.predict(inference_photos, batch_size=batch_size)
        for idx, guess in zip(inference_indices, predictions):
            records[idx]['predicted'] = guess
    finally:
        if cropper:
            cropper.close()
    metrics = summarize(records)
    result = {'model': MODEL_ID, 'labels_evaluated': list(LETTERS),
              'prediction_rule': 'argmax over all 26 pretrained output classes; 14 out-of-scope outputs count wrong',
              'method': 'full_original_frame' if method == 'full' else 'mediapipe_automatic_hand_crop',
              'split': split, 'manifest': str(manifest),
              'max_per_letter': max_per_letter, 'crop_margin': margin if method == 'crop' else None,
              'research_limitations': 'Public dataset supplies no signer IDs. Not signer-disjoint; plain/clutter only if manually annotated.',
              **metrics}
    target = Path(output)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(result, indent=2) + '\n')
    with target.with_name(target.stem + '_predictions.csv').open('w',newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['image','label','predicted','detection_failed','background','signer_id'])
        writer.writeheader()
        writer.writerows(records)
    print(json.dumps(result,indent=2))
    print(f'WROTE {target} and {target.with_name(target.stem + "_predictions.csv")}')
    return result


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--manifest', default='data/processed/manifest.csv')
    p.add_argument('--split', choices=['train','val','test'], default='val')
    p.add_argument('--method', choices=['full','crop'], required=True)
    p.add_argument('--output', required=True)
    p.add_argument('--max-per-letter', type=int, default=None)
    p.add_argument('--batch-size', type=int, default=16)
    p.add_argument('--task', default='data/raw/hand_landmarker.task')
    p.add_argument('--crop-margin', type=float, default=0.25)
    a = p.parse_args()
    evaluate(a.manifest, a.split, a.method, a.output, a.max_per_letter, a.batch_size,a.task,a.crop_margin)
