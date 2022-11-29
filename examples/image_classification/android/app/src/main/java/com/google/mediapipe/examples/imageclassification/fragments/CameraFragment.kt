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
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.Toast
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.mediapipe.examples.imageclassification.ImageClassifierHelper
import com.google.mediapipe.examples.imageclassification.MainViewModel
import com.google.mediapipe.examples.imageclassification.R
import com.google.mediapipe.examples.imageclassification.databinding.FragmentCameraBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CameraFragment : Fragment(), ImageClassifierHelper.ClassifierListener {

    companion object {
        private const val TAG = "Image Classifier"
    }

    private var _fragmentCameraBinding: FragmentCameraBinding? = null
    private val fragmentCameraBinding
        get() = _fragmentCameraBinding!!

    private val viewModel: MainViewModel by activityViewModels()
    private lateinit var imageClassifierHelper: ImageClassifierHelper
    private val classificationResultsAdapter by lazy {
        ClassificationResultsAdapter().apply {
            updateAdapterSize(viewModel.currentMaxResults)
        }
    }
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking operations are performed using this executor */
    private lateinit var backgroundExecutor: ExecutorService


    override fun onResume() {
        super.onResume()

        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(
                requireActivity(), R.id.fragment_container
            ).navigate(CameraFragmentDirections.actionCameraToPermissions())
        }

        backgroundExecutor.execute {
            if (imageClassifierHelper.isClosed()) {
                imageClassifierHelper.setupImageClassifier()
            }
        }
    }

    override fun onPause() {
        // save ImageClassifier settings
        viewModel.setModel(imageClassifierHelper.currentModel)
        viewModel.setDelegate(imageClassifierHelper.currentDelegate)
        viewModel.setThreshold(imageClassifierHelper.threshold)
        viewModel.setMaxResults(imageClassifierHelper.maxResults)
        super.onPause()

        // Close the image classifier and release resources
        backgroundExecutor.execute { imageClassifierHelper.clearImageClassifier() }
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
            adapter = classificationResultsAdapter
        }

        backgroundExecutor = Executors.newSingleThreadExecutor()
        backgroundExecutor.execute {
            imageClassifierHelper = ImageClassifierHelper(
                context = requireContext(),
                runningMode = RunningMode.LIVE_STREAM,
                threshold = viewModel.currentThreshold,
                currentDelegate = viewModel.currentDelegate,
                currentModel = viewModel.currentModel,
                maxResults = viewModel.currentMaxResults,
                imageClassifierListener = this
            )

            fragmentCameraBinding.viewFinder.post {
                // Set up the camera and its use cases
                setUpCamera()
            }
        }
        // Attach listeners to UI control widgets
        initBottomSheetControls()
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

    private fun initBottomSheetControls() {
        // Init bottom sheet settings
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text =
            viewModel.currentMaxResults.toString()

        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", viewModel.currentThreshold)

        // When clicked, lower classification score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdMinus.setOnClickListener {
            if (imageClassifierHelper.threshold >= 0.2) {
                imageClassifierHelper.threshold -= 0.1f
                updateControlsUi()
            }
        }

        // When clicked, raise classification score threshold floor
        fragmentCameraBinding.bottomSheetLayout.thresholdPlus.setOnClickListener {
            if (imageClassifierHelper.threshold <= 0.8) {
                imageClassifierHelper.threshold += 0.1f
                updateControlsUi()
            }
        }

        // When clicked, reduce the number of objects that can be classified at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsMinus.setOnClickListener {
            if (imageClassifierHelper.maxResults > 1) {
                imageClassifierHelper.maxResults--
                updateControlsUi()
                classificationResultsAdapter.updateAdapterSize(size = imageClassifierHelper.maxResults)
            }
        }

        // When clicked, increase the number of objects that can be classified at a time
        fragmentCameraBinding.bottomSheetLayout.maxResultsPlus.setOnClickListener {
            if (imageClassifierHelper.maxResults < 3) {
                imageClassifierHelper.maxResults++
                updateControlsUi()
                classificationResultsAdapter.updateAdapterSize(size = imageClassifierHelper.maxResults)
            }
        }

        // When clicked, change the underlying hardware used for inference. Current options are CPU
        // GPU, and NNAPI
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
            viewModel.currentDelegate, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    imageClassifierHelper.currentDelegate = position
                    updateControlsUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }

        // When clicked, change the underlying model used for object classification
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.setSelection(
            viewModel.currentModel, false
        )
        fragmentCameraBinding.bottomSheetLayout.spinnerModel.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    imageClassifierHelper.currentModel = position
                    updateControlsUi()
                }

                override fun onNothingSelected(parent: AdapterView<*>?) {
                    /* no op */
                }
            }
    }

    // Update the values displayed in the bottom sheet. Reset classifier.
    private fun updateControlsUi() {
        fragmentCameraBinding.bottomSheetLayout.maxResultsValue.text =
            imageClassifierHelper.maxResults.toString()

        fragmentCameraBinding.bottomSheetLayout.thresholdValue.text =
            String.format("%.2f", imageClassifierHelper.threshold)

        backgroundExecutor.execute {
            imageClassifierHelper.clearImageClassifier()
            imageClassifierHelper.setupImageClassifier()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation =
            fragmentCameraBinding.viewFinder.display.rotation
    }

    // Declare and bind preview, capture and analysis use cases
    @SuppressLint("UnsafeOptInUsageError")
    private fun bindCameraUseCases() {

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector - makes assumption that we're only using the back camera
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview. Only using the 4:3 ratio because this is the closest to our models
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
            .build()

        imageAnalyzer =
            ImageAnalysis.Builder().setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetRotation(fragmentCameraBinding.viewFinder.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(
                        backgroundExecutor,
                        imageClassifierHelper::classifyLiveStreamFrame
                    )
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

    @SuppressLint("NotifyDataSetChanged")
    override fun onError(error: String, errorCode: Int) {
        activity?.runOnUiThread {
            Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            classificationResultsAdapter.updateResults(null)
            classificationResultsAdapter.notifyDataSetChanged()

            if (errorCode == ImageClassifierHelper.GPU_ERROR) {
                fragmentCameraBinding.bottomSheetLayout.spinnerDelegate.setSelection(
                    ImageClassifierHelper.DELEGATE_CPU, false
                )
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    override fun onResults(
        resultBundle: ImageClassifierHelper.ResultBundle
    ) {
        activity?.runOnUiThread {
            if (_fragmentCameraBinding != null) {
                // Show result on bottom sheet
                classificationResultsAdapter.updateResults(
                    resultBundle.results.first()
                )
                classificationResultsAdapter.notifyDataSetChanged()
                fragmentCameraBinding.bottomSheetLayout.inferenceTimeVal.text =
                    String.format("%d ms", resultBundle.inferenceTime)
            }
        }
    }
}
