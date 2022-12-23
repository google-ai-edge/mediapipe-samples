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
import android.media.AudioFormat
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
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
import com.google.mediapipe.tasks.components.containers.AudioData
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

class LibraryFragment : Fragment() {

    private var _fragmentBinding: FragmentLibraryBinding? = null
    private val fragmentLibraryBinding get() = _fragmentBinding!!
    private val viewModel: MainViewModel by activityViewModels()
    private val getContent =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            // Handle the returned Uri
            uri?.let { runAudioClassification(it) }
        }

    private var audioClassifierHelper: AudioClassifierHelper? = null
    private lateinit var probabilitiesAdapter: ProbabilitiesAdapter
    private lateinit var backgroundExecutor: ExecutorService
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

        backgroundExecutor = Executors.newSingleThreadExecutor()

        initBottomSheetControls()
    }

    private fun startPickupAudio() {
        getContent.launch("audio/*")
    }

    private fun runAudioClassification(uri: Uri) {
        fragmentLibraryBinding.audioProgress.visibility = View.INVISIBLE
        fragmentLibraryBinding.classifierProgress.visibility = View.VISIBLE
        setUiEnabled(false)
        fragmentLibraryBinding.audioProgress.progress = 0

        val inputStream = requireContext().contentResolver.openInputStream(uri)
        val targetArray = inputStream?.available()?.let { ByteArray(it) }
        inputStream?.read(targetArray)
        backgroundExecutor.execute {

            audioClassifierHelper = AudioClassifierHelper(
                context = requireContext(),
                currentModel = viewModel.currentModel,
                classificationThreshold = viewModel.currentThreshold,
                overlap = viewModel.currentOverlapPosition,
                numOfResults = viewModel.currentMaxResults,
                currentDelegate = viewModel.currentDelegate,
                runningMode = RunningMode.AUDIO_CLIPS,
            )

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA).build()
                )
                setDataSource(requireContext(), uri)
                prepare()
            }

            val floatArray = targetArray?.let { toFloatArray(it) }
            floatArray?.let {
                val duration = mediaPlayer?.duration ?: 0
                val sampleRate =
                    it.size / (duration / 1000 / AudioClassifierHelper.EXPECTED_INPUT_LENGTH)
                val audioData = AudioData.create(
                    AudioData.AudioDataFormat.builder().setNumOfChannels(
                        AudioFormat.CHANNEL_IN_DEFAULT
                    ).setSampleRate(sampleRate).build(), it.size
                )
                audioData.load(it)

                val startTime = SystemClock.uptimeMillis()
                val result = audioClassifierHelper?.classifyAudio(audioData)
                val inferenceTime = SystemClock.uptimeMillis() - startTime

                val executor = ScheduledThreadPoolExecutor(1)
                val max = result?.classificationResultList()?.get()?.size ?: 1
                fragmentLibraryBinding.audioProgress.max = max
                val amountToUpdate = duration / max
                val runnable = Runnable {
                    activity?.runOnUiThread {
                        if (amountToUpdate * fragmentLibraryBinding.audioProgress.progress < duration) {
                            var process: Int =
                                fragmentLibraryBinding.audioProgress.progress
                            val list = result?.classificationResultList()?.get()
                                ?.get(process)?.classifications()?.get(0)
                                ?.categories() ?: emptyList()
                            probabilitiesAdapter.updateCategoryList(list)

                            process += 1
                            fragmentLibraryBinding.audioProgress.progress =
                                process
                            if (process == max) {
                                executor.shutdownNow()
                                setUiEnabled(true)
                            }
                        }
                    }
                }
                activity?.runOnUiThread {
                    mediaPlayer?.start()
                    executor.scheduleAtFixedRate(
                        runnable,
                        0,
                        amountToUpdate.toLong(),
                        TimeUnit.MILLISECONDS
                    )
                    fragmentLibraryBinding.classifierProgress.visibility =
                        View.GONE
                    fragmentLibraryBinding.audioProgress.visibility =
                        View.VISIBLE
                    fragmentLibraryBinding.bottomSheetLayout
                        .inferenceTimeVal.text = String.format("%d ms", inferenceTime)
                }
            }
        }
    }

    private fun initBottomSheetControls() {
        // Allow the user to change the amount of overlap used in classification. More overlap
        // can lead to more accurate resolves in classification.
        fragmentLibraryBinding.bottomSheetLayout.spinnerOverlap.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    viewModel.setOverlap(position)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    // no op
                }
            }

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

        fragmentLibraryBinding.bottomSheetLayout.spinnerOverlap.setSelection(
            viewModel.currentOverlapPosition, false
        )
        fragmentLibraryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentLibraryBinding.bottomSheetLayout.thresholdValue.text =
            viewModel.currentThreshold.toString()
        fragmentLibraryBinding.bottomSheetLayout.resultsValue.text =
            viewModel.currentMaxResults.toString()
    }

    // Update the values displayed in the bottom sheet. Reset classifier.
    private fun updateControlsUi() {
        fragmentLibraryBinding.bottomSheetLayout.resultsValue.text =
            viewModel.currentMaxResults.toString()

        fragmentLibraryBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", viewModel.currentThreshold)

        probabilitiesAdapter.updateCategoryList(emptyList())
        fragmentLibraryBinding.audioProgress.visibility = View.INVISIBLE
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentLibraryBinding.fabGetContent.isEnabled = enabled
        fragmentLibraryBinding.bottomSheetLayout.thresholdMinus.isEnabled =
            enabled
        fragmentLibraryBinding.bottomSheetLayout.thresholdPlus.isEnabled =
            enabled
        fragmentLibraryBinding.bottomSheetLayout.resultsMinus.isEnabled =
            enabled
        fragmentLibraryBinding.bottomSheetLayout.resultsPlus.isEnabled = enabled
        fragmentLibraryBinding.bottomSheetLayout.spinnerDelegate.isEnabled =
            enabled
        fragmentLibraryBinding.bottomSheetLayout.spinnerOverlap.isEnabled =
            enabled
    }

    private fun toFloatArray(byteArray: ByteArray): FloatArray {
        val result = FloatArray(byteArray.size / Float.SIZE_BYTES)
        ByteBuffer.wrap(byteArray).asFloatBuffer()[result, 0, result.size]
        return result
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        mediaPlayer?.stop()
        mediaPlayer = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        backgroundExecutor.shutdownNow()
        audioClassifierHelper?.stopAudioClassification()
    }
}
