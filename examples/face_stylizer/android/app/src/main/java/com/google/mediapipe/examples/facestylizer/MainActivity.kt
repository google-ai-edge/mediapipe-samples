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
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.mediapipe.examples.facestylizer.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.ByteBufferExtractor

class MainActivity : AppCompatActivity(), FaceStylizationHelper.FaceStylizerListener {

    private lateinit var binding: ActivityMainBinding
    private lateinit var faceStylizationHelper: FaceStylizationHelper
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

        faceStylizationHelper = FaceStylizationHelper(this, faceStylizerListener = this)

        binding.btnStylize.setOnClickListener {
            inputImage?.let { input ->
                onResult(faceStylizationHelper.stylize(input))
            }
        }

        binding.inputImage.setOnClickListener {
            getContent.launch("image/*")
        }
    }

    private fun Uri.getImage(context: Context): Bitmap {
        return BitmapFactory.decodeStream(
            context.contentResolver.openInputStream(this)
        )
    }

    override fun onError(error: String, errorCode: Int) {
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
    }

    fun onResult(result: FaceStylizationHelper.ResultBundle) {
        if( result.stylizedFace == null ) {
            onError("Failed to stylize image")
            return
        }
        val image = result.stylizedFace
        val byteBuffer =
            ByteBufferExtractor.extract(image.stylizedImage())

        val width = image.stylizedImage().width
        val height = image.stylizedImage().height

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(byteBuffer)

        binding.tvImageTwoDescription.visibility = View.GONE
        binding.outputImage.setImageBitmap(bitmap)
        binding.bottomSheetLayout.inferenceTimeVal.text = result.inferenceTime.toString()
    }
}