# Install Python dependencies.
python3 -m pip install pip --upgrade
python3 -m pip install -r requirements.txt

wget -O classifier.tflite -q https://huggingface.co/litert-community/MediaPipe-TextClassifier-MobileBERT/resolve/main/bert_classifier.tflite
