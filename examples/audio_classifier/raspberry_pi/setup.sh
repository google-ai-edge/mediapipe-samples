# Install the PortAudio library.
sudo apt update
sudo apt-get install libportaudio2

# Install Python dependencies.
python3 -m pip install pip --upgrade
python3 -m pip install -r requirements.txt

wget -O yamnet.tflite -q https://huggingface.co/litert-community/MediaPipe-AudioClassifier-YAMNet/resolve/main/yamnet.tflite
