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

import { oauthLoginUrl, oauthHandleRedirectIfPresent, type OAuthToken } from "./hf-hub";

/**
 * Extracts the filename from a URL or path.
 * @param path The URL or path string.
 * @returns The filename.
 */
function getFileName(path: string): string {
  const parts = path.split('/');
  return parts[parts.length - 1]!;
}

/**
 * Returns oauth token, following redirect if needed.
 * @returns The oauth token, or null if none exists yet.
 */
export async function getOauthToken(): Promise<OAuthToken | null> {
  let oauthToken = localStorage.getItem("oauth");
  if (oauthToken) {
    try {
      return JSON.parse(oauthToken);
    } catch {
      return null;
    }
  }
  const newOauthToken = await oauthHandleRedirectIfPresent();
  if (newOauthToken) {
    localStorage.setItem('oauth', JSON.stringify(newOauthToken));
  }
  return newOauthToken;
}

/**
 * Loads a model, utilizing the Origin Private File System (OPFS) as a cache.
 *
 * This function performs the following steps:
 * 1. Checks if the model file exists in the OPFS cache.
 * 2. If it exists, then we grab the stored expected size and ensure validity.
 * 3. If the cached file is valid, it returns a ReadableStream from the cache.
 * 4. If the file is missing or invalid, it fetches the full model from the network, first saving the expected size from headers.
 * 5. As the model downloads, it's streamed to the OPFS cache for future use.
 * 6. The download stream is returned for immediate consumption.
 *
 * @param modelPath The URL of the model to load.
 * @returns A promise that resolves to an object containing the model's
 *   ReadableStream and its total size in bytes.
 */
export async function loadModelWithCache(modelPath: string): Promise<{ stream: ReadableStream<Uint8Array>, size: number }> {
  const fileName = getFileName(modelPath);
  const opfsRoot = await navigator.storage.getDirectory();

  // 1. Check for and validate the cached file
  try {
    const fileHandle = await opfsRoot.getFileHandle(fileName);
    const file = await fileHandle.getFile();
    const sizeHandle = await opfsRoot.getFileHandle(fileName + '_size');
    const sizeFile = await sizeHandle.getFile();
    const expectedSizeText = await sizeFile.text();
    const expectedSize = parseInt(expectedSizeText);
    if (file.size === expectedSize) {
      console.log('Found valid model in cache.');
      return { stream: file.stream(), size: file.size };
    } else {
      console.warn('Cached model has incorrect size. Deleting and re-downloading.');
      console.warn('Expected size text: ', expectedSizeText);
      console.warn('Expected size: ', expectedSize);
      console.warn('Actual size: ', file.size);
      await opfsRoot.removeEntry(fileName);
      await opfsRoot.removeEntry(fileName + '_size');
      throw new Error('Incorrect file size');
    }
  } catch (e) {
    // Ignore error if file doesn't exist, but log other errors
    if ((e as DOMException).name !== 'NotFoundError') {
        console.error('Error accessing OPFS:', e);
    }
  }

  const oauthToken = await getOauthToken();
  const headers = oauthToken ? { "Authorization": `Bearer ${oauthToken.accessToken}` } : undefined;

  // 2. If model was missing or invalid in cache, first fetch the size from headers.
  let expectedSize = -1;
  try {
    const headResponse = await fetch(modelPath, { method: 'HEAD', headers });
    if (!headResponse.ok) {
      throw new Error(`Failed to fetch model headers for ${modelPath}: ${headResponse.statusText}. Ensure you have accepted the proper model license on your HuggingFace account for the selected model.`);
    }
    expectedSize = Number(headResponse.headers.get('Content-Length'));
    if (isNaN(expectedSize) || expectedSize <= 0) {
      throw new Error('Invalid Content-Length header received.');
    }
  } catch (e) {
    console.warn(e)
  }

  // 3. Then fetch model from network and cache it
  console.log('Fetching model from network and caching to OPFS.');
  const response = await fetch(modelPath, { headers });
  if (!response.ok || !response.body) {
    // If this happens, our credentials may be stale; ensure those are not being cached to allow for re-auth to be triggered.
    localStorage.removeItem("oauth");
    window.dispatchEvent(new CustomEvent('oauth-removed'));
    throw new Error(`Failed to download model from ${modelPath}: ${response.statusText}. Ensure you have accepted the proper model license on your HuggingFace account for the selected model.`);
  }

  const [streamForConsumer, streamForCache] = response.body.tee();

  // Asynchronously cache the stream
  (async () => {
    try {
      const fileHandle = await opfsRoot.getFileHandle(fileName, { create: true });
      const writable = await fileHandle.createWritable();

      // Write the expected size to a companion file
      const sizeHandle = await opfsRoot.getFileHandle(fileName + '_size', { create: true });
      const sizeWritable = await sizeHandle.createWritable();
      const sizeWriter = sizeWritable.getWriter();
      const encoder = new TextEncoder();
      await sizeWriter.write(encoder.encode(expectedSize.toString()));
      sizeWriter.close();

      await streamForCache.pipeTo(writable);
      console.log(`Successfully cached ${fileName}.`);
    } catch (error) {
      console.error(`Failed to cache model ${fileName}:`, error);
      // Clean up partial file on failure
      try {
        await opfsRoot.removeEntry(fileName);
        await opfsRoot.removeEntry(fileName + '_size');
        //eslint-disable-next-line @typescript-eslint/no-unused-vars
      } catch (_cleanupError) {
        // Ignore cleanup error
      }
    }
  })();

  return { stream: streamForConsumer, size: expectedSize };
}

/**
 * Lists the names of all files currently stored in the OPFS cache.
 *
 * @returns A promise that resolves to a Set<string> of cached filenames.
 */
export async function listCachedModels(): Promise<Set<string>> {
  const opfsRoot = await navigator.storage.getDirectory();
  const cachedFiles = new Set<string>();
  for await (const handle of opfsRoot.values()) {
    if (handle.kind === 'file') {
      cachedFiles.add(handle.name);
    }
  }
  return cachedFiles;
}

/**
 * Removes a model and its size file from the OPFS cache.
 *
 * @param modelPath The path of the model to remove.
 */
export async function removeCachedModel(modelPath: string): Promise<void> {
  const fileName = getFileName(modelPath);
  const opfsRoot = await navigator.storage.getDirectory();
  try {
    await opfsRoot.removeEntry(fileName);
    await opfsRoot.removeEntry(fileName + '_size');
    console.log(`Successfully removed ${fileName} from cache.`);
  } catch (e) {
    if ((e as DOMException).name !== 'NotFoundError') {
      console.error(`Failed to remove ${fileName} from cache:`, e);
    }
  }
}
