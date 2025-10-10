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
  private isOpen = false;

  private boundHandleOutsideClick: (e: MouseEvent) => void;

  constructor() {
    super();
    this.boundHandleOutsideClick = this.handleOutsideClick.bind(this);
  }

  override disconnectedCallback(): void {
    super.disconnectedCallback();
    document.removeEventListener('click', this.boundHandleOutsideClick);
  }

  private setOpen(isOpen: boolean) {
    if (this.isOpen === isOpen) {
      return;
    }
    this.isOpen = isOpen;
    if (this.isOpen) {
      setTimeout(() => {
        document.addEventListener('click', this.boundHandleOutsideClick);
      }, 0);
    } else {
      document.removeEventListener('click', this.boundHandleOutsideClick);
    }
  }

  private handleOutsideClick(e: MouseEvent) {
    if (!this.contains(e.target as Node)) {
      this.setOpen(false);
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

  private toggleDropdown() {
    this.setOpen(!this.isOpen);
  }

  private handleItemClick(e: Event) {
    const target = (e.target as HTMLElement).closest('[data-value]') as HTMLElement | null;
    if (target?.dataset['value'] && !target.hasAttribute('disabled')) {
      this.value = target.dataset['value'];
      this.setOpen(false);
      this.dispatchEvent(new CustomEvent('change', { detail: this.value }));
    }
  }

  override render() {
    // Find the selected item in the slot to display its text in the button
    const items = Array.from(this.querySelectorAll('[data-value]'));
    const selectedItem = items.find(item => (item as HTMLElement).dataset['value'] === this.value);
    const buttonText = selectedItem ? selectedItem.querySelector('span')?.textContent ?? selectedItem.textContent : 'Select...';

    return html`
      <button class="dropdown-button" @click=${this.toggleDropdown}>
        ${buttonText}
      </button>
      <div class="dropdown-content ${this.isOpen ? 'show' : ''}">
        <slot @slotchange=${() => this.requestUpdate()} @click=${this.handleItemClick}></slot>
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'custom-dropdown': CustomDropdown;
  }
}
