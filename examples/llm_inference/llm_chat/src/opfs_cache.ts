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
 * Loads a model, utilizing the Origin Private File System (OPFS) as a cache.
 *
 * This function performs the following steps:
 * 1. Fetches the model's headers to get the expected size (Content-Length).
 * 2. Checks if the model file exists in the OPFS cache.
 * 3. If it exists, validates its size against the expected size.
 * 4. If the cached file is valid, it returns a ReadableStream from the cache.
 * 5. If the file is missing or invalid, it fetches the full model from the network.
 * 6. As the model downloads, it's streamed to the OPFS cache for future use.
 * 7. The download stream is returned for immediate consumption.
 *
 * @param modelPath The URL of the model to load.
 * @returns A promise that resolves to an object containing the model's
 *   ReadableStream and its total size in bytes.
 */
export async function loadModelWithCache(modelPath: string): Promise<{ stream: ReadableStream<Uint8Array>, size: number }> {
  const fileName = getFileName(modelPath);
  const opfsRoot = await navigator.storage.getDirectory();

  // 1. Get expected model size from HEAD request
  const headResponse = await fetch(modelPath, { method: 'HEAD' });
  if (!headResponse.ok) {
    throw new Error(`Failed to fetch model headers for ${modelPath}: ${headResponse.statusText}`);
  }
  const expectedSize = Number(headResponse.headers.get('Content-Length'));
  if (isNaN(expectedSize) || expectedSize <= 0) {
    throw new Error('Invalid Content-Length header received.');
  }

  // 2. Check for and validate the cached file
  try {
    const fileHandle = await opfsRoot.getFileHandle(fileName);
    const file = await fileHandle.getFile();
    if (file.size === expectedSize) {
      console.log('Found valid model in cache.');
      return { stream: file.stream(), size: file.size };
    } else {
      console.warn('Cached model has incorrect size. Deleting and re-downloading.');
      await opfsRoot.removeEntry(fileName);
    }
  } catch (e) {
    // Ignore error if file doesn't exist, but log other errors
    if ((e as DOMException).name !== 'NotFoundError') {
        console.error('Error accessing OPFS:', e);
    }
  }

  // 3. If cache is invalid or missing, fetch from network and cache it
  console.log('Fetching model from network and caching to OPFS.');
  const response = await fetch(modelPath);
  if (!response.ok || !response.body) {
    throw new Error(`Failed to download model from ${modelPath}: ${response.statusText}`);
  }

  const [streamForConsumer, streamForCache] = response.body.tee();

  // Asynchronously cache the stream
  (async () => {
    try {
      const fileHandle = await opfsRoot.getFileHandle(fileName, { create: true });
      const writable = await fileHandle.createWritable();
      await streamForCache.pipeTo(writable);
      console.log(`Successfully cached ${fileName}.`);
    } catch (error) {
      console.error(`Failed to cache model ${fileName}:`, error);
      // Clean up partial file on failure
      try {
        await opfsRoot.removeEntry(fileName);
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
