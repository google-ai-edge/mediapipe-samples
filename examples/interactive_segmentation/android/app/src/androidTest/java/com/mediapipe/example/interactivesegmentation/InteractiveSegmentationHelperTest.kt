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

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class InteractiveSegmentationHelperTest {
    private companion object {
        private const val TEST_IMAGE = "cat.jpg"
        private const val EXPECTED_IMAGE = "expected_result.png"
        private const val EXPECTED_MASK_TOLERANCE = 1e-2
    }

    private lateinit var segmentationHelper: InteractiveSegmentationHelper
    private lateinit var inputImage: Bitmap
    private lateinit var expectedImage: Bitmap

    @Before
    fun setup() {
        inputImage = loadImage(TEST_IMAGE)!!
        expectedImage = loadImage(EXPECTED_IMAGE)!!
    }

    @Test
    fun segmentationResultShouldNotBeChanged() {
        val countDownLatch = CountDownLatch(1)

        var byteBuffer: ByteBuffer? = null
        segmentationHelper =
            InteractiveSegmentationHelper(
                ApplicationProvider.getApplicationContext(),
                object :
                    InteractiveSegmentationHelper.InteractiveSegmentationListener {
                    override fun onError(error: String) {
                        // no-op
                        countDownLatch.countDown()
                    }

                    override fun onResults(result: InteractiveSegmentationHelper.ResultBundle?) {
                        // assign the output byteBuffer
                        byteBuffer = result?.byteBuffer
                        countDownLatch.countDown()
                    }
                }
            )

        // select the cat in image
        val normX = 0.3f
        val normY = 0.4f
        segmentationHelper.setInputImage(inputImage)
        segmentationHelper.segment(normX, normY)

        // waiting segmentation
        countDownLatch.await()

        assertNotNull(byteBuffer)

        // converts output bytebuffer to actual int array
        val actualPixels = IntArray(byteBuffer!!.capacity())
        for (i in actualPixels.indices) {
            val index = byteBuffer!!.get(i).toInt()
            val color = if (index == 0) Color.TRANSPARENT else Color.BLACK
            actualPixels[i] = color
        }

        // convert bitmap to expected int array
        val expectedPixels = IntArray(byteBuffer!!.capacity())
        expectedImage.getPixels(
            expectedPixels,
            0,
            expectedImage.width,
            0,
            0,
            expectedImage.width,
            expectedImage.height
        )

        // compare the different between actual int array and expected int array
        var inconsistentPixels = 0
        actualPixels.forEachIndexed { index, i ->
            if (i != expectedPixels[index]) {
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
}
