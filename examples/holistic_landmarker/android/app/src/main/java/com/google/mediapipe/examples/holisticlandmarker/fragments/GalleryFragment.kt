package com.google.mediapipe.examples.holisticlandmarker.fragments

/*
 * Copyright 2024 The TensorFlow Authors. All Rights Reserved.
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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.mediapipe.examples.holisticlandmarker.HelperState
import com.google.mediapipe.examples.holisticlandmarker.HolisticLandmarkerHelper
import com.google.mediapipe.examples.holisticlandmarker.MainViewModel
import com.google.mediapipe.examples.holisticlandmarker.R
import com.google.mediapipe.examples.holisticlandmarker.databinding.FragmentGalleryBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


class GalleryFragment : Fragment(),
    HolisticLandmarkerHelper.LandmarkerListener {
    enum class MediaType {
        IMAGE, VIDEO, UNKNOWN
    }

    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null
    private val fragmentGalleryBinding
        get() = _fragmentGalleryBinding!!
    private lateinit var holisticLandmarkerHelper: HolisticLandmarkerHelper
    private val viewModel: MainViewModel by activityViewModels()
    private val faceBlendshapesResultAdapter by lazy {
        FaceBlendshapesResultAdapter()
    }
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
        with(fragmentGalleryBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = faceBlendshapesResultAdapter
        }
        fragmentGalleryBinding.fabGetContent.setOnClickListener {
            getContent.launch(arrayOf("image/*", "video/*"))
            // reset the view
            clearView()
        }
        setUpListener()
        viewModel.helperState.observe(viewLifecycleOwner) { helperState ->
            // Update Face Blend result
            updateFaceBlendResults(helperState.isFaceBlendMode)
            updateBottomSheetControlsUi(helperState)
        }
    }

    override fun onPause() {
        super.onPause()
        clearView()
    }

    private fun setUpListener() {
        with(fragmentGalleryBinding.bottomSheetLayout) {
            facePresenceThresholdMinus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minFacePresenceThreshold ?: 0.3f
                if (currentLandmarkConfidence > 0.3f) {
                    viewModel.setMinFaceLandmarkConfidence(currentLandmarkConfidence - 0.1f)
                }
            }
            facePresenceThresholdPlus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minFacePresenceThreshold ?: 0f
                if (currentLandmarkConfidence < 0.9f) {
                    viewModel.setMinFaceLandmarkConfidence(currentLandmarkConfidence + 0.1f)
                }
            }
            posePresenceThresholdMinus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minPosePresenceThreshold ?: 0f
                if (currentLandmarkConfidence > 0.3f) {
                    viewModel.setMinPoseLandmarkConfidence(currentLandmarkConfidence - 0.1f)
                }
            }
            posePresenceThresholdPlus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minPosePresenceThreshold ?: 0f
                if (currentLandmarkConfidence < 0.9f) {
                    viewModel.setMinPoseLandmarkConfidence(currentLandmarkConfidence + 0.1f)
                }
            }
            handLandmarksThresholdMinus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minHandLandmarkThreshold ?: 0f
                if (currentLandmarkConfidence > 0.3f) {
                    viewModel.setMinHandLandmarkConfidence(currentLandmarkConfidence - 0.1f)
                }
            }
            handLandmarksThresholdPlus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minHandLandmarkThreshold ?: 0f
                if (currentLandmarkConfidence < 0.9f) {
                    viewModel.setMinHandLandmarkConfidence(currentLandmarkConfidence + 0.1f)
                }
            }
            faceDetectionThresholdMinus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minFaceDetectionThreshold ?: 0f
                if (currentDetectionConfidence > 0.3f) {
                    viewModel.setMinFaceDetectionConfidence(currentDetectionConfidence - 0.1f)
                }
            }
            faceDetectionThresholdPlus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minFaceDetectionThreshold ?: 0f
                if (currentDetectionConfidence < 0.9f) {
                    viewModel.setMinFaceDetectionConfidence(currentDetectionConfidence + 0.1f)
                }
            }
            poseDetectionThresholdMinus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minPoseDetectionThreshold ?: 0f
                if (currentDetectionConfidence > 0.3f) {
                    viewModel.setMinPoseDetectionConfidence(currentDetectionConfidence - 0.1f)
                }
            }
            poseDetectionThresholdPlus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minPoseDetectionThreshold ?: 0f
                if (currentDetectionConfidence < 0.9f) {
                    viewModel.setMinPoseDetectionConfidence(currentDetectionConfidence + 0.1f)
                }
            }
            faceSuppressionMinus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minFaceSuppressionThreshold ?: 0f
                if (currentSuppressionConfidence > 0.3f) {
                    viewModel.setMinFaceSuppressionConfidence(currentSuppressionConfidence - 0.1f)
                }
            }
            faceSuppressionPlus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minFaceSuppressionThreshold ?: 0f
                if (currentSuppressionConfidence < 0.9f) {
                    viewModel.setMinFaceSuppressionConfidence(currentSuppressionConfidence + 0.1f)
                }
            }
            poseSuppressionMinus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minPoseSuppressionThreshold ?: 0f
                if (currentSuppressionConfidence > 0.3f) {
                    viewModel.setMinPoseSuppressionConfidence(currentSuppressionConfidence - 0.1f)
                }
            }
            poseSuppressionPlus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minPoseSuppressionThreshold ?: 0f
                if (currentSuppressionConfidence < 0.9f) {
                    viewModel.setMinPoseSuppressionConfidence(currentSuppressionConfidence + 0.1f)
                }
            }
            switchFaceBlendShapes.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setFaceBlendMode(isChecked)
            }
            switchPoseSegmentationMarks.setOnCheckedChangeListener { _, isChecked ->
                viewModel.setPoseSegmentationMarks(isChecked)
            }

            spinnerDelegate.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(
                        p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long
                    ) {
                        try {
                            viewModel.setDelegate(p2)
                        } catch (e: UninitializedPropertyAccessException) {
                            Log.e(
                                TAG,
                                "HolisticLandmarkerHelper has not been initialized yet."
                            )
                        }
                    }

                    override fun onNothingSelected(p0: AdapterView<*>?) {/* no op */
                    }
                }
        }
    }

    private var isFaceBlendShapes = false
    private var isPoseSegmentationMarks = false

    private fun updateBottomSheetControlsUi(helperState: HelperState) {
        isFaceBlendShapes =
            if (helperState.delegate == HolisticLandmarkerHelper.DELEGATE_CPU) helperState.isFaceBlendMode else false
        isPoseSegmentationMarks =
            if (helperState.delegate == HolisticLandmarkerHelper.DELEGATE_CPU) helperState.isPoseSegmentationMarks else false


        // init bottom sheet settings
        with(fragmentGalleryBinding.bottomSheetLayout) {
            facePresenceThresholdValue.text =
                FORMAT_STRING.format(helperState.minFacePresenceThreshold)
            posePresenceThresholdValue.text =
                FORMAT_STRING.format(helperState.minPosePresenceThreshold)
            handLandmarksThresholdValue.text =
                FORMAT_STRING.format(helperState.minHandLandmarkThreshold)
            faceDetectionThresholdValue.text =
                FORMAT_STRING.format(helperState.minFaceDetectionThreshold)
            poseDetectionThresholdValue.text =
                FORMAT_STRING.format(helperState.minPoseDetectionThreshold)
            faceSuppressionValue.text =
                FORMAT_STRING.format(helperState.minFaceSuppressionThreshold)
            poseSuppressionValue.text =
                FORMAT_STRING.format(helperState.minPoseSuppressionThreshold)
            // enable with CPU delegate
            switchFaceBlendShapes.isChecked = isFaceBlendShapes
            switchPoseSegmentationMarks.isChecked = isPoseSegmentationMarks
            switchFaceBlendShapes.isEnabled =
                helperState.delegate == HolisticLandmarkerHelper.DELEGATE_CPU
            switchPoseSegmentationMarks.isEnabled =
                helperState.delegate == HolisticLandmarkerHelper.DELEGATE_CPU
        }
        clearView()
    }

    private fun updateFaceBlendResults(isFaceBlendMode: Boolean) {
        // Hide Face Blend results if Face Blend mode is disable
        fragmentGalleryBinding.recyclerviewResults.isVisible = isFaceBlendMode
        // Change position of Floating Action Button base on Face Blend results visibility
        val fabGetContentParams =
            fragmentGalleryBinding.fabGetContent.layoutParams as CoordinatorLayout.LayoutParams
        if (isFaceBlendMode) {
            // Anchor Floating Action Button to Face Blend results if it's visible
            fabGetContentParams.anchorId = fragmentGalleryBinding.recyclerviewResults.id
        } else {
            // Anchor Floating Action Button to Setting Bottom sheet if Face Blend results is hidden
            fabGetContentParams.anchorId =
                fragmentGalleryBinding.bottomSheetLayout.bottomSheetLayout.id
        }
        fragmentGalleryBinding.fabGetContent.layoutParams = fabGetContentParams
    }

    // Load and display the image.
    @SuppressLint("NotifyDataSetChanged")
    private fun runDetectionOnImage(uri: Uri) {
        setUiEnabled(false)
        updateDisplayView(MediaType.IMAGE)
        backgroundExecutor = Executors.newSingleThreadScheduledExecutor()
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

            // Run holistic landmarker on the input image
            backgroundExecutor.execute {
                viewModel.helperState.value?.let {
                    holisticLandmarkerHelper = HolisticLandmarkerHelper(
                        context = requireContext(),
                        runningMode = RunningMode.IMAGE,
                        currentDelegate = it.delegate,
                        minFacePresenceConfidence = it.minFacePresenceThreshold,
                        minHandLandmarksConfidence = it.minHandLandmarkThreshold,
                        minPosePresenceConfidence = it.minPosePresenceThreshold,
                        minFaceDetectionConfidence = it.minFaceDetectionThreshold,
                        minPoseDetectionConfidence = it.minPoseDetectionThreshold,
                        minFaceSuppressionThreshold = it.minFaceSuppressionThreshold,
                        minPoseSuppressionThreshold = it.minPoseSuppressionThreshold,
                        isFaceBlendShapes = isFaceBlendShapes,
                        isPoseSegmentationMark = isPoseSegmentationMarks,
                        landmarkerHelperListener = this
                    )
                }

                holisticLandmarkerHelper.detectImage(bitmap)
                    ?.let { result ->
                        activity?.runOnUiThread {
                            if (fragmentGalleryBinding.recyclerviewResults.scrollState != ViewPager2.SCROLL_STATE_DRAGGING) {
                                faceBlendshapesResultAdapter.updateResults(
                                    result.result
                                )
                                faceBlendshapesResultAdapter.notifyDataSetChanged()
                            }

                            fragmentGalleryBinding.overlay.setResults(
                                result.result,
                                bitmap.height,
                                bitmap.width,
                                RunningMode.IMAGE
                            )

                            setUiEnabled(true)

                            fragmentGalleryBinding.bottomSheetLayout.inferenceTimeVal.text =
                                String.format("%d ms", result.inferenceTime)
                        }
                    } ?: run {
                    activity?.runOnUiThread {
                        setUiEnabled(true)
                    }
                    Log.e(
                        TAG, "Error running holistic landmarker."
                    )
                }

                holisticLandmarkerHelper.clearHolisticLandmarker()
            }
        }
    }

    // clear view when switching between image and video
    @SuppressLint("NotifyDataSetChanged")
    private fun clearView() {
        with(fragmentGalleryBinding) {
            tvPlaceholder.visibility = View.VISIBLE
            bottomSheetLayout.inferenceTimeVal.text =
                getString(R.string.tv_default_inference_time)
            if (videoView.isPlaying) {
                videoView.stopPlayback()
            }
            videoView.visibility = View.GONE
            imageResult.visibility = View.GONE
            overlay.clear()
        }
        faceBlendshapesResultAdapter.updateResults(null)
        faceBlendshapesResultAdapter.notifyDataSetChanged()
    }

    private fun runDetectionOnVideo(uri: Uri) {
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

            viewModel.helperState.value?.let {
                holisticLandmarkerHelper = HolisticLandmarkerHelper(
                    context = requireContext(),
                    runningMode = RunningMode.VIDEO,
                    currentDelegate = it.delegate,
                    minFacePresenceConfidence = it.minFacePresenceThreshold,
                    minHandLandmarksConfidence = it.minHandLandmarkThreshold,
                    minPosePresenceConfidence = it.minPosePresenceThreshold,
                    minFaceDetectionConfidence = it.minFaceDetectionThreshold,
                    minPoseDetectionConfidence = it.minPoseDetectionThreshold,
                    minFaceSuppressionThreshold = it.minFaceSuppressionThreshold,
                    minPoseSuppressionThreshold = it.minPoseSuppressionThreshold,
                    isFaceBlendShapes = isFaceBlendShapes,
                    isPoseSegmentationMark = isPoseSegmentationMarks,
                    landmarkerHelperListener = this
                )
            }

            activity?.runOnUiThread {
                fragmentGalleryBinding.videoView.visibility = View.GONE
                fragmentGalleryBinding.progress.visibility = View.VISIBLE
            }

            holisticLandmarkerHelper.detectVideoFile(uri, VIDEO_INTERVAL_MS)
                ?.let { resultBundle ->
                    activity?.runOnUiThread { displayVideoResult(resultBundle) }
                } ?: run {
                setUiEnabled(true)
                Log.e(TAG, "Error running holistic landmarker.")
            }

            holisticLandmarkerHelper.clearHolisticLandmarker()
        }
    }

    // Setup and display the video.
    @SuppressLint("NotifyDataSetChanged")
    private fun displayVideoResult(result: HolisticLandmarkerHelper.VideoResultBundle) {

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
                        // The video playback has finished so we stop drawing bounding boxes
                        backgroundExecutor.shutdown()
                    } else {
                        fragmentGalleryBinding.overlay.setResults(
                            result.results[resultIndex],
                            result.inputImageHeight,
                            result.inputImageWidth,
                            RunningMode.VIDEO
                        )

                        if (fragmentGalleryBinding.recyclerviewResults.scrollState != ViewPager2.SCROLL_STATE_DRAGGING) {
                            faceBlendshapesResultAdapter.updateResults(result.results[resultIndex])
                            faceBlendshapesResultAdapter.notifyDataSetChanged()
                        }

                        setUiEnabled(true)

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
        fragmentGalleryBinding.progress.visibility =
            if (enabled) View.GONE else View.VISIBLE
        fragmentGalleryBinding.fabGetContent.isEnabled = enabled
        with(fragmentGalleryBinding.bottomSheetLayout) {
            facePresenceThresholdMinus.isEnabled = enabled
            facePresenceThresholdPlus.isEnabled = enabled
            posePresenceThresholdMinus.isEnabled = enabled
            posePresenceThresholdPlus.isEnabled = enabled
            handLandmarksThresholdMinus.isEnabled = enabled
            handLandmarksThresholdPlus.isEnabled = enabled
            faceDetectionThresholdMinus.isEnabled = enabled
            faceDetectionThresholdPlus.isEnabled = enabled
            poseDetectionThresholdMinus.isEnabled = enabled
            poseDetectionThresholdPlus.isEnabled = enabled
            faceSuppressionMinus.isEnabled = enabled
            faceSuppressionPlus.isEnabled = enabled
            poseSuppressionMinus.isEnabled = enabled
            poseSuppressionPlus.isEnabled = enabled
            switchFaceBlendShapes.isEnabled = enabled
            // only enable with CPU delegate
            switchPoseSegmentationMarks.isEnabled =
                if (viewModel.helperState.value?.delegate == HolisticLandmarkerHelper.DELEGATE_CPU) enabled else false
            spinnerDelegate.isEnabled =
                if (viewModel.helperState.value?.delegate == HolisticLandmarkerHelper.DELEGATE_CPU) enabled else false
        }
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
        private const val FORMAT_STRING = "%.1f"
        // Value used to get frames at specific intervals for inference (e.g. every 300ms)
        private const val VIDEO_INTERVAL_MS = 300L
    }

}
