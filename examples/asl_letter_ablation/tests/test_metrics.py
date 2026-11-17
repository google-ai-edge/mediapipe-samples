import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'src'))
from asl_project.metrics import summarize, normalized_square_box
from asl_project import LETTERS

class MetricTests(unittest.TestCase):
    def test_selected_letters_match_proposal(self):
        self.assertEqual(LETTERS, ('A','B','C','D','E','F','L','O','R','S','V','Y'))
    def test_out_of_scope_26_class_outputs_are_wrong(self):
        results=[{'label':'A','predicted':'A','background':'plain'},
                 {'label':'B','predicted':'X','background':'cluttered'},
                 {'label':'C','predicted':None,'detection_failed':True,'background':'plain'}]
        m=summarize(results)
        self.assertEqual(m['images_evaluated'],3)
        self.assertAlmostEqual(m['top1_accuracy_pct'],33.33,places=2)
        self.assertEqual(m['detection_failures'],1)
        self.assertAlmostEqual(m['by_background']['plain']['top1_accuracy_pct'],50.0)
    def test_no_background_guessing(self):
        m=summarize([{'label':'A','predicted':'A','background':'unknown'}])
        self.assertEqual(m['by_background'],{})
        self.assertFalse(m['background_metadata_complete'])
    def test_landmark_box_within_image(self):
        box=normalized_square_box([(0.0,0.0),(0.1,0.2)],(320,240))
        self.assertEqual(box[0],0)
        self.assertTrue(box[2]<=320 and box[3]<=240)
    def test_empty_not_measurement(self):
        with self.assertRaises(ValueError): summarize([])

if __name__=='__main__':unittest.main()
