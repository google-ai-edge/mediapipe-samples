# MediaPipe LLM Inference task for web

## Overview

This web sample demonstrates how to use the LLM Inference API to run common text-to-text generation tasks like information retrieval, email drafting, and document summarization, on web.


## Prerequisites

* A browser with WebGPU support (eg. Chrome on OSX or Windows).

## Running the demo

Follow the instructions of either sample app 1 or 2 to run a LLM Inference task on your device.

### Sample app 1

This sample app is easier to run.
1. Download one of the [compatible models](https://developers.google.com/mediapipe/solutions/genai/llm_inference#models) that you want to run.
2. Download [demo.html](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/demo.html) and open it in a browser.

### Sample app 2

This sample app's codes help you understand the usage of API easier.

1. Make a folder for the task, named as `llm_task`, and copy the [index.html](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/index.html) and [index.js](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/index.js) files into your `llm_task` folder.
2. Download one of the [compatible models](https://developers.google.com/mediapipe/solutions/genai/llm_inference#models) that you want to run, into the `llm_task` folder.
3. In your `index.js` file, update [`modelFileName`](https://github.com/googlesamples/mediapipe/blob/main/examples/llm_inference/js/index.js#L23) with your model file's name.
4. Run `python3 -m http.server 8000` under the `llm_task` folder to host the three files (or `python -m SimpleHTTPServer 8000` for older python versions).
5. Open `localhost:8000` in a browser. Then the button on the webpage will be enabled when the task is ready (~10 seconds).
