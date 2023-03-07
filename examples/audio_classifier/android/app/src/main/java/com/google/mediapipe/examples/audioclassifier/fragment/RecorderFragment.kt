/*
 * Copyright 2023 The TensorFlow Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mediapipe.examples.audioclassifier.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.audioclassifier.AudioClassifierHelper
import com.google.mediapipe.examples.audioclassifier.MainViewModel
import com.google.mediapipe.examples.audioclassifier.R
import com.google.mediapipe.examples.audioclassifier.databinding.FragmentRecorderBinding
import com.google.mediapipe.tasks.audio.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class RecorderFragment : Fragment(), AudioClassifierHelper.ClassifierListener {
    private var _fragmentBinding: FragmentRecorderBinding? = null
    private val fragmentRecorderBinding get() = _fragmentBinding!!
    private lateinit var audioClassifierHelper: AudioClassifierHelper
    private lateinit var probabilitiesAdapter: ProbabilitiesAdapter
    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var backgroundExecutor: ExecutorService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentBinding =
            FragmentRecorderBinding.inflate(inflater, container, false)
        return fragmentRecorderBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentRecorderBinding.bottomSheetLayout.rlInferenceTime.visibility =
            View.GONE
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // init the result recyclerview
        probabilitiesAdapter = ProbabilitiesAdapter()
        val decoration = DividerItemDecoration(
            requireContext(),
            DividerItemDecoration.VERTICAL
        )
        ContextCompat.getDrawable(requireContext(), R.drawable.space_divider)
            ?.let { decoration.setDrawable(it) }
        with(fragmentRecorderBinding.recyclerView) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = probabilitiesAdapter
            addItemDecoration(decoration)
        }

        backgroundExecutor.execute {
            audioClassifierHelper =
                AudioClassifierHelper(
                    context = requireContext(),
                    classificationThreshold = viewModel.currentThreshold,
                    overlap = viewModel.currentOverlapPosition,
                    numOfResults = viewModel.currentMaxResults,
                    runningMode = RunningMode.AUDIO_STREAM,
                    listener = this
                )
            activity?.runOnUiThread {
                initBottomSheetControls()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(),
                R.id.fragment_container
            )
                .navigate(R.id.action_audio_to_permissions)
        }
        backgroundExecutor.execute {
            if (audioClassifierHelper.isClosed()) {
                audioClassifierHelper.initClassifier()
            }
        }
    }

    override fun onPause() {
        super.onPause()

        // save audio classifier settings
        viewModel.apply {
            setThreshold(audioClassifierHelper.classificationThreshold)
            setMaxResults(audioClassifierHelper.numOfResults)
            setOverlap(audioClassifierHelper.overlap)
        }

        backgroundExecutor.execute {
            if (::audioClassifierHelper.isInitialized) {
                audioClassifierHelper.stopAudioClassification()
            }
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _fragmentBinding = null
        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    private fun initBottomSheetControls() {

        // Allow the user to change the amount of overlap used in classification. More overlap
        // can lead to more accurate resolves in classification.
        fragmentRecorderBinding.bottomSheetLayout.spinnerOverlap.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    audioClassifierHelper.overlap = position
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    // no op
                }
            }

        // Allow the user to change the max number of results returned by the audio classifier.
        // Currently allows between 1 and 5 results, but can be edited here.
        fragmentRecorderBinding.bottomSheetLayout.resultsMinus.setOnClickListener {
            if (audioClassifierHelper.numOfResults > 1) {
                audioClassifierHelper.numOfResults--
                updateControlsUi()
            }
        }

        fragmentRecorderBinding.bottomSheetLayout.resultsPlus.setOnClickListener {
            if (audioClassifierHelper.numOfResults < 5) {
                audioClassifierHelper.numOfResults++
                updateControlsUi()
            }
        }

        // Allow the user to change the confidence threshold required for the classifier to return
        // a result. Increments in steps of 10%.
        fragmentRecorderBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (audioClassifierHelper.classificationThreshold >= 0.2) {
                audioClassifierHelper.classificationThreshold -= 0.1f
                updateControlsUi()
            }
        }

        fragmentRecorderBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (audioClassifierHelper.classificationThreshold <= 0.8) {
                audioClassifierHelper.classificationThreshold += 0.1f
                updateControlsUi()
            }
        }

        fragmentRecorderBinding.bottomSheetLayout.spinnerOverlap.setSelection(
            viewModel.currentOverlapPosition,
            false
        )

        fragmentRecorderBinding.bottomSheetLayout.thresholdValue.text =
            viewModel.currentThreshold.toString()
        fragmentRecorderBinding.bottomSheetLayout.resultsValue.text =
            viewModel.currentMaxResults.toString()
    }

    // Update the values displayed in the bottom sheet. Reset classifier.
    private fun updateControlsUi() {
        fragmentRecorderBinding.bottomSheetLayout.resultsValue.text =
            audioClassifierHelper.numOfResults.toString()
        fragmentRecorderBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", audioClassifierHelper.classificationThreshold)

        backgroundExecutor.execute {
            audioClassifierHelper.stopAudioClassification()
            audioClassifierHelper.initClassifier()
        }
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            probabilitiesAdapter.updateCategoryList(emptyList())
        }
    }

    override fun onResult(resultBundle: AudioClassifierHelper.ResultBundle) {
        activity?.runOnUiThread {
            if (_fragmentBinding != null) {
                resultBundle.results[0].classificationResults().first()
                    .classifications()?.get(0)?.categories()?.let {
                        // Show result on bottom sheet
                        probabilitiesAdapter.updateCategoryList(it)
                    }
            }
        }
    }
}
