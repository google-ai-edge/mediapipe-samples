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
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.google.mediapipe.examples.holisticlandmarker.HolisticLandmarkerHelper
import com.google.mediapipe.examples.holisticlandmarker.MainViewModel
import com.google.mediapipe.examples.holisticlandmarker.R
import com.google.mediapipe.examples.holisticlandmarker.HelperState
import com.google.mediapipe.examples.holisticlandmarker.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(),
    HolisticLandmarkerHelper.LandmarkerListener {
    companion object {
        private const val TAG = "Holistic Landmarker"
        private const val FORMAT_STRING = "%.1f"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private val viewModel: MainViewModel by activityViewModels()

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var holisticLandmarkerHelper: HolisticLandmarkerHelper
    private val faceBlendshapesResultAdapter by lazy {
        FaceBlendshapesResultAdapter()
    }

    /** Blocking ML operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(R.id.action_camera_to_permissions)
        }

        // Start the HolisticLandmarkerHelper again when users come back
        // to the foreground.
        backgroundExecutor.execute {
            if (holisticLandmarkerHelper.isClose()) {
                holisticLandmarkerHelper.setUpHolisticLandmarker()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (this::holisticLandmarkerHelper.isInitialized) {
            // Close the HolisticLandmarkerHelper and release resources
            backgroundExecutor.execute {
                holisticLandmarkerHelper.clearHolisticLandmarker()
            }
        }
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        backgroundExecutor.shutdown()
        backgroundExecutor.awaitTermination(
            Long.MAX_VALUE, TimeUnit.NANOSECONDS
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding =
            FragmentCameraBinding.inflate(inflater, container, false)

        return fragmentCameraBinding.root
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        with(fragmentCameraBinding.recyclerviewResults) {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = faceBlendshapesResultAdapter
        }

        // Initialize our background executor
        backgroundExecutor = Executors.newSingleThreadExecutor()

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {
            // Set up the camera and its use cases
            setUpCamera()
        }
        setUpListener()
        viewModel.helperState.observe(viewLifecycleOwner) { helperState ->
            // Only show face blend results view when Face Blend Mode available
            fragmentCameraBinding.recyclerviewResults.isVisible = helperState.isFaceBlendMode
            updateBottomSheetControlsUi(helperState)
        }
    }

    private fun setUpListener() {
        with(fragmentCameraBinding.bottomSheetLayout) {
            facePresenceThresholdMinus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minFacePresenceThreshold ?: 0.3f
                if (currentLandmarkConfidence > 0.3f) {
                    viewModel.setMinFaceLandmarkConfidence(currentLandmarkConfidence - 0.1f)
                }
            }
            facePresenceThresholdPlus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minFacePresenceThreshold ?: 0.3f
                if (currentLandmarkConfidence < 0.9f) {
                    viewModel.setMinFaceLandmarkConfidence(currentLandmarkConfidence + 0.1f)
                }
            }
            posePresenceThresholdMinus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minPosePresenceThreshold ?: 0.3f
                if (currentLandmarkConfidence > 0.3f) {
                    viewModel.setMinPoseLandmarkConfidence(currentLandmarkConfidence - 0.1f)
                }
            }
            posePresenceThresholdPlus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minPosePresenceThreshold ?: 0.3f
                if (currentLandmarkConfidence < 0.9f) {
                    viewModel.setMinPoseLandmarkConfidence(currentLandmarkConfidence + 0.1f)
                }
            }
            handLandmarksThresholdMinus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minHandLandmarkThreshold ?: 0.3f
                if (currentLandmarkConfidence > 0.3f) {
                    viewModel.setMinHandLandmarkConfidence(currentLandmarkConfidence - 0.1f)
                }
            }
            handLandmarksThresholdPlus.setOnClickListener {
                val currentLandmarkConfidence =
                    viewModel.helperState.value?.minHandLandmarkThreshold ?: 0.3f
                if (currentLandmarkConfidence < 0.9f) {
                    viewModel.setMinHandLandmarkConfidence(currentLandmarkConfidence + 0.1f)
                }
            }
            faceDetectionThresholdMinus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minFaceDetectionThreshold ?: 0.3f
                if (currentDetectionConfidence > 0.3f) {
                    viewModel.setMinFaceDetectionConfidence(currentDetectionConfidence - 0.1f)
                }
            }
            faceDetectionThresholdPlus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minFaceDetectionThreshold ?: 0.3f
                if (currentDetectionConfidence < 0.9f) {
                    viewModel.setMinFaceDetectionConfidence(currentDetectionConfidence + 0.1f)
                }
            }
            poseDetectionThresholdMinus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minPoseDetectionThreshold ?: 0.3f
                if (currentDetectionConfidence > 0.3f) {
                    viewModel.setMinPoseDetectionConfidence(currentDetectionConfidence - 0.1f)
                }
            }
            poseDetectionThresholdPlus.setOnClickListener {
                val currentDetectionConfidence =
                    viewModel.helperState.value?.minPoseDetectionThreshold ?: 0.3f
                if (currentDetectionConfidence < 0.9f) {
                    viewModel.setMinPoseDetectionConfidence(currentDetectionConfidence + 0.1f)
                }
            }
            faceSuppressionMinus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minFaceSuppressionThreshold ?: 0.3f
                if (currentSuppressionConfidence > 0.3f) {
                    viewModel.setMinFaceSuppressionConfidence(currentSuppressionConfidence - 0.1f)
                }
            }
            faceSuppressionPlus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minFaceSuppressionThreshold ?: 0.3f
                if (currentSuppressionConfidence < 0.9f) {
                    viewModel.setMinFaceSuppressionConfidence(currentSuppressionConfidence + 0.1f)
                }
            }
            poseSuppressionMinus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minPoseSuppressionThreshold ?: 0.3f
                if (currentSuppressionConfidence > 0.3f) {
                    viewModel.setMinPoseSuppressionConfidence(currentSuppressionConfidence - 0.1f)
                }
            }
            poseSuppressionPlus.setOnClickListener {
                val currentSuppressionConfidence =
                    viewModel.helperState.value?.minPoseSuppressionThreshold ?: 0.3f
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

                    override fun onNothingSelected(p0: AdapterView<*>?) {
                        /* no op */
                    }
                }
        }
    }

    private fun updateBottomSheetControlsUi(helperState: HelperState) {
        val isFaceBlendShapes =
            if (helperState.delegate == HolisticLandmarkerHelper.DELEGATE_CPU) helperState.isFaceBlendMode else false
        val isPoseSegmentationMarks =
            if (helperState.delegate == HolisticLandmarkerHelper.DELEGATE_CPU) helperState.isPoseSegmentationMarks else false

        // update bottom sheet settings
        with(fragmentCameraBinding.bottomSheetLayout) {
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
        // Create the HolisticLandmarkerHelper that will handle the inference
        backgroundExecutor.execute {
            // clear it and recreate with new settings
            holisticLandmarkerHelper = HolisticLandmarkerHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                currentDelegate = helperState.delegate,
                minFacePresenceConfidence = helperState.minFacePresenceThreshold,
                minHandLandmarksConfidence = helperState.minHandLandmarkThreshold,
                minPosePresenceConfidence = helperState.minPosePresenceThreshold,
                minFaceDetectionConfidence = helperState.minFaceDetectionThreshold,
                minPoseDetectionConfidence = helperState.minPoseDetectionThreshold,
                minFaceSuppressionThreshold = helperState.minFaceSuppressionThreshold,
                minPoseSuppressionThreshold = helperState.minPoseSuppressionThreshold,
                isFaceBlendShapes = isFaceBlendShapes,
                isPoseSegmentationMark = isPoseSegmentationMarks,
                landmarkerHelperListener = this
            )
            _fragmentCameraBinding?.overlay?.clear()
        }
    }

    // Initialize CameraX, and prepare to bind the camera use cases
    private fun setUpCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(
            {
                // CameraProvider
                cameraProvider = cameraProviderFuture.get()

                // Build and bind the camera use cases
                bindCameraUseCases()
            }, ContextCompat.getMainExecutor(requireContext())
        )
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector =
            CameraSelector.Builder().requireLensFacing(cameraFacing).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        // ImageAnalysis. Using RGBA 8888 to match how our models work
        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(backgroundExecutor) { image ->
                        detectFace(image)
                    }
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun detectFace(imageProxy: ImageProxy) {
        holisticLandmarkerHelper.detectLiveStreamCamera(
            imageProxy = imageProxy,
            isFrontCamera = cameraFacing == CameraSelector.LENS_FACING_FRONT
        )
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onError(error: String, errorCode: Int) {
        Log.e(TAG, "An error $error, code $errorCode occurred")
        activity?.runOnUiThread {
            faceBlendshapesResultAdapter.updateResults(null)
            faceBlendshapesResultAdapter.notifyDataSetChanged()
        }
    }

    // Update UI after holistic landmark have been detected. Extracts original
    // image height/width to scale and place the landmarks properly through
    // OverlayView
    @SuppressLint("NotifyDataSetChanged")
    override fun onResults(
        resultBundle: HolisticLandmarkerHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                if (fragmentCameraBinding.recyclerviewResults.scrollState != ViewPager2.SCROLL_STATE_DRAGGING) {
                    faceBlendshapesResultAdapter.updateResults(resultBundle.result)
                    faceBlendshapesResultAdapter.notifyDataSetChanged()
                }

                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)

                // Pass necessary information to OverlayView for drawing on the canvas
                fragmentCameraBinding.overlay.setResults(
                    resultBundle.result,
                    resultBundle.inputImageHeight,
                    resultBundle.inputImageWidth,
                    RunningMode.LIVE_STREAM
                )
                // Redraw LandMarker with every new result that get from listener
                fragmentCameraBinding.overlay.invalidate()
            }
        }
    }
}