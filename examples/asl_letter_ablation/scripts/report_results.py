#!/usr/bin/env python3
"""Build table from MEASURED JSON only; never fill with guessed accuracies."""
import csv
import json
from pathlib import Path

files={'Full frame (baseline)':'results/baseline_val.json',
       'MediaPipe hand crop':'results/crop_val.json'}
rows=[]
for method,path in files.items():
    if Path(path).exists():
        obj=json.loads(Path(path).read_text())
        bgs=obj['by_background']
        rows.append({'Method':method,'Plain BG top-1 (%)': bgs.get('plain',{}).get('top1_accuracy_pct','PENDING'),
                     'Cluttered BG top-1 (%)': bgs.get('cluttered',{}).get('top1_accuracy_pct','PENDING'),
                     'Overall top-1 (%)': obj['top1_accuracy_pct'],
                     'N':obj['images_evaluated'],
                     'Hand detection failure (%)': obj['detection_failure_rate_pct'] if 'crop' in method.lower() else 'N/A'})
    else:
        rows.append({'Method':method,'Plain BG top-1 (%)':'PENDING',
                     'Cluttered BG top-1 (%)':'PENDING','Overall top-1 (%)':'PENDING',
                     'N':'PENDING','Hand detection failure (%)':'PENDING'})
output=Path('results/main_table.csv')
with output.open('w',newline='') as f:
    w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
print('Saved',output)
for r in rows:print(r)
