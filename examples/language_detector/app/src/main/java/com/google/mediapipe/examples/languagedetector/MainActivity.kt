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
package com.google.mediapipe.examples.languagedetector

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.mediapipe.examples.languagedetector.databinding.ActivityMainBinding
import com.google.mediapipe.tasks.text.languagedetector.LanguageDetectorResult

class MainActivity : AppCompatActivity() {

    private var _activityMainBinding: ActivityMainBinding? = null
    private val activityMainBinding get() = _activityMainBinding!!
    private lateinit var helper: LanguageDetectorHelper
    private val adapter by lazy {
        ResultsAdapter()
    }

    private val listener = object :
        LanguageDetectorHelper.TextResultsListener {
        override fun onResult(
            results: LanguageDetectorResult,
            inferenceTime: Long
        ) {
            runOnUiThread {
                activityMainBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", inferenceTime)

                adapter.updateResult(results
                    .languagesAndScores().sortedByDescending {
                        it.probability()
                    }
                )
            }
        }

        override fun onError(error: String) {
            Toast.makeText(this@MainActivity, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

        // Create the classification helper that will do the heavy lifting
        helper = LanguageDetectorHelper(
            context = this@MainActivity,
            listener = listener
        )

        // Classify the text in the TextEdit box (or the default if nothing is added)
        // on button click.
        activityMainBinding.detectBtn.setOnClickListener {
            if (activityMainBinding.inputText.text.isNullOrEmpty()) {
                helper.detect(getString(R.string.default_edit_text))
            } else {
                helper.detect(activityMainBinding.inputText.text.toString())
            }
        }

        activityMainBinding.results.adapter = adapter
        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {
        val behavior =
            BottomSheetBehavior.from(activityMainBinding.bottomSheetLayout.bottomSheetLayout)

        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }
}
