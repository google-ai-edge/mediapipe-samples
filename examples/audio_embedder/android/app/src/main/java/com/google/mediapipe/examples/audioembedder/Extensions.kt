/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.audioembedder

import android.content.Context
import android.media.AudioFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mediapipe.tasks.components.containers.AudioData
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

// Convert audio uri to AudioData
fun Uri.createAudioData(context: Context): AudioData {
    val inputStream = context.contentResolver.openInputStream(this)
    val dataInputStream = DataInputStream(inputStream)
    val targetArray = ByteArray(dataInputStream.available())
    dataInputStream.read(targetArray)
    val audioFloatArrayData = targetArray.toShortArray()

    // get audio's duration
    val mmr = MediaMetadataRetriever()
    mmr.setDataSource(context, this)
    val durationStr =
        mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
    val audioDuration = durationStr!!.toInt()

    // calculate the sample rate
    val expectedSampleRate =
        audioFloatArrayData.size / (audioDuration / 1000F / AudioEmbedderHelper.YAMNET_EXPECTED_INPUT_LENGTH)

    // create audio data
    val audioData = AudioData.create(
        AudioData.AudioDataFormat.builder().setNumOfChannels(
            AudioFormat.CHANNEL_IN_DEFAULT
        ).setSampleRate(expectedSampleRate).build(), audioFloatArrayData.size
    )
    audioData.load(audioFloatArrayData)
    return audioData
}

fun ByteArray.toShortArray(): ShortArray {
    val result = ShortArray(this.size / 2)
    ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        .get(result)
    return result
}

// Get the audio's name
fun Uri.getName(context: Context): String? {
    val returnCursor =
        context.contentResolver.query(this, null, null, null, null)
    val nameIndex =
        returnCursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
    returnCursor?.moveToFirst()
    val fileName = nameIndex?.let { returnCursor.getString(it) }
    returnCursor?.close()
    return fileName
}
