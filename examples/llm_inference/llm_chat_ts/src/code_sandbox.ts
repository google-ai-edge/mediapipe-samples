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

export interface ConsoleMessage {
  type: 'log' | 'warn' | 'error';
  text: string;
}

export interface SandboxResult {
  resultAsString?: string;
  error?: string;
  consoleMessages: ConsoleMessage[];
}

export class CodeSandbox {
  private worker: Worker | null = null;
  private readonly workerScript: string;
  private static readonly DEFAULT_TIMEOUT_MS = 5000; // 5 seconds

  constructor() {
    // Worker script remains the same as the previous version
    // (capturing console, handling eval, posting results)
    this.workerScript = `
      self.onmessage = function(event) {
        const code = event.data;
        const consoleMessages = [];

        const originalConsole = {
          log: self.console.log,
          warn: self.console.warn,
          error: self.console.error,
        };

        const captureConsoleMessage = (type, ...args) => {
          const text = args.map(arg => {
            if (arg instanceof Error) return arg.stack || arg.toString();
            if (typeof arg === 'object' && arg !== null) {
              try {
                return JSON.stringify(arg);
              } catch (e) {
                return typeof arg.toString === 'function' ? arg.toString() : '[Unstringifiable Object]';
              }
            }
            return String(arg);
          }).join(' ');
          consoleMessages.push({ type, text });
          // originalConsole[type].apply(self.console, args); // Optional: log to worker's console
        };

        self.console.log = (...args) => captureConsoleMessage('log', ...args);
        self.console.warn = (...args) => captureConsoleMessage('warn', ...args);
        self.console.error = (...args) => captureConsoleMessage('error', ...args);

        let resultPayload = {
          resultAsString: undefined,
          error: undefined,
          consoleMessages: consoleMessages,
        };

        try {
          const result = eval(code);
          if (typeof result === 'undefined') {
            resultPayload.resultAsString = 'undefined';
          } else {
            try {
              resultPayload.resultAsString = String(result);
            } catch (e_to_string) {
              const err = e_to_string instanceof Error ? e_to_string : new Error(String(e_to_string));
              resultPayload.resultAsString = \`[Error converting result to string: \${err.message}]\`;
              consoleMessages.push({ type: 'error', text: \`Error converting result to string: \${err.message}\` });
            }
          }
        } catch (e) {
          const err = e instanceof Error ? e : new Error(String(e));
          resultPayload.error = err.stack || err.toString();
          consoleMessages.push({ type: 'error', text: \`Execution Error: \${resultPayload.error}\` });
        } finally {
          self.console.log = originalConsole.log;
          self.console.warn = originalConsole.warn;
          self.console.error = originalConsole.error;
          self.postMessage(resultPayload);
        }
      };
    `;
    // Initialize worker on construction
    this.initWorker();
  }

  private initWorker(): boolean {
    // Terminate existing worker if any, to prevent resource leaks if re-initializing
    if (this.worker) {
        try {
            this.worker.terminate();
        } catch (e) {
            console.warn("Error terminating existing worker during re-initialization:", e);
        }
        this.worker = null;
    }
    try {
      const blob = new Blob([this.workerScript], { type: 'application/javascript' });
      const workerUrl = URL.createObjectURL(blob);
      this.worker = new Worker(workerUrl);
      URL.revokeObjectURL(workerUrl);
      return true;
    } catch (error) {
      console.error("Failed to initialize Web Worker:", error);
      this.worker = null;
      return false;
    }
  }

  /**
   * Executes arbitrary JavaScript code in a Web Worker with a timeout.
   * @param code The JavaScript code string to execute.
   * @param timeoutMs The maximum time (in milliseconds) to allow for execution.
   * Defaults to 5000ms (5 seconds).
   * @returns A Promise that resolves with a SandboxResult object.
   */
  public run(code: string, timeoutMs: number = CodeSandbox.DEFAULT_TIMEOUT_MS): Promise<SandboxResult> {
    return new Promise((resolve) => {
      // If the worker was terminated (e.g., by a previous timeout) or failed to init,
      // try to create a new one for this run.
      if (!this.worker) {
        if (!this.initWorker()) { // Attempt to re-initialize
          resolve({
            error: "Web Worker is not available and could not be re-initialized.",
            consoleMessages: []
          });
          return;
        }
      }

      // Capture the worker instance that will be used for this specific run.
      // This is important because this.worker might be nulled by a timeout
      // from this or another concurrent (in terms of Promises) run.
      const workerForThisRun = this.worker!; // We've ensured it's initialized above.
      let timeoutId: number | null = null;

      const cleanupAndResolve = (result: SandboxResult) => {
        if (timeoutId !== null) {
          clearTimeout(timeoutId);
          timeoutId = null;
        }
        // Remove listeners from the specific worker instance used for this run.
        // Check if workerForThisRun is still valid (hasn't been terminated abruptly without cleanup)
        if (workerForThisRun && typeof workerForThisRun.removeEventListener === 'function') {
            try {
                workerForThisRun.removeEventListener('message', messageHandlerWrapper);
                workerForThisRun.removeEventListener('error', errorHandlerWrapper);
            } catch (e) {
                // Could happen if worker was terminated very abruptly.
                console.warn("Error removing event listeners during cleanup:", e);
            }
        }
        resolve(result);
      };

      const messageHandlerWrapper = (event: MessageEvent<SandboxResult>) => {
        cleanupAndResolve(event.data);
      };

      const errorHandlerWrapper = (event: ErrorEvent) => {
        // This handles errors in the worker script itself (e.g., syntax error in workerScript)
        // or other uncatchable errors within the worker, not typical eval errors.
        cleanupAndResolve({
          error: `Worker script error: ${event.message} (at ${event.filename}:${event.lineno})`,
          consoleMessages: [] // Console messages from eval'd code likely won't be available
        });
      };

      // Setup the timeout
      timeoutId = setTimeout(() => {
        timeoutId = null; // Mark timeout as having fired (or about to fire)

        // Terminate the worker associated with *this specific run*.
        try {
            workerForThisRun.terminate();
        } catch(e) {
            console.warn("Error terminating worker on timeout:", e);
        }
        
        console.warn(`Sandbox worker for this run terminated due to timeout (${timeoutMs}ms).`);

        // If the worker that timed out was the current main worker of this CodeSandbox instance,
        // then set this.worker to null. The next run() will attempt to re-initialize.
        if (this.worker === workerForThisRun) {
          this.worker = null;
        }
        
        // Ensure listeners are removed even if termination was abrupt
        // This is a bit redundant if cleanupAndResolve were to be called, but safe
        try {
            workerForThisRun.removeEventListener('message', messageHandlerWrapper);
            workerForThisRun.removeEventListener('error', errorHandlerWrapper);
          //eslint-disable-next-line @typescript-eslint/no-unused-vars
        } catch(_e) {/* ignore */}


        resolve({
          error: `Execution timed out after ${timeoutMs}ms. The worker for this run has been terminated.`,
          consoleMessages: [] // Console messages from worker are lost on timeout
        });
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      }, timeoutMs) as any; // Use 'as any' for Node.js setTimeout return type if in mixed env

      // Add event listeners to the worker instance for this run
      workerForThisRun.addEventListener('message', messageHandlerWrapper);
      workerForThisRun.addEventListener('error', errorHandlerWrapper);

      try {
        workerForThisRun.postMessage(code);
      } catch (e) {
        // This catch is for immediate errors from postMessage itself (e.g., worker already dead)
        const err = e instanceof Error ? e : new Error(String(e));
        cleanupAndResolve({
          error: `Failed to send code to worker: ${err.message}`,
          consoleMessages: []
        });
      }
    });
  }

  /**
   * Terminates the current Web Worker, if active.
   * Subsequent calls to run() will attempt to create a new worker.
   */
  public terminate(): void {
    if (this.worker) {
      this.worker.terminate();
      this.worker = null;
      console.log("Sandbox worker explicitly terminated.");
    }
  }
}
