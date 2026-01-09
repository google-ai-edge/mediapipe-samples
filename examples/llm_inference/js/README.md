# MediaPipe LLM Inference task for web

## Overview

This web sample demonstrates how to use the LLM Inference API to run common text-to-text generation tasks like information retrieval, email drafting, and document summarization, on web. When using a Gemma 3n model, image and audio inputs are also permitted.


## Prerequisites

* A browser with WebGPU support (eg. Chrome on macOS or Windows).

## Running the demo

Follow the following instructions to run the sample on your device:
1. Copy the [index.html](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/index.html) file to your computer.
2. Download a pre-converted Gemma model, like [Gemma 3 4B](https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/gemma3-4b-it-q4_0-web.task?download=true). See the [Web Models](https://huggingface.co/collections/litert-community/web-models) LiteRT community collection for more supported models. For image and audio inputs, see our [multimodal documentation](https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/web_js#multimodal).
3. Open the index.html file in your browser and upload your Gemma model using the "Choose File" button. The model will start loading. After a few seconds, a 'Get Response' button will be enabled and you can send queries to the LLM.
