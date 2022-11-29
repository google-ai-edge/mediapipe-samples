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
package com.google.mediapipe.examples.imageclassification.fragments

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.imageclassification.ImageClassifierHelper
import com.google.mediapipe.examples.imageclassification.MainViewModel
import com.google.mediapipe.examples.imageclassification.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GalleryFragment : Fragment(), ImageClassifierHelper.ClassifierListener {
    enum class MediaType {
        IMAGE, VIDEO, UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var imageClassifierHelper: ImageClassifierHelper
    private val classificationResultsAdapter by lazy {
        ClassificationResultsAdapter().apply {
            updateAdapterSize(viewModel.currentMaxResults)
        }
    }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService

    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Handle the returned Uri
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runClassificationOnImage(mediaUri)
                    MediaType.VIDEO -> runClassificationOnVideo(mediaUri)
                    MediaType.UNKNOWN -> {
                        updateDisplayView(mediaType)
                        Toast.makeText(
                            requireContext(),
                            "Unsupported data type.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding =
            FragmentGalleryBinding.inflate(inflater, container, false)

        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentGalleryBinding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
            updateDisplayView(MediaType.UNKNOWN)
        }
        with(fragmentGalleryBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = classificationResultsAdapter
        }

        initBottomSheetControls()
    }

    private fun initBottomSheetControls() {
        updateControlsUi()
        // When clicked, lower classification score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (viewModel.currentThreshold >= 0.2) {
                viewModel.setThreshold(viewModel.currentThreshold - 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, raise classification score threshold floor
        fragmentGalleryBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (viewModel.currentThreshold <= 0.8) {
                viewModel.setThreshold(viewModel.currentThreshold + 0.1f)
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of objects that can be classified
        // at a time
        fragmentGalleryBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (viewModel.currentMaxResults > 1) {
                viewModel.setMaxResults(viewModel.currentMaxResults - 1)
                updateControlsUi()
            }
        }

        // When clicked, increase the number of objects that can be
        // classified at a time
        fragmentGalleryBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (viewModel.currentMaxResults < 3) {
                viewModel.setMaxResults(viewModel.currentMaxResults + 1)
                updateControlsUi()
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {

                    viewModel.setDelegate(p2)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for image
        // classification
        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel, false
        )
        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                ) {
                    viewModel.setModel(p2)
                    updateControlsUi()
                }

                override fun onNothingSelected(p0: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset classifier.
    @SuppressLint("NotifyDataSetChanged")
    private fun updateControlsUi() {
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        fragmentGalleryBinding.imageResult.visibility = View.GONE
        fragmentGalleryBinding.bottomSheetLayout.maxResultsValue.text =
            viewModel.currentMaxResults.toString()
        fragmentGalleryBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", viewModel.currentThreshold)
        fragmentGalleryBinding.tvPlaceholder.visibility = View.VISIBLE
        classificationResultsAdapter.updateAdapterSize(viewModel.currentMaxResults)
        classificationResultsAdapter.updateResults(null)
        classificationResultsAdapter.notifyDataSetChanged()
    }

    // Load and display the image.
    private fun runClassificationOnImage(uri: Uri) {
        setUiEnabled(false)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        updateDisplayView(MediaType.IMAGE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(
                requireActivity().contentResolver, uri
            )
            ImageDecoder.decodeBitmap(source)
        } else {
            MediaStore.Images.Media.getBitmap(
                requireActivity().contentResolver, uri
            )
        }.copy(Bitmap.Config.ARGB_8888, true)?.let { bitmap ->
            fragmentGalleryBinding.imageResult.setImageBitmap(bitmap)

            // Run image classification on the input image
            backgroundExecutor.execute {

                imageClassifierHelper = ImageClassifierHelper(
                    context = requireContext(),
                    runningMode = RunningMode.IMAGE,
                    currentModel = viewModel.currentModel,
                    currentDelegate = viewModel.currentDelegate,
                    maxResults = viewModel.currentMaxResults,
                    threshold = viewModel.currentThreshold,
                    imageClassifierListener = this
                )
                imageClassifierHelper.classifyImage(bitmap)
                    ?.let { resultBundle ->
                        activity?.runOnUiThread {
                            classificationResultsAdapter.updateResults(
                                resultBundle.results.first()
                            )
                            classificationResultsAdapter.notifyDataSetChanged()
                            setUiEnabled(true)
                            fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                                String.format(
                                    "%d ms", resultBundle.inferenceTime
                                )
                        }
                    } ?: run {
                    Log.e(TAG, "Error running image classification.")
                }

                imageClassifierHelper.clearImageClassifier()
            }
        }
    }

    // Load and display the video.
    private fun runClassificationOnVideo(uri: Uri) {
        setUiEnabled(false)
        updateDisplayView(MediaType.VIDEO)

        with(fragmentGalleryBinding.videoView) {
            setVideoURI(uri)
            // mute the audio
            setOnPreparedListener { it.setVolume(0f, 0f) }
            requestFocus()
        }

        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {

            activity?.runOnUiThread {
                fragmentGalleryBinding.videoView.visibility = View.GONE
                fragmentGalleryBinding.progress.visibility = View.VISIBLE
            }

            imageClassifierHelper = ImageClassifierHelper(
                context = requireContext(),
                runningMode = RunningMode.VIDEO,
                currentModel = viewModel.currentModel,
                currentDelegate = viewModel.currentDelegate,
                maxResults = viewModel.currentMaxResults,
                threshold = viewModel.currentThreshold,
                imageClassifierListener = this
            )

            imageClassifierHelper.classifyVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    activity?.runOnUiThread {
                        displayVideoResult(resultBundle)
                    }
                } ?: run {
                Log.e(TAG, "Error running image classification.")
            }

            imageClassifierHelper.clearImageClassifier()
        }
    }

    // Setup and display the video.
    private fun displayVideoResult(result: ImageClassifierHelper.ResultBundle) {

        fragmentGalleryBinding.videoView.visibility = View.VISIBLE
        fragmentGalleryBinding.progress.visibility = View.GONE

        fragmentGalleryBinding.videoView.start()
        val videoStartTimeMs = SystemClock.uptimeMillis()

        backgroundExecutor.scheduleAtFixedRate(
            {
                activity?.runOnUiThread {
                    val videoElapsedTimeMs =
                        SystemClock.uptimeMillis() - videoStartTimeMs
                    val resultIndex =
                        videoElapsedTimeMs.div(VIDEO_INTERVAL_MS).toInt()

                    if (resultIndex >= result.results.size || fragmentGalleryBinding.videoView.visibility == View.GONE) {
                        setUiEnabled(true)
                        backgroundExecutor.shutdown()
                    } else {
                        classificationResultsAdapter.updateResults(result.results[resultIndex])
                        classificationResultsAdapter.notifyDataSetChanged()
                        setUiEnabled(false)

                        fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format("%d ms", result.inferenceTime)
                    }
                }
            }, 0, VIDEO_INTERVAL_MS, TimeUnit.MILLISECONDS
        )
    }

    private fun updateDisplayView(mediaType: MediaType) {
        fragmentGalleryBinding.imageResult.visibility =
            if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
        fragmentGalleryBinding.videoView.visibility =
            if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility =
            if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    // Check the type of media that user selected.
    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (mimeType.startsWith("image")) return MediaType.IMAGE
            if (mimeType.startsWith("video")) return MediaType.VIDEO
        }

        return MediaType.UNKNOWN
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.spinnerModel.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.thresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.thresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.maxResultsMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.maxResultsPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.isEnabled =
            enabled
    }

    private fun classifyingError() {
        activity?.runOnUiThread {
            fragmentGalleryBinding.progress.visibility = View.GONE
            setUiEnabled(true)
            updateDisplayView(MediaType.UNKNOWN)
        }
    }

    override fun onError(error: String, errorCode: Int) {
        classifyingError()
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            if (errorCode == ImageClassifierHelper.GPU_ERROR) {
                fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    ImageClassifierHelper.DELEGATE_CPU,
                    false
                )
            }
        }
    }

    override fun onResults(resultBundle: ImageClassifierHelper.ResultBundle) {
        // no-op
    }

    companion object {
        private const val TAG = "GalleryFragment"

        // Value used to get frames at specific intervals for inference (e.g. every 300ms)
        private const val VIDEO_INTERVAL_MS = 300L
    }
}
