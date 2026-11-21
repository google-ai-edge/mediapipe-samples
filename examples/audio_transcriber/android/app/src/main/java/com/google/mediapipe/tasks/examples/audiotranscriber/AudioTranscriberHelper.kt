/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.tasks.examples.audiotranscriber

import android.content.Context
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriber.AudioTranscriberOptions
import com.google.mediapipe.tasks.audio.audiotranscriber.AudioTranscriberResult
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Thin seam around MediaPipe's [AudioTranscriber] task API, so [AudioTranscriberViewModel] can be
 * tested without the model.
 */
interface AudioTranscriberHelper : AutoCloseable {
  fun prewarm()

  @Suppress("KotlinArray") // AudioTranscriber.transcribe() takes PCM samples as a FloatArray.
  fun transcribe(samples: FloatArray, endOfStream: Boolean = false): AudioTranscriberResult

  fun finishStream(): AudioTranscriberResult

  override fun close()

  companion object {
    const val DELEGATE_CPU = 0
    const val DELEGATE_GPU = 1
    const val MODEL_EXTENSION = "litertlm"
    const val DEFAULT_MODEL_FILE_NAME = "parakeet_tdt_0.6b_v3_5s_i8_stateful.$MODEL_EXTENSION"
    const val SAMPLE_RATE_HZ = 16_000

    /** Creates an [AudioTranscriberHelper] instance backed by MediaPipe's [AudioTranscriber]. */
    fun create(
      context: Context,
      modelFile: File,
      delegateIndex: Int = DELEGATE_CPU,
      enableInterimResults: Boolean = true,
      sampleRateHz: Int = SAMPLE_RATE_HZ,
    ): AudioTranscriberHelper {
      val delegate =
        if (delegateIndex == DELEGATE_GPU) Delegate.GPU else Delegate.CPU
      val baseOptions =
        BaseOptions.builder()
          .setModelAssetPath(modelFile.absolutePath)
          .setDelegate(delegate)
          .build()
      val options =
        AudioTranscriberOptions.builder()
          .setBaseOptions(baseOptions)
          .setSampleRateHz(sampleRateHz)
          .setEnableInterimResults(enableInterimResults)
          .setModelArchitecture(AudioTranscriber.ModelArchitecture.AUTO)
          .setCacheDir(context.cacheDir.absolutePath)
          .build()
      val transcriber = AudioTranscriber.createFromOptions(context, options)
      return object : AudioTranscriberHelper {
        override fun prewarm() = transcriber.prewarm()

        override fun transcribe(samples: FloatArray, endOfStream: Boolean) =
          transcriber.transcribe(samples, endOfStream)

        override fun finishStream() = transcriber.finishStream()

        override fun close() = transcriber.close()
      }
    }
  }
}

/**
 * Resolves the default `.litertlm` model file, preferring any sideloaded `.litertlm` file in
 * [sideloadDirectories] before staging the bundled asset [fileName] into [cacheDir].
 */
internal fun resolveDefaultModelFile(
  sideloadDirectories: List<File>,
  cacheDir: File,
  assetOpener: (String) -> InputStream,
  fileName: String = AudioTranscriberHelper.DEFAULT_MODEL_FILE_NAME,
): File {
  val sideloaded =
    sideloadDirectories
      .asSequence()
      .filter { it.isDirectory }
      .mapNotNull { dir ->
        dir
          .listFiles()
          ?.filter {
            it.isFile &&
              !it.isHidden &&
              it.extension.equals(AudioTranscriberHelper.MODEL_EXTENSION, ignoreCase = true)
          }
          ?.minByOrNull { it.name }
      }
      .firstOrNull()
  if (sideloaded != null) return sideloaded

  cacheDir.mkdirs()
  val stagedFile = File(cacheDir, fileName)
  if (stagedFile.exists() && stagedFile.length() > 0L) return stagedFile

  val candidatePaths =
    listOf(
      fileName,
      "models/$fileName",
      "third_party/odml/audio/transcription/data/models/$fileName",
    )
  for (path in candidatePaths) {
    try {
      assetOpener(path).use { input ->
        FileOutputStream(stagedFile).use { output -> input.copyTo(output) }
      }
      return stagedFile
    } catch (_: IOException) {
      // Try next candidate path.
    }
  }
  throw FileNotFoundException("Model not found in bundle: $fileName")
}

private const val SIDELOAD_CACHE_SUBDIR = "sideloaded_models"
private const val DEFAULT_SIDELOADED_FILE_NAME = "sideloaded_model.litertlm"

/**
 * Copies the stream provided by [inputStreamProvider] into a file inside
 * `[cacheDir]/sideloaded_models/` with a sanitized `.litertlm` filename derived from [displayName].
 */
internal fun stageSideloadedModelStream(
  cacheDir: File,
  displayName: String?,
  inputStreamProvider: () -> InputStream?,
): File {
  val targetDir = File(cacheDir, SIDELOAD_CACHE_SUBDIR)
  if (!targetDir.exists()) {
    targetDir.mkdirs()
  }

  val sanitizedBaseName =
    displayName?.substringAfterLast('/')?.replace(Regex("[^A-Za-z0-9._-]"), "_")?.takeIf {
      it.isNotBlank()
    } ?: DEFAULT_SIDELOADED_FILE_NAME
  val finalFileName =
    if (sanitizedBaseName.endsWith(".${AudioTranscriberHelper.MODEL_EXTENSION}", true)) {
      sanitizedBaseName
    } else {
      "$sanitizedBaseName.${AudioTranscriberHelper.MODEL_EXTENSION}"
    }

  val destinationFile = File(targetDir, finalFileName)
  val inputStream =
    inputStreamProvider() ?: throw IOException("Unable to open selected model document stream.")
  inputStream.use { input ->
    FileOutputStream(destinationFile).use { output -> input.copyTo(output) }
  }
  return destinationFile
}
