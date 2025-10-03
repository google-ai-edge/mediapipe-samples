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

import { LitElement, html, css } from 'lit';
import { customElement, property, state } from 'lit/decorators.js';

@customElement('custom-dropdown')
export class CustomDropdown extends LitElement {
  @property({ type: String })
  value: string = '';

  @state()
  private _isOpen = false;

  private _boundHandleOutsideClick: (e: MouseEvent) => void;

  constructor() {
    super();
    this._boundHandleOutsideClick = this._handleOutsideClick.bind(this);
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    document.removeEventListener('click', this._boundHandleOutsideClick);
  }

  private _setOpen(isOpen: boolean) {
    if (this._isOpen === isOpen) {
      return;
    }
    this._isOpen = isOpen;
    if (this._isOpen) {
      setTimeout(() => {
        document.addEventListener('click', this._boundHandleOutsideClick);
      }, 0);
    } else {
      document.removeEventListener('click', this._boundHandleOutsideClick);
    }
  }

  private _handleOutsideClick(e: MouseEvent) {
    if (!this.contains(e.target as Node)) {
      this._setOpen(false);
    }
  }

  static override styles = css`
    :host {
      display: block;
      position: relative;
      font-family: Arial, Helvetica, sans-serif;
    }
    .dropdown-button {
      width: 100%;
      padding: 8px;
      border-radius: 4px;
      border: 1px solid #ccc;
      background-color: #fff;
      cursor: pointer;
      text-align: left;
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
    .dropdown-button:after {
      content: '▼';
      font-size: 0.8em;
    }
    .dropdown-content {
      display: none;
      position: absolute;
      background-color: #f9f9f9;
      width: 100%;
      box-shadow: 0px 8px 16px 0px rgba(0,0,0,0.2);
      z-index: 1;
      border-radius: 4px;
      max-height: 300px;
      overflow-y: auto;
    }
    .dropdown-content.show {
      display: block;
    }
    ::slotted([disabled]) {
      opacity: 0.5;
      cursor: not-allowed;
      background-color: #eee;
    }
    ::slotted([disabled]:hover) {
      background-color: #eee;
    }
  `;

  private _toggleDropdown() {
    this._setOpen(!this._isOpen);
  }

  private _handleItemClick(e: Event) {
    const target = (e.target as HTMLElement).closest('[data-value]') as HTMLElement | null;
    if (target?.dataset['value'] && !target.hasAttribute('disabled')) {
      this.value = target.dataset['value'];
      this._setOpen(false);
      this.dispatchEvent(new CustomEvent('change', { detail: this.value }));
    }
  }

  override render() {
    // Find the selected item in the slot to display its text in the button
    const items = Array.from(this.querySelectorAll('[data-value]'));
    const selectedItem = items.find(item => (item as HTMLElement).dataset['value'] === this.value);
    const buttonText = selectedItem ? selectedItem.querySelector('span')?.textContent ?? selectedItem.textContent : 'Select...';

    return html`
      <button class="dropdown-button" @click=${this._toggleDropdown}>
        ${buttonText}
      </button>
      <div class="dropdown-content ${this._isOpen ? 'show' : ''}">
        <slot @slotchange=${() => this.requestUpdate()} @click=${this._handleItemClick}></slot>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'custom-dropdown': CustomDropdown;
  }
}
