import csv
import tempfile
import unittest
from pathlib import Path
from PIL import Image
import sys
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'scripts'))
from prepare_dataset import prepare, infer_split

class ManifestTests(unittest.TestCase):
    def test_manifest_from_real_voc_format(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)/'voc'; d=root/'valid';d.mkdir(parents=True)
            Image.new('RGB',(64,64),'white').save(d/'example.jpg')
            (d/'example.xml').write_text('<annotation><filename>example.jpg</filename><object><name>A</name></object></annotation>')
            out=Path(temp)/'manifest.csv'
            rows=prepare(root,out)
            self.assertEqual(rows[0]['label'],'A')
            self.assertEqual(rows[0]['split'],'val')
            with out.open() as f:
                self.assertEqual(len(list(csv.DictReader(f))),1)
            self.assertEqual(rows[0]['stable_id'],'valid/example.jpg')

if __name__=='__main__':unittest.main()
