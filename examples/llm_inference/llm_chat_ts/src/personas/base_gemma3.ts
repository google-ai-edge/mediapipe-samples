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

import { Persona } from "../types";

export const BASE_GEMMA3_PERSONA = {
  name: 'Base Gemma3',
  instructions: [],
  promptTemplate: {
    user: {
      pre: '<start_of_turn>user\n',
      post: '<end_of_turn>\n',
    },
    model: {
      pre: '<start_of_turn>model\n',
      post: '<end_of_turn>\n',
    },
    system: {
      pre: '<start_of_turn>user\n',
      post: '<end_of_turn>\n',
    }
  }
} satisfies Persona;
