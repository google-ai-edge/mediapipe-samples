# Install Python dependencies.
python3 -m pip install pip --upgrade
python3 -m pip install -r requirements.txt

wget -q -O hand_landmarker.task https://huggingface.co/litert-community/MediaPipe-HandLandmarker-Full/resolve/main/hand_landmarker.task
