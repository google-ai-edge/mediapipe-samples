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
package com.mediapipe.example.interactivesegmentation

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.mediapipe.example.interactivesegmentation.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date

class MainActivity : AppCompatActivity(), InteractiveSegmentationHelper.InteractiveSegmentationListener {

    private lateinit var activityMainBinding: ActivityMainBinding
    private lateinit var interactiveSegmentationHelper: InteractiveSegmentationHelper
    private var isAllFabsVisible = false
    private var pictureUri: Uri? = null

    // Launch camera to receive new image for segmentation
    // Set image in View, start segmentation helper
    // Update UI
    private val takePictureLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess && pictureUri != null) {
                val bitmap = pictureUri!!.toBitmap()
                activityMainBinding.imgSegmentation.setImageBitmap(bitmap)
                interactiveSegmentationHelper.setInputImage(bitmap)
            }

            if (isAllFabsVisible) {
                fabsStateChange(false)
                isAllFabsVisible = false
            }
            activityMainBinding.tvDescription.visibility =
                if (isSuccess) View.GONE else View.VISIBLE
        }

    // Open user gallery to select a photo for segmentation
    // Set image in View, start segmentation helper
    // Update UI
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            it?.toBitmap()?.let { bitmap ->
                activityMainBinding.imgSegmentation.setImageBitmap(bitmap)
                interactiveSegmentationHelper.setInputImage(
                    bitmap
                )
            }

            if (isAllFabsVisible) {
                fabsStateChange(false)
                isAllFabsVisible = false
            }
            activityMainBinding.tvDescription.visibility =
                if (it != null) View.GONE else View.VISIBLE
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
        initTouch()
    }

    private fun clearOverlapResult() {
        activityMainBinding.overlapView.clearAll()
        activityMainBinding.imgSegmentation.setImageBitmap(null)
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
    }

    /**
     * Takes the position where the user touches on image (x and y)
     * and draws a marker above it to highlight where item of significance is found
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun initTouch() {
        val viewCoords = IntArray(2)
        activityMainBinding.imgSegmentation.getLocationOnScreen(viewCoords)
        activityMainBinding.imgSegmentation.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (interactiveSegmentationHelper.isInputImageAssigned()) {
                        val touchX = event.x.toInt()
                        val touchY = event.y.toInt()

                        val imageX =
                            touchX - viewCoords[0] // viewCoords[0] is the X coordinate
                        val imageY =
                            touchY - viewCoords[1] // viewCoords[1] is the y coordinate

                        activityMainBinding.overlapView.setSelectPosition(
                            imageX.toFloat(),
                            imageY.toFloat()
                        )

                        val normX =
                            imageX.toFloat() / activityMainBinding.imgSegmentation.width
                        val normY =
                            imageY.toFloat() / activityMainBinding.imgSegmentation.height

                        interactiveSegmentationHelper.segment(normX, normY)
                    }
                }
                else -> {
                    // no-op
                }
            }
            true
        }
    }

    /**
     * Controls the state of the FAB buttons to show or hide.
     */
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

    /**
     * Create file ready for taking picture.
     */
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

    /**
     * Converts Uri to Bitmap.
     * If a Bitmap is not of the ARGB_8888 type, it needs to be converted to
     * that type because the interactive segmentation helper requires that
     * specific Bitmap type.
     */
    private fun Uri.toBitmap(): Bitmap {
        val maxWidth = 512f
        var bitmap = if (Build.VERSION.SDK_INT < 28) {
            MediaStore.Images.Media.getBitmap(contentResolver, this)
        } else {
            val source = ImageDecoder.createSource(contentResolver, this)
            ImageDecoder.decodeBitmap(source)
        }
        // reduce the size of image if it larger than maxWidth
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
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    override fun onError(error: String) {
        showError(error)
    }

    override fun onResults(result: InteractiveSegmentationHelper.ResultBundle?) {
        // Inform the overlap view to draw over the area of significance returned
        // from the helper
        result?.let {
            activityMainBinding.overlapView.setMaskResult(
                it.byteBuffer,
                it.maskWidth,
                it.maskHeight
            )
        } ?: kotlin.run {
            activityMainBinding.overlapView.clearAll()
        }
    }
}
