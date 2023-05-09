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

package com.google.mediapipe.examples.textembedder

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.textembedder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), TextEmbedderHelper.EmbedderListener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var textEmbedderHelper: TextEmbedderHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        textEmbedderHelper = TextEmbedderHelper(this, listener = this)

        binding.btnCompare.setOnClickListener {
            // Compare two texts here
            val firstText =
                if (binding.imgOne.text.isNullOrEmpty()) getString(R.string.default_hint_first_text) else binding.imgOne.text.toString()
            val secondText =
                if (binding.imgTwo.text.isNullOrEmpty()) getString(R.string.default_hint_second_text) else binding.imgTwo.text.toString()

            textEmbedderHelper.compare(firstText, secondText)
                ?.let { resultBundle ->
                    updateResult(resultBundle)
                }
        }
        initBottomSheetControls()
    }


    private fun initBottomSheetControls() {
        binding.bottomSheetLayout.spinnerDelegate.setSelection(
            textEmbedderHelper.currentDelegate, false
        )
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    textEmbedderHelper.currentDelegate = position
                    resetEmbedder()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for text embedding
        binding.bottomSheetLayout.spinnerModel.setSelection(
            textEmbedderHelper.currentModel, false
        )
        binding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    textEmbedderHelper.currentModel = position
                    resetEmbedder()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    private fun updateResult(resultBundle: TextEmbedderHelper.ResultBundle) {
        binding.tvTitle.visibility = View.INVISIBLE
        binding.tvSimilarity.visibility = View.VISIBLE
        binding.tvSimilarity.text =
            String.format("Similarity: %.2f", resultBundle.similarity)
        binding.bottomSheetLayout.inferenceTimeVal.text =
            String.format("%d ms", resultBundle.inferenceTime)
    }

    // Reset Embedder.
    private fun resetEmbedder() {
        textEmbedderHelper.clearTextEmbedder()
        textEmbedderHelper.setupTextEmbedder()
    }

    override fun onError(error: String, errorCode: Int) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()

        if (errorCode == TextEmbedderHelper.GPU_ERROR) {
            binding.bottomSheetLayout.spinnerDelegate.setSelection(
                TextEmbedderHelper.DELEGATE_CPU,
                false
            )
        }
    }
}
