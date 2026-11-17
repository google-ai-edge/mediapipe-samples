#!/usr/bin/env python3
"""Download the PUBLIC DOMAIN ASL Letters VOC data from University of Waikato.

This dataset includes original images + bounding boxes, unlike the already
cropped 'Spelling It Out' Kaggle dataset. The latter is not suitable for a
full-frame vs automatic crop ablation. No Kaggle login required.
"""
import argparse
from pathlib import Path
import requests

URL = ('https://datasets.cms.waikato.ac.nz/ufdl/data/'
       'american-sign-language-letters/american-sign-language-letters-voc.zip')


def download(url, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    if dest.exists() and dest.stat().st_size > 1_000_000:
        print(f'Using cached {dest} ({dest.stat().st_size:,} bytes)')
        return
    tmp = dest.with_suffix('.part')
    with requests.get(url, stream=True, timeout=120,
                      headers={'User-Agent': 'Mozilla/5.0 CS117 university research'}) as r:
        r.raise_for_status()
        with tmp.open('wb') as f:
            for chunk in r.iter_content(1024 * 1024):
                if chunk:
                    f.write(chunk)
    import zipfile
    if not zipfile.is_zipfile(tmp):
        raise RuntimeError(f'Downloaded content is not a ZIP: {tmp}. Check dataset link.')
    tmp.replace(dest)
    print(f'Downloaded {dest} ({dest.stat().st_size:,} bytes)')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--output', default='data/raw/asl_letters_voc.zip')
    a = p.parse_args()
    download(URL, Path(a.output))
