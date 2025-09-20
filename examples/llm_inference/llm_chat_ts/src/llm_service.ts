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

import { FilesetResolver, LlmInference, LlmInferenceOptions } from '@mediapipe/tasks-genai';
import { BehaviorSubject, lastValueFrom } from 'rxjs';
import { produce } from 'immer';
import { ChatMessage, Persona, Tool } from './types';
import { BASE_GEMMA3_PERSONA } from './personas/base_gemma3';
import { streamWithProgress } from './streaming_utils';
import { getOauthToken, loadModelWithCache } from './opfs_cache';

export const MODEL_PATHS = [
  [
    'Gemma3 1B IT int8',
    'https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int8-web.task',
  ] as const,
  [
    'Gemma3 1B IT int4',
    'https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/gemma3-1b-it-int4-web.task',
  ] as const,
  [
    'Gemma3 4B IT int8',
    'https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/gemma3-4b-it-int8-web.task',
  ] as const,
  [
    'Gemma3 4B IT int4',
    'https://huggingface.co/litert-community/Gemma3-4B-IT/resolve/main/gemma3-4b-it-int4-web.task',
  ] as const,
  [
    'Gemma3 12B IT int8',
    'https://huggingface.co/litert-community/Gemma3-12B-IT/resolve/main/gemma3-12b-it-int8-web.task',
  ] as const,
  [
    'Gemma3 12B IT int4',
    'https://huggingface.co/litert-community/Gemma3-12B-IT/resolve/main/gemma3-12b-it-int4-web.task',
  ] as const,
  [
    'Gemma3 27B IT int8',
    'https://huggingface.co/litert-community/Gemma3-27B-IT/resolve/main/gemma3-27b-it-int8-web.task',
  ] as const,
  [
    'Gemma3n E2B IT int4',
    'https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4-Web.litertlm',
  ] as const,
  [
    'Gemma3n E4B IT int4',
    'https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4-Web.litertlm',
  ] as const,
  [
    'MedGemma 27B IT int8',
    'https://huggingface.co/litert-community/MedGemma-27B-IT/resolve/main/medgemma-27b-it-int8-web.task',
  ] as const,
];

const DEFAULT_MAX_TOKENS = 1024

// TODO: Get these from the model itself.

type WithKeys<T, K extends keyof T> = Required<Pick<T, K>> & Omit<T, K>;

export class LlmService {
  private llmInferencePromise?: Promise<LlmInference> | undefined;
  private llmInference?: LlmInference | undefined;
  options: LlmInferenceOptions & {forceF32?: boolean} = {
    numResponses: 1,
    topK: 10,
    temperature: 0.8,
    maxTokens: 1024,
    forceF32: false,
  };
  private genaiFileset: ReturnType<typeof FilesetResolver['forGenAiTasks']>;
  history = new BehaviorSubject<ChatMessage[]>([]);
  loadingProgress$ = new BehaviorSubject<number | null>(null);
  persona: Persona = BASE_GEMMA3_PERSONA;
  private promptTemplate = BASE_GEMMA3_PERSONA.promptTemplate;

  // Minimum number of tokens for the model's response. History is clipped to
  // always allow the model this many tokens to respond in.
  responseTokens = 384;

  constructor() {
    this.genaiFileset = FilesetResolver.forGenAiTasks(
      "wasm"
    );
    getOauthToken();
  }

  isInitialized(): boolean {
    return !!this.llmInference;
  }

  setPersona(persona: Persona) {
    this.persona = persona;
    this.promptTemplate = this.persona.promptTemplate
      ?? BASE_GEMMA3_PERSONA.promptTemplate;
  }
  
  async setOptions(options: LlmInferenceOptions & {forceF32?: boolean}) {
    // Delete previous instance. Don't throw if the previous instance failed to
    // load.
    await this.llmInferencePromise?.catch(console.warn);
    this.llmInference?.close();
    this.llmInference = undefined;
    this.llmInferencePromise = undefined;

    this.options = options;
    
    const modelAssetPath = options.baseOptions?.modelAssetPath;
    if (!modelAssetPath) {
      throw new Error('modelAssetPath is required');
    }

    this.loadingProgress$.next(0); // Start progress
    try {
      const { stream: modelStream, size: contentLength } = await loadModelWithCache(modelAssetPath);
      const { stream, progress$ } = streamWithProgress(modelStream, contentLength);
      
      // Pipe progress updates to the service's public subject
      const progressSub = progress$.subscribe(p => this.loadingProgress$.next(p));

      const newOptions = structuredClone(options);
      newOptions.baseOptions ??= {};
      newOptions.baseOptions.modelAssetBuffer = stream.getReader();
      delete newOptions.baseOptions.modelAssetPath;

      this.llmInferencePromise = LlmInference.createFromOptions(
        await this.genaiFileset,
        newOptions,
      );

      this.llmInference = await this.llmInferencePromise;
      progressSub.unsubscribe(); // Clean up subscription
    } finally {
      this.loadingProgress$.next(null); // End progress
    }
  }

  clearHistory() {
    this.history.next([]);
  }

  removeLastMessage() {
    this.history.next(produce(this.history.value, history => {
      history.pop();
    }));
  }
  
  async generate(): Promise<void> {
    if (!this.llmInference) {
      throw new Error('Llm not done loading');
    }

    const renderedText = this.renderChatHistoryForModel(this.history.value)

    const responseSubject = new BehaviorSubject<string>('');
    this.history.next(produce(this.history.value, messages => {
      messages.push({
        role: 'model' as const,
        text: '',
      });
    }));

    const start = performance.now();
    this.llmInference.generateResponse(renderedText, async (partialResult, done) => {
      responseSubject.next(responseSubject.value + partialResult);
      this.history.next(produce(this.history.value, messages => {
        if (messages.length === 0) {
          return;
        }
        const lastMessage = messages.at(-1)!;
        lastMessage.text = responseSubject.value;
        lastMessage.doneGenerating = false;
      }));

      if (done) {
        const latencyMilliseconds = performance.now() - start;
        await sleep(0); // TODO: Let the user not have to do this.
        this.history.next(produce(this.history.value, messages => {
          if (messages.length === 0) {
            return;
          }
          const lastMessage = messages[messages.length - 1]!;
          lastMessage.latencyMilliseconds = latencyMilliseconds;
          lastMessage.generatedTokenCount =
            this.llmInference?.sizeInTokens(lastMessage.text) ?? 0;
          lastMessage.doneGenerating = true;
          messages[messages.length - 1] = this.applyTemplate(lastMessage);
        }));

        responseSubject.complete();
      }
    });

    const response = await lastValueFrom(responseSubject);
    const tool = this.checkTools(response);
    if (tool) {
      const toolResponse = await tool(response);
      this.history.next(produce(this.history.value, messages => {
        messages.push(this.applyTemplate({
          role: 'system',
          text: toolResponse,
        }));
      }));

      // Let the model generate after the tool is done.
      await this.generate();
    }
  }


  private checkTools(response: string): Tool | undefined {
    for (const [key, tool] of Object.entries(this.persona.tools ?? {})) {
      if (response.includes(key)) {
        return tool;
      }
    }
  }
  
  addUserMessage(text: string) {
    if (!this.llmInference) {
      throw new Error('Llm not done loading');
    }

    this.history.next(produce(this.history.value, messages => {
      messages.push(this.applyTemplate({
        role: 'user',
        text,
      }));
    }));
  }

  generateResponse(text: string): Promise<void> {
    this.addUserMessage(text);
    return this.generate();
  }

  private applyTemplate(message: ChatMessage): WithKeys<ChatMessage, 'templateApplied'> {
    if (!this.llmInference) {
      throw new Error('Llm not done loading');
    }
    const {pre, post} = this.promptTemplate[message.role];
    const text = `${pre}${message.text}${post}`
    const tokenCount = this.llmInference.sizeInTokens(text) ?? 0;

    return produce(message, newMessage => {
      newMessage.templateApplied = {
        text,
        tokenCount,
      };
    }) as WithKeys<ChatMessage, 'templateApplied'>;
  }

  private applyTemplateToMessages(messages: ChatMessage[]) {
    return messages.map(message => this.applyTemplate(message));
  }

  /**
   * Render the chat history for the model, concatenating it together with the
   * template applied. Only includes as many recent messages as fit in contextLimit.
   *
   * Additionally, applies the selected persona by prepending all its context
   * to the conversation.
   */
  private renderChatHistoryForModel(history: ChatMessage[]) {
    const contextLimit =
      (this.options.maxTokens ?? DEFAULT_MAX_TOKENS) - this.responseTokens;

    const personaMessages =
      this.applyTemplateToMessages(this.persona.instructions);

    const personaContextRequirement = personaMessages.reduce(
      (sum, message) => sum + message.templateApplied?.tokenCount , 0);

    if (personaContextRequirement > contextLimit) {
      throw new Error(`Persona ${this.persona.name} requires at least ${personaContextRequirement} tokens of context and only ${contextLimit} are available (${this.responseTokens} of the total ${this.options.maxTokens} are reserved for the model's response.)`);
    }

    let personaText = '';
    // Add all the persona messages to the input
    for (const personaMessage of personaMessages) {
      personaText += personaMessage.templateApplied!.text;
    }
    let usedContext = personaContextRequirement;

    // Take messages from recent history until they no longer fit in context
    let text = '';
    for (let i = history.length - 1; i >= 0; i--) {
      const message = history[i]!;
      const contextWithNewMessage =
        usedContext + message.templateApplied!.tokenCount;
      if (contextWithNewMessage >= contextLimit) {
        break;
      }
      // Prepend the text since we're going backward in messages.
      text = message.templateApplied!.text + text;
      usedContext  = contextWithNewMessage;
    }

    // Always include the pre-text of the gemma prompt when rendering for the
    // model.
    return personaText + text + this.promptTemplate.model.pre;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise(resolve => {
    setTimeout(resolve, ms);
  });
}
