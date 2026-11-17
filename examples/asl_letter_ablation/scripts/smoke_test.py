#!/usr/bin/env python3
"""Smoke test: 3 genuine hand sign images and optional hand crops.

Outputs a text report to commit. Run after downloading and preparing the data.
"""
import argparse
import csv
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from asl_project import LETTERS

p=argparse.ArgumentParser()
p.add_argument('--manifest',default='data/processed/manifest.csv')
p.add_argument('--output',default='results/smoke_test.txt')
p.add_argument('--check-crops',action='store_true')
a=p.parse_args()
from PIL import Image
from asl_project.inference import Classifier
from asl_project.crop import HandCropper
with open(a.manifest,newline='') as f:
    rows=[r for r in csv.DictReader(f) if r['split']=='val' and r['label'] in LETTERS]
unique={}
for r in rows:
    unique.setdefault(r['label'],r)
selected=list(unique.values())[:3]
if len(selected)<3:
    raise RuntimeError('Need 3 validation photos from distinct ASL letters')
model=Classifier()
photos=[]
for r in selected:
    with Image.open(r['image']) as f:
        photos.append(f.convert('RGB'))
preds=model.predict(photos)
lines=['ASL real-image smoke test (NOT an accuracy benchmark)',
       'Input source: public Waikato ASL Letters VOC validation split',
       'Classifier: abdollahhh/asl-sign-language-efficientnet-b0; 26-way argmax']
cropper=HandCropper('data/raw/hand_landmarker.task') if a.check_crops else None
try:
    for r,photo,pred in zip(selected,photos,preds):
        msg=f'letter={r["label"]} full_prediction={pred} image={Path(r["image"]).name}'
        if cropper:
            hand=cropper.crop(photo)
            msg+=f' mediapipe_hand_detected={hand is not None}'
            if hand:
                msg+=f' crop_prediction={model.predict([hand])[0]}'
        lines.append(msg)
finally:
    if cropper: cropper.close()
Path(a.output).parent.mkdir(parents=True,exist_ok=True)
Path(a.output).write_text('\n'.join(lines)+'\n')
print('\n'.join(lines))
