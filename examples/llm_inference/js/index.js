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

const modelFileName = 'llm.bin'; /* Update the file name */

/**
 * Display tokens to the output text box.
 */
function displayNewTokens(tokens, complete) {
  output.textContent += tokens;

  if (complete) {
    if (!output.textContent) {
      output.textContent = 'Result is empty';
    }
    submit.disabled = false;
  }
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
    llmInference.generateResponse(input.value, displayNewTokens);
  };

  submit.value = 'Loading the model...'
  LlmInference
      .createFromModelPath(genaiFileset, modelFileName)
      .then(llm => {
        llmInference = llm;
        submit.disabled = false;
        submit.value = 'Get Response'
      }).catch(() =>{
        alert('Failed to initialize the task.');
      });
}

runDemo();
