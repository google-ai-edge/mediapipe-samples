#!/usr/bin/env python3
"""Prepare ASL Letters Pascal VOC images as a documented CSV manifest.

Uses an existing train/valid/test directory as the published split. If the
archive does not contain splits, creates deterministic image-level splits.
These are NOT signer-disjoint without metadata and MUST NOT be reported so.
"""
import argparse
import csv
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile

import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'src'))
from asl_project import LETTERS


def extract_zip(archive: Path, target: Path):
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as z:
        for member in z.infolist():
            dest = (target / member.filename).resolve()
            if not dest.is_relative_to(target.resolve()):
                raise ValueError('Unsafe path in ZIP')
        z.extractall(target)


def read_label(xml_file):
    root = ET.parse(xml_file).getroot()
    labels = [x.text.strip().upper() for x in root.findall('./object/name') if x.text]
    labels = sorted(set(labels))
    if len(labels) != 1 or labels[0] not in LETTERS:
        return None
    return labels[0]


def infer_split(path):
    for part in path.parts:
        value = part.lower()
        if value in ('test', 'testing'):
            return 'test'
        if value in ('valid', 'validation', 'val'):
            return 'val'
        if value in ('train', 'training'):
            return 'train'
    return None


def fallback_split(filename, label):
    # Image-level only; cannot guarantee signer separation or near-duplicate independence.
    hashed = hashlib.sha256(('CS117-seed-117|' + label + '|' + filename).encode()).digest()
    p = int.from_bytes(hashed[:8], 'big') / (2**64)
    return 'train' if p < 0.70 else ('val' if p < 0.85 else 'test')


def prepare(source: Path, output: Path):
    records = []
    xmls = sorted(source.rglob('*.xml'))
    if not xmls:
        raise RuntimeError('No VOC XML annotations found. Use the VOC ZIP (not subdir classification ZIP).')
    for xml_file in xmls:
        label = read_label(xml_file)
        if not label:
            continue
        # Pascal VOC annotations have a filename field, but the dataset may have
        # duplicate basenames in different split directories. Prefer same folder.
        root = ET.parse(xml_file).getroot()
        declared = (root.findtext('filename') or '').strip()
        candidates = [xml_file.with_suffix(ext) for ext in ('.jpg','.jpeg','.png','.JPG','.JPEG','.PNG')]
        if declared:
            candidates.insert(0, xml_file.parent / Path(declared).name)
        photo = next((q for q in candidates if q.is_file()), None)
        if photo is None:
            # Roboflow exports may use nested 'images'/'annotations' folders.
            matches = [p for p in source.rglob(xml_file.stem + '.*')
                       if p.suffix.lower() in ('.jpg','.jpeg','.png')]
            photo = matches[0] if len(matches) == 1 else None
        if photo is None:
            continue
        sp = infer_split(photo.relative_to(source)) or infer_split(xml_file.relative_to(source))
        # Store project-relative paths when run inside the repository. This is
        # stable across teammates' clones/Colab working directories.
        # For temporary external fixtures, preserve a usable absolute path.
        try:
            image_path = photo.resolve().relative_to(Path.cwd().resolve()).as_posix()
        except ValueError:
            image_path = photo.resolve().as_posix()
        records.append({'image': image_path, 'label': label,
                        'stable_id': photo.relative_to(source).as_posix(),
                        'split': sp or '', 'background': 'unknown', 'signer_id': ''})
    if not records:
        raise RuntimeError('No usable (single ASL letter + image) pairs found in VOC ZIP')
    # Require published split consistency; if a few samples miss split, fill by hash.
    for r in records:
        r['split'] = r['split'] or fallback_split(r['stable_id'], r['label'])
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=list(records[0]))
        writer.writeheader()
        writer.writerows(records)
    print(f'Manifest: {output}')
    print(f'Labels: {len(set(r["label"] for r in records))}; usable images: {len(records)}')
    for sp in ('train','val','test'):
        print(sp, sum(r['split'] == sp for r in records))
    if not any(r['signer_id'] for r in records):
        print('WARNING: no signer IDs; split does NOT substantiate unseen-signer claims.')
    return records


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--archive', default='data/raw/asl_letters_voc.zip')
    p.add_argument('--extract-to', default='data/raw/asl_letters_voc')
    p.add_argument('--manifest', default='data/processed/manifest.csv')
    args = p.parse_args()
    extract_zip(Path(args.archive), Path(args.extract_to))
    prepare(Path(args.extract_to), Path(args.manifest))
