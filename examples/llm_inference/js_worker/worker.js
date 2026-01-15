/* Copyright 2026 The MediaPipe Authors.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

// Module importing from a worker is not yet supported, so we use a local copy
// of https://cdn.jsdelivr.net/npm/@mediapipe/tasks-genai@0.10.26/genai_bundle.mjs,
// edited to replace the 'export' statements at the very end with global
// declarations, so it can be imported via `importScripts`. 
importScripts('/tasks-genai.js')

function returnPartialResults(partialResults, complete) {
  self.postMessage({ type: "result", payload: {partialResults, complete}});
}

let llmInference = null;
async function initialize(modelStream) {
  const genaiFileset = await FilesetResolver.forGenAiTasks(
      'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-genai@0.10.26/wasm');

  llmInference = await LlmInference.createFromOptions(genaiFileset, {
    baseOptions: {modelAssetBuffer: modelStream},  // Use modelAssetPath
                                                   // instead for URLs.
        // maxTokens: 512,  // The maximum number of tokens (input tokens + output
        //                  // tokens) the model handles.
        // randomSeed: 1,   // The random seed used during text generation.
        // topK: 1,  // The number of tokens the model considers at each step of
        //           // generation. Limits predictions to the top k most-probable
        //           // tokens. Setting randomSeed is required for this to make
        //           // effects.
        // temperature:
        //     1.0,  // The amount of randomness introduced during generation.
        //           // Setting randomSeed is required for this to make effects.
        // For multimodal (Gemma 3n) options and more documentation, see
        // https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/web_js
      });
}

self.onmessage = async (event) => {
  const { type, payload } = event.data;

  if (type === "init") {
    await initialize(payload.modelStream.getReader());
    self.postMessage({
      type: "init",
      payload: {
        isSuccess: true,
      },
    });
    return;
  }

  if (type === "cancel") {
    if (llmInference) {
      llmInference.cancelProcessing();
    }
  }

  if (type === "query") {
    if (llmInference) {
      llmInference.generateResponse(payload.query, returnPartialResults);
    }
  }
};
