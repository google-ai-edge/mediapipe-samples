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
package com.google.mediapipe.examples.facestylizer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.mediapipe.examples.facestylizer.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.ByteBufferExtractor
import kotlin.jvm.optionals.getOrNull


class MainActivity : AppCompatActivity(),
    FaceStylizationHelper.FaceStylizerListener {

    private lateinit var binding: ActivityMainBinding
    private var faceStylizationHelper: FaceStylizationHelper? = null
    private var inputImage: Bitmap? = null


    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            inputImage = it?.getImage(this)
            binding.inputImage.setImageBitmap(inputImage)
            binding.tvImageOneDescription.visibility =
                if (it == null) View.VISIBLE else View.GONE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Init spinner model name
        val modelNameArray = resources.getStringArray(R.array.model_name_array)
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            modelNameArray
        )
        binding.bottomSheetLayout.modelSpinner.adapter = adapter

        binding.bottomSheetLayout.modelSpinner.onItemSelectedListener =
            object : OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    // Reset the helper if the model type is changed.
                    faceStylizationHelper?.close()
                    initHelper(position)
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    // do nothing
                }

            }

        binding.btnStylize.setOnClickListener {
            inputImage?.let { input ->
                faceStylizationHelper?.stylize(input)?.let {
                    onResult(it)
                }
            }
        }

        binding.inputImage.setOnClickListener {
            getContent.launch("image/*")
        }
    }

    private fun initHelper(modelPosition: Int) {
        faceStylizationHelper = FaceStylizationHelper(
            modelPosition,
            this,
            faceStylizerListener = this
        )
    }

    private fun Uri.getImage(context: Context): Bitmap {
        return BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(this)
        )
    }

    override fun onError(error: String, errorCode: Int) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun onResult(result: FaceStylizationHelper.ResultBundle) {
        if (result.stylizedFace == null || result.stylizedFace.stylizedImage().getOrNull() == null) {
            onError("Failed to stylize image")
            return
        }
        val image = result.stylizedFace
        val byteBuffer =
            ByteBufferExtractor.extract(image.stylizedImage().get())

        val width = image.stylizedImage().get().width
        val height = image.stylizedImage().get().height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(byteBuffer)

        binding.tvImageTwoDescription.visibility = View.GONE
        binding.outputImage.setImageBitmap(bitmap)
        binding.bottomSheetLayout.inferenceTimeVal.text =
            result.inferenceTime.toString()
    }
}
