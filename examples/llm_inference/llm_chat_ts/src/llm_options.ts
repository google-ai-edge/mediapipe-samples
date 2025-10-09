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
import { MODEL_PATHS } from './llm_service';
import { LlmInferenceOptions } from '@mediapipe/tasks-genai';
import { produce } from 'immer';
import { DEFAULT_OPTIONS } from './constants';
import { Persona } from './types';
import { PERSONAS } from './personas';
import { getOauthToken, listCachedModels, removeCachedModel } from './opfs_cache';
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
  cachedModels: Set<string> = new Set<string>();

  @state()
  private options: LlmInferenceOptions & { forceF32?: boolean } =
    structuredClone(DEFAULT_OPTIONS);

  @state()
  private isLoggedIn = !!localStorage.getItem('oauth');

  override async connectedCallback() {
    super.connectedCallback();
    await this._validateCache();
    this.cachedModels = await listCachedModels();
    this.isLoggedIn = !!(await getOauthToken());
    window.addEventListener('oauth-removed', this.handleOauthRemoved);
  }

  private async _validateCache() {
    const opfsRoot = await navigator.storage.getDirectory();
    const allFiles = await listCachedModels();
    for (const fileName of allFiles) {
      if (fileName.endsWith('_size')) {
        continue;
      }

      try {
        const fileHandle = await opfsRoot.getFileHandle(fileName);
        const file = await fileHandle.getFile();
        const sizeHandle = await opfsRoot.getFileHandle(fileName + '_size');
        const sizeFile = await sizeHandle.getFile();
        const expectedSize = parseInt(await sizeFile.text());
        if (file.size !== expectedSize) {
          await opfsRoot.removeEntry(fileName);
          await opfsRoot.removeEntry(fileName + '_size');
        }
      } catch (e) {
        // If any error occurs (e.g., size file not found), remove the cached model
        await opfsRoot.removeEntry(fileName);
        await opfsRoot.removeEntry(fileName + '_size');
      }
    }
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
    .cached-badge {
      background-color: #e0e0e0;
      color: #333;
      padding: 2px 6px;
      border-radius: 4px;
      font-size: 0.8em;
      margin-left: 8px;
      cursor: pointer;
    }
    .login-button {
      cursor: pointer;
      display: inline-block;
      margin-top: 8px;
    }
  `;

  private _dispatchOptionsChanged() {
    const event = new CustomEvent('options-changed', {
      detail: structuredClone(this.options),
      bubbles: true,
      composed: true,
    });
    this.dispatchEvent(event);
  }

  private _dispatchPersonaChanged(persona: Persona) {
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
      this._dispatchPersonaChanged(selectedPersona);
    }
  }

  private handleModelChange(e: CustomEvent<string>) {
    this.options = produce(this.options, (options) => {
      options.baseOptions!.modelAssetPath = e.detail;
    });
    this._dispatchOptionsChanged();
  }

  private handleTemperatureChange(e: Event) {
    this.options = produce(this.options, (options) => {
      options.temperature = parseFloat((e.target as HTMLInputElement).value);
    });
    this._dispatchOptionsChanged();
  }

  private handleMaxTokensChange(e: Event) {
    this.options = produce(this.options, (options) => {
      options.maxTokens = parseInt((e.target as HTMLInputElement).value);
    });
    this._dispatchOptionsChanged();
  }

  private handleTopKChange(e: Event) {
    this.options = produce(this.options, (options) => {
      options.topK = parseInt((e.target as HTMLInputElement).value);
    });
    this._dispatchOptionsChanged();
  }

  private handleForceF32Change(e: Event) {
    this.options = produce(this.options, (options) => {
      options.forceF32 = (e.target as HTMLInputElement).checked;
    });
    this._dispatchOptionsChanged();
  }

  private async handleRemoveCached(e: Event, path: string) {
    e.stopPropagation();
    if (confirm('Remove model from cache?')) {
      await removeCachedModel(path);
      this.cachedModels = await listCachedModels();
    }
  }

  private async handleLogin() {
    localStorage.removeItem("oauth");
    window.location.href = await oauthLoginUrl({
      scopes: ['read-repos'],
    }) + '&prompt=consent';
  }

  private _getFileName(path: string): string {
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
      this._dispatchOptionsChanged();
    }
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
                  ([name, path]) => {
                    const isCached = this.cachedModels.has(this._getFileName(path));
                    const isDisabled = !this.isLoggedIn && !isCached;
                    return html`
                    <div class="dropdown-item" data-value=${path} ?disabled=${isDisabled}>
                      <span>${name}</span>
                      ${isCached ?
                        html`<span class="cached-badge" @click=${(e: Event) => this.handleRemoveCached(e, path)}>Cached</span>` : ''
                      }
                    </div>
                  `
                  }
                )}
              </custom-dropdown>
              ${!this.isLoggedIn ? html`
                <img
                  class="login-button"
                  src="https://huggingface.co/datasets/huggingface/badges/resolve/main/sign-in-with-huggingface-xl-dark.svg"
                  alt="Sign in with Hugging Face"
                  @click=${this.handleLogin}
                />
              ` : ''}
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
