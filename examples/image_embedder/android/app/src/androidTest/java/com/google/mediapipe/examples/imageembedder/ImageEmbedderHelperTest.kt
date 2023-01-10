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

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.Before
import java.io.InputStream

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ImageEmbedderHelperTest {
    companion object {
        private const val IMAGE_AMERICAN_PIZZA = "american_pizza.jpg"
        private const val IMAGE_ITALY_PIZZA = "italy_pizza.png"
        private const val EXPECTED_SIMILARITY = 0.4
    }

    private lateinit var embedderHelper: ImageEmbedderHelper
    private lateinit var imageTestOne: Bitmap
    private lateinit var imageTestTwo: Bitmap

    @Before
    fun setup() {
        embedderHelper =
            ImageEmbedderHelper(ApplicationProvider.getApplicationContext())
        imageTestOne = IMAGE_AMERICAN_PIZZA.toBitmap()
        imageTestTwo = IMAGE_ITALY_PIZZA.toBitmap()
    }

    @Test
    fun embeddingResultsWithinAcceptedRange() {
        val bundleResult = embedderHelper.embed(imageTestOne, imageTestTwo)
        assertEquals(EXPECTED_SIMILARITY, bundleResult?.similarity ?: 0.0, 0.09)
    }

    private fun String.toBitmap(): Bitmap {
        val assetManager: AssetManager =
            InstrumentationRegistry.getInstrumentation().context.assets
        val inputStream: InputStream = assetManager.open(this)
        return BitmapFactory.decodeStream(inputStream)
    }
}
