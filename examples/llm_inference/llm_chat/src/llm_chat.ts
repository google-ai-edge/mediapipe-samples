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

import { LlmInferenceOptions } from '@mediapipe/tasks-genai';
import { LitElement, css, html } from 'lit';
import { customElement, query, state } from 'lit/decorators.js';
import { lastValueFrom } from 'rxjs';
import './chat_history';
import { DEFAULT_OPTIONS } from './constants';
import './llm_options';
import { LlmService } from './llm_service'; // Ensure LlmService has setPersona and removeLastMessage
import type { ChatMessage, Persona } from './types';
import { PERSONAS } from './personas';
import deepEqual from 'deep-equal';
import { BASE_GEMMA3_PERSONA } from './personas/base_gemma3';

@customElement('llm-chat')
export class LlmChat extends LitElement {
  private llmService: LlmService;

  @state()
  private userInput: string = '';

  @state()
  private isLoadingModel: boolean = true;

  @state()
  private isGenerating: boolean = false;

  @state()
  private errorMessage: string | null = null;

  @state()
  private chatHistory: ChatMessage[] = [];

  @state()
  private loadingProgress: number | null = null;

  @state()
  private currentAppliedOptions: LlmInferenceOptions & { forceF32?: boolean } =
    structuredClone(DEFAULT_OPTIONS);

  @state()
  private pendingOptions: LlmInferenceOptions & { forceF32?: boolean } =
    structuredClone(DEFAULT_OPTIONS);

  @state()
  private hasPendingOptionsChanges: boolean = false;

  @state()
  private selectedPersona: Persona = PERSONAS.find(p => p.name === BASE_GEMMA3_PERSONA.name) || PERSONAS[0] || { name: 'Default', instructions: [] };

  @query('#userInput')
  private userInputElement!: HTMLInputElement;

  constructor() {
    super();
    this.llmService = new LlmService();
    // Subscribe to history changes from LlmService
    this.llmService.history.subscribe((history) => {
      this.chatHistory = history;
      this.requestUpdate();
    });
    this.llmService.loadingProgress$.subscribe((progress) => {
      this.loadingProgress = progress;
      this.requestUpdate();
    });
    this.initializeLlm();
  }

  private async initializeLlm() {
    this.isLoadingModel = true;
    this.errorMessage = null;
    this.requestUpdate();

    try {
      this.llmService.setPersona(this.selectedPersona);
      await this.llmService.setOptions(structuredClone(DEFAULT_OPTIONS));
      this.currentAppliedOptions = structuredClone(DEFAULT_OPTIONS);
      this.pendingOptions = structuredClone(DEFAULT_OPTIONS);
      this.hasPendingOptionsChanges = false;

      console.log(
        'LLM Initialized with default options and persona:',
        this.currentAppliedOptions,
        this.selectedPersona.name
      );
    } catch (error) {
      console.error('Failed to initialize LLM:', error);
      this.errorMessage = `Failed to initialize LLM. Error: ${
        error instanceof Error ? error.message : String(error)
      }`;
    } finally {
      this.isLoadingModel = false;
      this.requestUpdate();
    }
  }

  async handlePersonaChanged(event: CustomEvent<Persona>) {
    const newPersona = event.detail;
    if (this.selectedPersona.name === newPersona.name) {
      return;
    }

    console.log('Persona selected:', newPersona.name);
    this.isGenerating = false;
    this.userInput = '';

    this.isLoadingModel = true;
    this.errorMessage = null;
    this.selectedPersona = newPersona;
    this.requestUpdate();

    await new Promise(resolve => setTimeout(resolve, 50)); // UI update for loading message

    try {
      this.llmService.setPersona(this.selectedPersona);
      console.log(`Chat context updated for persona: ${this.selectedPersona.name}`);
    } catch (error) {
        console.error('Failed to apply new persona:', error);
        this.errorMessage = `Failed to switch persona. Error: ${error instanceof Error ? error.message : String(error)}`;
    } finally {
        this.isLoadingModel = false;
        this.userInputElement?.focus();
        this.requestUpdate();
    }
  }

  handleOptionsChange(
    event: CustomEvent<LlmInferenceOptions & { forceF32?: boolean }>
  ) {
    const newOptionsFromChild = event.detail;
    this.pendingOptions = newOptionsFromChild;
    this.hasPendingOptionsChanges = !deepEqual(
      this.currentAppliedOptions,
      this.pendingOptions
    );
    this.requestUpdate();
  }

  private async _applyPendingOptionsIfNeeded(): Promise<boolean> {
    if (this.hasPendingOptionsChanges) {
      console.log('Applying pending LLM options:', this.pendingOptions);
      this.isLoadingModel = true;
      this.errorMessage = null;
      this.requestUpdate();

      try {
        await this.llmService.setOptions(structuredClone(this.pendingOptions));
        this.currentAppliedOptions = structuredClone(this.pendingOptions);
        this.hasPendingOptionsChanges = false;
        console.log('LLM options applied successfully.');
        this.isLoadingModel = false;
        this.requestUpdate();
        return true;
      } catch (error) {
        console.error('Failed to apply pending LLM options:', error);
        this.errorMessage = `Failed to apply new options. Error: ${
          error instanceof Error ? error.message : String(error)
        }`;
        this.isLoadingModel = false;
        this.requestUpdate();
        return false;
      }
    }
    return true;
  }

  private handleRemoveLastMessage() {
    // LlmService now handles the logic of what can be removed,
    // including protecting persona messages.
    this.llmService.removeLastMessage();
    // The history subscription in the constructor will handle updating
    // this.chatHistory and triggering a re-render.
  }

  private handleRegenerateLastMessage() {
    const currentHistory = this.llmService.history.value; // Get latest history
    const lastMessage = currentHistory[currentHistory.length - 1]!;
    if (lastMessage) {
      if (lastMessage.role === 'model') {
        // Ask LlmService to remove the last message (which is the model's response).
        // LlmService will ensure persona messages are not removed.
        this.llmService.removeLastMessage();
        // After LlmService updates its history (and LlmChat's via subscription),
        // generate a new response.
        this.generate();
      } else if (lastMessage.role === 'user') {
        // If the last message is from the user, generate a response for it.
        this.generate();
      }
      // If the last message is a system/persona message, LlmService.removeLastMessage()
      // should ideally do nothing or only remove if it's a non-essential system message.
      // Then, generate() would proceed based on the current (potentially unchanged) history.
    }
  }

  private handleUserInput(event: Event) {
    this.userInput = (event.target as HTMLInputElement).value;
  }

  private async generate() {
    if (this.isGenerating || this.isLoadingModel) {
      return;
    }

    const optionsApplied = await this._applyPendingOptionsIfNeeded();
    if (!optionsApplied) {
      return;
    }

    this.isGenerating = true;
    this.errorMessage = null;
    this.requestUpdate();

    try {
      await this.llmService.generate();
    } catch (error) {
      console.error('Failed to send message or generate response:', error);
      this.errorMessage = `Failed to send message. Error: ${
        error instanceof Error ? error.message : String(error)
      }`;
    } finally {
      this.isGenerating = false;
      this.userInputElement?.focus();
      this.requestUpdate();
    }
  }

  private async sendMessage() {
    if (!this.userInput.trim()) {
      return;
    }

    const userMessageText = this.userInput.trim();
    this.userInput = '';
    this.llmService.addUserMessage(userMessageText);
    return this.generate();
  }

  static override styles = css`
    :host {
      display: flex;
      flex-direction: row; /* Main layout: personas | chat | options */
      height: calc(100vh - 40px);
      max-width: 1400px; /* Adjusted max-width for three panels */
      margin: 20px auto;
      box-shadow: 0 0 10px rgba(0,0,0,0.1);
      border-radius: 8px;
      overflow: hidden;
      position: relative;
    }

    .status-bar {
      padding: 8px 16px;
      text-align: center;
      font-size: 0.9em;
      transition: background-color 0.3s ease;
      position: absolute;
      top: 0;
      left: 0;
      right: 0;
      z-index: 10;
      border-radius: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
    }
    .error-message {
      color: white;
      background-color: #d32f2f;
    }
    .loading-message {
      color: white;
      background-color: #1976d2;
    }
    .generating-message {
      color: #333;
      background-color: #fff59d;
    }

    progress {
      width: 100px;
    }

    .main-chat-area {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      overflow: hidden;
      height: 100%;
      position: relative;
      border-left: 1px solid #ddd;
      border-right: 1px solid #ddd;
    }

    .options-container {
      width: 320px;
      flex-shrink: 0;
      overflow-y: auto;
      background-color: #f9f9f9;
      height: 100%;
      box-sizing: border-box;
    }

    .options-container llm-options {
        display: block;
        height: 100%;
    }

    .chat-area {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      overflow-y: auto;
      padding: 16px;
      padding-top: 50px;
    }

    .input-area {
      display: flex;
      padding: 16px;
      border-top: 1px solid #ddd;
      background-color: #f9f9f9;
    }

    #userInput {
      flex-grow: 1;
      padding: 10px;
      border: 1px solid #ccc;
      border-radius: 4px;
      margin-right: 8px;
      font-size: 1em;
    }

    button {
      padding: 10px 15px;
      background-color: #007bff;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-size: 1em;
    }

    button:hover:not(:disabled) {
      background-color: #0056b3;
    }

    button:disabled {
      background-color: #ccc;
      cursor: not-allowed;
    }

    @media (max-width: 900px) {
      :host {
        flex-direction: column;
        height: calc(100vh - 20px);
        margin: 10px auto;
      }

      .status-bar {
        position: static;
        border-radius: 8px 8px 0 0;
        order: -1;
      }

      .main-chat-area {
        width: 100%;
        order: 1;
        border-left: none;
        border-right: none;
      }

      .options-container {
        width: 100%;
        order: 2;
        border-left: none;
        border-top: 1px solid #ddd;
        max-height: 35vh;
        height: auto;
      }

      .chat-area {
        padding-top: 16px;
      }
    }
  `;

  override render() {
    let statusMessageHtml = html``;
    if (this.errorMessage) {
      statusMessageHtml = html`<div class="status-bar error-message">${this.errorMessage}</div>`;
    } else if (this.isLoadingModel) {
      let message = 'Loading...';
      if (this.loadingProgress !== null && this.loadingProgress < 1) {
        message = `Loading model...`;
      } else if (this.hasPendingOptionsChanges) {
        message = 'Applying new options...';
      } else {
        message = `Setting up ${this.selectedPersona.name}...`;
      }
      statusMessageHtml = html`
        <div class="status-bar loading-message">
          <span>${message}</span>
          ${this.loadingProgress !== null && this.loadingProgress < 1 ?
            html`<progress .value=${this.loadingProgress}></progress>` : ''}
        </div>
      `;
    } else if (this.isGenerating) {
        statusMessageHtml = html`<div class="status-bar generating-message">Generating response with ${this.selectedPersona.name}...</div>`;
    }

    return html`
      <div class="main-chat-area">
        ${statusMessageHtml}
        <div class="chat-area">
          <chat-history
            .history=${this.chatHistory}
            @regenerate-last-model-message=${this.handleRegenerateLastMessage}
            @remove-last-message=${this.handleRemoveLastMessage}
          ></chat-history>
        </div>
        <div class="input-area">
          <input
            type="text"
            id="userInput"
            placeholder="Chat with ${this.selectedPersona.name}..."
            .value=${this.userInput}
            @input=${this.handleUserInput}
            @keypress=${(e: KeyboardEvent) => e.key === 'Enter' && this.sendMessage()}
          />
          <button
            @click=${this.sendMessage}
            ?disabled=${this.isGenerating || this.isLoadingModel || !this.userInput.trim()}
          >
            Send
          </button>
        </div>
      </div>

      <div class="options-container">
        <llm-options
          .options=${this.pendingOptions}
          .personas=${PERSONAS}
          .selectedPersonaName=${this.selectedPersona.name}
          @options-changed=${this.handleOptionsChange}
          @persona-changed=${this.handlePersonaChanged}
          ?disabled=${this.isLoadingModel || this.isGenerating}
        >
        </llm-options>
      </div>
    `;
  }
}
