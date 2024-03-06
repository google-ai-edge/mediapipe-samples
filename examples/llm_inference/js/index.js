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

const modelFileName = 'llm.tflite'; /* Update the file name */

/**
 * Display tokens to the output text box.
 */
function displayNewTokens(tokens, complete) {
  if (output.textContent != null && output.textContent.length > 0) {
    output.textContent += tokens;
  } else {
    output.textContent = tokens;
  }

  if (complete) {
    if (output.textContent == null || output.textContent.length === 0) {
      output.textContent = 'Result is empty';
    }
  }
}

/**
 * Main function to run LLM Inference.
 */
async function runDemo() {
  const genaiFileset = await FilesetResolver.forGenAiTasks(
      'https://cdn.jsdelivr.net/npm/@mediapipe/tasks-genai/wasm');
  const llmInferenceOptions = {
    baseOptions: {modelAssetPath: modelFileName},
  };

  let llmInference;

  submit.onclick = () => {
    output.textContent = '';
    submit.disabled = true;
    llmInference.generateResponse(input.value, displayNewTokens).finally(() => {
      submit.disabled = false;
    });
  };

  submit.value = 'Loading the model...'
  LlmInference
      .createFromOptions(
          genaiFileset,
          llmInferenceOptions,
          )
      .then(llm => {
        llmInference = llm;
        submit.disabled = false;
        submit.value = 'Get Response'
      });

  window.onbeforeunload = () => {
    if (llmInference) {
      llmInference.close();
      llmInference = null;
    }
  };
}

runDemo();