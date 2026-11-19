# Install Python dependencies.
python3 -m pip install pip --upgrade
python3 -m pip install -r requirements.txt

wget -q -O efficientdet.tflite -q https://huggingface.co/litert-community/MediaPipe-ObjectDetector-EfficientDet-Lite0/resolve/main/efficientdet_lite0_int8.tflite
