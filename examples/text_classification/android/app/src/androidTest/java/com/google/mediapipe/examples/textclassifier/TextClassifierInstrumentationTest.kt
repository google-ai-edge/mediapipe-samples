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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
        Category.create(0.7621565f, 0, "Positive", ""),
        Category.create(0.23784359f, 1, "Negative", "")
    )
    private lateinit var lock: ReentrantLock
    private lateinit var condition: Condition

    @Before
    fun setup() {
        lock = ReentrantLock()
        condition = lock.newCondition()
    }

    @Test
    fun textClassifierHelperReturnsConsistentConfidenceResults() {
        var textClassifierResult : TextClassifierResult? = null

        val textClassifierHelper =
            TextClassifierHelper(
                context = ApplicationProvider.getApplicationContext(),
                listener = object :
                    TextClassifierHelper.TextResultsListener {
                    override fun onError(error: String) {
                        // no op
                        println(error)

                        // Release the lock
                        lock.withLock {
                            condition.signal()
                        }
                    }

                    override fun onResult(
                        results: TextClassifierResult,
                        inferenceTime: Long
                    ) {
                        textClassifierResult = results

                        // Release the lock and start verifying the result
                        lock.withLock {
                            condition.signal()
                        }
                    }
                }
            )

        // Run the text classifier with the test text.
        textClassifierHelper.classify(testText)

        // Lock to wait the textClassifierHelper return the value.
        lock.withLock {
            condition.await()
        }

        // Verify that the text classifier result is not null.
        assertNotNull(textClassifierResult)

        // Verify that the categories are correct.
        assertEquals(
            expectedCategories[0].categoryName(),
            textClassifierResult!!.classificationResult().classifications()
                .first().categories()[0].categoryName(),
        )
        assertEquals(
            expectedCategories[1].categoryName(),
            textClassifierResult!!.classificationResult().classifications()
                .first().categories()[1].categoryName()
        )

        // Verify that the scores are correct.
        assertEquals(
            expectedCategories[0].score(),
            textClassifierResult!!.classificationResult().classifications()
                .first().categories()[0].score(),
            0.0001f
        )
        assertEquals(
            expectedCategories[1].score(),
            textClassifierResult!!.classificationResult().classifications()
                .first().categories()[1].score(),
            0.0001f
        )
    }
}
