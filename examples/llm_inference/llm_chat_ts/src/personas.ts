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

import { BASE_GEMMA3_PERSONA } from "./personas/base_gemma3";
import { JS_TOOL_USE } from "./personas/js_tool_use";
import { Persona } from "./types";

export const PERSONAS = [
  BASE_GEMMA3_PERSONA,
  JS_TOOL_USE,
] as const satisfies Persona[];
