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

package com.google.mediapipe.examples.imageembedder

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.imageembedder.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity(), ImageEmbedderHelper.EmbedderListener {
    private lateinit var binding: ActivityMainBinding
    private var selectImagePos = 1
    private lateinit var imageEmbedderHelper: ImageEmbedderHelper
    private var imageOne: Bitmap? = null
    private var imageTwo: Bitmap? = null

    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            // get two first images, and skip all image behind.
            when (selectImagePos) {
                1 -> {
                    imageOne = it?.getImage(this)
                    binding.imgOne.setImageBitmap(imageOne)
                    binding.tvImageOneDescription.visibility =
                        if (it == null) View.VISIBLE else View.GONE
                    checkIsReadyForCompare()
                }
                2 -> {
                    imageTwo = it?.getImage(this)
                    binding.imgTwo.setImageBitmap(imageTwo)
                    binding.tvImageTwoDescription.visibility =
                        if (it == null) View.VISIBLE else View.GONE
                    checkIsReadyForCompare()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        imageEmbedderHelper = ImageEmbedderHelper(this, listener = this)

        binding.btnCompare.setOnClickListener {
            // Compare two images here
            imageOne?.let { img1 ->
                imageTwo?.let { img2 ->
                    imageEmbedderHelper.embed(img1, img2)?.let { resultBundle ->
                        updateResult(resultBundle)
                    }
                }
            }
        }

        binding.imgOne.setOnClickListener {
            selectImagePos = 1
            getContent.launch("image/*")
        }

        binding.imgTwo.setOnClickListener {
            selectImagePos = 2
            getContent.launch("image/*")
        }

        initBottomSheetControls()
        checkIsReadyForCompare()
    }

    private fun initBottomSheetControls() {
        binding.bottomSheetLayout.spinnerDelegate.setSelection(
            imageEmbedderHelper.currentDelegate, false
        )
        binding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    imageEmbedderHelper.currentDelegate = position
                    resetEmbedder()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for image
        // embedding
        binding.bottomSheetLayout.spinnerModel.setSelection(
            imageEmbedderHelper.currentModel, false
        )
        binding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    imageEmbedderHelper.currentModel = position
                    resetEmbedder()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    private fun updateResult(resultBundle: ImageEmbedderHelper.ResultBundle) {
        binding.tvTitle.visibility = View.INVISIBLE
        binding.tvSimilarity.visibility = View.VISIBLE
        binding.tvSimilarity.text = String.format(
            "Similarity: %.2f",
            resultBundle.similarity
        )
        binding.bottomSheetLayout.inferenceTimeVal.text =
            String.format("%d ms", resultBundle.inferenceTime)
    }

    // Reset Embedder.
    private fun resetEmbedder() {
        imageEmbedderHelper.clearImageEmbedder()
        imageEmbedderHelper.setupImageEmbedder()
    }

    private fun checkIsReadyForCompare() {
        binding.btnCompare.isEnabled = (imageOne != null && imageTwo != null)
        if (imageOne == null || imageTwo == null) {
            binding.tvTitle.visibility = View.VISIBLE
            binding.tvSimilarity.visibility = View.GONE
        }
    }

    private fun Uri.getImage(context: Context): Bitmap {
        return BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(this)
        )
    }

    override fun onError(error: String, errorCode: Int) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()

        if (errorCode == ImageEmbedderHelper.GPU_ERROR) {
            binding.bottomSheetLayout.spinnerDelegate.setSelection(
                ImageEmbedderHelper.DELEGATE_CPU,
                false
            )
        }
    }
}
