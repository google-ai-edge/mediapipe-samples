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
package com.google.mediapipe.examples.textclassifier

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mediapipe.tasks.components.containers.Category
import com.google.mediapipe.tasks.text.textclassifier.TextClassifierResult
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class TextClassifierInstrumentationTest {

    private val testText =
        "This was a triumph. I\\'m making a note here, HUGE SUCCESS. It\\'s hard to " +
                "overstate my satisfaction."

    private val expectedCategories = listOf(
        Category.create(0.5736692f, 0, "Positive", ""),
        Category.create(0.4263308f, 1, "Negative", "")
    )

    @Test
    fun textClassifierHelperReturnsConsistentConfidenceResults() {
        val textClassifierHelper =
            TextClassifierHelper(
                context = ApplicationProvider.getApplicationContext(),
                listener = object :
                    TextClassifierHelper.TextResultsListener {
                    override fun onError(error: String) {
                        // no op
                        println(error)
                    }

                    override fun onResult(
                        results: TextClassifierResult,
                        inferenceTime: Long
                    ) {
                        // Verify that the categories are correct.
                        assertEquals(
                            results.classificationResult().classifications()
                                .first().categories()[0].categoryName(),
                            expectedCategories[0].categoryName()
                        )
                        assertEquals(
                            results.classificationResult().classifications()
                                .first().categories()[1].categoryName(),
                            expectedCategories[1].categoryName()
                        )

                        // Verify that the scores are correct.
                        assertEquals(
                            results.classificationResult().classifications()
                                .first().categories()[0].score(),
                            expectedCategories[0].score(), 0.0001f
                        )
                        assertEquals(
                            results.classificationResult().classifications()
                                .first().categories()[1].score(),
                            expectedCategories[1].score(), 0.0001f
                        )
                    }
                }
            )

        textClassifierHelper.classify(testText)
    }
}
