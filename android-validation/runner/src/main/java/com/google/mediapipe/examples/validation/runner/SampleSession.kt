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

package com.google.mediapipe.examples.validation.runner

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

/**
 * The running sample of a case: its real activity, started as the launcher starts it. An
 * adapter drives it through its ViewModel, as the screen of the sample does, and reads the
 * result from its UI state.
 */
class SampleSession
internal constructor(
    private val instrumentation: Instrumentation,
    val activity: ComponentActivity,
    private val case: ValidationCase,
    private val assets: JSONObject,
) {
    /** The model the sample loads for the case, once the adapter has named it: asset and SHA-256. */
    internal var model: Pair<String, String>? = null
        private set

    /**
     * The ViewModel of the sample, as its activity created it. Waits for the first composition,
     * which creates it.
     */
    fun <T : ViewModel> viewModel(type: Class<T>): T {
        val deadline = SystemClock.elapsedRealtime() + VIEW_MODEL_TIMEOUT_MS
        while (true) {
            val found = onMain { runCatching { ViewModelProvider(activity.viewModelStore, NotCreatedYet)[type] }.getOrNull() }
            if (found != null) return found
            if (SystemClock.elapsedRealtime() > deadline) {
                throw RunnerError(
                    "${activity.javaClass.name} did not create its ${type.simpleName} within " +
                        "${VIEW_MODEL_TIMEOUT_MS / 1000} s. Is the screen of the device on and unlocked?"
                )
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    /** Runs [block] on the main thread, where the screen of the sample calls its ViewModel. */
    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    /**
     * Marks in logcat that the sample is switching its delegate now, so a model that the sample
     * loaded on another delegate during startup is not mistaken for the delegate of the case.
     */
    fun beforeDelegateSwitch() {
        Log.i(CaseRunner.TAG, DELEGATE_SWITCH_MARKER)
    }

    /**
     * Waits until the UI state of the sample satisfies [done], and returns that state.
     *
     * @param what what is awaited, for the message of the timeout.
     */
    fun <S> await(state: StateFlow<S>, what: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS, done: (S) -> Boolean): S {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (true) {
            val current = state.value
            if (done(current)) return current
            if (SystemClock.elapsedRealtime() > deadline) {
                throw RunnerError(
                    "Timed out after ${timeoutMs / 1000} s waiting for $what. The UI state of the sample is: " +
                        current.toString().take(MAX_STATE_CHARS)
                )
            }
            SystemClock.sleep(POLL_MS)
        }
    }

    /** The pinned input file [asset] of the spec, as a file on the device. */
    fun inputFile(asset: String): File {
        val file = File(activity.cacheDir, "validation/inputs/$asset")
        file.parentFile?.mkdirs()
        try {
            activity.assets.open("validation/inputs/$asset").use { input -> file.outputStream().use { input.copyTo(it) } }
        } catch (error: IOException) {
            throw RunnerError("This app was built without the input $asset of the spec.")
        }
        return file
    }

    /**
     * The input file [asset] as a content:// URI with a MIME type, which is what a sample gets
     * when its user picks a file.
     */
    fun inputUri(asset: String): Uri =
        FileProvider.getUriForFile(activity, "${activity.packageName}.validation", inputFile(asset))

    /**
     * A short video file whose frames are the pinned photo [imageAsset], as a content:// URI
     * with a video MIME type, so a vision sample runs its real VIDEO mode path on that photo.
     */
    fun inputVideoUri(imageAsset: String): Uri {
        val sourceFile = inputFile(imageAsset)
        val decoded =
            BitmapFactory.decodeFile(sourceFile.absolutePath)
                ?: throw RunnerError("Could not decode the input image $imageAsset.")
        val bitmap =
            if (decoded.config == Bitmap.Config.ARGB_8888) decoded
            else decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        val width = bitmap.width
        val height = bitmap.height
        val even = width % 2 == 0 && height % 2 == 0
        val ext = if (even) "mp4" else "webm"
        val videoFile = File(activity.cacheDir, "validation/inputs/$imageAsset.$ext")
        if (!videoFile.isFile || videoFile.length() == 0L) {
            encodeStillVideo(bitmap, videoFile, even)
        }
        bitmap.recycle()
        return FileProvider.getUriForFile(activity, "${activity.packageName}.validation", videoFile)
    }

    /**
     * Stages a pinned asset from `cases.json` (for example `listenWith` for speech round-trip
     * verification) to a readable file in cache, verifying its SHA-256 against the spec.
     */
    fun assetFile(assetName: String): File {
        val pinned = assets.getJSONObject(assetName).getString("sha256")
        val target = File(activity.cacheDir, "validation/assets/$assetName")
        if (target.isFile && sha256File(target) == pinned) return target
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        try {
            activity.assets.open(assetName).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        } catch (error: IOException) {
            tmp.delete()
            throw RunnerError("This app was built without the asset $assetName of the spec.")
        }
        val actual = sha256File(tmp)
        if (actual != pinned) {
            tmp.delete()
            throw RunnerError("The asset $assetName has SHA-256 $actual, but the spec pins $pinned.")
        }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
        return target
    }

    /**
     * Every adapter states which asset of the app the sample loads as the model of the case.
     * The case runs only if that file is the model the spec pins: a sample may bundle it under
     * another name, but not another model.
     */
    fun usesModelAsset(assetPath: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        activity.assets.open(assetPath).use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
        val pinned = assets.getJSONObject(case.modelAsset).getString("sha256")
        if (sha256 != pinned) {
            throw RunnerError(
                "The sample loads the asset $assetPath (SHA-256 $sha256) for the model ${case.modelId}, " +
                    "but the spec pins ${case.modelAsset} with SHA-256 $pinned."
            )
        }
        model = assetPath to sha256
    }

    internal fun finish() {
        onMain { activity.finish() }
        instrumentation.waitForIdleSync()
    }

    private fun sha256File(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1 shl 16)
        file.inputStream().use { input ->
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun encodeStillVideo(bitmap: Bitmap, destination: File, evenDimensions: Boolean) {
        val width = bitmap.width
        val height = bitmap.height
        val mime = if (evenDimensions) MediaFormat.MIMETYPE_VIDEO_AVC else MediaFormat.MIMETYPE_VIDEO_VP9
        val codecName = if (evenDimensions) "c2.android.avc.encoder" else "c2.android.vp9.encoder"
        val muxerFormat =
            if (evenDimensions) MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            else MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val yPlane = ByteArray(width * height)
        val uvWidth = (width + 1) / 2
        val uvHeight = (height + 1) / 2
        val uPlane = ByteArray(uvWidth * uvHeight)
        val vPlane = ByteArray(uvWidth * uvHeight)
        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val argb = pixels[rowOffset + x]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                yPlane[rowOffset + x] = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255).toByte()
                if (x % 2 == 0 && y % 2 == 0) {
                    val uvIndex = (y / 2) * uvWidth + (x / 2)
                    uPlane[uvIndex] = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                    vPlane[uvIndex] = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255).toByte()
                }
            }
        }

        val tmp = File(destination.parentFile, "${destination.name}.tmp")
        tmp.delete()
        val codec =
            runCatching { MediaCodec.createByCodecName(codecName) }
                .getOrElse { MediaCodec.createEncoderByType(mime) }
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1
        try {
            val format =
                MediaFormat.createVideoFormat(mime, width, height).apply {
                    setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
                    setInteger(MediaFormat.KEY_BIT_RATE, 12_000_000)
                    setInteger(MediaFormat.KEY_FRAME_RATE, 10)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                }
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(tmp.absolutePath, muxerFormat)

            val bufferInfo = MediaCodec.BufferInfo()
            var submittedFrames = 0
            var inputDone = false
            var outputDone = false
            val totalFrames = 3
            while (!outputDone) {
                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000L)
                    if (inIndex >= 0) {
                        if (submittedFrames < totalFrames) {
                            val image = codec.getInputImage(inIndex)
                                ?: throw RunnerError("Video encoder $codecName returned no input Image.")
                            fillYuvImage(image, width, height, yPlane, uPlane, vPlane)
                            val ptsUs = submittedFrames * 100_000L
                            val size = codec.getInputBuffer(inIndex)?.limit() ?: (width * height * 3 / 2)
                            codec.queueInputBuffer(inIndex, 0, size, ptsUs, 0)
                            submittedFrames++
                        } else {
                            codec.queueInputBuffer(
                                inIndex,
                                0,
                                0,
                                totalFrames * 100_000L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputDone = true
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                when {
                    outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        trackIndex = muxer.addTrack(codec.outputFormat)
                        muxer.start()
                        muxerStarted = true
                    }
                    outIndex >= 0 -> {
                        val outBuffer = codec.getOutputBuffer(outIndex)
                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        if (!isConfig && bufferInfo.size > 0 && muxerStarted && outBuffer != null) {
                            outBuffer.position(bufferInfo.offset)
                            outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, outBuffer, bufferInfo)
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }
            }
        } finally {
            runCatching {
                if (muxerStarted) muxer?.stop()
                muxer?.release()
            }
            runCatching {
                codec.stop()
                codec.release()
            }
        }
        if (!tmp.renameTo(destination)) {
            tmp.copyTo(destination, overwrite = true)
            tmp.delete()
        }
    }

    private fun fillYuvImage(
        image: Image,
        width: Int,
        height: Int,
        yData: ByteArray,
        uData: ByteArray,
        vData: ByteArray,
    ) {
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        for (row in 0 until height) {
            val srcRow = row * width
            val dstRow = row * yRowStride
            if (yPixelStride == 1) {
                yBuffer.position(dstRow)
                yBuffer.put(yData, srcRow, width)
            } else {
                for (col in 0 until width) {
                    yBuffer.put(dstRow + col * yPixelStride, yData[srcRow + col])
                }
            }
        }

        val uvWidth = (width + 1) / 2
        val uvHeight = (height + 1) / 2
        val planes = listOf(image.planes[1] to uData, image.planes[2] to vData)
        for ((plane, src) in planes) {
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            val planeRows = minOf(uvHeight, (buffer.capacity() + rowStride - 1) / rowStride)
            for (row in 0 until planeRows) {
                val srcRow = row * uvWidth
                val dstRow = row * rowStride
                for (col in 0 until uvWidth) {
                    val idx = dstRow + col * pixelStride
                    if (idx < buffer.capacity()) {
                        buffer.put(idx, src[srcRow + col])
                    }
                }
            }
        }
    }

    private object NotCreatedYet : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = throw IllegalStateException("not created yet")

        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            throw IllegalStateException("not created yet")
    }

    companion object {
        /** Long enough to load the largest model of the spec on a slow phone. */
        const val DEFAULT_TIMEOUT_MS = 180_000L
        const val DELEGATE_SWITCH_MARKER = "=== VALIDATION_DELEGATE_SWITCH ==="
        private const val VIEW_MODEL_TIMEOUT_MS = 20_000L
        private const val POLL_MS = 20L
        private const val MAX_STATE_CHARS = 1500
    }
}
