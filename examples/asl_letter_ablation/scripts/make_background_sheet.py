#!/usr/bin/env python3
"""Create a CSV for human auditing background types, without peeking at results.

Open the output sheet and manually enter plain/cluttered per validation image.
Dataset provides no official plain/clutter labels: never fabricate them.
"""
import argparse
import csv
from pathlib import Path

p = argparse.ArgumentParser()
p.add_argument('--manifest', default='data/processed/manifest.csv')
p.add_argument('--split', default='val')
p.add_argument('--output', default='docs/background_audit.csv')
a = p.parse_args()
with open(a.manifest, newline='') as f:
    rows = [x for x in csv.DictReader(f) if x['split'] == a.split]
rows = sorted(rows,key=lambda x:(x['label'], x['image']))
Path(a.output).parent.mkdir(parents=True,exist_ok=True)
with open(a.output,'w',newline='') as f:
    w=csv.DictWriter(f,fieldnames=['image','stable_id','letter','background','notes'])
    w.writeheader()
    for r in rows:
        w.writerow({'image':r['image'],'stable_id':r.get('stable_id',''),'letter':r['label'],'background':'', 'notes':''})
print(f'Wrote {len(rows)} rows to {a.output}; fill only plain/cluttered as observed, not guessed.')
