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
package com.google.mediapipe.examples.imagesegmenter

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ImageSegmentationTest {
    companion object {
        private const val EXPECTED_MASK_TOLERANCE = 1e-2
        private const val INPUT_IMAGE = "input_image.jpeg"
        private const val EXPECTED_IMAGE = "expected_image.png"
        private val labelColors = listOf(
            -16777216,
            -8388608,
            -16744448,
            -8355840,
            -16777088,
            -8388480,
            -16744320,
            -8355712,
            -12582912,
            -4194304,
            -12550144,
            -4161536,
            -12582784,
            -4194176,
            -12550016,
            -4161408,
            -16760832,
            -8372224,
            -16728064,
            -8339456,
            -16760704
        )
    }

    private lateinit var imageSegmenterHelper: ImageSegmenterHelper

    @Test
    fun segmentationResultShouldNotBeChanged() {
        imageSegmenterHelper =
            ImageSegmenterHelper(context = ApplicationProvider.getApplicationContext(),
                imageSegmenterListener = object :
                    ImageSegmenterHelper.SegmenterListener {
                    override fun onError(error: String, errorCode: Int) {
                        // no-op
                    }

                    override fun onResults(resultBundle: ImageSegmenterHelper.ResultBundle) {
                        // no-op
                    }
                })

        val testBitmap = loadImage(INPUT_IMAGE)
        val mpImage = BitmapImageBuilder(testBitmap).build()

        // Run the image segmentation with the test image.
        val imageSegmentationResult =
            imageSegmenterHelper.segmentImageFile(mpImage)


        // Verify that the segmentation result is not null.
        assertNotNull(imageSegmentationResult)

        // Create the mask bitmap with colors
        val byteBuffer = ByteBufferExtractor.extract(
            imageSegmentationResult!!.categoryMask().get()
        )
        val outputMask = IntArray(byteBuffer.capacity())
        for (i in outputMask.indices) {
            val index = byteBuffer.get(i).toInt()
            val color: Int =
                if (index in 1..20) labelColors[index].toAlphaColor() else Color.TRANSPARENT
            outputMask[i] = color
        }

        val maskBitmap = Bitmap.createBitmap(
            outputMask,
            mpImage.width,
            mpImage.height,
            Bitmap.Config.ARGB_8888
        )

        // Verify output mask bitmap.
        val expectedPixels = getPixels(loadImage(EXPECTED_IMAGE)!!)
        val actualPixels = getPixels(maskBitmap)

        assertThat(actualPixels.size).isEqualTo(expectedPixels.size)

        var inconsistentPixels = 0
        for (i in actualPixels.indices) {
            if (actualPixels[i] != expectedPixels[i]) {
                inconsistentPixels++
            }
        }

        assertThat(inconsistentPixels.toDouble() / actualPixels.size).isLessThan(
            EXPECTED_MASK_TOLERANCE
        )
    }

    @Throws(Exception::class)
    private fun loadImage(fileName: String): Bitmap? {
        val assetManager: AssetManager =
            InstrumentationRegistry.getInstrumentation().context.assets
        val inputStream: InputStream = assetManager.open(fileName)
        return BitmapFactory.decodeStream(inputStream)
    }

    private fun getPixels(bitmap: Bitmap): IntArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        return pixels
    }
}
