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

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.assertEquals
import org.junit.Before

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class TextEmbedderHelperTest {
    companion object {
        private const val TEXT_TEST_ONE =
            "Da Nang is a coastal city with a 60-km long shoreline. With smooth and sandy beaches running across the coast, Da Nang beach is voted by Forbes (U.S.) to be 1 of 6 most beautiful beaches in the world."
        private const val TEXT_TEST_TWO =
            "Da Nang is the commercial and educational centre of Central Vietnam and is the largest city in the region. It has a well-sheltered, easily accessible port, and its location on National Route 1 and the North–South Railway makes it a transport hub."
        private const val EXPECTED_SIMILARITY = 0.9
    }

    private lateinit var embedderHelper: TextEmbedderHelper

    @Before
    fun setup() {
        embedderHelper =
            TextEmbedderHelper(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun embeddingResultsWithinAcceptedRange() {
        val bundleResult = embedderHelper.compare(TEXT_TEST_ONE, TEXT_TEST_TWO)
        assertEquals(EXPECTED_SIMILARITY, bundleResult?.similarity ?: 0.0, 0.09)
    }
}
