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
package com.google.mediapipe.examples.gesturerecognizer

import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.tasks.components.containers.Category
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream


/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

// TODO: Add tests
@RunWith(AndroidJUnit4::class)
class GestureRecognizerTest {

    private companion object {
        private const val MP_RECOGNITION_TASK = "gesture_recognizer.task"
        private const val testImage = "hand_victory.jpg"
    }

    private val expectedCategories = listOf(
        Category.create(0.9f, 0, "Victory", "Victory"),
    )

    @Test
    @Throws(Exception::class)
    fun recognitionResultsShouldNotChange() {
        // This test will need to be updated
    }

    @Test
    @Throws(Exception::class)
    fun recognize_successWithValidModels() {
//        val options = GestureRecognizerOptions.builder()
//            .setBaseOptions(
//                BaseOptions.builder()
//                    .setModelAssetPath(MP_RECOGNITION_TASK)
//                    .build()
//            ).build()
//        val gestureRecognizer = GestureRecognizer.createFromOptions(
//            ApplicationProvider.getApplicationContext(),
//            options
//        )
//        val actualResult = gestureRecognizer.recognize(
//            BitmapImageBuilder(loadImage(testImage)!!).build()
//        )
//
//        assertEquals(
//            actualResult.gestures().first().first().categoryName(),
//            expectedCategories.first().categoryName()
//        )
    }

    @Throws(Exception::class)
    private fun loadImage(fileName: String): Bitmap? {
        val assetManager: AssetManager =
            (ApplicationProvider.getApplicationContext() as Context).assets
        val inputStream: InputStream = assetManager.open(fileName)
        return BitmapFactory.decodeStream(inputStream)
    }
}
