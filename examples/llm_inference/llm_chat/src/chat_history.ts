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

import { LitElement, html, css, PropertyValues } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';
import { repeat } from 'lit/directives/repeat.js';
import { ChatMessage } from './types'; // Ensure this path is correct

const SCROLL_THRESHOLD = 10; // Pixels from bottom to consider "at bottom"

@customElement('chat-history')
export class ChatHistory extends LitElement {
  @property({ type: Array })
  history: ChatMessage[] = [];

  @state()
  private _isScrolledToBottom = true;

  private _resizeObserver!: ResizeObserver;

  static override styles = css`
    :host {
      display: block;
      font-family: sans-serif;
      padding: 16px;
      overflow-y: auto;
      flex-grow: 1;
      min-height: 0;
    }

    .message-wrapper {
      display: flex;
      flex-direction: column;
      margin-bottom: 12px;
    }

    .message-container {
      padding: 8px;
      border-radius: 6px;
      max-width: 80%;
      word-wrap: break-word;
    }

    .user-message .message-container {
      background-color: #e1f5fe;
      margin-left: auto;
      text-align: right;
    }
    .user-message {
      align-items: flex-end;
    }

    .model-message .message-container {
      background-color: #f0f0f0;
      margin-right: auto;
      text-align: left;
    }
    .model-message {
      align-items: flex-start;
    }

    .role {
      font-weight: bold;
      font-size: 0.9em;
      margin-bottom: 4px;
      color: #555;
    }

    .text {
      font-size: 1em;
      white-space: pre-wrap;
    }

    .token-info {
      font-size: 0.7em;
      color: grey;
      margin-top: 5px;
    }

    .action-buttons-container {
      margin-top: 2px; /* Reduced margin slightly for icon buttons */
      max-width: 80%;
      display: flex;
      gap: 4px; /* Reduced gap slightly */
    }

    .model-message .action-buttons-container {
      justify-content: flex-start;
    }

    .user-message .action-buttons-container {
      justify-content: flex-end;
    }

    .action-button {
      border: none;
      background-color: transparent;
      padding: 4px; /* Adjust padding for icon size */
      font-size: 1.1em; /* Make icons a bit larger */
      cursor: pointer;
      color: #555; /* Default icon color */
      border-radius: 4px; /* Optional: for hover effect consistency */
      line-height: 1; /* Ensure icon is vertically centered */
    }

    .action-button:hover {
      background-color: #e0e0e0; /* Subtle hover effect */
      color: #333;
    }
    .action-button:active {
      background-color: #d0d0d0; /* Subtle active effect */
    }

    .redo-button {
      /* Specific styles for redo icon if needed, e.g., color */
    }

    .remove-button {
      color: #c82333; /* Red color for the 'x' icon */
    }
    .remove-button:hover {
      color: #a81d2a;
      background-color: #f8d7da; /* Light red hover for remove */
    }
    .remove-button:active {
      background-color: #f1c1c7;
    }
  `;

  override connectedCallback() {
    super.connectedCallback();
    this.addEventListener('scroll', this._handleScroll);
    this._resizeObserver = new ResizeObserver(() => {
      if (this._isScrolledToBottom) {
        this._scrollToBottom();
      }
    });
    this._resizeObserver.observe(this);
  }

  override disconnectedCallback() {
    super.disconnectedCallback();
    this.removeEventListener('scroll', this._handleScroll);
    if (this._resizeObserver) {
      this._resizeObserver.disconnect();
    }
  }

  private _handleScroll() {
    const el = this;
    const atBottom = el.scrollHeight - el.scrollTop <= el.clientHeight + SCROLL_THRESHOLD;
    this._isScrolledToBottom = atBottom;
  }

  private _scrollToBottom() {
    this.scrollTop = this.scrollHeight;
  }

  override updated(changedProperties: PropertyValues<this>) {
    super.updated(changedProperties);
    if (changedProperties.has('history') && this._isScrolledToBottom) {
      Promise.resolve().then(() => this._scrollToBottom());
    }
  }

  private getTokenInfoText(message: ChatMessage): string {
    let latencyInfo = '';
    const { latencyMilliseconds, generatedTokenCount } = message;
    if (latencyMilliseconds != null && generatedTokenCount != null) {
      latencyInfo = ` in ${(latencyMilliseconds / 1000).toFixed(1)} sec`;
    }
    const tokenCount = message.templateApplied?.tokenCount ?? 'N/A';
    return `(${tokenCount} Tokens${latencyInfo})`;
  }

  private _handleRedoLastMessage() {
    const event = new CustomEvent('regenerate-last-model-message', {
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  private _handleRemoveLastMessage() {
    const event = new CustomEvent('remove-last-message', {
      bubbles: true,
      composed: true
    });
    this.dispatchEvent(event);
  }

  override render() {
    return html`
      ${repeat(
        this.history,
        (message, index) => `${message.role}-${index}-${message.text.length}-${message.doneGenerating}`,
        (message, index) => {
          const isLastMessage = index === this.history.length - 1 && this.history.length > 0;
          const canShowActions = isLastMessage && (message.role === 'model' ? message.doneGenerating : true)

          return html`
            <div class="message-wrapper ${message.role === 'user' ? 'user-message' : 'model-message'}">
              <div class="message-container">
                <div class="role">
                  ${message.role.charAt(0).toUpperCase() + message.role.slice(1)}
                </div>
                <div class="text">${message.text}</div>
                ${message.templateApplied
                  ? html`
                      <div class="token-info">
                        ${this.getTokenInfoText(message)}
                      </div>
                    `
                  : ''}
              </div>

              ${canShowActions
                ? html`
                    <div class="action-buttons-container">
                      <button
                        class="action-button remove-button"
                        @click=${this._handleRemoveLastMessage}
                        title="Remove this message"
                      >
                        ✕
                      </button>
                      ${message.role === 'model' && message.doneGenerating === true
                        ? html`
                            <button
                              class="action-button redo-button"
                              @click=${this._handleRedoLastMessage}
                              title="Regenerate this message"
                            >
                              ↻
                            </button>`
                        : ''}
                    </div>
                  `
                : ''}
            </div>
          `;
        }
      )}
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'chat-history': ChatHistory;
  }
}
