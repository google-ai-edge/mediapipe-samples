/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
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
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.audioembedder.databinding.ActivityMainBinding
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), AudioEmbedderHelper.EmbedderListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var audioEmbedderHelper: AudioEmbedderHelper
    private lateinit var backgroundExecutor: ExecutorService
    private var selectAudioPos = 1
    private var uriOne: Uri? = null
    private var uriTwo: Uri? = null
    private var mediaPlayer: MediaPlayer? = null

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            when (selectAudioPos) {
                1 -> {
                    uriOne = it
                    binding.tvDescriptionOne.text = it?.getName(this)
                        ?: getString(R.string.tv_pick_audio_description)
                    checkIsReadyForCompare()
                    checkIsReadyForPlayAudio()
                }
                2 -> {
                    uriTwo = it
                    binding.tvDescriptionTwo.text = it?.getName(this)
                        ?: getString(R.string.tv_pick_audio_description)
                    checkIsReadyForCompare()
                    checkIsReadyForPlayAudio()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute {
            audioEmbedderHelper = AudioEmbedderHelper(this, listener = this)
            runOnUiThread {
                initBottomSheetControls()
                checkIsReadyForCompare()
                checkIsReadyForPlayAudio()

                with(binding) {
                    btnCompare.setOnClickListener {
                        compareTwoAudioFiles()
                    }
                    btnPickAudioOne.setOnClickListener {
                        selectAudioPos = 1
                        getContent.launch("audio/*")
                    }
                    btnPickAudioTwo.setOnClickListener {
                        selectAudioPos = 2
                        getContent.launch("audio/*")
                    }
                    btnPlayAudioOne.setOnClickListener {
                        uriOne?.let { it1 -> playAudio(it1) }
                    }
                    btnPlayAudioTwo.setOnClickListener {
                        uriTwo?.let { it1 -> playAudio(it1) }
                    }
                }
            }
        }
    }

    private fun initBottomSheetControls() {
        binding.bottomSheetLayout.spinnerDelegate.setSelection(
            audioEmbedderHelper.currentDelegate, false
        )
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    audioEmbedderHelper.currentDelegate = position
                    resetEmbedder()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Compare two audio files here
    private fun compareTwoAudioFiles() {
        uriOne?.let { audio1 ->
            uriTwo?.let { audio2 ->
                // show progress bar
                binding.flProgress.visibility = View.VISIBLE
                backgroundExecutor.execute {
                    audioEmbedderHelper.compare(
                        audio1.createAudioData(this@MainActivity),
                        audio2.createAudioData(this@MainActivity)
                    )?.let { resultBundle ->
                        updateResult(resultBundle)
                    } ?: runOnUiThread {
                        binding.flProgress.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun updateResult(resultBundle: AudioEmbedderHelper.ResultBundle) {
        runOnUiThread {
            binding.flProgress.visibility = View.GONE
            binding.tvTitle.visibility = View.INVISIBLE
            binding.tvSimilarity.visibility = View.VISIBLE
            binding.tvSimilarity.text = String.format(
                "Similarity: %.2f", resultBundle.similarity
            )
            binding.bottomSheetLayout.inferenceTimeVal.text =
                String.format("%d ms", resultBundle.inferenceTime)
        }
    }

    // Reset Embedder.
    private fun resetEmbedder() {
        audioEmbedderHelper.clearAudioEmbedder()
        audioEmbedderHelper.setupAudioEmbedder()
    }

    private fun checkIsReadyForCompare() {
        binding.btnCompare.isEnabled = (uriOne != null && uriTwo != null)
        if (uriOne == null || uriTwo == null) {
            binding.tvTitle.visibility = View.VISIBLE
            binding.tvSimilarity.visibility = View.GONE
        }
    }

    private fun playAudio(uri: Uri) {
        mediaPlayer?.stop()

        mediaPlayer = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA).build()
            )
            setDataSource(this@MainActivity, uri)
            prepare()
        }

        mediaPlayer?.start()
    }

    private fun checkIsReadyForPlayAudio() {
        binding.btnPlayAudioOne.isEnabled = uriOne != null
        binding.btnPlayAudioTwo.isEnabled = uriTwo != null
    }

    override fun onError(error: String, errorCode: Int) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()

        if (errorCode == AudioEmbedderHelper.GPU_ERROR) {
            binding.bottomSheetLayout.spinnerDelegate.setSelection(
                AudioEmbedderHelper.DELEGATE_CPU, false
            )
        }
    }

    private fun Uri.getName(context: Context): String? {
        val returnCursor =
            context.contentResolver.query(this, null, null, null, null)
        val nameIndex =
            returnCursor?.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        returnCursor?.moveToFirst()
        val fileName = nameIndex?.let { returnCursor.getString(it) }
        returnCursor?.close()
        return fileName
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        mediaPlayer?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }
}
