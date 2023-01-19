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
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.audioembedder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), AudioEmbedderHelper.EmbedderListener {
    private lateinit var binding: ActivityMainBinding
    private var selectAudioPos = 1
    private lateinit var audioEmbedderHelper: AudioEmbedderHelper
    private var uriOne: Uri? = null
    private var uriTwo: Uri? = null

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            when (selectAudioPos) {
                1 -> {
                    uriOne = it
                    binding.tvDescriptionOne.text = it?.getName(this)
                        ?: getString(R.string.tv_pick_audio_description)
                    checkIsReadyForCompare()
                }
                2 -> {
                    uriTwo = it
                    binding.tvDescriptionTwo.text = it?.getName(this)
                        ?: getString(R.string.tv_pick_audio_description)
                    checkIsReadyForCompare()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        audioEmbedderHelper = AudioEmbedderHelper(this, listener = this)

        binding.btnCompare.setOnClickListener {
            // Compare two audios here
            uriOne?.let { audio1 ->
                uriTwo?.let { audio2 ->
                    audioEmbedderHelper.compare(
                        audio1.createAudioData(this),
                        audio2.createAudioData(this)
                    )?.let { resultBundle ->
                        updateResult(resultBundle)
                    }
                }
            }
        }

        binding.btnPickAudioOne.setOnClickListener {
            selectAudioPos = 1
            getContent.launch("audio/*")
        }

        binding.btnPickAudioTwo.setOnClickListener {
            selectAudioPos = 2
            getContent.launch("audio/*")
        }

        initBottomSheetControls()
        checkIsReadyForCompare()
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

    private fun updateResult(resultBundle: AudioEmbedderHelper.ResultBundle) {
        binding.tvTitle.visibility = View.INVISIBLE
        binding.tvSimilarity.visibility = View.VISIBLE
        binding.tvSimilarity.text = String.format(
            "Similarity: %.2f", resultBundle.similarity
        )
        binding.bottomSheetLayout.inferenceTimeVal.text =
            String.format("%d ms", resultBundle.inferenceTime)
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
}
