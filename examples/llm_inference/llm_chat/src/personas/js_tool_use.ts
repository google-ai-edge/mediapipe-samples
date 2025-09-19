/**
 * Copyright 2025 The MediaPipe Authors
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

import { Persona } from "../types";
import { BASE_GEMMA3_PERSONA } from "./base_gemma3";
import {CodeSandbox, SandboxResult} from '../code_sandbox';

const EVAL_TAG = '```js-eval';
const MAX_CONSOLE_LOG_CHARS = 1000;

export const JS_TOOL_USE = {
  name: 'JS Interpreter Tool Use',
  instructions: [
    {
      role: 'system',
      text: `You are a helpful assistant with access to a javascript interpreter. Use the interpreter when necessary to provide an accurate answer to the user.

If you choose to run an expression in the js interpreter, include a javascript code block in your response, like this:
${EVAL_TAG}
12345 * 5432;
\`\`\`

After you respond with a code block, the system will execute your code and give you the response, at which point you will have the opportunity to read it and respond to the user or run more javascript code if necessary. Note that the user does not see the javascript expressions you write, nor do they see the response (including \`console.log\` statements). Additionally, you will only receive the result of the expression or an error message if it fails. You won't see any \`console.log\` messages.

You may make multiple calls to the javascript interpreter before replying to the user.
`,
    },
    {
      role: 'user',
      text: 'What is the 50th Fibonacci number?'
    },
    {
      role: 'model',
      text: `${EVAL_TAG}
function fibonacci(n) {
  if (n <= 1) {
    return n;
  }

  let a = 0;
  let b = 1;

  for (let i = 2; i <= n; i++) {
    const temp = a + b;
    a = b;
    b = temp;
  }

  return b;
}

fibonacci(123);
\`\`\``,
    },
    {
      role: 'system',
      text: '12586269025',
    },
    {
      role: 'model',
      text: `The 50th Fibonacci number is 12586269025.`,
    },
  ],
  promptTemplate: {
    ...BASE_GEMMA3_PERSONA.promptTemplate,
    system: {
      pre: '<start_of_turn>system\n',
      post: '<end_of_turn>\n',
    }
  },
  tools: {
    [EVAL_TAG]: async (code: string): Promise<string> => {
      try {
        const sandbox = new CodeSandbox();
        const regex = /```js-eval\n([\s\S]*?)\n```/g;

        const matches = [...code.matchAll(regex)];
        const match = matches[0]?.[1]; // [1] is the capture group
        
        if (match == null) {
          throw new Error('Improper code block formatting');
        }

        const result = await sandbox.run(match);
        let consoleMessages = result.consoleMessages.map(message => `${message.type}: ${message.text}`).join('\n');

        if (consoleMessages.length > MAX_CONSOLE_LOG_CHARS) {
          consoleMessages = consoleMessages.slice(0, MAX_CONSOLE_LOG_CHARS)
            + `\nLog truncated at ${MAX_CONSOLE_LOG_CHARS} chars. ${consoleMessages.length - MAX_CONSOLE_LOG_CHARS} remain.`;
        }

        const error = result.error ? `\n\nError: ${result.error}` : '';
        return `Result: ${result.resultAsString}\n\nConsole messages:\n${consoleMessages}${error}`;
      } catch (e) {
        if (e instanceof Error) {
          return e.stack ?? String(e);
        } else {
          return `Thrown: ${String(e)}`;
        }
      }
    }
  }
} satisfies Persona;
