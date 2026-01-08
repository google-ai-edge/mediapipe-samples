// Copyright 2024 The MediaPipe Authors.

// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at

//      http://www.apache.org/licenses/LICENSE-2.0

// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

// ---------------------------------------------------------------------------------------- //

import {FilesetResolver, LlmInference} from 'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-genai';

const input = document.getElementById('input');
const output = document.getElementById('output');
const submit = document.getElementById('submit');

const modelFileName = 'gemma-2b-it-gpu-int4.bin'; /* Update the file name */
//const modelFileName = 'https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4-Web.litertlm'; /* Works with URLs as well! */

/**
 * Gets the final part of a path whether URL or file directory
 */
function getFileName(path) {
  const parts = path.split('/');
  return parts[parts.length - 1];
}

/**
 * Uses more advanced caching system which allows for the loading of larger models even in more limited environments
 */
async function loadModelWithCache(modelPath) {
  const fileName = getFileName(modelPath);
  const opfsRoot = await navigator.storage.getDirectory();

  try {
    const fileHandle = await opfsRoot.getFileHandle(fileName);
    const file = await fileHandle.getFile();
    const sizeHandle = await opfsRoot.getFileHandle(fileName + '_size');
    const sizeFile = await sizeHandle.getFile();
    const expectedSizeText = await sizeFile.text();
    const expectedSize = parseInt(expectedSizeText);

    if (file.size === expectedSize) {
      console.log('Found valid model in cache.');
      return { stream: file.stream(), size: file.size };
    }

    console.warn('Cached model has incorrect size. Deleting and re-downloading.');
    await opfsRoot.removeEntry(fileName);
    await opfsRoot.removeEntry(fileName + '_size');
    throw new Error('Incorrect file size');
  } catch (e) {
    if (e.name !== 'NotFoundError')
      console.error('Error accessing OPFS:', e);
  }

  console.log('Fetching model from network and caching to OPFS.');
  const response = await fetch(modelPath);
  if (!response.ok || !response.body)
    throw new Error(`Failed to download model from ${modelPath}: ${response.statusText}.`);
  const expectedSize = Number(response.headers.get('Content-Length'));


  const [streamForConsumer, streamForCache] = response.body.tee();

  (async () => {
    try {
      const fileHandle = await opfsRoot.getFileHandle(fileName, { create: true });
      const writable = await fileHandle.createWritable();

      const sizeHandle = await opfsRoot.getFileHandle(fileName + '_size', { create: true });
      const sizeWritable = await sizeHandle.createWritable();
      await sizeWritable.write(expectedSize.toString());
      await sizeWritable.close();
      
      await streamForCache.pipeTo(writable);
      console.log(`Successfully cached ${fileName}.`);
    } catch (error) {
      console.error(`Failed to cache model ${fileName}:`, error);
      try {
        await opfsRoot.removeEntry(fileName);
        await opfsRoot.removeEntry(fileName + '_size');
      } catch (cleanupError) {}
    }
  })();

  return { stream: streamForConsumer, size: expectedSize };
}

/**
 * Display newly generated partial results to the output text box.
 */
function displayPartialResults(partialResults, complete) {
  output.textContent += partialResults;

  if (!complete)
    return;

  if (!output.textContent)
    output.textContent = 'Result is empty';
  submit.disabled = false;
}

/**
 * Main function to run LLM Inference.
 */
async function runDemo() {
  const genaiFileset = await FilesetResolver.forGenAiTasks(
      'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-genai/wasm');
  let llmInference;

  submit.onclick = () => {
    output.textContent = '';
    submit.disabled = true;
    llmInference.generateResponse(input.value, displayPartialResults);
  };

  submit.value = 'Loading the model...'
  try {
    const { stream: modelStream } = await loadModelWithCache(modelFileName);
    
    const llm = await LlmInference.createFromOptions(genaiFileset, {
        baseOptions: {modelAssetBuffer: modelStream.getReader()},
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
      });

    llmInference = llm;
    submit.disabled = false;
    submit.value = 'Get Response'
  } catch (error) {
    console.error(error);
    alert('Failed to initialize the task.');
  }
}

runDemo();
