# MediaPipe LLM Inference task for web

## Overview

This web sample demonstrates how to use the LLM Inference API to run common text-to-text generation tasks like information retrieval, email drafting, and document summarization, on web.


## Prerequisites

* A browser with WebGPU support (eg. Chrome on macOS or Windows).

## Running the demo

Follow the following instructions to run the sample on your device:
1. Make a folder for the task, named as `llm_task`, and copy the [index.html](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/index.html) and [index.js](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/index.js) files into your `llm_task` folder.
2. Download a pre-converted Gemma model, like [Gemma 2 2B](https://www.kaggle.com/models/google/gemma-2/tfLite/gemma2-2b-it-gpu-int8) (LiteRT 2b-it-gpu-int8) or the smaller [Gemma 3 1B](https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4.task?download=true). Alternatively, you can convert an external LLM (Phi-2, Falcon, or StableLM) following the [guide](https://developers.google.com/mediapipe/solutions/genai/llm_inference/web_js#convert-model) (only gpu backend is currently supported), into the `llm_task` folder.
3. In your `index.js` file, update [`modelFileName`](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/index.js#L23) with your model file's name.
4. Run `python3 -m http.server 8000` under the `llm_task` folder to host the three files (or `python -m SimpleHTTPServer 8000` for older python versions).
5. Open `localhost:8000` in Chrome. Then the button on the webpage will be enabled when the task is ready (~10 seconds).
