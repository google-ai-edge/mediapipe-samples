/*
 * Copyright 2026 The MediaPipe Authors.
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
package com.mediapipe.example.interactivesegmentation

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.mediapipe.example.interactivesegmentation.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), InteractiveSegmentationHelper.InteractiveSegmentationListener {

    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var interactiveSegmentationHelper: InteractiveSegmentationHelper
    private var isAllFabsVisible = false
    private var pictureUri: Uri? = null

    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess && pictureUri != null) {
                val bitmap = pictureUri!!.toBitmap()
                activityMainBinding.imgSegmentation.setImageBitmap(bitmap)
                interactiveSegmentationHelper.setInputImage(bitmap)
                activityMainBinding.overlapView.clearAll()
                activityMainBinding.overlapView.isInteractionEnabled = true
            }

            if (isAllFabsVisible) {
                fabsStateChange(false)
                isAllFabsVisible = false
            }
            activityMainBinding.tvDescription.visibility =
                if (isSuccess) View.GONE else View.VISIBLE
        }

    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.toBitmap()?.let { bitmap ->
                activityMainBinding.imgSegmentation.setImageBitmap(bitmap)
                interactiveSegmentationHelper.setInputImage(bitmap)
                activityMainBinding.overlapView.clearAll()
                activityMainBinding.overlapView.isInteractionEnabled = true
            }

            if (isAllFabsVisible) {
                fabsStateChange(false)
                isAllFabsVisible = false
            }
            activityMainBinding.tvDescription.visibility =
                if (uri != null) View.GONE else View.VISIBLE
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        interactiveSegmentationHelper = InteractiveSegmentationHelper(
            this,
            this
        )

        fabsStateChange(false)
        initListener()
    }

    private fun clearOverlapResult() {
        activityMainBinding.overlapView.clearAll()
        activityMainBinding.imgSegmentation.setImageBitmap(null)
        activityMainBinding.overlapView.isInteractionEnabled = false
    }

    private fun initListener() {
        activityMainBinding.addFab.setOnClickListener {
            isAllFabsVisible = if (!isAllFabsVisible) {
                fabsStateChange(true)
                true
            } else {
                fabsStateChange(false)
                false
            }
        }

        activityMainBinding.takePicture.setOnClickListener {
            clearOverlapResult()
            pictureUri = getImageUri()
            pictureUri?.let {
                takePictureLauncher.launch(it)
            }
        }

        activityMainBinding.pickPicture.setOnClickListener {
            clearOverlapResult()
            pickImageLauncher.launch("image/*")
        }

        activityMainBinding.toggleBrushGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnPositive -> activityMainBinding.overlapView.currentBrushMode = AppBrushMode.POSITIVE
                    R.id.btnNegative -> activityMainBinding.overlapView.currentBrushMode = AppBrushMode.NEGATIVE
                    R.id.btnLasso -> activityMainBinding.overlapView.currentBrushMode = AppBrushMode.LASSO
                }
            }
        }

        activityMainBinding.btnClearStrokes.setOnClickListener {
            activityMainBinding.overlapView.clearAll()
        }

        activityMainBinding.overlapView.onStrokesUpdatedListener = { strokes ->
            interactiveSegmentationHelper.segment(strokes)
        }
    }

    private fun fabsStateChange(isStateShow: Boolean) {
        if (isStateShow) {
            with(activityMainBinding) {
                takePicture.show()
                pickPicture.show()
                tvPickImageDescription.visibility = View.VISIBLE
                tvTakePictureDescription.visibility = View.VISIBLE
                addFab.extend()
            }
        } else {
            with(activityMainBinding) {
                takePicture.hide()
                pickPicture.hide()
                tvPickImageDescription.visibility = View.GONE
                tvTakePictureDescription.visibility = View.GONE
                addFab.shrink()
            }
        }
    }

    private fun getImageUri(): Uri {
        val filePicture = File(
            cacheDir.path + File.separator + "JPEG_" + SimpleDateFormat(
                "yyyyMMdd_HHmmss",
                Locale.getDefault()
            ).format(Date()) + ".jpg"
        )

        return FileProvider.getUriForFile(
            this,
            applicationContext.packageName + ".fileprovider",
            filePicture
        )
    }

    private fun showError(errorMessage: String) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }

    private fun Uri.toBitmap(): Bitmap {
        val maxWidth = 512f
        var bitmap = if (Build.VERSION.SDK_INT < 28) {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(contentResolver, this)
        } else {
            val source = ImageDecoder.createSource(contentResolver, this)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        }
        if (bitmap.width > maxWidth) {
            val scaleFactor = maxWidth / bitmap.width
            bitmap = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scaleFactor).toInt(),
                (bitmap.height * scaleFactor).toInt(),
                false
            )
        }
        return if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, true)
        }
    }

    override fun onError(error: String) {
        showError(error)
    }

    override fun onResults(result: InteractiveSegmentationHelper.ResultBundle?) {
        result?.let {
            activityMainBinding.overlapView.setMaskResult(it.maskBitmap)
        } ?: kotlin.run {
            activityMainBinding.overlapView.setMaskResult(null)
        }
    }
}
