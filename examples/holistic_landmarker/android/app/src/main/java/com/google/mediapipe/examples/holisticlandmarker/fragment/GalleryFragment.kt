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
package com.google.mediapipe.examples.holisticlandmarker.fragment

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
import com.google.mediapipe.examples.holisticlandmarker.HolisticLandmarkerHelper
import com.google.mediapipe.examples.holisticlandmarker.MainViewModel
import com.google.mediapipe.examples.holisticlandmarker.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GalleryFragment : Fragment(), HolisticLandmarkerHelper.LandmarkerListener {

    enum class MediaType {
        IMAGE,
        VIDEO,
        UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private lateinit var holisticLandmarkerHelper: HolisticLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private val faceBlendshapesResultAdapter by lazy {
        FaceBlendshapesResultAdapter()
    }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ScheduledExecutorService

    private val getContent =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            // Handle the returned Uri
            uri?.let { mediaUri ->
                when (val mediaType = loadMediaType(mediaUri)) {
                    MediaType.IMAGE -> runDetectionOnImage(mediaUri)
                    MediaType.VIDEO -> runDetectionOnVideo(mediaUri)
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
        }
        with(fragmentGalleryBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = faceBlendshapesResultAdapter
        }

        initBottomSheetControls()
    }

    override fun onPause() {
        fragmentGalleryBinding.overlay.clear()
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        fragmentGalleryBinding.imageResult.visibility = View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility = View.VISIBLE

        activity?.runOnUiThread {
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()
        }
        super.onPause()
    }

    private fun initBottomSheetControls() {
        // init bottom sheet settings
        fragmentGalleryBinding.bottomSheetLayout.poseDetectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPoseDetectionConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.posePresenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinPosePresenceConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.faceDetectionThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinFaceDetectionConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.facePresenceThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinFacePresenceConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.handLandmarksThresholdValue.text =
            String.format(
                Locale.US, "%.2f", viewModel.currentMinHandLandmarksConfidence
            )

        // Pose detection threshold controls
        fragmentGalleryBinding.bottomSheetLayout.poseDetectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence >= 0.2) {
                viewModel.setMinPoseDetectionConfidence(viewModel.currentMinPoseDetectionConfidence - 0.1f)
                updateControlsUi()
            }
        }
        fragmentGalleryBinding.bottomSheetLayout.poseDetectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPoseDetectionConfidence <= 0.8) {
                viewModel.setMinPoseDetectionConfidence(viewModel.currentMinPoseDetectionConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // Pose presence threshold controls
        fragmentGalleryBinding.bottomSheetLayout.posePresenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence >= 0.2) {
                viewModel.setMinPosePresenceConfidence(viewModel.currentMinPosePresenceConfidence - 0.1f)
                updateControlsUi()
            }
        }
        fragmentGalleryBinding.bottomSheetLayout.posePresenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinPosePresenceConfidence <= 0.8) {
                viewModel.setMinPosePresenceConfidence(viewModel.currentMinPosePresenceConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // Face detection threshold controls
        fragmentGalleryBinding.bottomSheetLayout.faceDetectionThresholdMinus.setOnClickListener {
            if (viewModel.currentMinFaceDetectionConfidence >= 0.2) {
                viewModel.setMinFaceDetectionConfidence(viewModel.currentMinFaceDetectionConfidence - 0.1f)
                updateControlsUi()
            }
        }
        fragmentGalleryBinding.bottomSheetLayout.faceDetectionThresholdPlus.setOnClickListener {
            if (viewModel.currentMinFaceDetectionConfidence <= 0.8) {
                viewModel.setMinFaceDetectionConfidence(viewModel.currentMinFaceDetectionConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // Face presence threshold controls
        fragmentGalleryBinding.bottomSheetLayout.facePresenceThresholdMinus.setOnClickListener {
            if (viewModel.currentMinFacePresenceConfidence >= 0.2) {
                viewModel.setMinFacePresenceConfidence(viewModel.currentMinFacePresenceConfidence - 0.1f)
                updateControlsUi()
            }
        }
        fragmentGalleryBinding.bottomSheetLayout.facePresenceThresholdPlus.setOnClickListener {
            if (viewModel.currentMinFacePresenceConfidence <= 0.8) {
                viewModel.setMinFacePresenceConfidence(viewModel.currentMinFacePresenceConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // Hand landmarks threshold controls
        fragmentGalleryBinding.bottomSheetLayout.handLandmarksThresholdMinus.setOnClickListener {
            if (viewModel.currentMinHandLandmarksConfidence >= 0.2) {
                viewModel.setMinHandLandmarksConfidence(viewModel.currentMinHandLandmarksConfidence - 0.1f)
                updateControlsUi()
            }
        }
        fragmentGalleryBinding.bottomSheetLayout.handLandmarksThresholdPlus.setOnClickListener {
            if (viewModel.currentMinHandLandmarksConfidence <= 0.8) {
                viewModel.setMinHandLandmarksConfidence(viewModel.currentMinHandLandmarksConfidence + 0.1f)
                updateControlsUi()
            }
        }

        // Delegate spinner
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
    }

    private fun updateControlsUi() {
        if (fragmentGalleryBinding.videoView.isPlaying) {
            fragmentGalleryBinding.videoView.stopPlayback()
        }
        fragmentGalleryBinding.videoView.visibility = View.GONE
        fragmentGalleryBinding.imageResult.visibility = View.GONE
        fragmentGalleryBinding.overlay.clear()
        fragmentGalleryBinding.tvPlaceholder.visibility = View.VISIBLE

        setUiEnabled(false)
        activity?.runOnUiThread {
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()
        }

        fragmentGalleryBinding.bottomSheetLayout.poseDetectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinPoseDetectionConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.posePresenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinPosePresenceConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.faceDetectionThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinFaceDetectionConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.facePresenceThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinFacePresenceConfidence
            )
        fragmentGalleryBinding.bottomSheetLayout.handLandmarksThresholdValue.text =
            String.format(
                Locale.US,
                "%.2f",
                viewModel.currentMinHandLandmarksConfidence
            )
    }

    // Load and run detection on image
    private fun runDetectionOnImage(uri: Uri) {
        setUiEnabled(false)
        updateDisplayView(MediaType.IMAGE)
        displayProgressDialog()

        val currentImageConfig = Bitmap.Config.ARGB_8888
        val image = if (Build.VERSION.SDK_INT >= 28) {
            val source = ImageDecoder.createSource(
                requireActivity().contentResolver, uri
            )
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setTargetColorSpace(android.graphics.ColorSpace.get(android.graphics.ColorSpace.Named.SRGB))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
        } else {
            MediaStore.Images.Media.getBitmap(
                requireActivity().contentResolver, uri
            )
        }
        val formattedBitmap = image.copy(currentImageConfig, true)

        fragmentGalleryBinding.imageResult.setImageBitmap(formattedBitmap)

        // Run holistic landmarker on the background thread
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {
            holisticLandmarkerHelper = HolisticLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.IMAGE,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
                minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
                minHandLandmarksConfidence = viewModel.currentMinHandLandmarksConfidence,
                currentDelegate = viewModel.currentDelegate,
                holisticLandmarkerHelperListener = this
            )

            holisticLandmarkerHelper.detectImage(formattedBitmap)
                ?.let { resultBundle ->
                    activity?.runOnUiThread {
                        faceBlendshapesResultAdapter.updateResults(resultBundle.result)
                        faceBlendshapesResultAdapter.notifyDataSetChanged()
                        fragmentGalleryBinding.overlay.setResults(
                            resultBundle.result,
                            resultBundle.inputImageHeight,
                            resultBundle.inputImageWidth,
                            RunningMode.IMAGE
                        )

                        fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                            String.format("%d ms", resultBundle.inferenceTime)

                        closeProgressDialog()
                        setUiEnabled(true)
                    }
                } ?: run {
                Log.e(TAG, "Error running holistic landmarker.")
            }

            holisticLandmarkerHelper.clearHolisticLandmarker()
        }
    }

    // Load and run detection on video
    private fun runDetectionOnVideo(uri: Uri) {
        setUiEnabled(false)
        updateDisplayView(MediaType.VIDEO)

        // Show the progress bar while video is processed
        displayProgressDialog()

        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
        backgroundExecutor.execute {
            holisticLandmarkerHelper = HolisticLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.VIDEO,
                minPoseDetectionConfidence = viewModel.currentMinPoseDetectionConfidence,
                minPosePresenceConfidence = viewModel.currentMinPosePresenceConfidence,
                minFaceDetectionConfidence = viewModel.currentMinFaceDetectionConfidence,
                minFacePresenceConfidence = viewModel.currentMinFacePresenceConfidence,
                minHandLandmarksConfidence = viewModel.currentMinHandLandmarksConfidence,
                currentDelegate = viewModel.currentDelegate,
                holisticLandmarkerHelperListener = this
            )

            holisticLandmarkerHelper.detectVideoFile(
                uri,
                VIDEO_INTERVAL_MS
            )?.let { resultBundle ->
                activity?.runOnUiThread {
                    fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                        String.format(
                            "%d ms",
                            resultBundle.inferenceTime
                        )

                    fragmentGalleryBinding.videoView.apply {
                        setVideoURI(uri)
                        setOnPreparedListener {
                            val isLooping = false
                            start()
                            val videoStartTimeMs =
                                SystemClock.uptimeMillis()

                            backgroundExecutor.scheduleAtFixedRate(
                                {
                                    activity?.runOnUiThread {
                                        val videoCurrentTimeMs =
                                            SystemClock.uptimeMillis() - videoStartTimeMs
                                        val resultIndex =
                                            videoCurrentTimeMs.div(
                                                VIDEO_INTERVAL_MS
                                            ).toInt()

                                        if (resultIndex >= resultBundle.results.size) {
                                            if (isLooping) {
                                                seekTo(0)
                                                start()
                                            } else {
                                                stopPlayback()
                                                setUiEnabled(true)
                                            }
                                        } else {
                                            val result =
                                                resultBundle.results[resultIndex]
                                            faceBlendshapesResultAdapter.updateResults(
                                                result
                                            )
                                            faceBlendshapesResultAdapter.notifyDataSetChanged()
                                            fragmentGalleryBinding.overlay.setResults(
                                                result,
                                                resultBundle.inputImageHeight,
                                                resultBundle.inputImageWidth,
                                                RunningMode.VIDEO
                                            )
                                        }
                                    }
                                },
                                0,
                                VIDEO_INTERVAL_MS,
                                TimeUnit.MILLISECONDS
                            )
                        }
                    }
                    closeProgressDialog()
                }
            } ?: run {
                Log.e(TAG, "Error running holistic landmarker.")
                activity?.runOnUiThread {
                    closeProgressDialog()
                    setUiEnabled(true)
                }
            }
        }
    }

    private fun updateDisplayView(mediaType: MediaType) {
        fragmentGalleryBinding.imageResult.visibility =
            if (mediaType == MediaType.IMAGE) View.VISIBLE else View.GONE
        fragmentGalleryBinding.videoView.visibility =
            if (mediaType == MediaType.VIDEO) View.VISIBLE else View.GONE
        fragmentGalleryBinding.tvPlaceholder.visibility =
            if (mediaType == MediaType.UNKNOWN) View.VISIBLE else View.GONE
    }

    private fun displayProgressDialog() {
        fragmentGalleryBinding.progress.visibility = View.VISIBLE
    }

    private fun closeProgressDialog() {
        fragmentGalleryBinding.progress.visibility = View.GONE
    }

    private fun setUiEnabled(enabled: Boolean) {
        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
        fragmentGalleryBinding.bottomSheetLayout.poseDetectionThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.poseDetectionThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.posePresenceThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.posePresenceThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.faceDetectionThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.faceDetectionThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.facePresenceThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.facePresenceThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.handLandmarksThresholdMinus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.handLandmarksThresholdPlus.isEnabled =
            enabled
        fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.isEnabled =
            enabled
    }

    private fun loadMediaType(uri: Uri): MediaType {
        val mimeType = context?.contentResolver?.getType(uri)
        mimeType?.let {
            if (it.startsWith("image")) return MediaType.IMAGE
            if (it.startsWith("video")) return MediaType.VIDEO
        }

        return MediaType.UNKNOWN
    }

    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()

            if (errorCode == HolisticLandmarkerHelper.GPU_ERROR) {
                fragmentGalleryBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    HolisticLandmarkerHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    override fun onResults(resultBundle: HolisticLandmarkerHelper.ResultBundle) {
        // no-op
    }

    companion object {
        private const val TAG = "GalleryFragment"
        private const val VIDEO_INTERVAL_MS = 33L
    }
}
