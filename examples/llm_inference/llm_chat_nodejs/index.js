/**
 * Copyright 2026 The MediaPipe Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const { Readable } = require('stream');
const { LlmInference, FilesetResolver } = require('@mediapipe/tasks-genai');
const fs = require('fs/promises');
const fsSync = require('fs');
const path = require('path');
const readline = require('readline');
const { ArgumentParser } = require('argparse');

/**
 * The main function that initializes the environment, loads the model,
 * and starts the interactive chat REPL.
 * @param {object} args - The parsed command-line arguments.
 */
async function run(args) {
  // --- WebGPU & Wasm Polyfills/Setup ---
  // This section sets up a simulated browser environment for the MediaPipe library.
  const { create, globals } = await import('webgpu');

  // Polyfill OffscreenCanvas
  globalThis.OffscreenCanvas = class {
    getContext() {
      return {
        configure: () => undefined,
      };
    }
  };

  // Copy the wasm-related files from node_modules into a local './wasm/' directory.
  // We copy these files because `@mediapipe/tasks-genai` lists `type: "module"`
  // in its package.json file, so our `importScripts` polyfill, which calls
  // Node's `require()`, won't load `genai_wasm_internal.js` properly
  // when the files are within the `@mediapipe/tasks-genai/wasm/` directory.
  const base = './wasm/';
  const source = './node_modules/@mediapipe/tasks-genai/wasm/'
  const files = ['genai_wasm_internal.js', 'genai_wasm_internal.wasm'];
  if (!fsSync.existsSync(base)) {
    await fs.mkdir(base);
  }
  for (const f of files) {
    const filePath = path.join(base, f);
    if (!fsSync.existsSync(filePath)) {
      const sourcePath = path.join(source, f);
      await fs.cp(sourcePath, filePath);
    }
  }

  // Polyfill importScripts to load the wasm files using Node's require.
  globalThis.importScripts = (filename) => {
    if (filename === './wasm/genai_wasm_internal.js') {
      globalThis.ModuleFactory = require(filename);
    } else {
      throw new Error(`Unsupported script ${filename}`);
    }
  };

  // Polyfill other browser-specific globals.
  Object.assign(globalThis, globals);

  if (args.adapter) {
    globalThis.navigator = { gpu: create([`adapter=${args.adapter}`]) };
  } else {
    globalThis.navigator = { gpu: create([]) };
  }
  globalThis.self = globalThis;

  /**
   * Loads the model from a local file path.
   * @param {string} modelPath - The absolute path to the model file.
   * @param {object} llmOptions - The options for the LLM Inference task.
   * @returns {Promise<LlmInference|null>} A promise that resolves to an LlmInference instance or null if loading fails.
   */
  async function loadModel(modelPath, llmOptions) {
    try {
      console.log("Loading model, please wait...");
      await fs.access(modelPath);
      const genai = await FilesetResolver.forGenAiTasks('./wasm');
      const nodeStream = fsSync.createReadStream(modelPath);
      const modelStream = Readable.toWeb(nodeStream);

      const loadStart = performance.now();
      const llmInference = await LlmInference.createFromOptions(genai, {
        baseOptions: {
          modelAssetBuffer: modelStream.getReader(),
        },
        maxTokens: llmOptions.max_tokens,
        temperature: llmOptions.temperature,
        topK: llmOptions.top_k,
        randomSeed: llmOptions.random_seed,
      });
      const loadEnd = performance.now();

      const loadSec = (loadEnd - loadStart) / 1000;
      console.log(`Model loaded successfully in ~${loadSec.toFixed(1)}s.\n`);
      return llmInference;
    } catch (error) {
      console.error("Error loading model:", error);
      return null;
    }
  }

  /**
   * Starts the interactive REPL for chatting with the loaded model.
   * @param {LlmInference} model - The loaded LlmInference instance.
   * @param {object} args - The parsed command-line arguments.
   */
  function startChat(model, args) {
    const rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout
    });

    // Array to store the conversation history.
    const chatHistory = [];
    const maxInputTokens = args.max_tokens - args.response_tokens;
    console.log(`Max input tokens: ${maxInputTokens}\n`);

    console.log("Chat with the model! Type your message and press Enter.");
    console.log("Type '.clear' to reset history, or '.exit'/'quit' to end.\n");

    /**
     * Formats the conversation history and the new prompt according to the Gemma instruction format.
     * @param {Array<Object>} history - The array of past conversation turns.
     * @param {string} newPrompt - The new user prompt.
     * @returns {string} The fully formatted prompt string.
     */
    const formatPrompt = (history, newPrompt) => {
      const historyString = history.map(turn =>
        `<start_of_turn>${turn.role}\n${turn.text}<end_of_turn>`
      ).join('\n');

      const promptPrefix = history.length > 0 ? '\n' : '';

      // Combine history with the new user prompt and the model's turn signal
      return `${promptPrefix}${historyString}\n<start_of_turn>user\n${newPrompt}<end_of_turn>\n<start_of_turn>model`;
    };

    // This function defines the chat loop.
    const chat = () => {
      rl.question('You: ', async (prompt) => {
        if (prompt.toLowerCase() === '.exit' || prompt.toLowerCase() === 'quit') {
          process.exit(0);
        }

        if (prompt.toLowerCase() === '.clear') {
          chatHistory.length = 0; // Clears the array
          console.log('\nChat history cleared.\n');
          chat();
          return;
        }

        // --- Context Length Management ---
        // Create a temporary history to check for token length
        let tempHistory = [...chatHistory];
        let formattedPrompt;
        let totalInputTokens;

        while (true) {
            // Format the prompt with the current temporary history
            formattedPrompt = formatPrompt(tempHistory, prompt);
            totalInputTokens = model.sizeInTokens(formattedPrompt);

            if (totalInputTokens <= maxInputTokens) {
                // If the prompt fits, update the actual history and break the loop
                chatHistory.splice(0, chatHistory.length, ...tempHistory);
                break;
            }

            if (tempHistory.length === 0) {
                // If even the new prompt by itself is too long
                console.error(`\n[Error] Your prompt is too long (${totalInputTokens} tokens). Maximum input is ${maxInputTokens} tokens.`);
                return chat(); // Ask for a new prompt
            }

            // If the prompt is too long, remove the oldest two turns (user and model)
            console.log(`\n[Trimming history... Current tokens: ${totalInputTokens}, Max: ${maxInputTokens}]`);
            tempHistory.splice(0, 2);
        }
        let fullResponse = '';
        process.stdout.write('Model: ');

        const start = performance.now();
        try {
          // Generate response with streaming.
          await model.generateResponse(formattedPrompt, (partialResponse, done) => {
            fullResponse += partialResponse; // Accumulate the full response
            process.stdout.write(partialResponse);
            if (done) {
              const trimmedResponse = fullResponse.trim();
              // Add the new user prompt and model response to the (now truncated) history
              chatHistory.push({ role: 'user', text: prompt });
              chatHistory.push({ role: 'model', text: trimmedResponse });
              process.stdout.write('\n\n');
            }
          });
        } catch (error) {
          console.error("\nError during response generation:", error);
        }
        const end = performance.now();

        if (args.stats) {
          const timeSeconds = (end - start) / 1000;
          const responseTokens = model.sizeInTokens(fullResponse);
          console.log(`Input tokens (incl. history): ${totalInputTokens}
Response tokens: ${responseTokens}
Time: ${timeSeconds.toFixed(1)}s
Approximate tokens / sec: ${(responseTokens / timeSeconds).toFixed(1)}
`);
        }

        chat();
      });
    };

    chat(); // Start the chat loop.
  }

  const modelPath = path.resolve(process.cwd(), args.model);
  const model = await loadModel(modelPath, args);

  if (model) {
    startChat(model, args);
  } else {
    console.log("Could not start chat because model loading failed.");
  }
}

const parser = new ArgumentParser({
  description: 'Chat with a local model using @mediapipe/tasks-genai.'
});

parser.add_argument('--model', {
  help: 'Path to the model file.',
  required: true
});
parser.add_argument('--max_tokens', {
  help: 'The maximum number of tokens for the context window.',
  type: 'int',
  default: 2048
});
parser.add_argument('--response_tokens', {
    help: 'The number of tokens to reserve for the model\'s response.',
    type: 'int',
    default: 512
});
parser.add_argument('--temperature', {
  help: 'The temperature for sampling.',
  type: 'float',
  default: 1.0
});
parser.add_argument('--top_k', {
  help: 'The top-K for sampling.',
  type: 'int',
  default: 32
});
parser.add_argument('--random_seed', {
  help: 'The random seed for sampling for reproducibility.',
  type: 'int',
  default: Math.floor(Math.random() * 100000) // Default to a random seed
});
parser.add_argument('--adapter', {
  help: 'The GPU adapter to use. To list available adapters, pass a non-existent adapter value here',
  type: 'string',
});
parser.add_argument('--stats', {
  help: 'Print stats for loading the model and for each query / response',
  action: 'store_true',
});

const args = parser.parse_args();
run(args);
