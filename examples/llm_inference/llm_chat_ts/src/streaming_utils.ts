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

import { BehaviorSubject } from 'rxjs';

/**
 * Creates a TransformStream that monitors the progress of a stream
 * and reports it via a BehaviorSubject. This is backpressure-aware and
 * safe to use with fast sources (like OPFS).
 *
 * @param contentLength The total size of the stream's content in bytes.
 * @param progress$ The RxJS BehaviorSubject to emit progress updates to.
 * @returns A TransformStream to be piped through.
 */
function createProgressTransformer(
  contentLength: number,
  progress$: BehaviorSubject<number>
): TransformStream<Uint8Array, Uint8Array> {
  let bytesRead = 0;
  return new TransformStream({
    transform(chunk, controller) {
      bytesRead += chunk.length;
      const percentage = bytesRead / contentLength;
      progress$.next(percentage);
      controller.enqueue(chunk);
    },
    flush() {
      progress$.next(1);
      progress$.complete();
    }
  });
}


/**
 * Wraps a ReadableStream to provide progress updates without consuming it,
 * using a backpressure-aware TransformStream.
 *
 * @param inputStream The original ReadableStream to be monitored.
 * @param contentLength The total size of the stream's content in bytes.
 * @returns An object containing:
 *   - `stream`: A new ReadableStream that can be consumed by the application
 *     (e.g., passed to MediaPipe), with progress monitoring attached.
 *   - `progress$`: An RxJS BehaviorSubject that emits progress updates as
 *     numbers from 0.0 to 1.0.
 */
export function streamWithProgress(
  inputStream: ReadableStream<Uint8Array>,
  contentLength: number
): { stream: ReadableStream<Uint8Array>; progress$: BehaviorSubject<number> } {
  const progress$ = new BehaviorSubject<number>(0);
  const progressTransformer = createProgressTransformer(contentLength, progress$);
  const stream = inputStream.pipeThrough(progressTransformer);

  return { stream, progress$ };
}
