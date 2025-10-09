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

import { LlmInferenceOptions } from "@mediapipe/tasks-genai";

export const DEFAULT_OPTIONS: LlmInferenceOptions & {forceF32?: boolean} = {
  baseOptions: {
    modelAssetPath: undefined,
  },
  numResponses: 1,
  topK: 64,
  temperature: 1,
  maxTokens: 1536,
  forceF32: false,
};
