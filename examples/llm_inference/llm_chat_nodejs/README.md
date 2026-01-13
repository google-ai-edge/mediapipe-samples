# Run LLM Inference Engine through nodejs
This demo runs [LLM Inference Engine](https://www.npmjs.com/package/@mediapipe/tasks-genai) in WASM through NodeJS.

## Dependencies
If you don't have a model checkpoint already downloaded, start downloading one from the [HuggingFace LiteRT Community](https://huggingface.co/collections/litert-community/web-models)

For example:
* [Gemma3 270m](https://huggingface.co/litert-community/gemma-3-270m-it/blob/main/gemma3-270m-it-q8-web.task)
* [Gemma3 1B](https://huggingface.co/litert-community/Gemma3-1B-IT/blob/main/gemma3-1b-it-int8-web.task)
* [Gemma3 4B](https://huggingface.co/litert-community/Gemma3-4B-IT/blob/main/gemma3-4b-it-q4_0-web.task)
* [Gemma3 12B](https://huggingface.co/litert-community/Gemma3-12B-IT/blob/main/gemma3-12b-it-q4_0-web.task)
* [Gemma3 27B](https://huggingface.co/litert-community/Gemma3-27B-IT/blob/main/gemma3-27b-it-q4_0-web.task)
* [MedGemma3 27B](https://huggingface.co/litert-community/MedGemma-27B-IT/blob/main/medgemma-27b-it-int8-web.task)

Install npm and nodejs from, e.g., [nvm](https://github.com/nvm-sh/nvm).

Then, run `npm i` to install dependencies of this package.

If you're on an unusual platform, the native [webgpu](https://www.npmjs.com/package/webgpu) package might not have a binary built for you, in which case you'll have to [build it from source](https://github.com/dawn-gpu/node-webgpu?tab=readme-ov-file#building).

## Running the Demo

Run `node index.js --model your-model.task` to chat with the given LLM.

Run `node index.js --help` for options.
