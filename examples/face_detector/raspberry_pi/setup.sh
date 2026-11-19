# Install Python dependencies.
python3 -m pip install pip --upgrade
python3 -m pip install -r requirements.txt

wget -q -O detector.tflite -q https://huggingface.co/litert-community/MediaPipe-FaceDetector-BlazeFace-Short-Range/resolve/main/blaze_face_short_range.tflite
