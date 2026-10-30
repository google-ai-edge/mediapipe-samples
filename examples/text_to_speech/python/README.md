# MediaPipe Text to Speech — Python Sample

This sample shows how to run on-device text-to-speech (TTS) with the MediaPipe
Tasks `TextToSpeech` API in Python. It uses the
[Kokoro 82M](https://huggingface.co/litert-community/Kokoro-82M) model in the
LiteRT-LM format (`kokoro_int8_gpu_fp16.litertlm`, ~295 MB, Apache 2.0), which
includes 54 voices in 9 languages and produces 24 kHz audio.

The sample covers:

- **Synthesis**: turn text into speech with `synthesize()`, play it, save it
  as a 16-bit `.wav` file, and print word-level timestamps.
- **Streaming synthesis**: get audio clause by clause with
  `synthesize_stream()` for a low time to first audio.
- **Incremental input**: push text piece by piece (for example, from a
  language model) with `start_stream()`, `push_text()`, and
  `finish_input()`.
- **GPU or CPU**: run on `Delegate.GPU` with an automatic fallback to
  `Delegate.CPU`.

## Notebook

Open [`text_to_speech.ipynb`](text_to_speech.ipynb) in
[Colab](https://colab.research.google.com/github/google-ai-edge/mediapipe-samples/blob/main/examples/text_to_speech/python/text_to_speech.ipynb)
or a local Jupyter server, then run the cells in order. The notebook downloads
the model.

## Command-line script

Requires Python 3.10 or newer.

```bash
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt

# Synthesize the default sentence to tts_output.wav (downloads the model on first run).
python text_to_speech.py

# Pick a voice and stream the synthesis on the CPU.
python text_to_speech.py "Streaming starts playback early. Each clause is its own chunk." \
    --voice bm_george --delegate cpu --stream

# List all voices.
python text_to_speech.py --list_voices
```

Options:

- `--model PATH`: path to a `.litertlm` model. By default, the Kokoro model is
  downloaded into the current directory if it isn't there yet.
- `--voice ID`: voice to use (default: `af_heart`). The task speaks the voice's
  language, so pick a voice that matches your text.
- `--speaking_rate X`: speaking speed, where `1.0` is normal.
- `--delegate {gpu,cpu}`: hardware to run on (default: `gpu`, which falls back
  to CPU if no GPU is available).
- `--stream`: synthesize clause by clause and report when each chunk is ready.
- `--output PATH`: output `.wav` file (default: `tts_output.wav`).

## Notes

- Audio is returned as mono `float32` samples in `[-1.0, 1.0]` at 24 kHz.
- The sample sets `cache_directory` to a folder in the system temp directory.
  The task stores compiled kernels and weights there (~85 MB) to start up
  faster next time. Without it, these files are written next to the model.
- In streaming mode, word timestamps and character offsets are relative to the
  start of the stream and the full input text.
- On Linux, `Delegate.GPU` uses WebGPU over Vulkan. The user running the
  sample needs access to the GPU render node (for example, by being in the
  `render` group); otherwise Vulkan can fall back to a slow software
  implementation.
