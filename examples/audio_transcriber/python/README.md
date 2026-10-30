# MediaPipe Audio Transcriber — Python Sample

This sample shows how to run on-device automatic speech recognition (ASR) with
the MediaPipe Tasks `AudioTranscriber` API in Python. It uses the
[Parakeet TDT 0.6B v3](https://huggingface.co/litert-community/parakeet-tdt-0.6b-v3)
model in the LiteRT-LM format
(`parakeet_tdt_0.6b_v3_5s_i8_stateful.litertlm`, ~615 MB, CC-BY-4.0).

The sample covers:

- **Batch transcription**: transcribe a whole clip with
  `transcribe(samples, end_of_stream=True)` and print timestamped segments.
- **Streaming transcription**: feed audio in chunks with
  `transcribe(chunk, end_of_stream=False)`, print interim and final
  hypotheses as they arrive, and flush with `finish_stream()`.
- **GPU or CPU**: run on `Delegate.GPU` with an automatic fallback to
  `Delegate.CPU`.

## Notebook

Open [`audio_transcriber.ipynb`](audio_transcriber.ipynb) in
[Colab](https://colab.research.google.com/github/google-ai-edge/mediapipe-samples/blob/main/examples/audio_transcriber/python/audio_transcriber.ipynb)
or a local Jupyter server, then run the cells in order. The notebook downloads
the model and a sample speech clip.

## Command-line script

Requires Python 3.10 or newer.

```bash
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt

# Transcribe the sample clip (downloads the model on first run).
python audio_transcriber.py

# Transcribe your own recording on the CPU, streaming it in 500 ms chunks.
python audio_transcriber.py --audio my_recording.wav --delegate cpu --stream --chunk_ms 500
```

Options:

- `--model PATH`: path to a `.litertlm` model. By default, the Parakeet model
  is downloaded into the current directory if it isn't there yet.
- `--audio PATH`: PCM `.wav` file to transcribe (any sample rate, mono or
  stereo). By default, a sample clip is downloaded.
- `--delegate {gpu,cpu}`: hardware to run on (default: `gpu`, which falls back
  to CPU if no GPU is available).
- `--stream`: feed the audio in chunks instead of transcribing it in a single
  call.
- `--chunk_ms N`: chunk length for `--stream` (default: 1000).

## Notes

- The task accepts mono `float32` samples in `[-1.0, 1.0]`. Set
  `sample_rate_hz` to the sample rate of your audio; the task resamples it to
  the 16 kHz the model expects.
- In streaming mode, the model commits text a few seconds behind the incoming
  audio. Enable `enable_interim_results` to receive lower-latency interim
  hypotheses (`is_final=False`), and call `finish_stream()` at the end of each
  stream to get the remaining final text.
- On Linux, `Delegate.GPU` uses WebGPU over Vulkan. The user running the
  sample needs access to the GPU render node (for example, by being in the
  `render` group); otherwise Vulkan can fall back to a slow software
  implementation.
