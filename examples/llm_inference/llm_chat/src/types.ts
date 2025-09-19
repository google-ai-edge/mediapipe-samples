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

export interface Template {
  pre: string,
  post: string,
}

export type PromptTemplate = Record<ChatMessage['role'], Template>;

export type Tool = (text: string) => Promise<string>;

export interface Persona {
  name: string;
  // Instructions and few shot examples for the persona. Always included in the
  // prompt.
  instructions: ChatMessage[];
  // TODO: Model-agnostic
  promptTemplate?: PromptTemplate;
  tools?: Record<string /* line to match */, (text: string) => Promise<string>>; 
}

export interface ChatMessage {
  role: 'user' | 'model' | 'system';
  text: string, // Contains the full response or the response so far.
  templateApplied?: {
    text: string;
    tokenCount: number;
  };
  latencyMilliseconds?: number;
  // Different from templateApplied.tokenCount, which includes the template
  // tokens as well.
  generatedTokenCount?: number;
  doneGenerating?: boolean;
};
