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

package com.google.mediapipe.examples.textsummarizer

import android.app.Activity
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizer
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizer.TextSummarizerOptions
import com.google.mediapipe.tasks.text.textsummarizer.TextSummarizerStreamingResult
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** A simple demo app for MediaPipe Text Summarizer. */
class MainActivity : Activity() {

    private lateinit var resultTextView: TextView
    private lateinit var inputEditText: EditText
    private lateinit var summarizeButton: Button
    private lateinit var summarizeStreamingButton: Button
    private lateinit var clearButton: Button
    private lateinit var modeSwitch: Switch
    private lateinit var progressBar: ProgressBar

    @Volatile
    private var summarizer: TextSummarizer? = null
    private val backgroundExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_summarizer)

        inputEditText = findViewById(R.id.inputEditText)
        summarizeButton = findViewById(R.id.summarizeButton)
        summarizeStreamingButton = findViewById(R.id.summarizeStreamingButton)
        clearButton = findViewById(R.id.clearButton)
        modeSwitch = findViewById(R.id.modeSwitch)
        resultTextView = findViewById(R.id.resultTextView)
        progressBar = findViewById(R.id.progressBar)

        summarizeButton.setOnClickListener { summarizeText(streaming = false) }
        summarizeStreamingButton.setOnClickListener { summarizeText(streaming = true) }
        clearButton.setOnClickListener { clearText() }
        modeSwitch.setOnCheckedChangeListener { _, _ ->
            showProgress(true)
            backgroundExecutor.execute { initializeSummarizer() }
        }

        if (savedInstanceState != null) {
            resultTextView.text = savedInstanceState.getString(KEY_RESULT_TEXT)
        }

        backgroundExecutor.execute { initializeSummarizer() }
        showProgress(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_RESULT_TEXT, resultTextView.text.toString())
    }

    private fun initializeSummarizer() {
        try {
            summarizer?.close()
            summarizer = null

            val modelPath = copyAssetToCache(MODEL_PATH)
            val cacheFile = File(modelPath)
            var loadedFromFd = false

            val options = TextSummarizerOptions.builder()
                .setMode(
                    if (modeSwitch.isChecked)
                        TextSummarizerOptions.Mode.TLDR
                    else
                        TextSummarizerOptions.Mode.KEYPOINTS
                )

            summarizer = try {
                ParcelFileDescriptor.open(cacheFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    options.setModelAssetFileDescriptor(pfd)
                    loadedFromFd = true
                    TextSummarizer.createFromOptions(this, options.build())
                }
            } catch (e: IOException) {
                options.setModelPath(modelPath)
                TextSummarizer.createFromOptions(this, options.build())
            }

            val initMsg = if (loadedFromFd) "Initialized from FD" else "Initialized from path"
            runOnUiThread {
                Toast.makeText(this, initMsg, Toast.LENGTH_SHORT).show()
                showProgress(false)
            }
        } catch (t: Throwable) {
            summarizer = null
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

    private fun summarizeText(streaming: Boolean) {
        val text = inputEditText.text.toString()
        if (text.isEmpty()) {
            Toast.makeText(this, "Text is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val currentSummarizer = summarizer ?: run {
            Toast.makeText(this, "Summarizer not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        resultTextView.text = ""
        showProgress(true)

        if (streaming) {
            currentSummarizer.summarizeStreaming(
                text,
                object : TextSummarizer.SummarizationResultCallback {
                    override fun onNext(result: TextSummarizerStreamingResult) {
                        val chunk = try { result.getChunk() } catch (e: Throwable) { "" }
                        runOnUiThread {
                            try {
                                resultTextView.append(chunk)
                            } catch (t: Throwable) {
                                resultTextView.text = "Error: ${t.message}"
                                showProgress(false)
                            }
                        }
                    }

                    override fun onError(throwable: Throwable) {
                        val errorMsg = throwable.message ?: "Unknown"
                        runOnUiThread {
                            resultTextView.text = "Error: $errorMsg. Reinitializing..."
                            showProgress(true)
                        }
                        backgroundExecutor.execute { initializeSummarizer() }
                    }

                    override fun onDone() {
                        runOnUiThread { showProgress(false) }
                    }
                }
            )
        } else {
            backgroundExecutor.execute {
                try {
                    val result = currentSummarizer.summarize(text).getSummary()
                    runOnUiThread {
                        resultTextView.text = result
                        showProgress(false)
                    }
                } catch (t: Throwable) {
                    runOnUiThread {
                        resultTextView.text = "Error: ${t.message}. Reinitializing..."
                        showProgress(true)
                    }
                    initializeSummarizer()
                }
            }
        }
    }

    private fun showProgress(show: Boolean) {
        runOnUiThread {
            progressBar.visibility = if (show) View.VISIBLE else View.GONE
            summarizeButton.isEnabled = !show
            summarizeStreamingButton.isEnabled = !show
            clearButton.isEnabled = !show
            modeSwitch.isEnabled = !show
        }
    }

    private fun clearText() {
        inputEditText.setText("")
        resultTextView.text = ""
    }

    override fun onDestroy() {
        super.onDestroy()
        summarizer?.close()
        summarizer = null
        backgroundExecutor.shutdownNow()
    }

    companion object {
        private const val MODEL_PATH = "summarization_quant_200m_2modes.litertlm"
        private const val KEY_RESULT_TEXT = "result_text"
    }
}
