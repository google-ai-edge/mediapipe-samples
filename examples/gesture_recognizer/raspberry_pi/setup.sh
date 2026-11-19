# Install Python dependencies.
python3 -m pip install pip --upgrade
python3 -m pip install -r requirements.txt

wget -O gesture_recognizer.task -q https://huggingface.co/litert-community/MediaPipe-GestureRecognizer/resolve/main/gesture_recognizer.task
