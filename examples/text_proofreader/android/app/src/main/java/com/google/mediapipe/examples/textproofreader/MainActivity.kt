/*
 * Copyright 2026 The MediaPipe Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.textproofreader

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.mediapipe.framework.MediaPipeException
import com.google.mediapipe.tasks.text.textproofreader.TextProofreader
import com.google.mediapipe.tasks.text.textproofreader.TextProofreader.TextProofreaderOptions
import com.google.mediapipe.tasks.text.textproofreader.TextProofreaderStreamingResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A simple demo app for MediaPipe Text Proofreader. */
class MainActivity : Activity() {

    private lateinit var resultTextView: TextView
    private lateinit var diffTextView: TextView
    private lateinit var inputEditText: EditText
    private lateinit var proofreadButton: Button
    private lateinit var proofreadStreamingButton: Button
    private lateinit var clearButton: Button
    private lateinit var progressBar: ProgressBar

    @Volatile
    private var proofreader: TextProofreader? = null
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private data class LocalCorrection(val text: String, val type: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ====================================================================
        // SAFETY NET 1: Catch unhandled MediaPipe background thread crashes
        // ====================================================================
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val msg = throwable.message ?: ""
            if (msg.contains("echo loop", ignoreCase = true) || throwable is MediaPipeException) {
                // The native C++ thread threw an exception as it died.
                // Swallow it to protect the app from crashing.
                runOnUiThread {
                    Toast.makeText(this, "Recovered from background engine crash.", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Not a MediaPipe bug, let the app crash normally
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
        // ====================================================================

        setContentView(R.layout.activity_text_proofreader)

        inputEditText = findViewById(R.id.inputEditText)
        proofreadButton = findViewById(R.id.proofreadButton)
        proofreadStreamingButton = findViewById(R.id.proofreadStreamingButton)
        clearButton = findViewById(R.id.clearButton)
        resultTextView = findViewById(R.id.resultTextView)
        diffTextView = findViewById(R.id.diffTextView)
        progressBar = findViewById(R.id.progressBar)

        proofreadButton.setOnClickListener { proofreadText(streaming = false) }
        proofreadStreamingButton.setOnClickListener { proofreadText(streaming = true) }
        clearButton.setOnClickListener { clearText() }

        if (savedInstanceState != null) {
            resultTextView.text = savedInstanceState.getString(KEY_RESULT_TEXT) ?: ""
        }

        backgroundExecutor.execute { initializeProofreader() }
        showProgress(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_RESULT_TEXT, resultTextView.text.toString())
    }

    private fun initializeProofreader() {
        try {
            proofreader?.close()
            proofreader = null

            // Limit tokens slightly to prevent Out-Of-Memory (OOM) issues during heavy loops
            val optionsBuilder = TextProofreaderOptions.builder().setMaxNumTokens(2048)

            val modelPath = copyAssetToCache(MODEL_PATH)
            var loadedFromFd = false
            val cacheFile = File(modelPath)
            
            proofreader = try {
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    optionsBuilder.setModelAssetFileDescriptor(pfd)
                    loadedFromFd = true
                    TextProofreader.createFromOptions(this, optionsBuilder.build())
                }
            } catch (e: IOException) {
                optionsBuilder.setModelPath(modelPath)
                TextProofreader.createFromOptions(this, optionsBuilder.build())
            }

            val initMsg = if (loadedFromFd) "Initialized from FD" else "Initialized from Path"
            runOnUiThread {
                Toast.makeText(this, initMsg, Toast.LENGTH_SHORT).show()
                showProgress(false)
            }
        } catch (t: Throwable) {
            proofreader = null
            runOnUiThread {
                Toast.makeText(this, "Failed to init: ${t.message}", Toast.LENGTH_LONG).show()
                resultTextView.append("\nError: ${t.message}")
                showProgress(false)
            }
        }
    }

    @Throws(IOException::class)
    private fun copyAssetToCache(assetName: String): String {
        val file = File(cacheDir, assetName)
        assets.open(assetName).use { inputStream ->
            if (file.exists() && file.length() == inputStream.available().toLong()) {
                return file.absolutePath
            }
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file.absolutePath
    }

    private fun proofreadText(streaming: Boolean) {
        val text = inputEditText.text.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, "Text is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val currentProofreader = proofreader ?: run {
            Toast.makeText(this, "Proofreader not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        resultTextView.text = ""
        diffTextView.text = ""
        showProgress(true)

        if (streaming) {
            try {
                currentProofreader.proofreadStreaming(
                    text,
                    object : TextProofreader.ProofreaderResultCallback {
                        override fun onNext(result: TextProofreaderStreamingResult) {
                            try {
                                val chunk = result.getChunk() ?: ""
                                val isDone = result.isDone()

                                val localCorrections = if (isDone) {
                                    result.getCorrections()?.map {
                                        LocalCorrection(it.text, it.type?.toString() ?: "")
                                    }
                                } else null

                                runOnUiThread {
                                    if (chunk.isNotEmpty()) {
                                        resultTextView.append(chunk)
                                    }
                                    if (isDone && localCorrections != null) {
                                        diffTextView.text = formatCorrections(localCorrections)
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore Use-After-Free UI updates if the graph is already dead
                            }
                        }

                        override fun onError(throwable: Throwable) {
                            val errorMsg = throwable.message ?: "Unknown error"
                            runOnUiThread {
                                if (errorMsg.contains("echo loop", ignoreCase = true)) {
                                    resultTextView.append("\n\n[Stopped: Low quality output detected. Resetting Engine...]")
                                } else {
                                    resultTextView.append("\n[Error: $errorMsg]")
                                }
                                showProgress(true) // Keep UI locked while engine resets
                            }

                            // SAFETY NET 2: Delayed Teardown
                            // Give the native C++ thread 1000ms to finish its error state
                            // before we destroy the object from memory.
                            Handler(Looper.getMainLooper()).postDelayed({
                                backgroundExecutor.execute { initializeProofreader() }
                            }, 1000)
                        }

                        override fun onDone() {
                            runOnUiThread { showProgress(false) }
                        }
                    })
            } catch (t: Throwable) {
                resultTextView.text = "Error starting stream: ${t.message}"
                showProgress(true)
                backgroundExecutor.execute { initializeProofreader() }
            }
        } else {
            backgroundExecutor.execute {
                try {
                    val resultObj = currentProofreader.proofread(text)
                    val resultTextString: String = resultObj.getProofreadText() ?: ""

                    val localCorrections = try {
                        resultObj.getCorrections()?.map {
                            LocalCorrection(it.text, it.type?.toString() ?: "")
                        }
                    } catch (e: Throwable) { null }

                    val styledDiffText = formatCorrections(localCorrections)
                    runOnUiThread {
                        resultTextView.text = resultTextString
                        diffTextView.text = styledDiffText
                        showProgress(false)
                    }
                } catch (t: Throwable) {
                    runOnUiThread {
                        resultTextView.text = "Error: ${t.message}. Reinitializing..."
                        showProgress(true)
                    }
                    initializeProofreader()
                }
            }
        }
    }

    private fun formatCorrections(corrections: List<LocalCorrection>?): CharSequence {
        val builder = SpannableStringBuilder()
        if (corrections == null) return builder

        for (correction in corrections) {
            val start = builder.length
            builder.append(correction.text)
            val end = builder.length

            if (correction.type == "INSERTION") {
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#006400")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(BackgroundColorSpan(Color.parseColor("#e6ffe6")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            } else if (correction.type == "DELETION") {
                builder.setSpan(ForegroundColorSpan(Color.parseColor("#8B0000")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(BackgroundColorSpan(Color.parseColor("#ffe6e6")), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                builder.setSpan(StrikethroughSpan(), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return builder
    }

    private fun showProgress(show: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            proofreadButton.isEnabled = !show
            proofreadStreamingButton.isEnabled = !show
            clearButton.isEnabled = !show
        }
    }

    private fun clearText() {
        inputEditText.setText("")
        resultTextView.text = ""
        diffTextView.text = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        proofreader?.close()
        proofreader = null
        backgroundExecutor.shutdownNow()
    }

    companion object {
        private const val MODEL_PATH = "proofread_quant_200m.litertlm"
        private const val KEY_RESULT_TEXT = "result_text"
    }
}