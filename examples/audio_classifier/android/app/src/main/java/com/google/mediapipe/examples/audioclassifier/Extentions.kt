package com.google.mediapipe.examples.audioclassifier

import android.content.Context
import android.media.AudioFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.mediapipe.tasks.components.containers.AudioData
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        audioFloatArrayData.size / (audioDuration / 1000F / AudioClassifierHelper.EXPECTED_INPUT_LENGTH)

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
