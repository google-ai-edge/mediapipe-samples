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

import { oauthLoginUrl } from './hf-hub';
import { LitElement, html, css, TemplateResult } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { MODEL_PATHS, getModelUrl, isHostedOnHuggingFace } from './llm_service';
import { LlmInferenceOptions } from '@mediapipe/tasks-genai';
import { produce } from 'immer';
import { DEFAULT_OPTIONS } from './constants';
import { Persona } from './types';
import { PERSONAS } from './personas';
import { getOauthToken, getCachedModelsInfo, removeCachedModel, removeAllCachedModels } from './opfs_cache';
import './custom_dropdown';

/**
 * @fires options-changed - Dispatched every time an option value is changed.
 * @fires persona-changed - Dispatched when the persona is changed.
 */
@customElement('llm-options')
export class LlmOptions extends LitElement {
  @property({ type: Array })
  personas: Persona[] = PERSONAS;

  @property({ type: String })
  selectedPersonaName: string = PERSONAS[0]?.name ?? '';

  @property({ type: Array })
  cachedModels: Map<string, number> = new Map<string, number>();

  @state()
  private options: LlmInferenceOptions & { forceF32?: boolean } =
    structuredClone(DEFAULT_OPTIONS);

  @state()
  private isLoggedIn = !!localStorage.getItem('oauth');

  override async connectedCallback() {
    super.connectedCallback();
    this.cachedModels = await getCachedModelsInfo();
    this.isLoggedIn = !!(await getOauthToken());
    window.addEventListener('oauth-removed', this.handleOauthRemoved);
  }



  override disconnectedCallback() {
    super.disconnectedCallback();
    window.removeEventListener('oauth-removed', this.handleOauthRemoved);
  }

  private handleOauthRemoved = () => {
    this.isLoggedIn = false;
  }

  static override styles = css`
    :host {
      display: block;
      padding: 16px;
      background-color: #f9f9f9;
      height: 100%;
      box-sizing: border-box;
      overflow-y: auto;
      font-family: Arial, Helvetica, sans-serif;
    }
    .options-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
      gap: 16px;
    }
    label {
      display: block;
      margin-bottom: 4px;
      font-weight: bold;
      font-size: 0.9em;
    }
    input,
    select {
      width: 100%;
      padding: 8px;
      border-radius: 4px;
      border: 1px solid #ccc;
      box-sizing: border-box;
      font-family: inherit;
    }
    input[type=range] {
      padding: 0;
    }
    .full-width {
      grid-column: 1 / -1;
    }
    h3 {
      margin-top: 0;
      font-size: 1.2em;
      color: #333;
      margin-bottom: 16px;
    }
    .dropdown-item {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 8px;
      cursor: pointer;
    }
    .dropdown-item:hover {
      background-color: #f0f0f0;
    }
    .cached-info {
      display: flex;
      align-items: center;
      gap: 4px;
      background-color: #e0e0e0;
      color: #333;
      padding: 2px 4px 2px 6px;
      border-radius: 4px;
      font-size: 0.8em;
      margin-left: 8px;
    }
    .delete-cache-btn {
      background: transparent;
      border: none;
      color: #888;
      cursor: pointer;
      font-weight: bold;
      padding: 0;
      margin: 0;
      font-size: 1.2em;
      line-height: 1;
    }
    .delete-cache-btn:hover {
      color: #c00;
    }
    .clear-all-btn {
      background: transparent;
      border: none;
      color: #888;
      cursor: pointer;
      padding: 0;
      margin: 0;
      line-height: 1;
      text-decoration: underline;
    }
    .clear-all-btn:hover {
      color: #c00;
    }
    .login-button {
      cursor: pointer;
      display: inline-block;
    }
    .cache-info {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-top: 8px;
      font-size: 0.9em;
      color: #666;
    }
  `;

  private dispatchOptionsChanged() {
    const event = new CustomEvent('options-changed', {
      detail: structuredClone(this.options),
      bubbles: true,
      composed: true,
    });
    this.dispatchEvent(event);
  }

  private dispatchPersonaChanged(persona: Persona) {
    const event = new CustomEvent('persona-changed', {
      detail: persona,
      bubbles: true,
      composed: true,
    });
    this.dispatchEvent(event);
  }

  private handlePersonaChange(e: Event) {
    const selectedName = (e.target as HTMLSelectElement).value;
    const selectedPersona = this.personas.find(p => p.name === selectedName);
    if (selectedPersona) {
      this.selectedPersonaName = selectedName;
      this.dispatchPersonaChanged(selectedPersona);
    }
  }



  private handleTemperatureChange(e: Event) {
    this.options = produce(this.options, (options) => {
      options.temperature = parseFloat((e.target as HTMLInputElement).value);
    });
    this.dispatchOptionsChanged();
  }

  private handleMaxTokensChange(e: Event) {
    this.options = produce(this.options, (options) => {
      options.maxTokens = parseInt((e.target as HTMLInputElement).value);
    });
    this.dispatchOptionsChanged();
  }

  private handleTopKChange(e: Event) {
    this.options = produce(this.options, (options) => {
      options.topK = parseInt((e.target as HTMLInputElement).value);
    });
    this.dispatchOptionsChanged();
  }

  private handleForceF32Change(e: Event) {
    this.options = produce(this.options, (options) => {
      options.forceF32 = (e.target as HTMLInputElement).checked;
    });
    this.dispatchOptionsChanged();
  }

  private async handleRemoveCached(e: Event, path: string) {
    e.stopPropagation();
    if (confirm('Remove model from cache?')) {
      await removeCachedModel(path);
      this.cachedModels = await getCachedModelsInfo();
    }
  }

  private async handleRemoveAllCached(e: Event) {
    e.stopPropagation();
    if (confirm('Remove all models from cache?')) {
      await removeAllCachedModels();
      this.cachedModels = await getCachedModelsInfo();
    }
  }

  private async handleLogin() {
    localStorage.removeItem("oauth");
    window.location.href = await oauthLoginUrl({
      scopes: ['read-repos'],
    }) + '&prompt=consent';
  }

  private getFileName(path: string): string {
    return path.split('/').pop()!;
  }

  private renderPersonaOptions(): Array<TemplateResult> {
    return this.personas.map(
      (persona) =>
        html`<option value="${persona.name}">${persona.name}</option>`
    );
  }

  private handleModelFileChange(e: Event) {
    const file = (e.target as HTMLInputElement).files?.[0];
    if (file) {
      this.options = produce(this.options, (options) => {
        options.baseOptions!.modelAssetPath = file.name;
        (options.baseOptions as any).modelAssetFile = file;
      });
      this.dispatchOptionsChanged();
    }
  }

  private getTotalCacheSize(): string {
    const totalSize = Array.from(this.cachedModels.values()).reduce((acc, size) => acc + size, 0);
    return (totalSize / 1e9).toFixed(2);
  }

  private handleModelChange(e: CustomEvent<string>) {
    this.options = produce(this.options, (options) => {
      options.baseOptions!.modelAssetPath = e.detail;
    });
    this.dispatchOptionsChanged();
  }

  override render() {
    const isChrome = navigator.userAgent.includes('Chrome');
    const isEdge = navigator.userAgent.includes('Edg');
    const showNativeFileChooser = !isChrome && !isEdge;

    return html`
      <h3>LLM Options</h3>
      <div class="options-grid">
        <div>
          <label for="model-select">Model:</label>
          ${showNativeFileChooser ?
            html`
              <input type="file" @change=${this.handleModelFileChange} />
              <p>Selected model: ${this.options.baseOptions?.modelAssetPath?.replace('file:', '')}</p>
            ` :
            html`
              <custom-dropdown
                .value=${this.options.baseOptions?.modelAssetPath}
                @change=${this.handleModelChange}
              >
                ${MODEL_PATHS.map(
                  (model) => {
                    const path = getModelUrl(model);
                    const cachedSize = this.cachedModels.get(this.getFileName(path));
                    const isDisabled = isHostedOnHuggingFace() && !this.isLoggedIn && !cachedSize;
                    return html`
                    <div class="dropdown-item" data-value=${path} ?disabled=${isDisabled}>
                      <span>${model.name}</span>
                      ${cachedSize ?
                        html`
                          <span class="cached-info">
                            <span>${(cachedSize / 1e9).toFixed(2)}GB</span>
                            <button class="delete-cache-btn" title="Remove from cache" @click=${(e: Event) => this.handleRemoveCached(e, path)}>✕</button>
                          </span>
                        ` : ''
                      }
                    </div>
                  `
                  }
                )}
              </custom-dropdown>
              <div class="cache-info">
                <span>Total cached: ${this.getTotalCacheSize()}GB</span>
                ${this.cachedModels.size > 0 ?
                  html`<button class="clear-all-btn" title="Remove all from cache" @click=${this.handleRemoveAllCached}>Clear all</button>` : ''
                }
                ${!this.isLoggedIn && isHostedOnHuggingFace() ? html`
                  <img
                    class="login-button"
                    src="https://huggingface.co/datasets/huggingface/badges/resolve/main/sign-in-with-huggingface-xl-dark.svg"
                    alt="Sign in with Hugging Face"
                    @click=${this.handleLogin}
                  />
                ` : ''}
              </div>
            `
          }
        </div>
        <div>
          <label for="persona-select">Prompt Template:</label>
          <select
            id="persona-select"
            .value=${this.selectedPersonaName}
            @change=${this.handlePersonaChange}
          >
            ${this.renderPersonaOptions() as any}
          </select>
        </div>
        <div>
          <label for="temperature"
            >Temperature: ${this.options.temperature!.toFixed(2)}</label
          >
          <input
            type="range"
            id="temperature"
            min="0"
            max="2"
            step="0.01"
            .value=${this.options.temperature!.toString()}
            @input=${this.handleTemperatureChange}
          />
        </div>
        <div>
          <label for="max-tokens">Max Tokens:</label> <input
            type="number"
            id="max-tokens"
            min="64"
            max="4096"
            step="64"
            .value=${this.options.maxTokens!.toString()}
            @input=${this.handleMaxTokensChange}
          />
        </div>
        <div>
          <label for="top-k">Top K:</label>
          <input
            type="number"
            id="top-k"
            min="1"
            max="100"
            step="1"
            .value=${this.options.topK!.toString()}
            @input=${this.handleTopKChange}
          />
        </div>
        <div>
          <label for="force-f32">Force F32 Fallback:</label>
          <input
            type="checkbox"
            id="force-f32"
            .checked=${this.options.forceF32}
            @change=${this.handleForceF32Change}
          />
        </div>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'llm-options': LlmOptions;
  }
  interface HTMLElementEventMap {
    'options-changed': CustomEvent<
      LlmInferenceOptions & { forceF32?: boolean }
    >;
    'persona-changed': CustomEvent<Persona>;
  }
}
