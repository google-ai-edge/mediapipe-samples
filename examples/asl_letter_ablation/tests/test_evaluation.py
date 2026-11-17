"""End-to-end evidence-file test using deterministic fakes, NOT pretrained weights.

This validates the data/scoring/output mechanics only. Its test accuracy must
NEVER be submitted as an experimental baseline.
"""
import csv
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from types import ModuleType
import sys
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'scripts'))
from evaluate import evaluate


class StubClassifier:
    def __init__(self, *args, **kwargs):
        pass

    def predict(self, images, batch_size=16):
        return ['A', 'X'][:len(images)]


class StubCropper:
    def __init__(self, *args, **kwargs):
        pass

    def crop(self, im):
        return im

    def close(self):
        pass


class EvaluationTests(unittest.TestCase):
    def test_accuracy_json_and_csv_use_same_two_images(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            for letter in ('A', 'B'):
                Image.new('RGB', (16, 16), color='white').save(folder / f'{letter}.png')
            manifest = folder / 'manifest.csv'
            with manifest.open('w', newline='') as f:
                w = csv.DictWriter(f, fieldnames=['image', 'label', 'split', 'background', 'signer_id'])
                w.writeheader()
                for letter in ('A', 'B'):
                    w.writerow({'image':str(folder / f'{letter}.png'), 'label':letter,
                                'split':'val','background':'unknown','signer_id':''})
            output = folder / 'baseline_val.json'
            fake_inference = ModuleType('asl_project.inference')
            fake_inference.Classifier = StubClassifier
            fake_crop = ModuleType('asl_project.crop')
            fake_crop.HandCropper = StubCropper
            with patch.dict(sys.modules, {'asl_project.inference':fake_inference,
                                          'asl_project.crop':fake_crop}):
                result = evaluate(manifest, 'val', 'full', output)
            self.assertEqual(result['images_evaluated'], 2)
            self.assertEqual(result['correct'], 1)
            self.assertEqual(result['top1_accuracy_pct'], 50.0)
            self.assertEqual(json.loads(output.read_text())['images_evaluated'], 2)
            with (folder / 'baseline_val_predictions.csv').open() as f:
                self.assertEqual(len(list(csv.DictReader(f))), 2)


if __name__ == '__main__':
    unittest.main()
