# Install Python dependencies.
python3 -m pip install pip --upgrade
python3 -m pip install -r requirements.txt

# You can try out the other models here: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker#models
wget -O pose_landmarker.task -q https://huggingface.co/litert-community/MediaPipe-PoseLandmarker-Heavy/resolve/main/pose_landmarker_heavy.task
