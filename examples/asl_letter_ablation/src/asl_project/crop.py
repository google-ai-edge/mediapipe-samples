"""MediaPipe Tasks hand-ROI from still image; no annotations used for inference."""
from pathlib import Path
import urllib.request
import numpy as np
from PIL import Image
from .metrics import normalized_square_box

TASK_URL = ('https://storage.googleapis.com/mediapipe-models/hand_landmarker/'
            'hand_landmarker/float16/1/hand_landmarker.task')


def obtain_task(path):
    path = Path(path)
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        print(f'Downloading MediaPipe hand landmarker to {path}')
        urllib.request.urlretrieve(TASK_URL, str(path))
    return str(path)


class HandCropper:
    def __init__(self, task_path, margin=0.25):
        import mediapipe as mp
        self.mp = mp
        self.margin = margin
        self.landmarker = mp.tasks.vision.HandLandmarker.create_from_options(
            mp.tasks.vision.HandLandmarkerOptions(
                base_options=mp.tasks.BaseOptions(model_asset_path=obtain_task(task_path)),
                running_mode=mp.tasks.vision.RunningMode.IMAGE,
                num_hands=1,
                min_hand_detection_confidence=0.5,
            ))

    def crop(self, pil_image):
        rgb = pil_image.convert('RGB')
        frame = np.asarray(rgb)
        result = self.landmarker.detect(self.mp.Image(image_format=self.mp.ImageFormat.SRGB, data=frame))
        if not result.hand_landmarks:
            return None
        points = [(pt.x,pt.y) for pt in result.hand_landmarks[0]]
        box = normalized_square_box(points, rgb.size, self.margin)
        return rgb.crop(box) if box else None

    def close(self):
        self.landmarker.close()
