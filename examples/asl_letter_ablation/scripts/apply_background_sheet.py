#!/usr/bin/env python3
"""Apply human-audited background tags to manifest, preserving train/val/test.
Must be done before viewing model errors to avoid labeling bias.
"""
import csv
from pathlib import Path

MANIFEST=Path('data/processed/manifest.csv')
AUDIT=Path('docs/background_audit.csv')
if not AUDIT.exists():
    raise SystemExit('No docs/background_audit.csv; run make_background_sheet.py first.')
with AUDIT.open(newline='') as f:
    annotations={(r.get('stable_id') or r['image']): r['background'].strip().lower()
                 for r in csv.DictReader(f)}
for path, value in annotations.items():
    if value and value not in ('plain','cluttered'):
        raise ValueError(f'Unexpected background for {path}: {value!r}')
with MANIFEST.open(newline='') as f:
    rows=list(csv.DictReader(f))
updated=0
for r in rows:
    x=annotations.get(r.get('stable_id') or r['image'],'')
    if x:
        r['background']=x
        updated+=1
with MANIFEST.open('w',newline='') as f:
    w=csv.DictWriter(f,fieldnames=list(rows[0])); w.writeheader();w.writerows(rows)
print(f'Applied {updated} audited labels. Unlabeled rows remain unknown.')
