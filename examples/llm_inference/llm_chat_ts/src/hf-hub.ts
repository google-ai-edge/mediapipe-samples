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

// Workaround for @huggingface/hub not having a browser-compatible export
// that esbuild can resolve. We use a dynamic import from a CDN (esm.sh)
// as suggested by other official Hugging Face projects.
// This module encapsulates the dynamic import so the rest of the app can
// import from here with proper types.

import type { OAuthToken as HubOAuthToken, oauthLoginUrl as hubOauthLoginUrl, oauthHandleRedirectIfPresent as hubOauthHandleRedirectIfPresent } from '@huggingface/hub';

// Re-export the type directly from the installed package.
// TypeScript will use this for type-checking.
export type OAuthToken = HubOAuthToken;

// Dynamically import the browser-compatible module from the CDN.
const hubModulePromise = import('https://esm.sh/@huggingface/hub');

// Export functions that resolve the promise and call the actual implementation.
export const oauthLoginUrl: typeof hubOauthLoginUrl = async (...args) => {
  const hub = await hubModulePromise;
  return hub.oauthLoginUrl(...args);
};

export const oauthHandleRedirectIfPresent: typeof hubOauthHandleRedirectIfPresent = async (...args) => {
  const hub = await hubModulePromise;
  return hub.oauthHandleRedirectIfPresent(...args);
};
