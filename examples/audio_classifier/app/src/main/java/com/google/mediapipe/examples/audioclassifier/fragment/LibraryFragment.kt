/*
 * Copyright 2022 The TensorFlow Authors. All Rights Reserved.
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

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.audioclassifier.AudioClassifierHelper
import com.google.mediapipe.examples.audioclassifier.MainViewModel
import com.google.mediapipe.examples.audioclassifier.R
import com.google.mediapipe.examples.audioclassifier.databinding.FragmentLibraryBinding
import com.google.mediapipe.tasks.audio.core.RunningMode
import java.io.DataInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LibraryFragment : Fragment(), AudioClassifierHelper.ClassifierListener {

    private var _fragmentBinding: FragmentLibraryBinding? = null
    private val fragmentLibraryBinding get() = _fragmentBinding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            // Handle the returned Uri
            uri?.let { runAudioClassification(it) }
        }

    private lateinit var probabilitiesAdapter: ProbabilitiesAdapter
    private var audioClassifierHelper: AudioClassifierHelper? = null
    private var progressExecutor: ScheduledThreadPoolExecutor? = null
    private var backgroundExecutor: ScheduledThreadPoolExecutor? = null

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentBinding =
            FragmentLibraryBinding.inflate(inflater, container, false)
        return fragmentLibraryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        probabilitiesAdapter = ProbabilitiesAdapter()
        val decoration = DividerItemDecoration(
            requireContext(), DividerItemDecoration.VERTICAL
        )
        ContextCompat.getDrawable(requireContext(), R.drawable.space_divider)
            ?.let { decoration.setDrawable(it) }
        with(fragmentLibraryBinding.recyclerView) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = probabilitiesAdapter
            addItemDecoration(decoration)
        }

        fragmentLibraryBinding.fabGetContent.setOnClickListener {
            startPickupAudio()
        }

        initBottomSheetControls()
    }

    override fun onPause() {
        super.onPause()
        resetUi()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        audioClassifierHelper?.stopAudioClassification()
    }

    private fun resetUi() {
        // stop audio when it in background
        mediaPlayer?.stop()
        mediaPlayer = null
        // stop all background tasks
        backgroundExecutor?.shutdownNow()
        progressExecutor?.shutdownNow()
        setUiEnabled(true)
        fragmentLibraryBinding.audioProgress.visibility = View.INVISIBLE
        fragmentLibraryBinding.classifierProgress.visibility = View.GONE
        probabilitiesAdapter.updateCategoryList(emptyList())
    }

    private fun startPickupAudio() {
        getContent.launch("audio/*")
    }

    private fun runAudioClassification(uri: Uri) {
        backgroundExecutor = ScheduledThreadPoolExecutor(1)
        with(fragmentLibraryBinding) {
            audioProgress.visibility = View.INVISIBLE
            audioProgress.progress = 0
            classifierProgress.visibility = View.VISIBLE
        }
        setUiEnabled(false)

        // load the audio from uri
        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val dataInputStream = DataInputStream(inputStream)
        val targetArray = ByteArray(dataInputStream.available())
        dataInputStream.read(targetArray)

        // run on background to avoid block the ui
        backgroundExecutor?.execute {

            audioClassifierHelper = AudioClassifierHelper(
                context = requireContext(),
                currentModel = viewModel.currentModel,
                classificationThreshold = viewModel.currentThreshold,
                overlap = viewModel.currentOverlapPosition,
                numOfResults = viewModel.currentMaxResults,
                currentDelegate = viewModel.currentDelegate,
                runningMode = RunningMode.AUDIO_CLIPS,
                listener = this
            )

            // prepare media player
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build()
                )
                setDataSource(requireContext(), uri)
                prepare()
            }

            val audioFloatArrayData = toFloatArray(targetArray)

            // create audio data to match with model expected length
            val audioDuration = mediaPlayer!!.duration
            // calculate the sample rate
            val expectedSampleRate =
                audioFloatArrayData.size / (audioDuration / 1000 / AudioClassifierHelper
                    .EXPECTED_INPUT_LENGTH)

            val resultBundle =
                audioClassifierHelper?.classifyAudio(
                    audioFloatArrayData,
                    expectedSampleRate
                )
            resultBundle?.results?.let { audioClassifierResults ->
                // if the thread is interrupted, skip the next block code.
                if (Thread.currentThread().isInterrupted) {
                    return@let
                }
                progressExecutor = ScheduledThreadPoolExecutor(1)
                audioClassifierResults.first().classificationResultList()
                    ?.get()?.size?.let { maxProgressCount ->
                        fragmentLibraryBinding.audioProgress.max =
                            maxProgressCount
                        val amountToUpdate = audioDuration / maxProgressCount
                        val runnable = Runnable {
                            activity?.runOnUiThread {
                                if (amountToUpdate * fragmentLibraryBinding.audioProgress.progress < audioDuration) {
                                    var process: Int =
                                        fragmentLibraryBinding.audioProgress.progress
                                    val categories =
                                        audioClassifierResults.first()
                                            .classificationResultList()
                                            ?.get()
                                            ?.get(process)?.classifications()
                                            ?.get(0)
                                            ?.categories() ?: emptyList()
                                    probabilitiesAdapter.updateCategoryList(
                                        categories
                                    )

                                    process += 1
                                    // update the audio process
                                    fragmentLibraryBinding.audioProgress.progress =
                                        process

                                    if (process == maxProgressCount) {
                                        // stop the audio process.
                                        progressExecutor?.shutdownNow()
                                        setUiEnabled(true)
                                    }
                                }
                            }
                        }
                        activity?.runOnUiThread {
                            // start audio
                            mediaPlayer?.start()
                            progressExecutor?.scheduleAtFixedRate(
                                runnable,
                                0,
                                amountToUpdate.toLong(),
                                TimeUnit.MILLISECONDS
                            )
                            with(fragmentLibraryBinding) {
                                classifierProgress.visibility = View.GONE
                                audioProgress.visibility = View.VISIBLE
                                bottomSheetLayout.inferenceTimeVal.text =
                                    String.format(
                                        "%d ms",
                                        resultBundle.inferenceTime
                                    )
                            }
                        }
                    }

            } ?: run {
                Log.e(TAG, "Error running audio classification.")
            }
        }
    }

    private fun initBottomSheetControls() {
        // Allow the user to change the max number of results returned by the audio classifier.
        // Currently allows between 1 and 5 results, but can be edited here.
        fragmentLibraryBinding.bottomSheetLayout.resultsMinus.setOnClickListener {
            if (viewModel.currentMaxResults > 1) {
                viewModel.setMaxResults(viewModel.currentMaxResults - 1)
                updateControlsUi()
            }
        }

        fragmentLibraryBinding.bottomSheetLayout.resultsPlus.setOnClickListener {
            if (viewModel.currentMaxResults < 5) {
                viewModel.setMaxResults(viewModel.currentMaxResults + 1)
                updateControlsUi()
            }
        }

        // Allow the user to change the confidence threshold required for the classifier to return
        // a result. Increments in steps of 10%.
        fragmentLibraryBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (viewModel.currentThreshold >= 0.2) {
                viewModel.setThreshold(viewModel.currentThreshold - 0.1f)
                updateControlsUi()
            }
        }

        fragmentLibraryBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (viewModel.currentThreshold <= 0.8) {
                viewModel.setThreshold(viewModel.currentThreshold + 0.1f)
                updateControlsUi()
            }
        }

        fragmentLibraryBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setDelegate(position)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        with(fragmentLibraryBinding.bottomSheetLayout) {
            spinnerDelegate.setSelection(viewModel.currentDelegate, false)
            thresholdValue.text = viewModel.currentThreshold.toString()
            resultsValue.text = viewModel.currentMaxResults.toString()
            // hide overlap in audio clip mode
            rlOverlap.visibility = View.GONE
        }
    }

    // Update the values displayed in the bottom sheet. Reset classifier.
    private fun updateControlsUi() {
        with(fragmentLibraryBinding.bottomSheetLayout) {
            resultsValue.text = viewModel.currentMaxResults.toString()
            thresholdValue.text =
                String.format("%.2f", viewModel.currentThreshold)
        }
        fragmentLibraryBinding.audioProgress.visibility = View.INVISIBLE
        probabilitiesAdapter.updateCategoryList(emptyList())
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentLibraryBinding.fabGetContent.isEnabled = enabled
        with(fragmentLibraryBinding.bottomSheetLayout) {
            thresholdMinus.isEnabled = enabled
            thresholdPlus.isEnabled = enabled
            resultsMinus.isEnabled = enabled
            resultsPlus.isEnabled = enabled
            spinnerDelegate.isEnabled = enabled
        }
    }

    private fun toFloatArray(byteArray: ByteArray): FloatArray {
        val result = FloatArray(byteArray.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(byteArray).asFloatBuffer()[result, 0, result.size]
        return result
    }

    override fun onError(error: String) {
        activity?.runOnUiThread {
            resetUi()
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResult(resultBundle: AudioClassifierHelper.ResultBundle) {
        // no-op
    }

    companion object {
        private const val TAG = "LibraryFragment"
    }
}
